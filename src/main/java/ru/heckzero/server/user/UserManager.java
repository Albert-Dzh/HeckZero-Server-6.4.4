package ru.heckzero.server.user;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.query.Query;
import ru.heckzero.server.Defines;
import ru.heckzero.server.ServerMain;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UserManager {                                                                                                                  //yes, this class name ends in 'er' fuck off, Egor ;)
    public enum ErrCodes {NOERROR, WRONG_USER, WRONG_PASSWORD, ANOTHER_CONNECTION, USER_BLOCKED, OLD_VERSION, NEED_KEY, WRONG_KEY, ANOTHER_WINDOW, SRV_FAIL} //Error codes for the client on unsuccessful login
    public enum UserType {CACHED, ONLINE_GAME, ONLINE_CHAT, ONLINE_GAME_OR_CHAT, IN_BATTLE, NPC, HUMAN, POLICE}

    private static final Logger logger = LogManager.getFormatterLogger();
    private static final CopyOnWriteArrayList<User> cachedUsers = new CopyOnWriteArrayList<>();

    private static boolean isValidPassword(String pasword) {return isValidSHA1(pasword);}                                                   //check if a user provided password conforms the requirements
    public static boolean isValidSHA1(String s) {return s.matches("^[a-fA-F0-9]{40}$");}                                                    //validate a string as a valid SHA1 hash

    public UserManager() { }

    public static List<User> getCachedUsers(UserType type) {
        Predicate<User> isOnlineGame = User::isOnlineGame;
        Predicate<User> isOnlineChat = User::isOnlineChat;
        Predicate<User> isOnlineGameOrChat = isOnlineGame.or(isOnlineChat);
        Predicate<User> isNPC = User::isBot;
        Predicate<User> isHuman = isNPC.negate();
        Predicate<User> isCop = User::isCop;
        Predicate<User> isInBattle = User::isInBattle;

        return switch (type) {
            case CACHED -> cachedUsers;
            case ONLINE_GAME -> cachedUsers.stream().filter(isOnlineGame).collect(Collectors.toList());		                                //all online users game
            case ONLINE_CHAT -> cachedUsers.stream().filter(isOnlineChat).collect(Collectors.toList());					                    //all online users chat
            case ONLINE_GAME_OR_CHAT -> cachedUsers.stream().filter(isOnlineGameOrChat).collect(Collectors.toList());		                //all online users with game or chat channel
            case IN_BATTLE -> cachedUsers.stream().filter(isInBattle).collect(Collectors.toList());				                            //users that are in a battle
            case NPC -> cachedUsers.stream().filter(isNPC).collect(Collectors.toList());												    //NPC only
            case HUMAN -> cachedUsers.stream().filter(isHuman).collect(Collectors.toList());					                            //not NPS users
            case POLICE -> cachedUsers.stream().filter(isCop).collect(Collectors.toList());							                        //cops (clan = police)
        };
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
        logger.info("phase 0 validating user chat credentials");
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

        logger.info("phase 2 found user %s to associate chat channel with. checking if it's chat channel is already online", user.getLogin());
        if (user.isOnlineChat()) {
            logger.info("user %s chat socket is already online, disconnecting it", user.getLogin());
            user.getChatChannel().deregister().addListener(ChannelFutureListener.CLOSE);
            logger.info("user %s chat channel disconnected, continue further processing");
        }

        logger.info("phase 3 setting a chat channel for user %s and switching it's chat on", user.getLogin());
        ch.attr(AttributeKey.valueOf("chStr")).set("user " + user.getLogin() + " (chat)");
        user.chatOn(ch);
        logger.info("phase 4 All done! User %s has it chat on", user.getLogin());
        return;
    }

    public static void loginUser(Channel ch, String login, String userCryptedPass) {                                                        //check if the user can login and set it online
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
            String errMsg = String.format("<ERROR code = \"%d\" />", ErrCodes.SRV_FAIL.ordinal());
            ch.writeAndFlush(errMsg).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (user.isEmpty()) {                                                                                                               //this an empty User instance, which means the user has not been found in a database
            logger.info("user '%s' does not exist", login);
            String errMsg = String.format("<ERROR code = \"%d\" />", ErrCodes.WRONG_USER.ordinal());                                        //user does not exist
            ch.writeAndFlush(errMsg).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        logger.info("phase 2 checking user '%s' credentials", user.getLogin());
        String userClearPass = user.getParamStr(User.Params.password);                                                                      //the user clear password from database
        String serverCryptedPass = encrypt((String)ch.attr(AttributeKey.valueOf("encKey")).get(), userClearPass);                           //encrypt user password using the same algorithm as a client does
        if (!serverCryptedPass.equals(userCryptedPass)) {                                                                                   //passwords mismatch detected
            logger.info("wrong password for user '%s'", user.getLogin());
            String errMsg = String.format("<ERROR code = \"%d\" />", ErrCodes.WRONG_PASSWORD.ordinal());
            ch.writeAndFlush(errMsg).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        if (!user.getParamStr(User.Params.dismiss).isBlank()) {                                                                             //user is blocked (dismiss is not empty)
            logger.info("user '%s' is banned, reason: '%s'", user.getLogin(), user.getParamStr(User.Params.dismiss));
            String errMsg = String.format("<ERROR code = \"%d\" txt=\"%s\" />", ErrCodes.USER_BLOCKED.ordinal(), user.getParamStr(User.Params.dismiss));
            ch.writeAndFlush(errMsg).addListener(ChannelFutureListener.CLOSE);
            return;
        }
        logger.info("phase 3 checking if user '%s' is already online", user.getLogin());
        Channel gameChannel = user.getGameChannel();
        if (gameChannel != null) {                                                                                                          //user is already online by it's game channel
            logger.info("user '%s' is already online, disconnecting it", user.getLogin());

            user.sendMsg(String.format("<ERROR code = \"%d\" />", ErrCodes.ANOTHER_CONNECTION.ordinal()));                                  //an error message that will be send to existing online user
            gameChannel.close();                                                                                                            //close the existing online user game channel
            try {
                CountDownLatch disconnectLatch = (CountDownLatch)gameChannel.attr(AttributeKey.valueOf("disconnectLatch")).get();
                if (!disconnectLatch.await(3000, TimeUnit.MILLISECONDS)) {                                                                  //wait max 3 seconds for the user disconnection
                    logger.error("timeout waiting for disconnect CountDownLatch, pay attention to it!!!");
                }
            } catch (InterruptedException e) {
                logger.error("waiting for CountDownLatch to release was interrupted: %s", e.getMessage());
                String errMsg = String.format("<ERROR code = \"%d\" />", ErrCodes.SRV_FAIL.ordinal());
                ch.writeAndFlush(errMsg).addListener(ChannelFutureListener.CLOSE);
                return;                                                                                                                     //stop further login processing
            }
        }

        user.online(ch);                                                                                                                    //perform initial procedures to set the user online
        cachedUsers.addIfAbsent(user);
        logger.info("phase 4 All done! User '%s' has been set online with socket address %s", user.getLogin(), ch.attr(AttributeKey.valueOf("chStr")).get());
        ch.attr(AttributeKey.valueOf("chStr")).set("user " + user.getLogin());                                                              //replace a client representation string to 'user <login>' instead of IP:port
        String resultMsg = String.format("<OK l=\"%s\" ses=\"%s\"/>", user.getLogin(), ch.attr(AttributeKey.valueOf("encKey")).get());      //send OK with a chat auth key in ses attribute (using already existing key)
        user.sendMsg(resultMsg);                                                                                                            //now we can use user native send function
        return;
    }

    public static void logoutUser(Channel ch) {
        logger.info("processing channel inactive event from %s", ch.attr(AttributeKey.valueOf("sockStr")).get());

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
        logger.debug("found online user %s which has this channel set as a %s channel, disconnecting the channel from the user", user.getLogin(), chType);

        switch (chType) {
            case GAME -> user.offline();                                                                                                    //detach user game socket
            case CHAT -> user.chatOff();                                                                                                    //detach user chat socket
        }
        return;
    }

    private static void cleanCachedUsers() {
        logger.info("removing rotten users from the cache");
        Predicate<User> isRotten = (u) -> u.isOffline() && !u.isInBattle() && u.getParamInt(User.Params.lastlogout) - Instant.now().getEpochSecond() > Defines.CACHE_KEEP_TIME;
        cachedUsers.removeIf(isRotten);
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
        byte [] s_block = {
                        0, 30, 30, 28, 28, 37, 37,  9,  9, 18, 18, 34, 34, 35,                                                              // the 1st chain of pairs' transpositions
                        1, 26, 26, 32, 32, 22, 22, 23, 23, 21, 21, 14, 14, 33, 33, 16,                                                      // the 2nd chain of pairs' transpositions
                        16,  7,  7,  4,  4,  2,  2, 24, 24, 29, 29, 20, 20,  8,  8,  5,
                        5, 15, 15, 17, 17, 36, 36,  6,                                                                                      // the 3rd chain of pairs' transpositions
                        3, 39, 39, 12, 12, 10, 10, 27, 27, 25,                                                                              // the 4th chain of pairs' transpositions
                        11, 38, 38, 13, 13, 19, 19, 31                                                                                      // the 5th chain of pairs' transpositions
                };

        String result = StringUtil.EMPTY_STRING;
        try {
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");                                                                        // stage a (get SHA-1 encryptor)

            String passKey = msg.substring(0, 1) + key.substring(0, 10) + msg.substring(1) + key.substring(10);                             // stage b (collect the string)
            char[] shuffled_SHA1 = ByteBufUtil.hexDump(sha1.digest(passKey.getBytes(StandardCharsets.UTF_8))).toUpperCase().toCharArray();  // stage c (cipher the string with SHA-1)

            for (byte i = 0; i < s_block.length; i += 2) {                                                                                   // stage d (shuffle result of ciphering)
                char tmp = shuffled_SHA1[s_block[i]];
                shuffled_SHA1[s_block[i]] = shuffled_SHA1[s_block[i + 1]];
                shuffled_SHA1[s_block[i + 1]] = tmp;
            }
            result = new String(shuffled_SHA1);
        }
        catch (NoSuchAlgorithmException e) {
            logger.error("encrypt: %s", e.getMessage());
        }
        return result;
    }
}
