package ru.heckzero.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;

import java.io.File;

public class ServerMain {
    private static final Logger logger = LogManager.getFormatterLogger();
    public static final String CONF_DIR = "conf";
    public static final String CONF_FILE = "heckzero.xml";
    public static final XMLConfiguration config = null;
    public static final AttributeKey<String> encKey = AttributeKey.valueOf("encKey");                                                       //encryption key generated for each channel
    public static final AttributeKey<String> sockAddrStr = AttributeKey.valueOf("sockAddStr");                                             //encryption key generated for each channel

    private static String OS = System.getProperty("os.name").toLowerCase();
    public static boolean IS_UNIX = (OS.indexOf("nix") >= 0 || OS.indexOf("nux") >= 0 || OS.indexOf("aix") > 0);

    static {
        LoggerContext context = (LoggerContext) LogManager.getContext(false);                                                               //set a  log4j configuration file
        context.setConfigLocation(new File(System.getProperty("user.dir") + File.separatorChar + "conf" + File.separatorChar + "log4j2.xml").toURI());
    }

    public static void main(String[] args) {
        new ServerMain().startOperation();
        return;
    }

    public void startOperation()  {
        logger.info("HeckZero server version %s copyright (C) 2021 by HeckZero team is starting...", Defines.VERSION);

        EventLoopGroup group = IS_UNIX ? new EpollEventLoopGroup() : new NioEventLoopGroup();                                               //an event loop group for server and client channels
        try {
            NetInHandlerMain netInHandlerMain = new NetInHandlerMain();                                                                     //create an inbound handler instance
            NetOutHandler netOutHandler = new NetOutHandler();                                                                              //create an outbound handler instance
            ServerBootstrap b = new ServerBootstrap();                                                                                      //start a server bootstrapping procedure
            b.group(group).                                                                                                                 //specify an event loop group for the server and the client
                    channel(IS_UNIX ? EpollServerSocketChannel.class : NioServerSocketChannel.class).
                    option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true).
                    childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) throws Exception {                                                        //adding client channel handlers
                            ChannelPipeline pl = ch.pipeline();                                                                             //get the channel pipeline

                            pl.addLast(new ReadTimeoutHandler(Defines.READ_TIMEOUT));                                                       //set a read timeout handler
                            pl.addLast(netOutHandler);                                                                                      //adding 0x00 byte terminator to an outbound XML string

                            pl.addLast(new DelimiterBasedFrameDecoder(Defines.MAX_PACKET_SIZE, Delimiters.nulDelimiter()));                 //flash XML Socket 0x0 byte terminator detection
                            pl.addLast(netInHandlerMain);                                                                                   //the inbound handler
                        }
                    });
            ChannelFuture f = b.bind(Defines.PORT).syncUninterruptibly();                                                                   //bind and start to accept incoming connections
            logger.info("server has been started and is listening on %s", f.channel().localAddress().toString());
            f.channel().closeFuture().sync();                                                                                               //wait for the server to stop completely
        } catch (Exception e) {
            logger.error(e);
        }
        group.shutdownGracefully().syncUninterruptibly();                                                                                   //shut down the event group
        DbUtil.close();
        return;
    }
}
