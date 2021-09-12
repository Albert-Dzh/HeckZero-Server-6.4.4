package ru.heckzero.server.net;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.AttributeKey;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.SAXException;
import ru.heckzero.server.CommandProcessor;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;

import javax.xml.parsers.SAXParser;
import javax.xml.parsers.SAXParserFactory;
import java.io.ByteArrayInputStream;
import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;
                                                                                                                                            //TODO change class name to NetInHandler (remove 'Main' word)

@Sharable
public class NetInHandlerMain extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
                                                                                                                                            //we have only one instance of SAXFactory and ThreadLocal because this class is Sharable, so they don't need to be declared ad static
    private final SAXParserFactory saxParserFactory = SAXParserFactory.newDefaultInstance();                                                //create SAX XML parser factory
    private final ThreadLocal<SAXParser> tlParser = ThreadLocal.withInitial(() -> {try {return saxParserFactory.newSAXParser();} catch (Exception e) {logger.error("can't create SAX parser: %s", e.getMessage()); return null;}});

    public NetInHandlerMain() {
        saxParserFactory.setValidating(false);                                                                                              //disable XML validation, will cause the parser to give a fuck to malformed XML
        return;
    }

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {                                                                 //a new client has connected
        InetSocketAddress sa = ((InetSocketAddress)ctx.channel().localAddress());                                                           //server address client connected to
        InetSocketAddress ca = (InetSocketAddress)ctx.channel().remoteAddress();                                                            //client address client connected from
        String serverFQDN = sa.getAddress().getCanonicalHostName();                                                                         //server FQDN (reverse lookup of sa)

        String sockStr = String.format("%s:%d", ca.getHostString(), ca.getPort());                                                          //client socket address as a string
        logger.info("client connected from %s -> %s:%d", sockStr, serverFQDN, sa.getPort());

        ctx.channel().attr(AttributeKey.valueOf("sockStr")).set(sockStr);                                                                   //and store it as a channel attribute for login purpose
        ctx.channel().attr(AttributeKey.valueOf("chStr")).set(sockStr);                                                                     //initial chStr = sockStr (will be replaced to user login after successful authorization)
        ctx.channel().attr(AttributeKey.valueOf("chType")).set(User.ChannelType.NOUSER);                                                    //initial channel type set to NOUSER

        String genKey = RandomStringUtils.randomAlphanumeric(ServerMain.hzConfiguration.getInt("ServerSetup.EncryptionKeySize", ServerMain.DEF_ENCRYPTION_KEY_SIZE));  //generate a random string - an encryption key for the future user authentication
        ctx.channel().attr(AttributeKey.valueOf("encKey")).set(genKey);                                                                     //store generated encryption key as a channel attribute
        if (!serverFQDN.equals("main.timezero.ru"))                                                                                         //send an encryption key only to the clients connected to NOT main.timezero.ru (they will come up with a <LIST> later)
            ctx.writeAndFlush(String.format("<KEY s =\"%s\"/>", genKey));
        return;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {                                                       //here in msg we have a row 0x00 terminated bytes from a client
        String rcvd = ((ByteBuf)msg).toString(StandardCharsets.UTF_8);                                                                      //get the context of a received message for the logging and preprocessing purpose
        rcvd = rcvd.replace("\r", "&#xD;").trim();                                                                                          //replace CR with the corresponding XML code

        String chStr = (String)ctx.channel().attr(AttributeKey.valueOf("chStr")).get();                                                     //login or socket address if a User is still unknown
        logger.info("received %s from %s", rcvd, chStr);                                                                                    //log the received message

        ReferenceCountUtil.release(msg);                                                                                                    //we don't need the source ByteBuf anymore, releasing it
        String xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\" ?><ROOT>" + rcvd + "</ROOT>";                                          //wrap the source message into XML root elements <ROOT>source_message</ROOT>
        SAXParser parser = tlParser.get();                                                                                                  //parser per each thead is stored in ThreadLocal
        parser.parse(new ByteArrayInputStream(xmlString.getBytes(StandardCharsets.UTF_8)), new CommandProcessor(ctx.channel()));            //parse and process the received command by a CommandProcessor instance
        return;
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        String chStr = (String) ctx.channel().attr(AttributeKey.valueOf("chStr")).get();                                                    //set sender from string - login or socket address if a User is unknown

        if (cause instanceof ReadTimeoutException) {                                                                                        //read timeout has happened
            logger.warn("read timeout from %s", chStr);
        } else {
                logger.error("Houston, we've had a problem");
                if (cause instanceof SAXException) {                                                                                        //malformed XML was received from a client
                    logger.error("XML stinks like shit from %s \uD83E\uDD2E %s", chStr, cause.getMessage());                                //XML govnoy vonyaet
                } else {                                                                                                                    //all other exceptions
                    logger.error("an exception while processing a command from %s: %s", chStr, cause.getMessage());
//                    cause.printStackTrace();
                }
            }
        logger.info("closing connection with %s", chStr);
        ctx.close();
        return;
    }

    @Override
    public void channelInactive(ChannelHandlerContext ctx) throws Exception {                                                               //client channel has been disconnected, channel become inactive (closed)
        String sockStr = (String) ctx.channel().attr(AttributeKey.valueOf("sockStr")).get();                                                //set sender from string - login or socket address if a User is unknown
        String userStr = (String) ctx.channel().attr(AttributeKey.valueOf("chStr")).get();                                                  //set sender from string - login or socket address if a User is unknown
        String chType = ((User.ChannelType)ctx.channel().attr(AttributeKey.valueOf("chType")).get()).name();                                //get Channel type (Game, Chat)
        logger.info("channel %s %s %s disconnected", sockStr, chType, chType.equals(User.ChannelType.NOUSER.name()) ? "" :  userStr);
        UserManager.logoutUser(ctx.channel());                                                                                              //do user logout procedures
        return;
    }
}
