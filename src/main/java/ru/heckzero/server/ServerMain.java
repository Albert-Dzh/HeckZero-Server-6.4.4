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
import org.hibernate.SessionFactory;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.ServiceRegistry;
import ru.heckzero.server.user.User;

import java.io.File;

public class ServerMain {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final XMLConfiguration config = null;
    public static final AttributeKey<String> encKey = AttributeKey.valueOf("encKey");                                                       //channel attr holding an encryption key
    public static final AttributeKey<String> sockAddrStr = AttributeKey.valueOf("sockAddStr");                                              //channel attr holding a string representation of the channel
    public static SessionFactory sessionFactory;
    private final static File log4jCfgUri = new File(System.getProperty("user.dir") + File.separatorChar + Defines.CONF_DIR + File.separatorChar + "log4j2.xml");
    private final static File hbCfgUri = new File(System.getProperty("user.dir") + File.separatorChar + Defines.CONF_DIR + File.separatorChar + "hibernate.cfg.xml");
    private final static File ehcacheCfgUri = new File(System.getProperty("user.dir") + File.separatorChar + Defines.CONF_DIR + File.separatorChar + "ehcache.xml");
    private static final String OS = System.getProperty("os.name").toLowerCase();                                                           //get the OS type we are running on
    private static final boolean IS_UNIX = (OS.contains("nix") || OS.contains("nux")) ;                                                     //if the running OS is Linux/Unix family

    static {
        ((LoggerContext) LogManager.getContext(false)).setConfigLocation(log4jCfgUri.toURI());                                              //set and read log4j configuration file name
    }


    public static void main(String[] args) {
        new ServerMain().startOperation();
        return;
    }

    public void startOperation() {
        logger.info("HeckZero server version %s copyright (C) 2021 by HeckZero team is starting...", Defines.VERSION);
        sessionFactory = dbInit();                                                                                                          //init hibernate and 2nd level cache

        EventLoopGroup group = IS_UNIX ? new EpollEventLoopGroup() : new NioEventLoopGroup();                                               //an event loop group for server and client channels
        try {
            NetInHandlerMain netInHandlerMain = new NetInHandlerMain();                                                                     //create an inbound handler
            NetOutHandler netOutHandler = new NetOutHandler();                                                                              //create an outbound handler

            ServerBootstrap b = new ServerBootstrap();                                                                                      //start a server bootstrapping procedure
            b.group(group).                                                                                                                 //specify an event loop group for the server and the client
                    channel(IS_UNIX ? EpollServerSocketChannel.class : NioServerSocketChannel.class).
                    option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true).
                    childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {                                                                         //add client channel handlers
                            ChannelPipeline pl = ch.pipeline();                                                                             //get the channel pipeline

                            pl.addLast(new ReadTimeoutHandler(Defines.READ_TIMEOUT));                                                       //set a read timeout handler
                            pl.addLast(netOutHandler);                                                                                      //adding 0x00 byte terminator to an outbound XML string for the sake of XML Flash requirements

                            pl.addLast(new DelimiterBasedFrameDecoder(Defines.MAX_PACKET_SIZE, Delimiters.nulDelimiter()));                 //Flash XML Socket 0x0 byte terminator detection
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
        return;
    }

    private SessionFactory dbInit() {
        StandardServiceRegistryBuilder standardServiceRegistryBuilder = new StandardServiceRegistryBuilder().configure(hbCfgUri);           //read hibernate configuration from file
        standardServiceRegistryBuilder.applySetting("hibernate.javax.cache.uri", ehcacheCfgUri.toURI().toString());                         //add ehcache config file name to settings
        ServiceRegistry serviceRegistry = standardServiceRegistryBuilder.build();

        MetadataSources sources = new MetadataSources(serviceRegistry).
                addAnnotatedClass(User.class);
        MetadataBuilder metadataBuilder = sources.getMetadataBuilder();
        Metadata metadata = metadataBuilder.build();
        sessionFactory = metadata.getSessionFactoryBuilder().build();
        return sessionFactory;
    }
}