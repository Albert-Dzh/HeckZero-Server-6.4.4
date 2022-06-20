package ru.heckzero.server.utils;

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
import ru.heckzero.server.ServerMain;
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

    private void com_GAME_TRANSFER(Attributes attrs) {                                                                                      //transfer money between users
        logger.debug("processing <TRANSFER/> command from user %s", user.getLogin());
        String login = StringUtils.defaultString(attrs.getValue("l"));                                                                      //money receiver login
        String r = StringUtils.defaultString(attrs.getValue("r"));                                                                          //transfer description
        int t = NumberUtils.toInt(attrs.getValue("t"), -1);                                                                                 //type of coins
        int c = NumberUtils.toInt(attrs.getValue("c"), -1);                                                                                 //count of copper coins
        user.com_TRANSFER(login, r, t, c);
        return;
    }

    private void com_GAME_MR(Attributes attrs) {                                                                                            //city hall workflow
        logger.debug("processing <MR/> command from user %s", user.getLogin());
        int p1 = NumberUtils.toInt(attrs.getValue("p1"), -1);                                                                               //passport cost
        int p2 = NumberUtils.toInt(attrs.getValue("p2"), -1);                                                                               //citizenship monthly fee
        int d1 = NumberUtils.toInt(attrs.getValue("d1"), -1);                                                                               //holiday outfit rent
        int ds = NumberUtils.toInt(attrs.getValue("ds"), -1);                                                                               //license discount for citizens
        int o  = NumberUtils.toInt(attrs.getValue("o"), -1);                                                                                //flag to sell license only to citizens of this city
        int vip = NumberUtils.toInt(attrs.getValue("vip"), -1);                                                                             //user buys a VIP status
        int img = NumberUtils.toInt(attrs.getValue("img"), -1);                                                                             //passport selected image
        int lic = NumberUtils.toInt(attrs.getValue("lic"), -1);                                                                             //buying licenses
        int buy = NumberUtils.toInt(attrs.getValue("buy"), -1);                                                                             //license id to buy
        int count = NumberUtils.toInt(attrs.getValue("count"), -1);                                                                         //license count to buy
        int citizenship = NumberUtils.toInt(attrs.getValue("citizenship"), -1);                                                             //citizenship required
        String m1 = StringUtils.defaultString(attrs.getValue("m1"));                                                                        //name of new mayor and his assistant
        int mod = NumberUtils.toInt(attrs.getValue("mod"), -1);                                                                             //item id to be modified
        String paint = StringUtils.defaultString(attrs.getValue("paint"));                                                                  //ids of items that user wants to paint
        String color = StringUtils.defaultString(attrs.getValue("color"));                                                                  //new color of recolored item
        String trade = StringUtils.defaultString(attrs.getValue("trade"));                                                                  //register a new trademark
        int tax = NumberUtils.toInt(attrs.getValue("tax"), -1);                                                                             //tax for citizenship
        int ch = NumberUtils.toInt(attrs.getValue("ch"), -1);                                                                               //license cost changing (license id)
        int cost = NumberUtils.toInt(attrs.getValue("cost"), -1);                                                                           //license cost changing (a new cost)
        int w = NumberUtils.toInt(attrs.getValue("w"), -1);                                                                                 //wedding dress rent
        int get = NumberUtils.toInt(attrs.getValue("get"), -1);                                                                             //money withdraw


        user.com_MR(p1, p2, d1, ds, m1, o, vip, citizenship, img, lic, buy, count, mod, paint, color, tax, ch, cost, w, trade, get);
        return;
    }

    private void com_GAME_USE(Attributes attrs) {                                                                                           //do something with an Item
        long vip = NumberUtils.toLong(attrs.getValue("vip"), -1);                                                                           //VIP card id
        int set = NumberUtils.toInt(attrs.getValue("set"), -1);                                                                             //set VIP card visibility
        user.com_USE(vip, set);
        return;
    }

    private void com_GAME_EX(Attributes attrs) {                                                                                            //exchange silver/gold to copper
        logger.debug("processing <EX/> command from user %s", user.getLogin());
        int t1 = NumberUtils.toInt(attrs.getValue("t1"), -1);                                                                               //from type (1 - copper, 2 - silver, 3 - gold)
        int t2 = NumberUtils.toInt(attrs.getValue("t2"), -1);                                                                               //to type (1 - copper, 2 - silver, 3 - gold)
        double c = NumberUtils.toFloat(attrs.getValue("c"), 0);                                                                             //amount of money to exchange
        user.com_EX(t1, t2, c);
        return;
    }

    private void com_GAME_GETINFO(Attributes attrs) {                                                                                       //user information query
        logger.debug("processing <GETINFO/> command from user %s", user.getLogin());
        String login = attrs.getValue("login");                                                                                             //login attribute
        int details = NumberUtils.toInt(attrs.getValue("details"), -1);                                                                     //extended user info request
        user.com_GETINFO(login, details);
        return;
    }

    private void com_GAME_PT(Attributes attrs) {                                                                                            //post workflow
        logger.debug("processing <PT/> command from user %s", user.getLogin());
        int get = NumberUtils.toInt(attrs.getValue("get"), -1);                                                                             //user wants to get some cash from post office
        int me = NumberUtils.toInt(attrs.getValue("me"), -1);                                                                               //user checks if there are parcels for him
        int p1 = NumberUtils.toInt(attrs.getValue("p1"), -1);                                                                               //set p1 - wire cost
        int p2 = NumberUtils.toInt(attrs.getValue("p2"), -1);                                                                               //set p2 - parcel cost
        int d1 = NumberUtils.toInt(attrs.getValue("d1"), -1);                                                                               //set d1 - urgent parcel costs
        long a = NumberUtils.toLong(attrs.getValue("a"), -1);                                                                               //user takes an item from parcel - id
        int s = NumberUtils.toInt(attrs.getValue("s"), 0);                                                                                  //section to place an item into
        int c = NumberUtils.toInt(attrs.getValue("c"), 0);                                                                                  //item count
        String login = attrs.getValue("login");                                                                                             //wire recipient
        String wire = attrs.getValue("wire");                                                                                               //send a wire to user 'login' (wire content)
        String parcel = attrs.getValue("parcel");                                                                                           //parcel recipient
        String itm = attrs.getValue("itm");                                                                                                 //items to send in parcel
        int fast = NumberUtils.toInt(attrs.getValue("fast"));                                                                               //urgent delivery

        user.com_PT(get, me, p1, p2, d1, a, c, s, login, wire, parcel, itm, fast);
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
        user.com_DROP(NumberUtils.toLong(id), NumberUtils.toInt(count, -1));
        return;
    }

    private void com_GAME_PR(Attributes attrs) {                                                                                            //portal operations
        int comein = NumberUtils.toInt(attrs.getValue("comein"));                                                                           //incoming route list request
        int id = NumberUtils.toInt(attrs.getValue("id"), -1);                                                                               //changing an incoming route cost
        double new_cost = NumberUtils.toDouble(attrs.getValue("new_cost"), -1.0);                                                           //new incoming route cost
        int to = NumberUtils.toInt(attrs.getValue("to"), -1);                                                                               //destination route id
        long d = NumberUtils.toLong(attrs.getValue("d"), -1);                                                                               //item id user puts to the portal
        long a = NumberUtils.toLong(attrs.getValue("a"), -1);                                                                               //item id user takes from portal
        int s = NumberUtils.toInt(attrs.getValue("s"));                                                                                     //section to put the item into
        int c = NumberUtils.toInt(attrs.getValue("c"), -1);                                                                                 //item count
        int get = NumberUtils.toInt(attrs.getValue("get"), -1);                                                                             //amount of money user takes from portal cache
        int ds = NumberUtils.toInt(attrs.getValue("ds"), -1);                                                                               //portal discount for the citizens

        user.com_PR(comein, id, new_cost, to, d, a, s, c, get, ds);
        return;
    }

    private void com_GAME_BK(Attributes attrs) {                                                                                            //bank workflow
        int put = NumberUtils.toInt(attrs.getValue("put"));                                                                                 //put money to bank cash
        int get = NumberUtils.toInt(attrs.getValue("get"));                                                                                 //get money from bank cash
        int cost = NumberUtils.toInt(attrs.getValue("cost"), -1);                                                                           //new cell cost
        int cost2 = NumberUtils.toInt(attrs.getValue("cost2"), -1);                                                                         //cell monthly rent cost
        int buy = NumberUtils.toInt(attrs.getValue("buy"));                                                                                 //buy a new cell
        int go = NumberUtils.toInt(attrs.getValue("go"));                                                                                   //open a cell
        int sell = NumberUtils.toInt(attrs.getValue("sell"), -1);                                                                           //cell id
        int sell2 = NumberUtils.toInt(attrs.getValue("sell2"), -1);                                                                         //cell id to transfer items to
        long a = NumberUtils.toLong(attrs.getValue("a"), -1);                                                                               //item id to take from the bank cell
        long d = NumberUtils.toLong(attrs.getValue("d"), -1);                                                                               //item id to put to a cell
        int s = NumberUtils.toInt(attrs.getValue("s"), -1);                                                                                 //section
        int c = NumberUtils.toInt(attrs.getValue("c"), -1);                                                                                 //count
        long f = NumberUtils.toLong(attrs.getValue("f"), -1);                                                                               //item id, whose section is to be changed
        int newkey = NumberUtils.toInt(attrs.getValue("newkey"), -1);                                                                       //duplicate cell key request
        int addsection = NumberUtils.toInt(attrs.getValue("addsection"));                                                                   //add an additional section to the cell
        int extend = NumberUtils.toInt(attrs.getValue("extend"));                                                                           //increase cell capacity
        int check_sell = NumberUtils.toInt(attrs.getValue("check_sell"), -1);                                                               //cell account to transfer resources to
        int tr = NumberUtils.toInt(attrs.getValue("tr"));                                                                                   //transfer resources between cells

        String p = attrs.getValue("p");
        String newpsw = attrs.getValue("newpsw");
        String newemail = attrs.getValue("newemail");

        user.com_BK(put, get, cost, cost2, buy, p, newpsw, newemail, go, sell, d, s, c, f, a, newkey, addsection, extend, check_sell, tr, sell2);
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
        user.com_NEWID();                                                                                                                   //we might send a new ID2 to the client
        return;                                                                                                                             //congratulations to the client
    }

    private void com_GAME_GETH(Attributes attrs) {                                                                                          //player requests log
        String login = attrs.getValue("login");                                                                                             //login for which log is requested
        String date = attrs.getValue("date");                                                                                               //requested date
        String dx = attrs.getValue("dx");                                                                                                   //date shift (+/-) from the last date the log was sent
        String b = attrs.getValue("b");                                                                                                     //request from a policeman
        user.com_HISTORY(login, date, dx, b);
        return;
    }

    private void com_GAME_CLIMS(Attributes attrs) {                                                                                         //clear user IMS messages (set ims flag to 0)
        user.com_CLIMS();
        return;
    }

    public void com_GAME_AR(Attributes attrs) {                                                                                             //arsenal operation
        long a = NumberUtils.toLong(attrs.getValue("a"), -1);                                                                               //item id user takes from arsenal
        long d = NumberUtils.toLong(attrs.getValue("d"), -1);                                                                               //item id user puts to arsenal
        int  s = NumberUtils.toInt(attrs.getValue("s"), 0);                                                                                 //section to place an item to
        int  c = NumberUtils.toInt(attrs.getValue("c"), -1);                                                                                //item count
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
        user.com_N(id1, id2, i1);                                                                                                           //compare client values with server ones
        return;
    }

    public void com_GAME_TO_SECTION(Attributes attrs) {
        String id = attrs.getValue("id");
        String section = attrs.getValue("section");
        user.com_TO_SECTION(NumberUtils.toLong(id), section);
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
