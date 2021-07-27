package ru.heckzero.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;
import ru.heckzero.server.CommandProcessor;
import ru.heckzero.server.Defines;
import ru.heckzero.server.ServerMain;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
                                                                                                                                            //TODO change class name to NetInHandler (remove 'Main' word)
@Sharable
public class NetInHandlerMain extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
    private final SAXParser parser;                                                                                                         //XML SAX parser

    public NetInHandlerMain() throws Exception {
        SAXParserFactory saxParserFactory = SAXParserFactory.newDefaultInstance();                                                          //create SAX XML parser factory
        saxParserFactory.setValidating(false);                                                                                              //disable XML validation, will cause the parser to give a fuck to malformed XML
        parser = saxParserFactory.newSAXParser();                                                                                           //create an XML parser for parsing incoming client data
        return;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {                                                                 //new client has just connected
        InetSocketAddress sa = (InetSocketAddress)ctx.channel().remoteAddress();                                                            //get client's socket address

        String fromStr = String.format("%s:%d", sa.getHostString(), sa.getPort());                                                          //format socket address to a string
        ctx.channel().attr(ServerMain.userStr).set(fromStr);                                                                                //and store it as a channel attribute for a login usage
        logger.info("client connected from %s", fromStr);

        String genKey = RandomStringUtils.randomAlphanumeric(Defines.ENCRYPTION_KEY_SIZE);                                                  //generate a random string - an encryption key for the future user authentication
        ctx.channel().attr(ServerMain.encKey).set(genKey);                                                                                  //store generated encryption key as a channel attribute

        ctx.writeAndFlush(String.format("<KEY s =\"%s\"/>", genKey));                                                                       //send a reply message containing the encryption key to the client
        return;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {                                                       //here in msg we have a row 0x00 terminated bytes from a client
        String rcvd = ((ByteBuf)msg).toString(StandardCharsets.UTF_8);                                                                      //get the context of a received message for the logging and preprocessing purpose
        rcvd = rcvd.replace("\r", "&#xD;").trim();                                                                                          //replace CR with the corresponding XML code

        String fromStr = ctx.channel().attr(ServerMain.userStr).get();                                                                      //set sender from string - login or socket address if a User is unknown
        logger.info("received %s from %s, length = %d", rcvd, fromStr, rcvd.length());                                                      //log the received message

        ReferenceCountUtil.release(msg);                                                                                                    //we don't need the source ByteBuf anymore, releasing it

        String xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ROOT>" + rcvd + "</ROOT>";                                           //wrap the source message into XML root elements <ROOT>source_message</ROOT>
        CommandProcessor commandProcessor = new CommandProcessor(ctx.channel());
        parser.parse(new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8)), commandProcessor);                               //process the command by a CommandProcessor instance
        return;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Houston, we've had a problem");
        String fromStr = ctx.channel().attr(ServerMain.userStr).get();                                                                      //set sender from string - login or socket address if a User is unknown

        if (cause instanceof ReadTimeoutException) {                                                                                        //read timeout has happened
            logger.warn("read timeout from %s", fromStr);
        } else {
                if (cause instanceof SAXException) {                                                                                        //malformed XML was received from a client
                    logger.error("XML stinks like shit from %s \uD83E\uDD2E %s", fromStr, cause.getMessage());                              //XML govnoy vonyaet
                } else {                                                                                                                    //all other exceptions
                    logger.error("an exception caught from %s: %s", fromStr, cause.getMessage());
                }
            }
        logger.info("closing the connection with %s", fromStr);
        ctx.close();
        return;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {                                                               //client channel has been disconnected
        String fromStr = ctx.channel().attr(ServerMain.userStr).get();                                                                      //set sender from string - login or socket address if a User is unknown
        logger.info("channel inactive. User %s.... logged out", fromStr);
        return;
    }
}
