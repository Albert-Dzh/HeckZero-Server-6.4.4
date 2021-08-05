package ru.heckzero.server.user;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import io.netty.util.internal.StringUtil;
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
import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.stream.Collectors;

public class UserManager {                                                                                                                  //yes, this class name ends in 'er' fuck off, Egor ;)
    public static final int ERROR_CODE_NOERROR = 0;
    public static final int ERROR_CODE_WRONG_USER = 1;
    public static final int ERROR_CODE_WRONG_PASSWORD = 2;
    public static final int ERROR_CODE_ANOTHER_CONNECTION = 3;
    public static final int ERROR_CODE_USER_BLOCKED = 4;
    public static final int ERROR_CODE_OLD_VERSION = 5;
    public static final int ERROR_CODE_NEED_KEY = 6;
    public static final int ERROR_CODE_WRONG_KEY = 7;
    public static final int ERROR_CODE_SRV_FAIL = 9;

    public enum UserType {INGAME, ONLINE, IN_BATTLE, CHAT_ON, NPC, HUMAN, POLICE}

    private static final Logger logger = LogManager.getFormatterLogger();
    private static final CopyOnWriteArrayList<User> inGameUsers = new CopyOnWriteArrayList<>();

    private boolean isValidPassword(String pasword) {return isValidSHA1(pasword);}                                                          //check if a user provided password conforms the requirements
    public boolean isValidSHA1(String s) {return s.matches("^[a-fA-F0-9]{40}$");}                                                           //validate a string as a valid SHA1 hash

    public UserManager() { }

    public static List<User> findInGameUsers(UserType type) {
        Predicate<User> isOnline = User::isOnline;
        Predicate<User> isChatOn = User::isChatOn;
        Predicate<User> isNPC = User::isBot;
        Predicate<User> isHuman = isNPC.negate();
        Predicate<User> isCop = User::isCop;
        Predicate<User> isInBattle = User::isInBattle;

        switch (type) {
            case INGAME:                                                                                                                    //all cached users (online and offline that are in a battle)
                return inGameUsers;
            case ONLINE:																							                        //all online users
                return inGameUsers.stream().filter(isOnline).collect(Collectors.toList());
            case IN_BATTLE:																							                        //users that are in a battle
                return inGameUsers.stream().filter(isInBattle).collect(Collectors.toList());
            case CHAT_ON:																							                        //all users having their chats on
                return inGameUsers.stream().filter(isChatOn).collect(Collectors.toList());
            case NPC:																							                            //NPC only
                return inGameUsers.stream().filter(isNPC).collect(Collectors.toList());
            case HUMAN:																							                            //not NPS users
                return inGameUsers.stream().filter(isHuman).collect(Collectors.toList());
            case POLICE:																							                        //cops (clan = police)
                return inGameUsers.stream().filter(isCop).collect(Collectors.toList());
        }
        return new ArrayList<>(0);					        															                	//return an empty list in case of unknown requested user type
    }

    public static User getUser(Channel ch) {                                                                                                //search from cached online users by a game channel
        return ch != null ? findInGameUsers(UserType.ONLINE).stream().filter(u -> ch.equals(u.getGameChannel()) || ch.equals(u.getChatChannel())).findFirst().orElseGet(User::new) : new User();
    }

    public static User getUser(String login) {                                                                                              //search from cached users by login
        User user = findInGameUsers(UserType.INGAME).stream().filter(u -> u.getLogin().equals(login)).findFirst().orElseGet(User::new);
        return user.isEmpty() ? getDbUser(login) : user;
    }

    private static User getDbUser(String login) {                                                                                           //instantiate User from database
        Session session = ServerMain.sessionFactory.openSession();
        Transaction tx = session.beginTransaction();
        Query<User> query = session.createQuery("select u from User u where lower(u.params.login) = lower(:login)", User.class).setParameter("login", login);
        User user;
        try {
            user = query.uniqueResult();
            if (user == null)
                return new User();                                                                                                          //return an existing User having params set from db
            tx.commit();
        } catch (Exception e) {                                                                                                             //some db problem
            logger.error("can't execute query %s: %s", query.getQueryString(), e.getMessage());
            tx.rollback();
            return new User();                                                                                                              //return null in case of SQL exception
        }finally {
            session.close();
        }
        return user;
    }

