package ru.heckzero.server.user;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.TimeUnit;
import java.util.function.Predicate;
import java.util.stream.Collectors;
import java.util.stream.IntStream;

public class UserManager {                                                                                                                  //yes, this class name ends in 'er' fuck off, Egor ;)
    public enum ErrCodes {NOERROR, WRONG_USER, WRONG_PASSWORD, ANOTHER_CONNECTION, USER_BLOCKED, OLD_VERSION, NEED_KEY, WRONG_KEY, ANOTHER_WINDOW, SRV_FAIL} //Error codes for the client on unsuccessful login
    public enum UserType {CACHED, IN_GAME, ONLINE_GAME, ONLINE_CHAT, ONLINE_GAME_OR_CHAT, IN_BATTLE, NPC, HUMAN, POLICE}

    private static final Logger logger = LogManager.getFormatterLogger();
    private static final CopyOnWriteArrayList<User> cachedUsers = new CopyOnWriteArrayList<>();

    private static boolean isValidPassword(String pasword) {return isValidSHA1(pasword);}                                                   //check if a user provided password conforms the requirements
    private static boolean isValidSHA1(String s) {return s.matches("^[a-fA-F0-9]{40}$");}                                                   //validate a string has a valid SHA1 hash format

    static {
        ServerMain.mainScheduledExecutor.scheduleWithFixedDelay(UserManager::purgeCachedUsers, 60L, 60L , TimeUnit.SECONDS);                //purging offline users from the cachedUsers
    }
    public UserManager() { }

    public static List<User> getCachedUsers(UserType type) {
        Predicate<User> isOnlineGame = User::isOnlineGame;
        Predicate<User> isOnlineChat = User::isOnlineChat;
        Predicate<User> isOnlineGameOrChat = isOnlineGame.or(isOnlineChat);
        Predicate<User> isNPC = User::isBot;
        Predicate<User> isHuman = isNPC.negate();
        Predicate<User> isCop = User::isCop;
        Predicate<User> isInBattle = User::isInBattle;
        Predicate<User> isInGame = User::isInGame;

        return switch (type) {
            case CACHED -> cachedUsers;
            case IN_GAME -> cachedUsers.stream().filter(isInGame).collect(Collectors.toList());
            case ONLINE_GAME -> cachedUsers.stream().filter(isOnlineGame).collect(Collectors.toList());		                                //all online users game
            case ONLINE_CHAT -> cachedUsers.stream().filter(isOnlineChat).collect(Collectors.toList());					                    //all online users chat
            case ONLINE_GAME_OR_CHAT -> cachedUsers.stream().filter(isOnlineGameOrChat).collect(Collectors.toList());		                //all online users with game or chat channel
            case IN_BATTLE -> cachedUsers.stream().filter(isInBattle).collect(Collectors.toList());				                            //users that are in a battle
            case NPC -> cachedUsers.stream().filter(isNPC).collect(Collectors.toList());												    //NPC only
            case HUMAN -> cachedUsers.stream().filter(isHuman).collect(Collectors.toList());					                            //not NPS users
            case POLICE -> cachedUsers.stream().filter(isCop).collect(Collectors.toList());							                        //cops (clan = police)
        };
    }
    private static boolean areInSameLoc(User user1, User user2) {                                                                           //are users in a same location
        return user1.getParamInt(User.Params.X) == user2.getParamInt(User.Params.X) && user1.getParamInt(User.Params.Y) == user2.getParamInt(User.Params.Y) && user1.getParamInt(User.Params.Z) == user2.getParamInt(User.Params.Z) ;
    }
    private static boolean areInSameRoom(User user1, User user2) {                                                                          //are users in a same room
        return areInSameLoc(user1, user2) && user1.getParamInt(User.Params.ROOM) == user2.getParamInt(User.Params.ROOM);
    }

    public static List<User> getRoomMates(User user) {                                                                                      //get a list of in game users that are in a same room
        return getCachedUsers(UserType.IN_GAME).stream().filter(u -> UserManager.areInSameRoom(u, user)).collect(Collectors.toList());
    }

