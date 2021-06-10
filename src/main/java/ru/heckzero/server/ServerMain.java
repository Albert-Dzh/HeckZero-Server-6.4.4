package ru.heckzero.server;

import io.netty.bootstrap.ServerBootstrap;
import io.netty.channel.*;
import io.netty.channel.epoll.EpollEventLoopGroup;
import io.netty.channel.epoll.EpollServerSocketChannel;
import io.netty.channel.socket.SocketChannel;
import io.netty.handler.codec.DelimiterBasedFrameDecoder;
import io.netty.handler.codec.Delimiters;
import io.netty.handler.codec.xml.XmlDecoder;
import io.netty.handler.timeout.ReadTimeoutHandler;
import org.apache.commons.configuration2.XMLConfiguration;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

public class ServerMain {
    private static final Logger logger = LogManager.getFormatterLogger();
    public static final String CONF_DIR = "conf";
    public static final String CONF_FILE = "heckzero.xml";
    public static final String VERSION = "1.0";
    public static XMLConfiguration config;

    public static void main(String[] args) {
        new ServerMain().startOperation();
        return;
    }

    public void startOperation()  {
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
                            ChannelPipeline pl = ch.pipeline();                                                                             //the channel pipeline

                            pl.addLast(new ReadTimeoutHandler(Defines.READ_TIMEOUT));                                                       //set a read timeout handler
                            pl.addLast(new HZOutHanlder());                                                                                 //outbound handler to add null terminator to an outbound string

                            pl.addLast(new DelimiterBasedFrameDecoder(Defines.MAX_PACKET_SIZE, Delimiters.nulDelimiter()));                 //enable Flash XML Socket (\0x0) terminator detection
                            pl.addLast(new XmlDecoder());                                                                                   //ByteBuf to XML decoder
                            pl.addLast(hzMainHandler);                                                                                      //main inbound handler
                        }
                    });
            ChannelFuture f = b.bind(Defines.PORT).syncUninterruptibly();                                                                   //bind and start to accept incoming connections
            f.channel().closeFuture().sync();                                                                                               //wait for the server to stop
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                group.shutdownGracefully().sync();                                                                                          //shut down the server
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
        return;
    }
}
