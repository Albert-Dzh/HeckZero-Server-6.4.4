package ru.heckzero.server;

import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import ru.heckzero.server.user.UserManager;

import java.lang.reflect.Method;

public class CommandProcessor extends DefaultHandler {
    private static final Logger logger = LogManager.getFormatterLogger();
    private final Channel ch;
    private final UserManager userManager = new UserManager();

    public CommandProcessor(Channel ch) {
        this.ch = ch;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {                       //this will be called for the every XML element received from the client
        logger.debug("got XML element uri: %s, localName: %s, qname:%s", uri, localName, qName);
        String handleMethodName = String.format("com_%s", qName);			            													//handler method name to process a command
        logger.debug("trying to find and execute method %s" , handleMethodName);

        try {
            Method handlerMethod = this.getClass().getDeclaredMethod(handleMethodName, Attributes.class);	                                //get a handler method reference
            handlerMethod.invoke(CommandProcessor.this, attributes);
        }catch (NoSuchMethodException e) {                                                                                                  //method was not found
            logger.warn("cannot process command %s, a method void %s(Attributes) is not yet implemented", qName, handleMethodName);
        }catch (Exception e) {																						                        //method invocation error occurred while executing the handler method
            logger.error("cannot execute method %s: %s", handleMethodName, e.getMessage());
        }
        return;
    }

    private void com_ROOT(Attributes attrs) {                                                                                               //silently ignore <ROOT> element
        logger.debug("silently ignoring a <ROOT> element");
        return;
    }

    private void com_LOGIN(Attributes attrs) {                                                                                              //<LOGIN> handler
        logger.info("authenticating user from %s", ch.attr(ServerMain.sockAddrStr).get());
        String login = attrs.getValue("l");                                                                                                 //login attribute
        String password = attrs.getValue("p");                                                                                              //password attribute
        userManager.loginUser(ch, login, password);                                                                                         //try to set a new user online
        return;
    }

    @Override
    public void error(SAXParseException e) throws SAXException {                                                                            //this will be called on no-critical errors in XML parsing
        logger.error("XML parse error: %s", e.getMessage());
        return;
    }
}
