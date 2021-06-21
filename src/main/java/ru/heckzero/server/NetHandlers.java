package ru.heckzero.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.handler.codec.MessageToByteEncoder;
import io.netty.handler.codec.xml.XmlElementStart;
import io.netty.handler.timeout.ReadTimeoutException;
import io.netty.util.ReferenceCountUtil;
import org.apache.commons.lang3.RandomStringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.Charset;

@Sharable
class MainHandler extends ChannelInboundHandlerAdapter {
    private static final Logger logger = LogManager.getFormatterLogger();
    private CommandProccessor commandProccessor = new CommandProccessor();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {                                                                 //new client has connected
        logger.info("client connected from %s", ctx.channel().attr(ServerMain.sockAddrStr));

        String genKey = RandomStringUtils.randomAlphanumeric(Defines.ENCRYPTION_KEY_SIZE);                                                  //generate an encryption key for the new client authentication
        ctx.channel().attr(ServerMain.encKey).set(genKey);                                                                                  //store the encryption key as a channel attribute
        ctx.writeAndFlush(String.format("<KEY s =\"%s\"/>", genKey));                                                                       //send a generated message with the encryption key to the client
        return;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {                                                       //here we have a valid XmlElement as a msg
        logger.debug("channel read element is: %s toString() is: %s", msg.getClass(), msg.toString());
        if (!(msg instanceof XmlElementStart))                                                                                              //we are interested in only XmlElementStart elements
            return;
        commandProccessor.processCommand(ctx.channel(), (XmlElementStart)msg);                                                              //process the command by commandProcessor
        return;
    }


    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        if (cause instanceof ReadTimeoutException) {                                                                                        //read timeout
            logger.warn("client %s read timeout, closing connection", ctx.channel().attr(ServerMain.sockAddrStr).get());
        } else {
            logger.error("exception: %s", cause.getMessage());
        }
        ctx.close();
        return;
    }
}

@Sharable
class PreprocessHandler extends ChannelInboundHandlerAdapter {                                                                              //log and preprocess an incoming message
    private static final Logger logger = LogManager.getFormatterLogger();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress)ctx.channel().remoteAddress();                                                            //get client's address
        String sockStr = String.format("%s:%d", sa.getHostString(), sa.getPort());                                                          //and set it as a channel attribute for future use
        ctx.channel().attr(ServerMain.sockAddrStr).set(sockStr);
        ctx.fireChannelActive();                                                                                                            //call the next handler
        return;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {                                                       //here we have a received String after striping the trailing 0x00
        String rcvd = ((ByteBuf)msg).toString(Charset.forName("UTF-8"));                                                                    //get the context of a received message for the logging purpose
        logger.info("received %d bytes from %s %s, length = %d", ((ByteBuf) msg).readableBytes(), ctx.channel().attr(ServerMain.sockAddrStr).get(), rcvd, rcvd.length());
        ReferenceCountUtil.release(msg);                                                                                                    //we don't need the source ByteBuf anymore, releasing it

        rcvd.replace("\r", "&#xD;");                                                                                                        //replace CR with the corresponding XML code
        String xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><BODY>" + rcvd + "</BODY>";                                           //wrap the source message into XML root elements <BODY>source_message>/BODY>
        ByteBuf out = ctx.alloc().buffer(ByteBufUtil.utf8Bytes(xmlString), ByteBufUtil.utf8MaxBytes(xmlString));                            //allocate a new ByteBuf for the wrapped message
        out.writeCharSequence(xmlString, Charset.forName("UTF-8"));                                                                         //write the wrapped message to the new ByteBuf
        ctx.fireChannelRead(out);                                                                                                           //call the next Channel Inbound Handler with the new ByteBuf
        return;
    }
}

class OutHanlder extends MessageToByteEncoder<String> {                                                                                     //add '\0' (null terminator) to all outbound messages
    private static final Logger logger = LogManager.getFormatterLogger();

    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        logger.info("sending %s to %s", msg, ctx.channel().attr(ServerMain.sockAddrStr).get());                                             //log an outbound message
        out.writeCharSequence(msg, Charset.defaultCharset());
        out.writeZero(1);                                                                                                                   //add terminating 0x00 to the message
        return;
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, String msg, boolean preferDirect) throws Exception {                        //allocate ByteBuff for the outgoing message with a capacity enough to hold one additional byte for the null terminator
        return super.allocateBuffer(ctx, msg, preferDirect).capacity(msg.length() + 1);
    }
}