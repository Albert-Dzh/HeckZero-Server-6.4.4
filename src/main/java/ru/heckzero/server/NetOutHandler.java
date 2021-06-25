package ru.heckzero.server;

import io.netty.buffer.ByteBuf;
import io.netty.channel.ChannelHandlerContext;
import io.netty.handler.codec.MessageToByteEncoder;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.nio.charset.Charset;

public class NetOutHandler extends MessageToByteEncoder<String> {
    private static final Logger logger = LogManager.getFormatterLogger();

    @Override
    protected void encode(ChannelHandlerContext ctx, String msg, ByteBuf out) throws Exception {
        logger.info("sending %s to %s", msg, ctx.channel().attr(ServerMain.sockAddrStr).get());                                             //log an outbound message
        out.writeCharSequence(msg, Charset.defaultCharset());                                                                               //write the source message to a allocated ByteBuf
        out.writeZero(1);                                                                                                                   //add terminating 0x00 byte to the end of the ByteBuf
        return;
    }

    @Override
    protected ByteBuf allocateBuffer(ChannelHandlerContext ctx, String msg, boolean preferDirect) throws Exception {                        //allocate ByteBuff for the outgoing message with a capacity enough to hold one additional byte for the null terminator
        return super.allocateBuffer(ctx, msg, preferDirect).capacity(msg.length() + 1);
    }
}
