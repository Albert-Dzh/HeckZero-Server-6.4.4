package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.xml.sax.Attributes;
import org.xml.sax.SAXException;
import org.xml.sax.SAXParseException;
import org.xml.sax.helpers.DefaultHandler;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;

import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.List;
import java.util.StringJoiner;

public class CommandProcessor extends DefaultHandler {
    private static final Logger logger = LogManager.getFormatterLogger();
    private final Channel ch;                                                                                                               //a channel the command came from
    private final User user;                                                                                                                //a user associated with the channel

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
            logger.warn("can't process command %s: method void %s(Attributes) is not yet implemented", qName, handleMethodName);
        }catch (Exception e) {																						                        //method invocation error occurred while executing the handler method
            logger.error("can't execute method %s: %s", handleMethodName, e.getMessage());
            e.printStackTrace();
        }
        return;
    }

    private void com_NOUSER_LIST(Attributes attrs) {                                                                                        //<LIST> initial client request for the list of game servers
        List<String> servers = ServerMain.hzConfiguration.getList(String.class, "ServerList.Server", new ArrayList<>());                    //read server list from the configuration
        StringJoiner sj = new StringJoiner(" ", "<LIST>", "</LIST>");                                                                       //format the resulting XML containing server LIST
        servers.forEach(s -> sj.add(String.format("<SERVER host=\"%s\"%s/>", s, ServerMain.hzConfiguration.getString(String.format("ServerList.Server(%d)[@first]", servers.indexOf(s)), "").transform(f -> f.isEmpty() ? "" : " first=\"" + f + "\"")))); //магия рептилий
        ch.writeAndFlush(sj.toString());
        ch.close();                                                                                                                         //we don't need a sniff socket any more
        return;
    }

    private void com_NOUSER_LOGIN(Attributes attrs) {                                                                                       //<LOGIN /> handler
        logger.debug("processing <LOGIN/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        String login = attrs.getValue("l");                                                                                                 //login attribute
        String password = attrs.getValue("p");                                                                                              //password attribute
        UserManager.loginUser(ch, login, password);                                                                                         //set a new user online
        return;
    }

    private void com_GAME_CHAT(Attributes attrs) {                                                                                          //chat server host request comes from a game channel
        logger.debug("processing <CHAT/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        String xmlReply = String.format("<CHAT server=\"%s\"/>", ServerMain.hzConfiguration.getString("ServerList.ChatServer", StringUtils.EMPTY)); //get and send chat server host from the configuration
        ch.writeAndFlush(xmlReply);
        return;
    }
    private void com_NOUSER_CHAT(Attributes attrs) {                                                                                        //a new CHAT connection from a chat channel arrives
        logger.debug("processing <CHAT/> command from %s", ch.attr(AttributeKey.valueOf("chStr")).get());
        String login = attrs.getValue("l");                                                                                                 //chat authorization login - must much an already registered online user
        String ses = attrs.getValue("ses");                                                                                                 //chat authorization key (was sent by the server to the client in a game authorization phase  inside <OK ses=""> response)
        UserManager.loginUserChat(ch, ses, login);                                                                                          //let the UserManager do the job by authorizing and association the chat channel
        return;
    }

    private void com_GAME_GETME(Attributes attrs) {                                                                                         //initial (just right after the login) request of the user params
        logger.debug("processing <GETME/> command from %s", user.getLogin());
        user.com_MYPARAM();
        return;
    }

    private void com_GAME_GOLOC(Attributes attrs) {                                                                                         //user wants to move to  another location or asks for nearest locations description
        logger.debug("processing <GOLOC/> command from %s", user.getLogin());
        String n = attrs.getValue("n");
        String d = attrs.getValue("d");
        String slow = attrs.getValue("slow");
        String force = attrs.getValue("force");
        String pay = attrs.getValue("pay");
        String t1 = attrs.getValue("t1");
        String t2 = attrs.getValue("t2");

        user.com_GOLOC(n, d, slow, force, pay, t1, t2);
        return;
    }

    private void com_GAME_MMP(Attributes attrs) {                                                                                           //user requests a location set for a 5x5 big map
        String param = attrs.getValue("param");
        user.com_MMP(param);
        return;
    }

    private void com_GAME_BIGMAP(Attributes attrs) {                                                                                        //user requests a world map
        user.com_BIGMAP();
        return;
    }

    private void com_GAME_GOBLD(Attributes attrs) {                                                                                         //user wants to enter a building
        logger.debug("processing <GOBLD/> command from %s", user.getLogin());
        String n = attrs.getValue("n");
        user.com_GOBLD(NumberUtils.toInt(n));
        return;
    }

    private void com_GAME_DROP(Attributes attrs) {                                                                                          //drop an Item from user Box
        String id = attrs.getValue("id");
        String count = attrs.getValue("count");
        user.com_DROP(id, count);
        return;
    }

    private void com_GAME_PR(Attributes attrs) {                                                                                            //portal operations
        String comein = attrs.getValue("comein");
        String id = attrs.getValue("id");
        String new_cost = attrs.getValue("new_cost");
        String to = attrs.getValue("to");
        String d = attrs.getValue("d");
        String a = attrs.getValue("a");
        String s = attrs.getValue("s");
        String c = attrs.getValue("c");

        user.com_PR(comein, id, new_cost, to, d, a, s, c);
        return;
    }

    private void com_GAME_CHECK(Attributes attrs) {                                                                                         //check user items for the expiration
        user.com_CHECK();
        return;
    }

    private void com_GAME_TAKE_ON(Attributes attrs) {                                                                                       //user takes on an item
        String id = attrs.getValue("id");
        String slot = attrs.getValue("slot");
        user.com_TAKE_ON(id, slot);
        return;
    }

    private void com_GAME_TAKE_OFF(Attributes attrs) {                                                                                      //user takes off an item
        String id = attrs.getValue("id");
        user.com_TAKE_OFF(id);
        return;
    }

    private void com_GAME_NEWID(Attributes attrs) {                                                                                         //client just created a new item id
        return;                                                                                                                             //congratulations to the client
    }

    public void com_GAME_AR(Attributes attrs) {                                                                                             //arsenal operation
        String a = attrs.getValue("a");                                                                                                     //item id to take from arsenal
        String d = attrs.getValue("d");                                                                                                     //item id to put to arsenal
        String s = attrs.getValue("s");                                                                                                     //section to place an item to
        String c = attrs.getValue("c");                                                                                                     //item count
        user.com_AR(a, d, s, c);
        return;
    }

    public void com_GAME_LOGOUT(Attributes attrs) {                                                                                         //<LOGOUT/> - user game channel has disconnected
        logger.debug("processing <LOGOUT/> command from %s", user.getLogin());
        user.disconnect();                                                                                                                  //just close the channel and let channelInactive in NetInHandler do the job when the channel gets closed
        return;
    }

    private void com_GAME_SILUET(Attributes attrs) {                                                                                        //user changes its body look
        logger.debug("processing <SILUET/> command from %s", user.getLogin());
        String slt = attrs.getValue("slt");                                                                                                 //siluet attributes
        String set = attrs.getValue("set");
        user.com_SILUET(slt, set);
        return;
    }

    private void com_GAME_N(Attributes attrs) {                                                                                             //NOP (keep alive) packet from the game socket
        logger.debug("processing <N/> command from %s", user.getLogin());
        String id1 = attrs.getValue("id1");
        String id2 = attrs.getValue("id2");
        String i1 = attrs.getValue("i1");
        if (id1 != null && id2 != null && i1 != null)
            user.com_N(id1, id2, i1);                                                                                                       //compare the values
        return;
    }

    public void com_GAME_TO_SECTION(Attributes attrs) {
        String id = attrs.getValue("id");
        String section = attrs.getValue("section");
        user.com_TO_SECTION(id, section);
        return;
    }

    private void com_CHAT_N(Attributes attrs) {                                                                                             //NOP (keep alive) packet from the chat socket
        logger.debug("processing <N/> command from %s", user.getLogin());
        return;
    }

    private void com_CHAT_POST(Attributes attrs) {
        logger.debug("processing <POST/> command from %s", user.getLogin());
        String t = attrs.getValue("t");                                                                                                     //text? attribute of the <POST> request - the message itself
        user.com_POST(t);
        return;
    }

    @Override
    public void error(SAXParseException e) throws SAXException {                                                                            //this will be called on no-critical errors in XML parsing
        logger.error("SAX XML parse error: %s", e.getMessage());
        return;
    }
}
