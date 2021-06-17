package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.handler.codec.xml.XmlElementStart;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

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
                logger.info("authenticating user from %s", ch.attr(ServerMain.sockAddrStr).get());
                userManager.loginUser(ch, command);
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
