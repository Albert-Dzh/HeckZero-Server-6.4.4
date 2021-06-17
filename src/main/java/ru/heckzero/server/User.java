package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.util.internal.StringUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;

class User {
    private static final Logger logger = LogManager.getFormatterLogger();
    private Channel channel;
    private String login;
    private Map<String, Object> userParams;

    public boolean empty() { return login.isEmpty(); }

    public User (String login) {
        this(StringUtil.EMPTY_STRING, null);
    }

    public User(String login, Channel ch) {
        this.login = login;
        this.channel = ch;
    }

    public void sendMsg(String msg) {
        channel.writeAndFlush(msg);
        return;
    }
}
