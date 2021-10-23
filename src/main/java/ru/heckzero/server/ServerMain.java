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
import io.netty.util.concurrent.DefaultEventExecutorGroup;
import io.netty.util.concurrent.EventExecutorGroup;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.commons.configuration2.builder.fluent.Configurations;
import org.apache.commons.configuration2.ex.ConfigurationException;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.apache.logging.log4j.core.LoggerContext;
import org.hibernate.Session;
import org.hibernate.SessionFactory;
import org.hibernate.Transaction;
import org.hibernate.boot.Metadata;
import org.hibernate.boot.MetadataBuilder;
import org.hibernate.boot.MetadataSources;
import org.hibernate.boot.registry.StandardServiceRegistryBuilder;
import org.hibernate.service.ServiceRegistry;
import ru.heckzero.server.items.ArsenalLoot;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemTemplate;
import ru.heckzero.server.net.NetInHandlerMain;
import ru.heckzero.server.net.NetOutHandler;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserLevelData;
import ru.heckzero.server.world.*;

import java.io.File;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;

public class ServerMain {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final String VERSION = "0.6";                                                                                            //server version
    private static final String CONF_DIR = "conf";                                                                                          //configuration directory
    private static final String CONF_FILE = "heckzero.xml";                                                                                 //server configuration file
    private static final Integer DEF_MAX_WORKER_THREADS = 8;                                                                                //MAX threads in EventExecutorGroup for the offloading EventLoop threads
    private static final String DEF_LISTEN_HOST = "0.0.0.0";                                                                                //default IP (host) to listen may be IP or FQDN
    private static final Integer DEF_LISTEN_PORT = 5190;                                                                                    //default port to listen
    private static final Integer DEF_MAX_PACKET_SIZE = 26500;                                                                                //max packet length to parse by DelimiterBasedFrameDecoder handler
    private static final Integer DEF_MAX_SOCKET_IDLE_TIME = 5;                                                                              //default socket(non an authorized user) idle timeout (sec)
    public static final Integer DEF_MAX_USER_IDLE_TIME = 32;                                                                                //Max user timeout
    public static final Integer DEF_ENCRYPTION_KEY_SIZE = 32;                                                                               //encryption key length
    public static final Integer DEF_USER_CACHE_TIMEOUT = 600;                                                                               //users cache timout after which it will be purged

    private final static File log4jCfg = new File(System.getProperty("user.dir") + File.separatorChar + CONF_DIR + File.separatorChar + "log4j2.xml");
    private final static File hbnateCfg = new File(System.getProperty("user.dir") + File.separatorChar + CONF_DIR + File.separatorChar + "hibernate.cfg.xml");
    private final static File confFile = new File(System.getProperty("user.dir") + File.separatorChar + CONF_DIR + File.separatorChar + CONF_FILE);

    private static final String OS = System.getProperty("os.name").toLowerCase();                                                           //OS type we are running on
    private static final boolean IS_UNIX = (OS.contains("nix") || OS.contains("nux")) ;                                                     //if the running OS is Linux/Unix family
    public static final ScheduledExecutorService userTasksScheduledExecutor = Executors.newSingleThreadScheduledExecutor();                 //scheduled executor used in various classes

    public static XMLConfiguration hzConfiguration = null;
    public static SessionFactory sessionFactory = null;                                                                                     //Hibernate SessionFactory used across the server

    static {
        ((LoggerContext) LogManager.getContext(false)).setConfigLocation(log4jCfg.toURI());                                                 //set and read log4j configuration file name
        dbInit();                                                                                                                           //bootstrap the hibernate and 2nd level cache and create a SessionFactory
    }

    public static void main(String[] args) {
        new ServerMain().startOperation();
        return;
    }

