package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.channel.group.ChannelGroup;
import io.netty.channel.group.DefaultChannelGroup;
import io.netty.util.concurrent.GlobalEventExecutor;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.RejectedExecutionException;

public class CommandProcessor extends DefaultHandler {
    private static final Logger logger = LogManager.getFormatterLogger();

    public CommandProcessor() {
        return;
    }

    private Channel findChannelById(String chId) throws SAXException {                                                                      //find a channel by ID in a ChannelGroup
        return  ServerMain.channelGroup.stream().filter(ch -> ch.id().asLongText().equals(chId)).findFirst().orElseThrow(() -> new SAXException("can't find a channel by ID: " + chId + " a channel has been probably already closed"));
    }

    @Override
    public void startElement(String chId, String localName, String qName, Attributes attributes) throws SAXException {                      //this will be called for the every XML element received from the client, chId as a namespace uri contains channel id
        logger.debug("got an XML element: uri: %s, localName: %s, qname: %s, atrrs len = %d", chId, localName, qName, attributes.getLength());
        String handleMethodName = String.format("com_%s", qName);			            													//handler method name to process a command
        logger.debug("trying to find and execute method %s" , handleMethodName);

        if (qName.equals("ROOT"))                                                                                                           //silently ignoring <ROOT/> element
            return;

        Channel ch = findChannelById(chId);                                                                                                 //XML namespace here contains a channel ID
        User user = UserManager.getUser(ch);

        if (!qName.equals("LOGIN") && !qName.equals("CHAT") && user.isEmpty()) {
            logger.warn("user is unknown, closing the channel %s", ch.attr(ServerMain.userStr).get());
            ch.close();
            return;
        }

        try {
            Method handlerMethod = this.getClass().getDeclaredMethod(handleMethodName, Attributes.class, Channel.class);	                //get a handler method reference
            handlerMethod.invoke(this, attributes, ch);
        }catch (NoSuchMethodException e) {                                                                                                  //method is not found
            logger.warn("cannot process command %s, a method void %s(Attribute, Channel) is not yet implemented", qName, handleMethodName);
        }catch (Exception e) {																						                        //method invocation error occurred while executing the handler method
            logger.error("cannot execute method %s: %s", handleMethodName, e.getMessage());
        }
        return;
    }

    private void com_LOGIN(Attributes attrs, Channel ch) {                                                                                  //<LOGIN /> handler
        logger.debug("processing <LOGIN/> command from %s", ch.attr(ServerMain.userStr).get());
        String login = attrs.getValue("l");                                                                                                 //login attribute
        String password = attrs.getValue("p");                                                                                              //password attribute
        new UserManager().loginUser(ch, login, password);                                                                                   //set a new user online
        return;
    }

    private void com_GETME(Attributes attrs, Channel ch) {
        logger.debug("processing <GETME/> command from %s", ch.attr(ServerMain.userStr).get());
        ServerMain.mainExecutor.execute(() ->  UserManager.getUser(ch).com_MYPARAM());
        return;
    }

    private void com_GOLOC(Attributes attrs, Channel ch) {
        logger.debug("processing <GOLOC/> command from %s", ch.attr(ServerMain.userStr).get());
        UserManager.getUser(ch).com_GOLOC();
        return;
    }

    private void com_SILUET(Attributes attrs, Channel ch) {
        logger.debug("processing <SILUET/> command from %s", ch.attr(ServerMain.userStr).get());
        String slt = attrs.getValue("slt");                                                                                                 //login attribute
        String set = attrs.getValue("set");                                                                                                 //password attribute
        ServerMain.mainExecutor.execute(() -> UserManager.getUser(ch).com_SILUET(slt, set));
        return;
    }

    private void com_CHAT(Attributes attrs, Channel ch) {
        logger.debug("processing <CHAT/> command from %s", ch.attr(ServerMain.userStr).get());
        String login = attrs.getValue("l");                                                                                                 //chat authorization login - must much a registered online user
        String ses = attrs.getValue("ses");                                                                                                 //chat authorization key (was sent by the server to the client in authorization phase in <OK ses=""> response)
        new UserManager().loginUserChat(ch, ses, login);
        return;
    }
    private void com_N(Attributes attrs, Channel ch) {
        logger.debug("processing <N/> command from %s", ch.attr(ServerMain.userStr).get());
        return;
    }
    private void com_POST(Attributes attrs, Channel ch) {
        logger.debug("processing <POST/> command from %s", ch.attr(ServerMain.userStr).get());
        return;
    }


    @Override
    public void error(SAXParseException e) throws SAXException {                                                                            //this will be called on no-critical errors in XML parsing
        logger.error("XML parse error: %s", e.getMessage());
        return;
    }
}
