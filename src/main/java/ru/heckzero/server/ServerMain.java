package ru.heckzero.server;

import io.netty.bootstrap.*;
import io.netty.channel.*;
import io.netty.channel.epoll.*;
import io.netty.channel.socket.*;

public class ServerMain {

    public static void main(String[] args) {
        new ServerMain().startOperation();
        return;
    }

    public void startOperation() {
        EventLoopGroup group = new EpollEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(group).
                    channel(EpollServerSocketChannel.class).
                    option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true).
                    childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new HZChannelHandler());
                        }
                    });
            ChannelFuture f = b.bind(5190).sync();                                                                                 //Bind and start to accept incoming connections
            f.channel().closeFuture().sync();
        } catch (Exception e) {e.printStackTrace();}
        finally {
            group.shutdownGracefully();
        }

        return;
    }
}
