package ru.heckzero.server;

import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

class User {
    private static final Logger logger = LogManager.getFormatterLogger();
    private final Channel channel;
    private final Map<String, Object> userParams;


    public User(Channel ch, Map<String, Object> userParams) {
        this.channel = ch;
        this.userParams = new ConcurrentHashMap<>(userParams);
        return;
    }

    public void sendMsg(String msg) {

        channel.writeAndFlush(msg);
        return;
    }
}
