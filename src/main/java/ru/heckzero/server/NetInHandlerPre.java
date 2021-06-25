package ru.heckzero.server;

import io.netty.buffer.ByteBuf;
import io.netty.buffer.ByteBufUtil;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.channel.ChannelInboundHandlerAdapter;
import io.netty.util.ReferenceCountUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;
import java.nio.charset.StandardCharsets;

@Sharable
class NetInHandlerPre extends ChannelInboundHandlerAdapter {                                                                              //log and preprocess an incoming message
    private static final Logger logger = LogManager.getFormatterLogger();

    @Override
    public void channelActive(ChannelHandlerContext ctx) throws Exception {
        InetSocketAddress sa = (InetSocketAddress)ctx.channel().remoteAddress();                                                            //get client's address
        String sockStr = String.format("%s:%d", sa.getHostString(), sa.getPort());

        ctx.channel().attr(ServerMain.sockAddrStr).set(sockStr);                                                                            //and set it as a channel attribute for a future usage
        ctx.fireChannelActive();                                                                                                            //call the next handler
        return;
    }

    @Override
    public void channelRead(ChannelHandlerContext ctx, Object msg) throws Exception {                                                       //here we have a received String after striping the trailing 0x00 by DelimiterBasedFrameDecoder
        String rcvd = ((ByteBuf)msg).toString(StandardCharsets.UTF_8);                                                                      //get the context of a received message for the logging and preprocessing purpose
        rcvd = rcvd.replace("\r", "&#xD;").trim();                                                                                          //replace CR with the corresponding XML code

        User user = UserManager.getUser(ctx.channel());                                                                                     //try to get a User by Channel
        String sender = user.isEmpty() ? ctx.channel().attr(ServerMain.sockAddrStr).get() : user.getParam("login");                         //set sender string - login or socket address if a User is unknown
        logger.info("received %s from %s, length = %d", rcvd, sender, rcvd.length());                                                       //log the received message

        ReferenceCountUtil.release(msg);                                                                                                    //we don't need the source ByteBuf anymore, releasing it
        String xmlString = "<?xml version=\"1.0\" encoding=\"UTF-8\"?><ROOT>" + rcvd + "</ROOT>";                                           //wrap the source message into XML root elements <BODY>source_message>/BODY>
        ByteBuf out = ctx.alloc().buffer(ByteBufUtil.utf8Bytes(xmlString), ByteBufUtil.utf8MaxBytes(xmlString));                            //allocate a new ByteBuf for the wrapped message
        out.writeCharSequence(xmlString, StandardCharsets.UTF_8);                                                                           //write the wrapped message to the new ByteBuf
        ctx.fireChannelRead(out);                                                                                                           //call the next Channel Inbound Handler with the new ByteBuf
        return;
    }
}
