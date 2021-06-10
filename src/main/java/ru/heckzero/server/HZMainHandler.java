package ru.heckzero.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.MessageToMessageDecoder;
import io.netty.handler.codec.xml.XmlAttribute;
import io.netty.handler.codec.xml.XmlElementStart;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.List;

@Sharable
public class HZMainHandler extends MessageToMessageDecoder {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final AttributeKey<String> encKey = AttributeKey.newInstance("encKey");                                                  //encryption key generated for each channel

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //get client's address
        logger.info("client connected from %s:%d", sa.getHostString(), sa.getPort());

        String genKey = RandomStringUtils.randomAlphanumeric(Defines.CHANNEL_KEY_SIZE);                                                     //generate an encryption key for the new client
        ctx.channel().attr(encKey).set(genKey);                                                                                             //store an encryption key as a channel attribute
        ctx.writeAndFlush(String.format("<KEY s =\"%s\" />", genKey));                                                                      //send a generated message with the encryption key to the client
        return;
    }

    @Override
    protected void decode(ChannelHandlerContext ctx, Object msg, List out) throws Exception {
        logger.info("decoding XML object");

        if (msg instanceof XmlElementStart) {
            XmlElementStart xes = (XmlElementStart) msg;
            logger.info("we've got XmlElementStart : %s", xes.toString());
            List<XmlAttribute> attrs = xes.attributes();
            logger.info("name of the element: %s, num attributes = %d", xes.name(), xes.attributes().size());
            attrs.forEach(attr -> {logger.info("%s = %s", attr.name(), attr.value());});
        }
        return;
    }

/*

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
//        ByteBuf in = (ByteBuf)msg;
//        String s = (String)in.readCharSequence(in.readableBytes() - 2, Charset.defaultCharset());
        logger.info("received string: %s: ");

//        in.release();
        return;
    }
*/


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //client's socket address
        if (cause instanceof ReadTimeoutException) {                                                                                        //read timeout
            logger.error("client %s:%d read timeout, closing connection", sa.getHostString(), sa.getPort());
        } else {
            logger.error("we have exception: %s ", cause.getMessage());
//            super.exceptionCaught(ctx, cause);
        }
        ctx.close();
        return;
    }
}

class HZLoggerHandler extends ChannelInboundHandlerAdapter {
    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {

    }
}

class HZOutHanlder extends MessageToByteEncoder<String> {                                                                                   //add '\0' (null terminator) to all outbound messages
    private static final Logger logger = LogManager.getFormatterLogger();
    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        msg = msg + "\0";
        out.writeCharSequence(msg, Charset.defaultCharset());
        logger.info("sending %s to ", msg);
        return;
    }
}
