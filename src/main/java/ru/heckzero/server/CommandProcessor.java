package ru.heckzero.server;

import io.netty.channel.Channel;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;

import java.lang.reflect.Method;

public class CommandProcessor extends DefaultHandler {
    private static final Logger logger = LogManager.getFormatterLogger();
    private final Channel ch;                                                                                                               //the channel we received a command from
    private final String userStr;                                                                                                           //sender string representation
    private final User user;                                                                                                                //a user witch we got a command from, may be empty if it hasn't passed authorization yet

    public CommandProcessor(Channel ch) {
        this.ch = ch;                                                                                                                       //does it need to be commented?
        this.userStr = ch.attr(ServerMain.userStr).get();                                                                                   //get a sender representation from the channel attribute
        this.user = UserManager.getUser(ch);                                                                                                //set a user (by a game channel)
        return;
    }

    @Override
    public void startElement(String uri, String localName, String qName, Attributes attributes) {                                           //this will be called for the every XML element received from the client
        logger.debug("got an XML element: uri: %s, localName: %s, qname: %s, atrrs len = %d", uri, localName, qName, attributes.getLength());
        String handleMethodName = String.format("com_%s", qName);			            													//handler method name to process a command
        logger.debug("trying to find and execute method %s" , handleMethodName);

        if (qName.equals("ROOT"))                                                                                                           //silently ignoring <ROOT/> element
            return;

        if (!qName.equals("LOGIN") && !qName.equals("CHAT") && user.isEmpty()) {
            logger.warn("user is unknown, closing the channel %s", userStr);
            ch.close();
            return;
        }

        try {
            Method handlerMethod = this.getClass().getDeclaredMethod(handleMethodName, Attributes.class);	                                //get a handler method reference
            handlerMethod.invoke(this, attributes);
        }catch (NoSuchMethodException e) {                                                                                                  //method was not found
            logger.warn("cannot process command %s, a method void %s(Attributes) is not yet implemented", qName, handleMethodName);
        }catch (Exception e) {																						                        //method invocation error occurred while executing the handler method
            logger.error("cannot execute method %s: %s", handleMethodName, e.getMessage());
        }
        return;
    }

    private void com_LOGIN(Attributes attrs) {                                                                                              //<LOGIN /> handler
        logger.debug("processing <LOGIN/> command from %s", ch.attr(ServerMain.userStr).get());
        String login = attrs.getValue("l");                                                                                                 //login attribute
        String password = attrs.getValue("p");                                                                                              //password attribute
        new UserManager().loginUser(ch, login, password);                                                                                   //try to set a new user online
        return;
    }

    private void com_GETME(Attributes attrs) {                                                                                              //<GETME/> handler
        logger.debug("processing <GETME/> command from %s", ch.attr(ServerMain.userStr).get());
        user.execCommand(User.Commands.MYPARAM);
        return;
    }

    private void com_N(Attributes attrs) {
        logger.debug("processing <N/> command from %s", ch.attr(ServerMain.userStr).get());
        return;
    }
    private void com_POST(Attributes attrs) {
        logger.debug("processing <POST/> command from %s", ch.attr(ServerMain.userStr).get());
        return;
    }

    private void com_CHAT(Attributes attrs) {                                                                                               //<CHAT/> handler
        logger.debug("processing <CHAT/> command from %s", ch.attr(ServerMain.userStr).get());
        if (attrs.getLength() == 0) {
            ch.writeAndFlush("<CHAT/>");
            return;
        }
        String ses = attrs.getValue("ses");                                                                                                 //chat auth key
        String login = attrs.getValue("l");                                                                                                 //chat login name for chat auth
        new UserManager().loginUserChat(ch, ses, login);
        return;
    }

    @Override
    public void error(SAXParseException e) throws SAXException {                                                                            //this will be called on no-critical errors in XML parsing
        logger.error("XML parse error: %s", e.getMessage());
        return;
    }
}