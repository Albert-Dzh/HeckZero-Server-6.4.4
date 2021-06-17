package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.handler.codec.xml.XmlAttribute;
import io.netty.handler.codec.xml.XmlElementStart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
        XmlAttribute attrLogin = xmlLogin.attributes().stream().filter(a -> a.name().equals("login")).findFirst().orElse(null);             //login is absent in <LOGIN attributes/>
        XmlAttribute attrCryptedPass = xmlLogin.attributes().stream().filter(a -> a.name().equals("p")).findFirst().orElse(null);           //pass is absent in <LOGIN attributes/>
        if (attrLogin == null || attrCryptedPass == null) {
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
        return;
    }

    private String encryptPass(String encrKey, String userClearPass) {

        return null;
    }
}
