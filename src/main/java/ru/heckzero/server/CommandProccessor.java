package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.handler.codec.xml.XmlElementStart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.net.InetSocketAddress;

public class CommandProccessor {
    private static final Logger logger = LogManager.getFormatterLogger();
    private UserManager userManager = new UserManager();
    public CommandProccessor() { }

    public void processCommand(Channel ch, XmlElementStart command) {
        String commandName = command.name();                                                                                                //XML element name (command from a client)
        switch (commandName) {
            case "BODY":                                                                                                                    //silently ignore <BODY> element
                break;
            case "LOGIN":
                InetSocketAddress sa = (InetSocketAddress)ch.remoteAddress();                                                               //client's socket address
                logger.info("authenticating user from %s:%d", sa.getHostString(), sa.getPort());
                User user = userManager.loginUser(ch, command);
                if (user == null)
                    ch.writeAndFlush("<ERROR code = \"3\" />");
                break;
            default:
                logger.warn("command %s is not implemented", commandName);
                break;
        }

        return;
    }
    private void sendError(Channel ch, int errCode, String errMsg) {

        return;
    }

}