    public void loginUserChat(Channel ch, String ses, String login) {
        logger.info("processing <CHAT/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        logger.info("phase 0 validating provided chat user credentials");
        if (ses == null || login == null) {                                                                                                 //login or sess attributes are missed, this is abnormal. closing the channel
            logger.info("no credentials are provided, seems this is an initial chat session request");
            ch.writeAndFlush("<CHAT/>");
            return;
        }

        logger.info("phase 1 checking if a corresponding user with login '%s' is online and ses key is valid", login);
        User user = findInGameUsers(UserType.ONLINE).stream().filter(u -> u.getLogin().equals(login)).findFirst().orElseGet(User::new);
        if (!(ses.equals(user.getGameChannel().attr(AttributeKey.valueOf("encKey")).get()) && login.equals(user.getLogin()))) {
            logger.warn("can't find an online user to associate the chat channel with, closing the channel");
            ch.close();
            return;
        }
        logger.info("phase 2 found user %s to associate the chat channel with, switching it's chat on", user.getLogin());
        user.chatOn(ch);
        ch.attr(AttributeKey.valueOf("chStr")).set("user " + user.getLogin() + " (chat)");
        return;
    }

    public void loginUser(Channel ch, String login, String userCryptedPass) {                                                               //check if the user can login and set it online
        logger.info("processing <LOGIN/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        logger.info("phase 0 validating received user credentials");
        if (login == null || userCryptedPass == null) {                                                                                     //login or password attributes are missed, this is abnormal. closing the channel
            ch.close();
            logger.warn("no valid login or password attributes exist in a received message, closing connection with %s", ch.attr(AttributeKey.valueOf("chStr")).get());
            return;
        }

        if (!isValidLogin(login) || !isValidPassword(userCryptedPass)) {                                                                    //login or password attributes are invalid, this is illegal
            ch.close();
            logger.warn("login or password don't conform the requirement, closing connection with %s", ch.attr(AttributeKey.valueOf("chStr")).get());
            return;
        }

        logger.info("phase 1 checking if a user '%s' exists and can be set online", login);
        User user = getUser(login);
        if (user == null) {                                                                                                                 //SQL Exception was thrown while getting user data from a db
            logger.error("can't get user data from database by login '%s' due to a DB error", login);
            String errMsg = String.format("<ERROR code = \"%d\" />", ERROR_CODE_SRV_FAIL);
            ch.writeAndFlush(errMsg);
            ch.close();
            return;
        }
        if (user.isEmpty()) {                                                                                                               //this an empty User instance, which means the user has not been found in a database
            logger.info("user with login '%s' does not exist", login);
            String errMsg = String.format("<ERROR code = \"%d\" />", ERROR_CODE_WRONG_USER);                                                //user does not exist
            ch.writeAndFlush(errMsg);
            ch.close();
            return;
        }
        logger.info("phase 2 checking user '%s' credentials", user.getLogin());
        String userClearPass = user.getParamStr(User.Params.password);                                                                      //user clear password from database
        String serverCryptedPass = encrypt((String)ch.attr(AttributeKey.valueOf("encKey")).get(), userClearPass);                           //encrypt user password using the same algorithm as a client does
        if (!serverCryptedPass.equals(userCryptedPass)) {                                                                                   //passwords mismatch detected
            logger.info("wrong password for user '%s'", user.getLogin());
            String errMsg = String.format("<ERROR code = \"%d\" />", ERROR_CODE_WRONG_PASSWORD);
            ch.writeAndFlush(errMsg);
            ch.close();
            return;
        }
        if (!user.getParamStr(User.Params.dismiss).isBlank()) {                                                                             //user is blocked (dismiss is not empty)
            logger.info("user '%s' is banned, reason: '%s'", user.getLogin(), user.getParamStr(User.Params.dismiss));
            String errMsg = String.format("<ERROR code = \"%d\" txt=\"%s\" />", ERROR_CODE_USER_BLOCKED, user.getParamStr(User.Params.dismiss));
            ch.writeAndFlush(errMsg);
            ch.close();
        }
        logger.info("phase 3 checking if user '%s' is already online", user.getLogin());
        if (user.isOnline()) {                                                                                                              //user is already online
            logger.info("user '%s' is already online, disconnecting it", user.getLogin());
            user.sendMsg(String.format("<ERROR code = \"%d\" />", ERROR_CODE_ANOTHER_CONNECTION));
            user.offline();
        }
        user.online(ch);                                                                                                                    //perform initial procedures to set user online
        inGameUsers.addIfAbsent(user);
        logger.info("phase 4 All done! User '%s' has been set online with socket address %s", user.getLogin(), ch.attr(AttributeKey.valueOf("chStr")).get());
        ch.attr(AttributeKey.valueOf("chStr")).set("user " + user.getLogin());                                                              //replace a client representation string to 'user <login>' instead of IP:port
        String resultMsg = String.format("<OK l=\"%s\" ses=\"%s\"/>", user.getLogin(), ch.attr(AttributeKey.valueOf("encKey")).get());      //send OK with a chat auth key in ses attribute (using already existing key)
        user.sendMsg(resultMsg);                                                                                                            //now we can use user native send function
        return;
    }

    public void logoutUser(User u) {


        return;
    }

    public boolean isValidLogin(String login) {
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

    private String encrypt(String key, String msg) {
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
