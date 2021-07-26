package ru.heckzero.server;

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
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@Sharable
class NetInHandlerMain extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
    private final SAXParserFactory factory = SAXParserFactory.newDefaultInstance();                                                         //create SAX XML parser factory
    private final SAXParser parser;

    public NetInHandlerMain() throws Exception {
        factory.setValidating(false);                                                                                                       //disabling XML validation, will cause the parser to give a fuck to malformed XML
        parser = factory.newSAXParser();                                                                                                    //create an XML parser for parsing incoming client data
        return;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {                                                                 //new client has just connected
        InetSocketAddress sa = (InetSocketAddress)ctx.channel().remoteAddress();                                                            //get client's socket address

        String fromStr = String.format("%s:%d", sa.getHostString(), sa.getPort());                                                          //format socket address to a string
        ctx.channel().attr(ServerMain.sockAddrStr).set(fromStr);                                                                            //and store it as a channel attribute for a login usage

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

        User user = UserManager.getUser(ctx.channel());                                                                                     //try to get a User by Channel
        String fromStr = user.isEmpty() ? ctx.channel().attr(ServerMain.sockAddrStr).get() : "user " + user.getParam(User.Params.LOGIN);    //set sender from string - login or socket address if a User is unknown
        logger.info("received %s from %s, length = %d", rcvd, fromStr, rcvd.length());                                                      //log the received message

        ReferenceCountUtil.release(msg);                                                                                                    //we don't need the source ByteBuf anymore, releasing it

        String xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ROOT>" + rcvd + "</ROOT>";                                           //wrap the source message into XML root elements <ROOT>source_message</ROOT>
        parser.parse(new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8)), new CommandProcessor(ctx.channel()));            //process the command by a CommandProcessor instance
        return;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        logger.error("Houston, we've had a problem");
        User user = UserManager.getUser(ctx.channel());                                                                                     //try to get a User by Channel
        String fromStr = user.isEmpty() ? ctx.channel().attr(ServerMain.sockAddrStr).get() : "user " + user.getParam(User.Params.LOGIN);    //set a from string - login or socket address if a User is unknown

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
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {
        logger.info("chanell inactive. User login.... logged out");
    }
}
