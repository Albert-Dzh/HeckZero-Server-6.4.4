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

    public CommandProcessor() { }                                                                                                           //default constructor

    private Channel findChannelById(String chId) {                                                                                          //search for a channel by ID in a ServerMain.ChannelGroup
        return ServerMain.channelGroup.stream().filter(ch -> ch.id().asLongText().equals(chId)).findFirst().orElseThrow(() -> new NoSuchElementException("can't find channel with id " + chId + " in ChannelGroup"));                   //the channel might be already closed
    }

    private boolean sanityCheck(Channel ch, User user, String command) {
        boolean isSpecialCommand = command.equals("LOGIN") || command.equals("CHAT");                                                       //is the command we just received a "special"
        if (isSpecialCommand && !user.isEmpty()) {
            logger.warn("found an online user %s which has channel Id %s set as %s channel. This is abnormal for the %s command, I'm about to close the channel. Pay attention to it!", user.getLogin(), ch.id().asLongText(), ((User.ChannelType)ch.attr(AttributeKey.valueOf("chType")).get()).name(), command);
            return false;
        }
        if (!isSpecialCommand && user.isEmpty()) {
            logger.warn("cannot find an online user which has channel Id %s. This is abnormal for a regular command, I'm about to close the channel. Pay attention to it!",  ch.id().asLongText());
            return false;
        }
        return true;
    }

    @Override
    public void startElement(String chId, String localName, String qName, Attributes attributes) throws SAXException {                      //this will be called for the every XML element received from the client, chId as a namespace uri contains channel id
        logger.debug("got an XML element: uri: %s, localName: %s, qname: %s, atrrs len = %d", chId, localName, qName, attributes.getLength());

        if (qName.equals("ROOT"))                                                                                                           //silently ignoring <ROOT/> element
            return;

        Channel ch = findChannelById(chId);                                                                                                 //XML namespace param (chId) contains a channel ID
        User user = UserManager.getOnlineUser(ch);                                                                                          //get an online user having it;s game or chat channel set to ch
        if (!sanityCheck(ch, user, qName)) {                                                                                                //check if it is a legal for the command to come from this channel
            ch.close();
            return;
        }

        switch (qName) {                                                                                                                    //CHAT and LOGIN are special case, call the corresponding method explicitly with Channel instead of User as a param
            case "LOGIN" -> {com_LOGIN(attributes, ch); return;}
            case "CHAT" -> {com_CHAT(attributes, ch); return;}
        }
        String handleMethodName = String.format("com_%s_%s", ((User.ChannelType)ch.attr(AttributeKey.valueOf("chType")).get()).name(), qName); //handler method name to process a user command
        logger.debug("trying to find and execute method %s" , handleMethodName);

        try {
            Method handlerMethod = this.getClass().getDeclaredMethod(handleMethodName, Attributes.class, User.class);	                    //get a handler method reference
            handlerMethod.invoke(this, attributes, user);
        }catch (NoSuchMethodException e) {                                                                                                  //method is not found
            logger.warn("cannot process command %s, a method void %s(Attribute, Channel) is not yet implemented", qName, handleMethodName);
        }catch (Exception e) {																						                        //method invocation error occurred while executing the handler method
            logger.error("cannot execute method %s: %s", handleMethodName, e.getMessage());
        }
        return;
    }

    private void com_GAME_GETME(Attributes attrs, User u) {
        logger.debug("processing <GETME/> command from %s", u.getLogin());
        ServerMain.mainExecutor.execute(u::com_MYPARAM);
        return;
    }

    private void com_GAME_GOLOC(Attributes attrs, User u) {
        logger.debug("processing <GOLOC/> command from %s", u.getLogin());
        ServerMain.mainExecutor.execute(u::com_GOLOC);
        return;
    }

    private void com_LOGIN(Attributes attrs, Channel ch) {                                                                                  //<LOGIN /> handler
        logger.debug("processing <LOGIN/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        String login = attrs.getValue("l");                                                                                                 //login attribute
        String password = attrs.getValue("p");                                                                                              //password attribute
        new UserManager().loginUser(ch, login, password);                                                                                   //set a new user online
        return;
    }

    public void com_GAME_LOGOUT(Attributes attrs, User u) {                                                                                 //<LOGOUT/> handler
        logger.debug("processing <LOGOUT/> command from %s", u.getLogin());
        u.getGameChannel().close();                                                                                                         //just close the channel and let channelInactive in NetInHandler do the job  when the channel gets closed
        return;
    }

    private void com_GAME_SILUET(Attributes attrs, User u) {
        logger.debug("processing <SILUET/> command from %s", u.getLogin());
        String slt = attrs.getValue("slt");                                                                                                 //siluet attributes
        String set = attrs.getValue("set");
        ServerMain.mainExecutor.execute(() -> u.com_SILUET(slt, set));
        return;
    }


    private void com_CHAT(Attributes attrs, Channel ch) {
        logger.debug("processing <CHAT/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        String login = attrs.getValue("l");                                                                                                 //chat authorization login - must much a registered online user
        String ses = attrs.getValue("ses");                                                                                                 //chat authorization key (was sent by the server to the client in authorization phase in <OK ses=""> response)
        new UserManager().loginUserChat(ch, ses, login);
        return;
    }
    private void com_GAME_N(Attributes attrs, User u) {
        logger.debug("processing <N/> command from %s", u.getLogin());
        return;
    }

    private void com_CHAT_N(Attributes attrs, User u) {
        logger.debug("processing <N/> command from %s", u.getLogin());
        return;
    }

    private void com_CHAT_POST(Attributes attrs, User u) {
        logger.debug("processing <POST/> command from %s", u.getLogin());
        return;
    }

    @Override
    public void error(SAXParseException e) throws SAXException {                                                                            //this will be called on no-critical errors in XML parsing
        logger.error("XML parse error: %s", e.getMessage());
        return;
    }
}
