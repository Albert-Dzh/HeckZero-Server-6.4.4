package ru.heckzero.server;

import io.netty.channel.*;

import java.net.*;

public class HZChannelHandler extends ChannelInboundHandlerAdapter {

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {

        InetSocketAddress clntAddr = (InetSocketAddress)ctx.channel().remoteAddress();
        System.out.format("incoming connection from %s:%d\n", clntAddr.getAddress().getHostAddress(), clntAddr.getPort());
        ctx.channel().close().sync();
        return; 
    }
}
