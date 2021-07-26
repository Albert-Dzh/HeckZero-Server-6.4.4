package ru.heckzero.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandler.Sharable;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;

import java.nio.charset.Charset;

@Sharable
public class NetOutHandler extends MessageToByteEncoder<String> {                                                                           //the outbound handler has the two primary purposes
    private static final Logger logger = LogManager.getFormatterLogger();                                                                   //1)log out the outbound message
                                                                                                                                            //2)add 0x00 byte the the end of the outbound message to conform flash XML socket requests
    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        User user = UserManager.getUser(ctx.channel());                                                                                     //try to get a User by a Channel
        String rcptStr = user.isEmpty() ? ctx.channel().attr(ServerMain.sockAddrStr).get() : "user " + user.getParam("login");              //set a "from" string  for the logging purpose - user login or a socket address if a User is unknown

        logger.info("sending %s to %s", msg, rcptStr);                                                                                      //log the outbound message
        out.writeCharSequence(msg, Charset.defaultCharset());                                                                               //write the source message to a allocated ByteBuf
        out.writeZero(1);                                                                                                                   //add a terminating 0x00 byte to the end of the ByteBuf
        return;
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, String msg, boolean preferDirect) throws Exception {                        //allocate ByteBuff for the outgoing message with a capacity enough to hold one additional byte for the null terminator
        return super.allocateBuffer(ctx, msg, preferDirect).capacity(msg.length() + 1);
    }

    @Override
    public void exceptionCaught(ChannelHandlerContext ctx, Throwable cause) throws Exception {
        super.exceptionCaught(ctx, cause);
    }
}
