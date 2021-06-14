package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.handler.codec.xml.XmlElementStart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

class Player {
    private static final Logger logger = LogManager.getFormatterLogger();
    private final Channel channel;

    public Player(Channel channel) {
        this.channel = channel;
    }

    public void process(XmlElementStart xmlElement) {
        logger.info("processing element %s", xmlElement.name());
        return;
    }

}
