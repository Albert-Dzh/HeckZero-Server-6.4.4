package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;

import java.lang.reflect.Method;
import java.util.NoSuchElementException;

public class CommandProcessor extends DefaultHandler {
    private static final Logger logger = LogManager.getFormatterLogger();
    private final Channel ch;
    private final User user;

    public CommandProcessor(Channel ch) {
        this.ch = ch;                                                                                                                       //the channel the command came from
        this.user = UserManager.getOnlineUser(ch);                                                                                          //a user associated with the channel
        return;
    }

    @Override
    public void startElement(String chId, String localName, String qName, Attributes attributes) throws SAXException {                      //this will be called for the every XML element received from the client, chId as a namespace uri contains channel id
        logger.debug("got an XML element: uri: %s, localName: %s, qname: %s, atrrs len = %d", chId, localName, qName, attributes.getLength());

        if (qName.equals("ROOT"))                                                                                                           //silently ignoring <ROOT/> element
            return;

        String handleMethodName = String.format("com_%s_%s", ((User.ChannelType)ch.attr(AttributeKey.valueOf("chType")).get()).name(), qName); //handler method name to process a user command
        logger.debug("trying to find and execute method %s", handleMethodName);
        try {
            Method handlerMethod = this.getClass().getDeclaredMethod(handleMethodName, Attributes.class);	                                //get a handler method reference
            handlerMethod.invoke(this, attributes);
        }catch (NoSuchMethodException e) {                                                                                                  //corresponding method is not found
            logger.warn("can't process command %s: method void %s(Attribute, Channel) is not yet implemented", qName, handleMethodName);
        }catch (Exception e) {																						                        //method invocation error occurred while executing the handler method
            logger.error("can't execute method %s: %s", handleMethodName, e.getMessage());
        }
        return;
    }

    private void com_GAME_GETME(Attributes attrs) {
        logger.debug("processing <GETME/> command from %s", user.getLogin());
        ServerMain.mainExecutor.execute(user::com_MYPARAM);
        return;
    }

    private void com_GAME_GOLOC(Attributes attrs) {
        logger.debug("processing <GOLOC/> command from %s", user.getLogin());
        ServerMain.mainExecutor.execute(user::com_GOLOC);
        return;
    }

    private void com_NOUSER_LOGIN(Attributes attrs) {                                                                                       //<LOGIN /> handler
        logger.debug("processing <LOGIN/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        String login = attrs.getValue("l");                                                                                                 //login attribute
        String password = attrs.getValue("p");                                                                                              //password attribute
        UserManager.loginUser(ch, login, password);                                                                                         //set a new user online
        return;
    }

    public void com_GAME_LOGOUT(Attributes attrs) {                                                                                         //<LOGOUT/> handler
        logger.debug("processing <LOGOUT/> command from %s", user.getLogin());
        user.disconnect();                                                                                                                  //just close the channel and let channelInactive in NetInHandler do the job  when the channel gets closed
        return;
    }

    private void com_GAME_SILUET(Attributes attrs) {
        logger.debug("processing <SILUET/> command from %s", user.getLogin());
        String slt = attrs.getValue("slt");                                                                                                 //siluet attributes
        String set = attrs.getValue("set");
        ServerMain.mainExecutor.execute(() -> user.com_SILUET(slt, set));
        return;
    }


    private void com_NOUSER_CHAT(Attributes attrs) {
        logger.debug("processing <CHAT/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        String login = attrs.getValue("l");                                                                                                 //chat authorization login - must much a registered online user
        String ses = attrs.getValue("ses");                                                                                                 //chat authorization key (was sent by the server to the client in authorization phase in <OK ses=""> response)
        UserManager.loginUserChat(ch, ses, login);
        return;
    }
    private void com_GAME_N(Attributes attrs) {
        logger.debug("processing <N/> command from %s", user.getLogin());
        return;
    }

    private void com_CHAT_N(Attributes attrs) {
        logger.debug("processing <N/> command from %s", user.getLogin());
        return;
    }

    private void com_CHAT_POST(Attributes attrs) {
        logger.debug("processing <POST/> command from %s", user.getLogin());
        return;
    }

    @Override
    public void error(SAXParseException e) throws SAXException {                                                                            //this will be called on no-critical errors in XML parsing
        logger.error("SAX XML parse error: %s", e.getMessage());
        return;
    }
}
