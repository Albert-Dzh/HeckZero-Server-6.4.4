package ru.heckzero.server;

import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;

import java.lang.reflect.Method;

public class CommandProccessor extends DefaultHandler {
    private static final Logger logger = LogManager.getFormatterLogger();
    private Channel ch;
    private UserManager userManager = new UserManager();

    public CommandProccessor(Channel ch) {
        this.ch = ch;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) throws SAXException {                       //this will be called for the every XML element received from the client
        logger.debug("got XML element uri: %s, localName: %s, qname:%s", uri, localName, qName);
        String handleMethodName = String.format("com_%s", qName);			            													//handler method name to process a command
        logger.debug("trying to find and executed method %s in this class", handleMethodName);

        try {
            Method handlerMethod = this.getClass().getDeclaredMethod(handleMethodName, Attributes.class);	                                //handler method reference
            handlerMethod.invoke(CommandProccessor.this, attributes);
        } catch (NoSuchMethodException e) {
            logger.warn("cannot process command %s, a method void %s(Attributes) is not yet implemented", qName, handleMethodName);
        }catch (Exception e) {																						                        //handler is not defined or method invocation error occurred
            logger.error("cannot execute method %s: %s", handleMethodName, e.getMessage());
        }
        return;
    }

    private void com_ROOT(Attributes attrs) {
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
