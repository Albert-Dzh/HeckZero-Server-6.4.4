package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.handler.codec.xml.XmlElement;

import java.util.concurrent.CopyOnWriteArrayList;

public class UserManager {                                                                                                                   //yes, this class name ends in 'er' fuck off, Egor ;)
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

    public User loginUser(Channel ch, XmlElement xmlLogin) {
//        String sqlBuf = DbUtil.findByCondition()
        return null;
    }

}
