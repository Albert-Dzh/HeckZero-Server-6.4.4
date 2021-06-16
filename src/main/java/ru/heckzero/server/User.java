package ru.heckzero.server;

import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class User {
    private static final Logger logger = LogManager.getFormatterLogger();
    private Channel channel;
    private String login;


    public User(String login, Channel ch) {
        this.login = login;
        this.channel = ch;
    }
}
