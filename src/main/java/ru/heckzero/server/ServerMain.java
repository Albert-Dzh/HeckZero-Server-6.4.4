package ru.heckzero.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelInitializer;
import io.netty.channel.ChannelOption;
import io.netty.channel.EventLoopGroup;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerMain {
    private static final Logger logger = LogManager.getFormatterLogger();
    public static final String CONF_DIR = "conf";
    public static final String CONF_FILE = "mb-dup-check.xml";
    public static final String VERSION = "1.0";
    public static XMLConfiguration config;

    public static void main(String[] args) {
        new ServerMain().startOperation();
        return;
    }

    public void startOperation() {
        logger.info("HeckZero server version %s starting", Defines.VERSION);
        HZMainHandler hzMainHandler = new HZMainHandler();

        EventLoopGroup group = new EpollEventLoopGroup();
        try {
            ServerBootstrap b = new ServerBootstrap();
            b.group(group).
                    channel(EpollServerSocketChannel.class).
                    option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true).
                    childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {
                            ch.pipeline().addLast(new ReadTimeoutHandler(Defines.READ_TIMEOUT));
                            ch.pipeline().addLast(new HZOutHanlder());

                            ch.pipeline().addLast(new DelimiterBasedFrameDecoder(Defines.MAX_PACKET_SIZE, Delimiters.nulDelimiter()));      //detect Flash XML Socket NULL - termination string
                            ch.pipeline().addLast(hzMainHandler);
                        }
                    });
            ChannelFuture f = b.bind(5190).sync();                                                                                          //Bind and start to accept incoming connections
            f.channel().closeFuture().sync();
        } catch (Exception e) {e.printStackTrace();}
        finally {
            group.shutdownGracefully();
        }

        return;
    }
}
