package ru.heckzero.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

@Sharable
public class HZMainHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final AttributeKey<String> encKey = AttributeKey.newInstance("encKey");                                                  //encryption key generated for each channel

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //get client's address
        logger.info("client connected from %s:%d", sa.getHostString(), sa.getPort());
        String genKey = RandomStringUtils.randomAlphanumeric(Defines.CHANNEL_KEY_SIZE);                                                     //generate an encryption key for the new client
        ctx.channel().attr(encKey).set(genKey);                                                                                             //put the channel id and the key on the MAP
        ctx.writeAndFlush(String.format("<KEY s =\"%s\" />", genKey));                                                                      //send the generated encryption key to the client
        return;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf)msg;
        String s = (String)in.readCharSequence(in.readableBytes() - 2, Charset.defaultCharset());
        logger.info("received line feed delimited string: %s", s);

        in.release();
        return;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //get client's address
        if (cause instanceof ReadTimeoutException) {
            logger.error("client %s:%d read timeout, closing connection", sa.getHostString(), sa.getPort());
        } else {
            logger.error(cause);
            super.exceptionCaught(ctx, cause);
        }
        ctx.close();
        return;
    }


}

class HZOutHanlder extends MessageToByteEncoder<String> {                                                                                   //add '\0' (null terminator) to all outbound messages
    private static final Logger logger = LogManager.getFormatterLogger();
    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        msg = msg + "\0";
        out.writeCharSequence(msg, Charset.defaultCharset());
        return;
    }
}
