package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.handler.codec.xml.XmlElement;

import java.util.concurrent.CopyOnWriteArrayList;

public class UserHelper {                                                                                                                   //yes, this class name ends in 'er' fuck off, Egor ;)
    private CopyOnWriteArrayList<User> onlineUsers = new CopyOnWriteArrayList<>();

    public UserHelper() { }

    public User getUser(Channel ch) {

        return null;
    }
    public User getUser(String login) {

        return null;
    }

    public User canLogin(Channel ch, XmlElement xmlLogin) {
        return null;
    }

}
