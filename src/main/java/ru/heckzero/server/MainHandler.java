package ru.heckzero.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.*;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.xml.XmlElementStart;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

@Sharable
class MainHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final AttributeKey<String> encKey = AttributeKey.newInstance("encKey");                                                  //encryption key generated for each channel

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //get client's address
        logger.info("client connected from %s:%d", sa.getHostString(), sa.getPort());

        String genKey = RandomStringUtils.randomAlphanumeric(Defines.CHANNEL_KEY_SIZE);                                                     //generate an encryption key for the new client
        ctx.channel().attr(encKey).set(genKey);                                                                                             //store an encryption key as a channel attribute
        ctx.writeAndFlush(String.format("<KEY s =\"%s\"/>", genKey));                                                                       //send a generated message with the encryption key to the client

        return;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        logger.info("channel read element is: %s toString() is: %s", msg.getClass(), msg.toString());

        if (!(msg instanceof XmlElementStart))
            return;
        XmlElementStart element = (XmlElementStart) msg;
        Player player = new Player(ctx.channel());
//        player.process(element);


        return;
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //client's socket address
        if (cause instanceof ReadTimeoutException) {                                                                                        //read timeout
            logger.error("client %s:%d read timeout, closing connection", sa.getHostString(), sa.getPort());
        } else {
            logger.error("exception: %s", cause.getMessage());
        }
        ctx.close();
        return;
    }
}

class LoggerHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = ((ByteBuf)msg).slice();
        String rcvd = (String)in.readCharSequence(in.readableBytes(), Charset.defaultCharset());
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //get client's address
        logger.info("received from %s:%d  %s", sa.getHostString(), sa.getPort(), rcvd);
        ctx.fireChannelRead(msg);
        return;
    }
}

class OutHanlder extends MessageToByteEncoder<String> {                                                                                     //add '\0' (null terminator) to all outbound messages
    private static final Logger logger = LogManager.getFormatterLogger();
    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //get client's address
        logger.info("sending %s to %s;%d", msg, sa.getHostString(), sa.getPort());
        msg = msg + "\0";
        out.writeCharSequence(msg, Charset.defaultCharset());
        return;
    }
}

