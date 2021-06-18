package ru.heckzero.server;

import io.netty.buffer.ByteBufUtil;
import io.netty.channel.Channel;
import io.netty.handler.codec.xml.XmlAttribute;
import io.netty.handler.codec.xml.XmlElementStart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.StandardCharsets;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;

public class UserManager {                                                                                                                   //yes, this class name ends in 'er' fuck off, Egor ;)
    public static final int ERROR_CODE_NOERROR = 0;
    public static final int ERROR_CODE_WRONG_USER = 1;
    public static final int ERROR_CODE_WRONG_PASSWORD = 2;
    public static final int ERROR_CODE_ANOTHER_CONNECTION = 3;
    public static final int ERROR_CODE_USER_BLOCKED = 4;
    public static final int ERROR_CODE_OLD_VERSION = 5;
    public static final int ERROR_CODE_NEED_KEY = 6;
    public static final int ERROR_CODE_WRONG_KEY = 7;
    public static final int ERROR_CODE_SRV_FAIL = 9;

    private static final Logger logger = LogManager.getFormatterLogger();
    private CopyOnWriteArrayList<User> onlineUsers = new CopyOnWriteArrayList<>();

    public UserManager() { }

    public User getUser(Channel ch) {                                                                                                       //only online users search

        return null;
    }
    public User getUser(String login) {                                                                                                     //online and db search
        User user = getDbUser(login);
        return user;
    }

    private User getDbUser(String logn) {
        String sqlBuf = "select login, password from users where login ILIKE '?'";

        return null;
    }

    public void loginUser(Channel ch, XmlElementStart xmlLogin) {
        XmlAttribute attrLogin = xmlLogin.attributes().stream().filter(a -> a.name().equals("l")).findFirst().orElse(null);                 //get login (l) attribute from <LOGIN />element
        XmlAttribute attrCryptedPass = xmlLogin.attributes().stream().filter(a -> a.name().equals("p")).findFirst().orElse(null);           //get password (p) attribute from <LOGIN />element
        if (attrLogin == null || attrCryptedPass == null) {                                                                                 //login or password attributes are missed, this is abnormal. close the channel silently
            ch.close();
            logger.error("login or password(p) attribute were not found in <LOGIN /> element of client %s, closing channel", ch.attr(ServerMain.sockAddrStr).get());
            return;
        }
        String login = attrLogin.value();
        String userClientCryptedPass = attrCryptedPass.value();

        Map<String, Object> userDbParams = DbUtil.findUserByLogin(login);
        if (userDbParams == null) {                                                                                                         //SQL Exception
            String errMsg = String.format("<ERROR code = \"%d\" />", ERROR_CODE_SRV_FAIL);
            ch.writeAndFlush(errMsg);
            ch.close();
            return;
        }
        String userClearPass = (String)userDbParams.get("password");
        String userServerCryptedPass = encryptPass(ch.attr(ServerMain.encKey).get(), userClearPass);
        logger.info("user crypted pass: %s, server crypted pass = %s, equals = %s", userClientCryptedPass, userServerCryptedPass, userClientCryptedPass.equals(userServerCryptedPass));
        return;
    }

    private String encryptPass(String encrKey, String userClearPass) {

        int[] s_block =
                {
                        0, 30, 30, 28, 28, 37, 37,  9,  9, 18, 18, 34, 34, 35,                      // the 1st chain of pairs' transpositions
                        1, 26, 26, 32, 32, 22, 22, 23, 23, 21, 21, 14, 14, 33, 33, 16,              // the 2nd chain of pairs' transpositions
                        16,  7,  7,  4,  4,  2,  2, 24, 24, 29, 29, 20, 20,  8,  8,  5,
                        5, 15, 15, 17, 17, 36, 36,  6,                                              // the 3rd chain of pairs' transpositions
                        3, 39, 39, 12, 12, 10, 10, 27, 27, 25,                                      // the 4th chain of pairs' transpositions
                        11, 38, 38, 13, 13, 19, 19, 31                                              // the 5th chain of pairs' transpositions
                };

        String result = "";

        try {
            // stage a (get SHA-1 encryptor)
            MessageDigest sha1 = MessageDigest.getInstance("SHA-1");

            // stage b (collect the string)
            String passKey = userClearPass.substring(0, 1) + encrKey.substring(0, 10) + userClearPass.substring(1) + encrKey.substring(10);

            // stage c (cipher the string with SHA-1)
            char[] shuffled_SHA1 = ByteBufUtil.hexDump(sha1.digest(passKey.getBytes(StandardCharsets.UTF_8))).toUpperCase().toCharArray();

            // stage d (shuffle result of ciphering)
            for (int i = 0; i < s_block.length; i += 2) {
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
