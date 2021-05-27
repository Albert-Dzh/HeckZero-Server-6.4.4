package ru.heckzero.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelId;
import io.netty.channel.ChannelInboundHandlerAdapter;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;
import java.util.HashMap;


public class HZMainHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
    HashMap<ChannelId, String> channellKeys = new HashMap<>();                                                                              //crypt keys for new clients

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress) ctx.channel().remoteAddress();                                                           //get client's address
        logger.info("client connected from %s:%d", sa.getHostString(), sa.getPort());
        ctx.channel().id();
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
        logger.error(cause);
        ctx.close();
        return;
    }

    private void genChannelKey() {
        byte[] key;

        return;
    }
}