    public static User getOnlineUserGame(Channel ch) {                                                                                      //search from cached online users by a game channel
        return getCachedUsers(UserType.ONLINE_GAME).stream().filter(u -> ch.equals(u.getGameChannel())).findFirst().orElseGet(User::new);
    }
    public static User getOnlineUserChat(Channel ch) {                                                                                      //search from cached online users by a chat channel
        return getCachedUsers(UserType.ONLINE_CHAT).stream().filter(u -> ch.equals(u.getChatChannel())).findFirst().orElseGet(User::new);
    }
    public static User getOnlineUser(Channel ch) {                                                                                          //search from cached online users by a game or chat channel
        return getCachedUsers(UserType.ONLINE_GAME_OR_CHAT).stream().filter(u -> ch.equals(u.getGameChannel()) || ch.equals(u.getChatChannel())).findFirst().orElseGet(User::new);
    }

    public static User getOnlineUserGame(String login) {                                                                                    //search from cached online game users by login
        return getCachedUsers(UserType.ONLINE_GAME).stream().filter(u -> u.getLogin().equals(login)).findFirst().orElseGet(User::new);
    }
    public static User getUser(String login) {                                                                                              //search from all cached users by login
        return cachedUsers.stream().filter(u -> u.getLogin().equals(login)).findFirst().orElseGet(() -> getDbUser(login));
    }

    private static User getDbUser(String login) {                                                                                           //instantiate a User from a database
        Session session = ServerMain.sessionFactory.openSession();
        Transaction tx = session.beginTransaction();
        Query<User> query = session.createQuery("select u from User u where lower(u.params.login) = lower(:login)", User.class).setParameter("login", login);
        try (session) {
            User user = query.uniqueResult();
            tx.commit();
            return (user == null) ? new User() : user;                                                                                      //return a user or an empty user if none found
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't execute query %s: %s", query.getQueryString(), e.getMessage());
            tx.rollback();
        }
        return null;                                                                                                                        //in case of database error
    }

