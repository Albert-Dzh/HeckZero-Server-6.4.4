package ru.heckzero.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.channel.nio.NioEventLoopGroup;
import io.netty.channel.socket.SocketChannel;
import io.netty.channel.socket.nio.NioServerSocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import io.netty.util.concurrent.GlobalEventExecutor;
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
import ru.heckzero.server.net.NetInHandlerMain;
import ru.heckzero.server.net.NetOutHandler;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;

import java.io.File;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ServerMain {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final XMLConfiguration hzConfiguration = null;

    private final static File log4jCfg = new File(System.getProperty("user.dir") + File.separatorChar + Defines.CONF_DIR + File.separatorChar + "log4j2.xml");
    private final static File hbnateCfg = new File(System.getProperty("user.dir") + File.separatorChar + Defines.CONF_DIR + File.separatorChar + "hibernate.cfg.xml");
    private final static File ehcacheCfg = new File(System.getProperty("user.dir") + File.separatorChar + Defines.CONF_DIR + File.separatorChar + "ehcache.xml");
    private static final String OS = System.getProperty("os.name").toLowerCase();                                                           //OS type we are running on
    private static final boolean IS_UNIX = (OS.contains("nix") || OS.contains("nux")) ;                                                     //if the running OS is Linux/Unix family
    public static final ExecutorService mainExecutor = Executors.newCachedThreadPool();                                                     //main client command executor service
    public static final ScheduledExecutorService mainScheduledExecutor = Executors.newSingleThreadScheduledExecutor();                      //scheduled executor used in various classes

    public static SessionFactory sessionFactory;                                                                                            //Hibernate SessionFactory used across the server

    static {
        ((LoggerContext) LogManager.getContext(false)).setConfigLocation(log4jCfg.toURI());                                                 //set and read log4j configuration file name
    }


    public static void main(String[] args) {
        new ServerMain().startOperation();
        return;
    }

    public void startOperation() {                                                                                                          //mainly bootstrapping the netty stuff
        logger.info("HeckZero server version %s copyright (C) 2021 by HeckZero team is starting...", Defines.VERSION);
        sessionFactory = dbInit();                                                                                                          //init hibernate and 2nd level cache and create the SessionFactory
        EventLoopGroup group = IS_UNIX ? new EpollEventLoopGroup() : new NioEventLoopGroup();                                               //an event loop group for server and client channels (netty)
        try {
            NetInHandlerMain netInHandlerMain = new NetInHandlerMain();                                                                     //an inbound handler (will do client command processing)
            NetOutHandler netOutHandler = new NetOutHandler();                                                                              //an outbound handler (server response massage)

            ServerBootstrap b = new ServerBootstrap();                                                                                      //TCP server bootstrapping procedure (netty)
            b.group(group).                                                                                                                 //an event loop group used by server and client threads
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
            f.channel().closeFuture().sync();                                                                                               //wait for the server channel to close. (when???) but we have to wait to keep application running
        } catch (Exception e) {
            logger.error(e);
        }
        group.shutdownGracefully().syncUninterruptibly();                                                                                   //shut down the event group
        mainExecutor.shutdownNow();                                                                                                         //shut down the main executor service
        mainScheduledExecutor.shutdownNow();
        return;
    }

    private SessionFactory dbInit() {                                                                                                       //bootstrap the Hibernate
        StandardServiceRegistryBuilder standardServiceRegistryBuilder = new StandardServiceRegistryBuilder().configure(hbnateCfg);          //read hibernate configuration from file
        standardServiceRegistryBuilder.applySetting("hibernate.javax.cache.uri", ehcacheCfg.toURI().toString());                            //add ehcache config file name to hibernate settings (by setting "hibernate.javax.cache.uri" to ehcache config file name)
        ServiceRegistry serviceRegistry = standardServiceRegistryBuilder.build();                                                           //continue hibernate bootstrapping

        MetadataSources sources = new MetadataSources(serviceRegistry).
                addAnnotatedClass(User.class);
        MetadataBuilder metadataBuilder = sources.getMetadataBuilder();
        Metadata metadata = metadataBuilder.build();
        sessionFactory = metadata.getSessionFactoryBuilder().build();
        return sessionFactory;
    }
}