    public void startOperation() {                                                                                                          //mainly bootstrapping the netty stuff
        logger.info("HeckZero server version %s copyright (C) 2021 by HeckZero team is starting...", VERSION);
        if (!readServerConfig())                                                                                                            //can't read config file
            return;
        EventLoopGroup group = IS_UNIX ? new EpollEventLoopGroup() : new NioEventLoopGroup();                                               //an event loop group for server and client channels (netty)
        EventExecutorGroup execGroup = new DefaultEventExecutorGroup(hzConfiguration.getInt("MaxWorkerThreads", DEF_MAX_WORKER_THREADS));   //DefaultEventLoopGroup will offload operations from the EventLoop
        int listenPort = hzConfiguration.getInt("ServerSetup.ListenPort", DEF_LISTEN_PORT);                                                 //port the server will be listening on
        String listenHost = hzConfiguration.getString("ServerSetup.ListenHost", DEF_LISTEN_HOST).trim().replace("*", "0.0.0.0");            //host the server will be listening on

        try {
            NetInHandlerMain netInHandlerMain = new NetInHandlerMain();                                                                     //an inbound handler (will do client command processing)
            NetOutHandler netOutHandler = new NetOutHandler();                                                                              //an outbound handler (server response massage)

            ServerBootstrap b = new ServerBootstrap();                                                                                      //TCP server bootstrapping procedure (netty)
            b.group(group).                                                                                                                 //an event loop group used by server and client threads
                    channel(IS_UNIX ? EpollServerSocketChannel.class : NioServerSocketChannel.class).
                    option(ChannelOption.SO_BACKLOG, 128).childOption(ChannelOption.SO_KEEPALIVE, true).
                    childHandler(new ChannelInitializer<SocketChannel>() {
                        @Override
                        public void initChannel(SocketChannel ch) {                                                                         //add channel handlers
                            ChannelPipeline pl = ch.pipeline();                                                                             //get the channel pipeline

                            pl.addLast("socketIdleHandler", new ReadTimeoutHandler(hzConfiguration.getInt("ServerSetup.MaxSocketIdleTime", DEF_MAX_SOCKET_IDLE_TIME)));  //set a read timeout handler
                            pl.addLast(netOutHandler);                                                                                      //adding 0x00 byte terminator to an outbound XML string for the sake of XML Flash requirements

                            pl.addLast(new DelimiterBasedFrameDecoder(DEF_MAX_PACKET_SIZE, Delimiters.nulDelimiter()));                     //Adobe Flash XML Socket 0x0 byte terminator detection
                            pl.addLast(execGroup, netInHandlerMain);                                                                        //the inbound handler will be executed in separate event exec group
                        }
                    });

            ChannelFuture f = b.bind(listenHost, listenPort).sync();                                                                        //bind and start accepting incoming connections
            logger.info("server has been started and is listening on %s", f.channel().localAddress().toString());
            f.channel().closeFuture().sync();                                                                                               //wait for the server channel to close. (when???) but we have to wait to keep application running
        } catch (Exception e) {
            logger.error("can't bootstrap the server: %s:%s", e.toString(), e.getMessage());
        }
        group.shutdownGracefully();                                                                                                         //shut down the main event group
        execGroup.shutdownGracefully();                                                                                                     //shut down the offload event group
        userTasksScheduledExecutor.shutdownNow();
        return;
    }

    private static void dbInit() {                                                                                                          //bootstrap the Hibernate
        StandardServiceRegistryBuilder standardServiceRegistryBuilder = new StandardServiceRegistryBuilder().configure(hbnateCfg);          //read hibernate configuration from file
        ServiceRegistry serviceRegistry = standardServiceRegistryBuilder.build();                                                           //continue hibernate bootstrapping

        MetadataSources sources = new MetadataSources(serviceRegistry).
                addAnnotatedClass(User.class).addAnnotatedClass(Location.class).addAnnotatedClass(Building.class).addAnnotatedClass(Portal.class).
                addAnnotatedClass(PortalRoute.class).addAnnotatedClass(Item.class).addAnnotatedClass(ItemTemplate.class).addAnnotatedClass(ArsenalLoot.class).
                addAnnotatedClass(UserLevelData.class);
        MetadataBuilder metadataBuilder = sources.getMetadataBuilder();
        Metadata metadata = metadataBuilder.build();
        sessionFactory = metadata.getSessionFactoryBuilder().build();
        return;
    }

    public static void sync(Object entity) {
        Transaction tx = null;
        logger.debug("saving an entity of type: %s, hash = %d", entity.getClass().getSimpleName(), entity.hashCode());
        try (Session session = sessionFactory.openSession()) {
            tx  = session.beginTransaction();
            session.saveOrUpdate(entity);
            tx.commit();
        }catch (Exception e) {
            if (tx != null && tx.isActive())
                tx.rollback();
            logger.error("can't save entity %s: %s:%s", entity.toString(), e.getClass().getSimpleName(), e.getMessage());
        }
        return;
    }

    public static boolean refresh(Object entity) {
        logger.debug("refreshing an entity of type: %s, hash = %d", entity.getClass().getSimpleName(), entity.hashCode());
        try (Session session = sessionFactory.openSession()) {
            session.refresh(entity);
            return true;
        }catch (Exception e) {
            logger.error("can't refresh entity %s: %s:%s", entity.toString(), e.getClass().getSimpleName(), e.getMessage());
        }
        return false;
    }

    private boolean readServerConfig() {                                                                                                    //read properties from a configuration file
        logger.info("reading server settings from %s%s%s", CONF_DIR, File.separatorChar, CONF_FILE);
        try {
            hzConfiguration = new Configurations().xml(confFile);
            logger.info("server settings have been read ok");
        } catch (ConfigurationException e) {
            logger.error("cant read config file %s, check if the server config file  exists and contains correct settings: %s", confFile.getPath(), e.getMessage());
            return false;
        }
        return true;
    }

}