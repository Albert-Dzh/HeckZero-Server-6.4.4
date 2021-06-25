package ru.heckzero.server;

import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.xml.XmlElementStart;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

@Sharable
class NetInHandlerMain extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
    private CommandProccessor commandProccessor = new CommandProccessor();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {                                                                 //new client has connected
        logger.info("client connected from %s", ctx.channel().attr(ServerMain.sockAddrStr));

        String genKey = RandomStringUtils.randomAlphanumeric(Defines.ENCRYPTION_KEY_SIZE);                                                  //generate an encryption key for the new client authentication
        ctx.channel().attr(ServerMain.encKey).set(genKey);                                                                                  //store the encryption key as a channel attribute
        ctx.writeAndFlush(String.format("<KEY s =\"%s\"/>", genKey));                                                                       //send a generated message with the encryption key to the client
        return;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {                                                       //here we have a valid XmlElement as a msg
        logger.info("channel read element is: %s toString() is: %s", msg.getClass(), msg.toString());
        if (!(msg instanceof XmlElementStart))                                                                                              //we are interested in only XmlElementStart elements
            return;
        commandProccessor.processCommand(ctx.channel(), (XmlElementStart)msg);                                                              //process the command by commandProcessor
        return;
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReadTimeoutException) {                                                                                        //read timeout
            logger.warn("client %s read timeout, closing connection", ctx.channel().attr(ServerMain.sockAddrStr).get());
        } else {
            logger.error("exception: %s", cause.getMessage());
            cause.printStackTrace();
        }
        ctx.close();
        return;
    }
}

