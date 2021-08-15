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
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.commons.configuration2.Configuration;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
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
    public static final String VERSION = "0.3";
    private static final String CONF_DIR = "conf";                                                                                          //configuration directory
    private static final String CONF_FILE = "heckzero.xml";                                                                                 //server configuration file

    private static final Integer DEF_PORT = 5190;                                                                                           //default port to listen
    private static final Integer DEF_MAX_PACKET_SIZE = 1500;                                                                                //max packet length to parse by DelimiterBasedFrameDecoder handler
    private static final Integer DEF_MAX_SOCKET_IDLE_TIME = 5;                                                                              //default socket(non an authorized user) idle timeout (sec)
    public static final Integer DEF_MAX_USER_IDLE_TIME = 32;                                                                                //Max user timeout
    public static final Integer DEF_ENCRYPTION_KEY_SIZE = 32;                                                                               //encryption key length
    public static final Integer DEF_USER_CACHE_TIMEOUT = 600;                                                                               //users cache timout after which it will be purged

    private final static File log4jCfg = new File(System.getProperty("user.dir") + File.separatorChar + CONF_DIR + File.separatorChar + "log4j2.xml");
    private final static File hbnateCfg = new File(System.getProperty("user.dir") + File.separatorChar + CONF_DIR + File.separatorChar + "hibernate.cfg.xml");
    private final static File ehcacheCfg = new File(System.getProperty("user.dir") + File.separatorChar + CONF_DIR + File.separatorChar + "ehcache.xml");
    private static final String OS = System.getProperty("os.name").toLowerCase();                                                           //OS type we are running on
    private static final boolean IS_UNIX = (OS.contains("nix") || OS.contains("nux")) ;                                                     //if the running OS is Linux/Unix family
    public static final ExecutorService mainExecutor = Executors.newCachedThreadPool();                                                     //main client command executor service
    public static final ScheduledExecutorService mainScheduledExecutor = Executors.newSingleThreadScheduledExecutor();                      //scheduled executor used in various classes

    public static XMLConfiguration hzConfiguration = null;
    public static SessionFactory sessionFactory;                                                                                            //Hibernate SessionFactory used across the server

    static {
        ((LoggerContext) LogManager.getContext(false)).setConfigLocation(log4jCfg.toURI());                                                 //set and read log4j configuration file name
    }


    public static void main(String[] args) {
        new ServerMain().startOperation();
        return;
    }

    public void startOperation() {                                                                                                          //mainly bootstrapping the netty stuff
        logger.info("HeckZero server version %s copyright (C) 2021 by HeckZero team is starting...", VERSION);
        if (!readServerConfig())
            return;
        sessionFactory = dbInit();                                                                                                          //init hibernate and 2nd level cache and create the SessionFactory
        EventLoopGroup group = IS_UNIX ? new EpollEventLoopGroup() : new NioEventLoopGroup();                                               //an event loop group for server and client channels (netty)
        EventExecutorGroup execGroup = new DefaultEventExecutorGroup(16);                                                                   //DefaultEventLoopGroup will offload the operation from the EventLoop

        int listenPort = hzConfiguration.getInt("ServerSetup.ServerPort", DEF_PORT);                                                        //port the server will be listening on

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

                            pl.addLast("socketIdleHandler", new ReadTimeoutHandler(hzConfiguration.getInt("ServerSetup.MaxSocketIdleTime", DEF_MAX_SOCKET_IDLE_TIME)));                                                       //set a read timeout handler
                            pl.addLast(netOutHandler);                                                                                      //adding 0x00 byte terminator to an outbound XML string for the sake of XML Flash requirements

                            pl.addLast(new DelimiterBasedFrameDecoder(DEF_MAX_PACKET_SIZE, Delimiters.nulDelimiter()));                     //Flash XML Socket 0x0 byte terminator detection
                            pl.addLast(execGroup, netInHandlerMain);                                                                        //the inbound handler will be executed in separate event exec group
                        }
                    });
            ChannelFuture f = b.bind(listenPort).syncUninterruptibly();                                                                     //bind and start accepting incoming connections
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

    private boolean readServerConfig() {                                                                                                    //read properties from a configuration file
        File confFile = new File(System.getProperty("user.dir") + File.separatorChar + CONF_DIR + File.separatorChar + CONF_FILE);
        logger.info("reading server settings from %s", confFile.getPath());
        try {
            hzConfiguration = new Configurations().xml(new File(CONF_DIR, CONF_FILE));
            logger.info("server settings have been read ok");
        } catch (ConfigurationException e) {
            logger.error("cant read config file %s, check if the server config file  exists and contains correct settings: %s", confFile.getPath(), e.getMessage());
            return false;
        }
        return true;
    }

}