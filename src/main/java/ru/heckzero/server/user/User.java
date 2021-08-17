package ru.heckzero.server.user;

import io.netty.channel.Channel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Arrays;
import java.util.Calendar;
import java.util.EnumSet;
import java.util.StringJoiner;
import java.util.stream.Collectors;
import java.util.stream.Stream;

@Entity(name = "User")
@Table(name = "users")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class User {
    public enum ChannelType {NOUSER, GAME, CHAT}                                                                                            //user channel type, set on login by online() and chatOn() methods
    public enum Params {login, password, email, reg_time, lastlogin, lastlogout, lastclantime, loc_time, cure_time, god, hint, exp, pro, propwr,rank_points, clan, str, dex, intu, pow, acc, intel, X, Y, Z, hz, ROOM, id1, id2, i1, bot, siluet, dismiss}  //all possible params that can be accessed via get/setParam()

    @Transient
    public EnumSet<Params> getmeParams = EnumSet.of(Params.login, Params.password, Params.email, Params.reg_time, Params.lastlogin, Params.lastlogout, Params.lastclantime, Params.loc_time, Params.cure_time, Params.god, Params.hint, Params.exp);

    private static final Logger logger = LogManager.getFormatterLogger();

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generator_sequence")
    @SequenceGenerator(name = "generator_sequence", sequenceName = "users_id_seq", allocationSize = 1)
    private Integer id;

    @Embedded
    private final UserParams params = new UserParams();                                                                                     //user params that can be set (read-write) are placed there

    @Transient volatile private Channel gameChannel = null;                                                                                 //user game socket
    @Transient volatile private Channel chatChannel = null;                                                                                 //user chat socket

    public Channel getGameChannel() { return this.gameChannel;}
    public Channel getChatChannel() { return this.chatChannel;}

    public User() { }                                                                                                                       //default constructor
    public void setId(Integer id) {this.id = id;}

    public boolean isEmpty() {return id == null;}                                                                                           //user is empty (having empty params)
    public boolean isOnlineGame() {return gameChannel != null;}                                                                             //this user has a game channel assigned
    public boolean isOnlineChat() {return chatChannel != null;}                                                                             //this user has a chat channel assigned
    public boolean isOffline() {return !(isOnlineGame() || isOnlineChat());}                                                                //user is offline
    public boolean isBot() {return !getParamStr(Params.bot).isEmpty();}                                                                     //user is a bot (not a human)
    public boolean isCop() {return getParamStr(Params.clan).equals("police");}                                                              //user is a cop (is a member of police clan)
    public boolean isInBattle() {return false;}                                                                                             //just a stub yet, take some cognac when you are about to change this method

    public String getLogin() {return getParamStr(Params.login);}                                                                            //just a shortcut
    private Long getParam_time() {return Instant.now().getEpochSecond();}                                                                   //always return epoch time is seconds
    private Integer getParam_tdt() {return Calendar.getInstance().getTimeZone().getOffset(Instant.now().getEpochSecond() / 3600L);}         //user time zone, used in user history log

    public Integer getParamInt(Params param) {return NumberUtils.toInt(getParamStr(param));}
    public Long getParamLong(Params param) {return NumberUtils.toLong(getParamStr(param));}
    public Double getParamDouble(Params param) {return NumberUtils.toDouble(getParamStr(param));}
    public String getParamXml(Params param, boolean appendEmpty) {
        String paramValue = getParamStr(param);
        return !paramValue.isEmpty() || appendEmpty ? String.format("%s=\"%s\"", param == Params.intu ? "int" : param.toString(), paramValue) : StringUtil.EMPTY_STRING;
    }
    private String getParamsXml(EnumSet<Params> params, boolean appendEmpty) {
        return params.stream().map(p -> getParamXml(p, appendEmpty)).filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));
    }

    public String getParamStr(Params param) {                                                                                               //get user param value (param must be in Params enum)
        try {                                                                                                                               //try to find param in UserParam instance
            return params.getParam(param);
        } catch (Exception e) {logger.debug("cannot find param in UserParams params, gonna look for a special method to compute that param");}

        String paramName = param.toString();                                                                                                //param name as a String
        String methodName = String.format("getParam_%s", paramName);
        try {                                                                                                                               //if not found in params. try to compute the param value via the dedicated method
            Method method = this.getClass().getDeclaredMethod(methodName);
            return method.invoke(this).toString();
        } catch (Exception e) {
            logger.warn("cannot find or compute param %s, neither in User params nor by a dedicated method: %s", paramName, e.getMessage());
        }
        return StringUtil.EMPTY_STRING;                                                                                                     //return an empty string as a default value
    }
    public void setParam(Params paramName, Integer paramValue) {setParam(paramName, paramValue.toString());}                                //set param to Integer value
    public void setParam(Params paramName, Long paramValue) {setParam(paramName, paramValue.toString());}                                   //set param to Long value
    public void setParam(Params paramName, Double paramValue) {setParam(paramName, paramValue.toString());}                                 //set param to Double value
    public void setParam(Params paramName, String paramValue) {this.params.setParam(paramName, paramValue);}                                //set param to String value



    synchronized void onlineGame(Channel ch) {
        logger.debug("setting user '%s' game channel online", getLogin());

        this.gameChannel = ch;                                                                                                              //set user game channel
        this.gameChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.GAME);                                                        //set the user channel type to GAME
        this.gameChannel.attr(AttributeKey.valueOf("chStr")).set("user '" + getLogin() + "'");                                              //replace a client representation string to 'user <login>' instead of IP:port
        this.gameChannel.pipeline().replace("socketIdleHandler", "userIdleHandler", new ReadTimeoutHandler(ServerMain.hzConfiguration.getInt("ServerSetup.MaxUserIdleTime", ServerMain.DEF_MAX_USER_IDLE_TIME))); //replace read timeout handler to a new one with a longer timeout defined for authorized user
        setParam(Params.lastlogin, Instant.now().getEpochSecond());                                                                         //set user last login time, needed to compute loc_time
        String resultMsg = String.format("<OK l=\"%s\" ses=\"%s\"/>", getLogin(), ch.attr(AttributeKey.valueOf("encKey")).get());           //send OK with a chat auth key in ses attribute (using already existing key)
        sendMsg(resultMsg);
        return;
    }

    synchronized void offlineGame() {
        logger.debug("setting user '%s' game channel offline", getLogin());
        this.gameChannel = null;                                                                                                            //a marker that user is offline now
        disconnectChat();                                                                                                                   //chat without a game is ridiculous
        notifyAll();                                                                                                                        //awake all threads waiting for the user to get offline
        logger.info("user '%s' game channel logged out", getLogin());
        return;
    }

    synchronized void onlineChat(Channel ch) {
        logger.debug("turning user '%s' chat on", getLogin());
        this.chatChannel = ch;
        this.chatChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.CHAT);
        this.chatChannel.attr(AttributeKey.valueOf("chStr")).set("user '" + getLogin() + "' (chat)");
        this.chatChannel.pipeline().replace("socketIdleHandler", "userIdleHandler", new ReadTimeoutHandler(ServerMain.hzConfiguration.getInt("ServerSetup.MaxUserIdleTime", ServerMain.DEF_MAX_USER_IDLE_TIME)));
        return;
    }
    synchronized void offlineChat() {
        logger.debug("turning user '%s' chat off", getLogin());
        this.chatChannel = null;
        notifyAll();
        logger.info("user '%s' chat channel logged out", getLogin());
        return;
    }

    public void com_MYPARAM() {
        logger.info("processing <GETME/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());
        StringJoiner xmlGetme = new StringJoiner(" ", "<MYPARAM ",   "></MYPARAM>").add(getParamsXml(getmeParams, false));
        sendMsg(xmlGetme.toString());
        return;
    }
    public void com_GOLOC() {
        logger.info("processing <GOLOC/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());
        String xml = String.format("<GOLOC><L/></GOLOC>");
        sendMsg(xml);
        return;
    }

    public void com_SILUET(String slt, String set) {
        logger.info("processing <SILUET/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")));
        logger.info("slt = %s, set = %s", slt, set);
        setParam(Params.siluet, set);
        String response = String.format("<SILUET code=\"0\"/><MYPARAM siluet=\"%s\"/>",  set);
        sendMsg(response);
        return;
    }


    public void sendMsg(String msg) {sendMsg(gameChannel, msg);}                                                                            //send a message to the game socket
    public void sendMsgChat(String msg) {sendMsg(chatChannel, msg);}                                                                        //send a message to the chat socket
    private void sendMsg(Channel ch, String msg) {
        if (ch == null || !ch.isActive())
            return;
        ch.writeAndFlush(msg);
        return;
    }

    public void disconnect() {disconnect(gameChannel);}                                                                                     //disconnect (close) the game channel
    public void disconnect(String msg) {sendMsg(msg); disconnect();}                                                                        //send message and disconnect the channel
    public void disconnectChat() {disconnect(chatChannel);}
    public void disconnectChat(String msg) {sendMsgChat(msg); disconnectChat();}
    private void disconnect(Channel ch) {
        if (ch == null || !ch.isActive())                                                                                                   //nothing to do
            return;
        ch.close();
        return;
    }
}
