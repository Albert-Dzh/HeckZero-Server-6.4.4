package ru.heckzero.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.timeout.ReadTimeoutException;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.concurrent.ConcurrentHashMap;

@Sharable
public class HZMainHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
    ConcurrentHashMap<String, String> channellKeys = new ConcurrentHashMap<>();                                                             //channels - encryption keys map

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //get client's address
        logger.info("client connected from %s:%d", sa.getHostString(), sa.getPort());
        ChannelId chId = ctx.channel().id();                                                                                                //get client's channelId
        String key = genEncryptionKey();                                                                                                    //generate an encryption key for the client
        channellKeys.put(chId.asLongText(), key);                                                                                           //put the channel id and the key on the MAP
        ctx.writeAndFlush(String.format("<KEY s =\"%s\" />", key));                                                                         //send the key to the client
        return;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {
        ByteBuf in = (ByteBuf)msg;
        logger.info("readable bytes = %d", in.readableBytes());
        String s = (String)in.readCharSequence(in.readableBytes(), Charset.defaultCharset());
        logger.info("received line feed delimited string: %s", s);
        return;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //get client's address
        if (cause instanceof ReadTimeoutException) {
            logger.error("read timeout for client %s:%d, closing connection", sa.getHostString(), sa.getPort());
            channellKeys.remove(ctx.channel().id().asLongText());                                                                           //remove channel key from the MAP
        } else {
            logger.error(cause);
            super.exceptionCaught(ctx, cause);
        }
        ctx.close();
        return;
    }

    private String genEncryptionKey() {
        return  RandomStringUtils.randomAlphanumeric(Defines.CHANNEL_KEY_SIZE);
    }
}

class HZOutHanlder extends MessageToByteEncoder<String> {
    private static final Logger logger = LogManager.getFormatterLogger();
    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        out.writeCharSequence(msg, Charset.defaultCharset());
        return;
    }
}