    public static void loginUserChat(Channel ch, String ses, String login) {
        logger.info("processing <CHAT/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        logger.info("phase 0 validating provided chat credentials");
        if (ses == null || login == null) {                                                                                                 //login or sess attributes are missed, this is abnormal. closing the channel
            logger.info("no credentials are provided, seems this is an initial chat request");
            ch.writeAndFlush("<CHAT/>");
            return;
        }

        logger.info("phase 1 checking if a corresponding game user with login '%s' is online and ses key is valid", login);
        User user = getOnlineUserGame(login);
        if (user.isEmpty() || !(ses.equals(user.getGameChannel().attr(AttributeKey.valueOf("encKey")).get()))) {
            logger.warn("can't find an online user to associate the chat channel with, closing the channel");
            ch.close();
            return;
        }

        logger.info("phase 2 found online user '%s' to associate chat channel with, checking if it's chat channel is already online", user.getLogin());
        synchronized (user) {
            while (user.isOnlineChat()) {
                if (user.getChatChannel().isActive()) {
                    logger.info("user '%s' already has his chat channel online and active, disconnecting it", user.getLogin());
                    user.disconnectChat();                                                                                                  //send error message and close the connection
                }else
                    logger.info("user '%s' still has his chat channel online but inactive", user.getLogin());

                logger.info("waiting for the user '%s' chat channel to get offline", user.getLogin());
                try {
                    user.wait();
                } catch (InterruptedException e) {
                    logger.error("exception while waiting for user %s chat channel to get offline, stopping this login attempt and closing the incoming channel", user.getLogin());
                    ch.close();
                    return;
                }
            }
            logger.info("phase 3 checking if user '%s' is still online by it's game channel", user.getLogin());                             //the user might be disconnected by game channel before we locked the monitor
            if (!user.isOnlineGame()) {
                logger.warn("user %s seemed already disconnected from it's game channel, won't continue chat registration, closing the channel");
                ch.close();
                return;
            }
            user.onlineChat(ch);                                                                                                            //associate the channel as user chat channel
            logger.info("phase 4 All done! User '%s' chat channel has been set online with address %s", user.getLogin(), ch.attr(AttributeKey.valueOf("sockStr")).get());
        }
        return;
    }

    public static void loginUser(Channel ch, String login, String userCryptedPass) {                                                        //check if the user can log in and set him online
        logger.info("processing <LOGIN/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        logger.info("phase 0 validating received user credentials");
        if (login == null || userCryptedPass == null) {                                                                                     //login or password attributes are missed, this is abnormal. closing the channel
            logger.warn("no valid login or password attributes exist in a received message, closing connection with %s", ch.attr(AttributeKey.valueOf("chStr")).get());
            ch.close();
            return;
        }
        if (!isValidLogin(login) || !isValidPassword(userCryptedPass)) {                                                                    //login or password attributes are invalid
            logger.warn("login or password don't conform the requirement, closing connection with %s", ch.attr(AttributeKey.valueOf("chStr")).get());
            ch.close();
            return;
        }

        logger.info("phase 1 checking if a user '%s' exists and can be set online", login);
        User user = getUser(login);                                                                                                         //get a user by login from a list of cached users or from database
        if (user == null) {                                                                                                                 //SQL Exception was thrown while getting user data from a db
            logger.error("can't get user data from database by login '%s' due to a DB error", login);
            String errMsg = String.format("<ERROR code=\"%d\"/>", ErrCodes.SRV_FAIL.ordinal());
            ch.writeAndFlush(errMsg).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (user.isEmpty()) {                                                                                                               //this an empty User instance, which means the user has not been found in a database
            logger.info("user '%s' does not exist", login);
            String errMsg = String.format("<ERROR code=\"%d\"/>", ErrCodes.WRONG_USER.ordinal());                                           //user does not exist
            ch.writeAndFlush(errMsg).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        logger.info("phase 2 checking user '%s' credentials", user.getLogin());
        String userClearPass = user.getParamStr(User.Params.password);                                                                      //the user clear password from database
        String serverCryptedPass = encrypt((String)ch.attr(AttributeKey.valueOf("encKey")).get(), userClearPass);                           //encrypt user password using the same algorithm as a client does
        if (!StringUtils.equals(serverCryptedPass, userCryptedPass)) {                                                                      //passwords don't match
            logger.info("wrong password for user '%s'", user.getLogin());
            String errMsg = String.format("<ERROR code=\"%d\"/>", ErrCodes.WRONG_PASSWORD.ordinal());
            ch.writeAndFlush(errMsg).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        logger.info("phase 3 checking if user '%s' if blocked", user.getLogin());
        if (!user.getParamStr(User.Params.dismiss).isBlank()) {                                                                             //user is blocked (dismiss is not empty)
            logger.info("user '%s' is blocked, reason: '%s'", user.getLogin(), user.getParamStr(User.Params.dismiss));
            String errMsg = String.format("<ERROR code=\"%d\" txt=\"%s\" />", ErrCodes.USER_BLOCKED.ordinal(), user.getParamStr(User.Params.dismiss));
            ch.writeAndFlush(errMsg).addListener(ChannelFutureListener.CLOSE);
            return;
        }

        logger.info("phase 4 checking if user '%s' is already online", user.getLogin());
        synchronized (user) {
            while (user.isOnlineGame()) {                                                                                                   //gameChannel != null, we might receive notify from offlineChat() instead of offlineGame(), so we have to wait again
                if (user.getGameChannel().isActive()) {                                                                                     //channel is active, we have to send an error message and close the channel
                    logger.info("user %s is already online with game channel online and active, disconnecting it", user.getLogin());
                    user.disconnect(String.format("<ERROR code = \"%d\" />", ErrCodes.ANOTHER_CONNECTION.ordinal()));                       //send error message and close the game channel connection
                }else                                                                                                                       //channel is inactive (closed)
                    logger.info("user '%s' still has his game channel online but inactive", user.getLogin());

                logger.info("waiting for the user '%s' game channel to get offline", user.getLogin());                                      //wait for the offlineGame() to finish and sends notifyAll() to awake us
                try {
                    user.wait();                                                                                                            //wait for notify() from offlineGame(), releasing the monitor
                } catch (InterruptedException e) {
                    logger.error("exception while waiting for user '%s' to get offline, stopping this login attempt, and closing incoming channel", user.getLogin());
                    ch.close();
                    return;
                }
            }
            user.onlineGame(ch);                                                                                                            //perform initial procedures to set the user game channel online
            cachedUsers.addIfAbsent(user);
            logger.info("phase 5 All done! User '%s' game channel has been set online with address %s", user.getLogin(), ch.attr(AttributeKey.valueOf("sockStr")).get());
        }
        return;
    }

    public static void logoutUser(Channel ch) {
        logger.debug("processing channel inactive event from %s", ch.attr(AttributeKey.valueOf("sockStr")).get());

        User.ChannelType chType = (User.ChannelType)ch.attr(AttributeKey.valueOf("chType")).get();                                          //get a channel type (GAME, CHAT)
        if (chType == User.ChannelType.NOUSER) {                                                                                            //this channel is not associated with any online user
            logger.info("this channel is not associated with any user, nothing to do");
            return;
        }
        User user = getOnlineUser(ch);                                                                                                      //get an online user having this channel set as a game or chat
        if (user.isEmpty()) {
            logger.error("%s is a %s channel but I can't find an online user associated with it. tis is an abnormal situation. Take a look at it!", ch.attr(AttributeKey.valueOf("sockStr")).get(), chType.name());
            return;
        }
        logger.debug("found online user '%s' which has this channel set as a %s channel, disconnecting the channel", user.getLogin(), chType);
        switch (chType) {
            case GAME -> user.offlineGame();                                                                                                //perform user game channel logout procedure
            case CHAT -> user.offlineChat();                                                                                                //perform user chat channel logout procedure
        }
        return;
    }

    private static void purgeCachedUsers() {                                                                                                //remove offline users from the cache. the user must not be in a battle and must be offline for a defined amount of time
        logger.debug("purging rotten users from the cache");
        Predicate<User> isRotten = (u) -> !u.isInGame() && u.getParamInt(User.Params.lastlogout) - Instant.now().getEpochSecond() > ServerMain.hzConfiguration.getLong("ServerSetup.UsersCachePurgeTime", ServerMain.DEF_USER_CACHE_TIMEOUT);
        cachedUsers.removeIf(isRotten);                                                                                                     //remove users matching the predicate from cashedUsers
        return;
    }

    private static boolean isValidLogin(String login) {
        int len = StringUtils.length(login);																				            	//null safe string length calculation
        String en_c = "abcdefghijklmnopqrstuvwxyzABCDEFGHIJKLMNOPQRSTUVWXYZ";
        String ru_c = "абвгдежзийклмнопрстуфхцчшщьыъэюяАБВГДЕЖЗИЙКЛМНОПРСТУФХЦЧШЩЬЫЪЭЮЯЁё";
        String symbolChars = " -_*0123456789";
        String validChars = ru_c + en_c + symbolChars;										        										//the set of the allowed symbols

        if  (len < 3  ||  len > 16 || login.startsWith(" ")  || login.endsWith(" ")  || login.contains("  "))								//login is too short or too long or contains spaces at a wrong places
            return false;
        if (login.chars().anyMatch((ch) -> validChars.indexOf(ch) == -1)) 																	//login contains illegal characters
            return false;

        long numEng = login.chars().filter((ch) -> en_c.indexOf(ch) != -1).distinct().count();												//number of unique english chars in login
        long numRus = login.chars().filter((ch) -> ru_c.indexOf(ch) != -1).distinct().count();												//number of unique russian chars in login
        if (numEng > 0 && numRus > 0)																						                //В имени разрешено использовать только буквы одного алфавита русского или английского. Нельзя смешивать.
            return false;
        if (numEng < 2 && numRus < 2)  																					                	//login must contains at least 2 unique English or Russian characters В имени обязательно должны содержаться хотя бы две разные буквы",
            return false;
        return true;
    }

    private static String encrypt(String key, String msg) {
        int [] shuffle_indexes = {35, 6, 4, 25, 7, 8, 36, 16, 20, 37, 12, 31, 39, 38, 21, 5, 33, 15, 9, 13, 29, 23, 32, 22, 2, 27, 1, 10, 30, 24, 0, 19, 26, 14, 18, 34, 17, 28, 11, 3};
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");                                                                        //get a SHA-1 encryptor
            String passKey = msg.charAt(0) + key.substring(0, 10) + msg.substring(1) + key.substring(10);                                   //compose the string to be hashed
            String passKey_SHA1 = ByteBufUtil.hexDump(sha1.digest(passKey.getBytes(StandardCharsets.UTF_8))).toUpperCase();                 //cipher the string with SHA-1
            return IntStream.range(0, 40).mapToObj(i -> passKey_SHA1.charAt(ArrayUtils.indexOf(shuffle_indexes, i))).map(String::valueOf).collect(Collectors.joining());
        } catch (NoSuchAlgorithmException e) {
            logger.error("encrypt: %s", e.getMessage());
        }
        return null;
    }
}
