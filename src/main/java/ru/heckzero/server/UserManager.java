package ru.heckzero.server;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.util.internal.StringUtil;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;
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

    private enum inGameUserType {CACHED, ONLINE, IN_BATTLE, IN_GAME, CHAT_ON, NPC, HUMAN, POLICE}

    private static final Logger logger = LogManager.getFormatterLogger();
    private static final CopyOnWriteArrayList<User> inGameUsers = new CopyOnWriteArrayList<>();

    private boolean isValidPassword(String pasword) {return isValidSHA1(pasword);}                                                          //validate if a user provided password conforms the requirements
    public boolean isValidSHA1(String s) {return s.matches("^[a-fA-F0-9]{40}$");}                                                           //validate a string as a valid SHA1 hash

    public UserManager() { }

    private static List<User> findInGameUsers(inGameUserType type) {
        Predicate<User> isOnline = User::isOnline;
        Predicate<User> isChatOn = User::isChatOn;
        Predicate<User> isNPC = User::isBot;
        Predicate<User> isHuman = isNPC.negate();
        Predicate<User> isCop = User::isCop;
        Predicate<User> isInBattle = User::isInBattle;
        Predicate<User> isInGame = isOnline.or(isInBattle);

        switch (type) {
            case ONLINE:																							                        //all online users
                return inGameUsers.stream().filter(isOnline).collect(Collectors.toList());
            case IN_BATTLE:																							                        //users that are in a battle
                return inGameUsers.stream().filter(isInBattle).collect(Collectors.toList());
            case IN_GAME:
                return inGameUsers.stream().filter(isInGame).collect(Collectors.toList());
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

    public static User getUser(Channel ch) {                                                                                                //do only online users search
        return findInGameUsers(inGameUserType.ONLINE).stream().filter(u -> u.getGameChannel().equals(ch)).findFirst().orElseGet(User::new);
    }

    public static User getUser(String login) {                                                                                              //try to find a user from the cached inGame, then from db
        User user = findInGameUsers(inGameUserType.ONLINE).stream().filter(u -> u.getParam("login").equals(login)).findFirst().orElseGet(User::new);
        return user.isEmpty() ? getDbUser(login) : user;
    }

    private static User getDbUser(String login) {                                                                                           //instantiate User by getting it's data from db
        String sql = "select * from users where login ILIKE ?";
        try {
            Map<String, Object> userDbParams = DbUtil.query(sql, new MapHandler(), login);
            if (userDbParams == null)                                                                                                       //user was not found in db
                return new User();                                                                                                          //return a stub - empty User instance
            logger.info("userDbParams: %s", userDbParams);
            return new User(userDbParams);                                                                                                  //return an existing User having params set from db
        } catch (SQLException e) {                                                                                                          //some db problem
            logger.error("can't execute query %s: %s", sql, e.getMessage());
            return null;                                                                                                                    //return null in case of SQL exception
        }
    }

    public void loginUser(Channel ch, String login, String userCryptedPass) {                                                               //check if the user can login and set it online
        logger.info("phase 0 validating received user credentials");
        if (login == null || userCryptedPass == null) {                                                                                     //login or password attributes are missed, this is abnormal. close the channel silently
            ch.close();
            logger.warn("no valid login or password attributes exist in a received message, closing connection with %s", ch.attr(ServerMain.sockAddrStr).get());
            return;
        }

        if (!isValidLogin(login) || !isValidPassword(userCryptedPass)) {                                                                    //login or password attributes are invalid, this is illegal
            ch.close();
            logger.warn("login or password don't conform the requirement, closing connection with %s", ch.attr(ServerMain.sockAddrStr).get());
            return;
        }

        logger.info("phase 1 checking if a user '%s' exists", login);
        User user = getUser(login);
        if (user == null) {                                                                                                                 //SQL Exception was thrown while getting user data from a db
            logger.error("can't get user data from database by login '%s' due to DB error", login);
            String errMsg = String.format("<ERROR code = \"%d\" />", ERROR_CODE_SRV_FAIL);
            ch.writeAndFlush(errMsg);
            ch.close();
            return;
        }
        if (user.isEmpty()) {                                                                                                               //this an empty User instance, which means the user has not been found in a database
            logger.info("user with login '%s' does not exist", login);
            String errMsg = String.format("<ERROR code = \"%d\" />", ERROR_CODE_WRONG_USER);
            ch.writeAndFlush(errMsg);
            ch.close();
            return;
        }
        logger.info("phase 2 checking user '%s' credentials", user.getParam("login"));
        String userClearPass = user.getParam("password");                                                                                   //user clear password from database
        String serverCryptedPass = encrypt(ch.attr(ServerMain.encKey).get(), userClearPass);                                                //encrypt user password using the same algorithm as a client does
        if (!serverCryptedPass.equals(userCryptedPass)) {                                                                                   //passwords mismatch detected
            logger.info("wrong password for user '%s'", user.getParam("login"));
            String errMsg = String.format("<ERROR code = \"%d\" />", ERROR_CODE_WRONG_PASSWORD);
            ch.writeAndFlush(errMsg);
            ch.close();
            return;
        }
        if (!user.getParam("dismiss").isBlank()) {                                                                                          //user is blocked (dismiss is not empty)
            logger.info("user '%s' is banned, reason: '%s'", user.getParam("login"), user.getParam("dismiss"));
            String errMsg = String.format("<ERROR code = \"%d\" txt=\"%s\" />", ERROR_CODE_USER_BLOCKED, user.getParam("dismiss"));
            ch.writeAndFlush(errMsg);
            ch.close();
        }
        logger.info("phase 3 checking if user '%s' is already online", user.getParam("login"));
        if (user.isOnline()) {                                                                                                              //user is already online
            logger.info("user '%s' is already online, disconnecting user from %s", user.getGameChannel().attr(ServerMain.sockAddrStr).get());
            user.sendMsg(String.format("<ERROR code = \"%d\" />", ERROR_CODE_ANOTHER_CONNECTION)).syncUninterruptibly();
            user.setOffline();
        }
        user.setOnline(ch);
        logger.info("phase 4 all done, user '%s' has been set online with socket address %s", user.getParam("login"), ch.attr(ServerMain.sockAddrStr).get());

        String resultMsg = String.format("<OK l=\"%s\" ses=\"s\"/>", user.getParam("login"), RandomStringUtils.randomAlphanumeric(Defines.ENCRYPTION_KEY_SIZE));
        ch.writeAndFlush(resultMsg);
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
