package ru.heckzero.server.user;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.channel.ChannelFutureListener;
import io.netty.util.AttributeKey;
import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.Calendar;
import java.util.concurrent.CountDownLatch;

@Entity(name = "User")
@Table(name = "users")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class User {
    public enum ChannelType {NOUSER, GAME, CHAT}                                                                                            //user channel type, set on login by online() and chatOn() methods
    public enum Params {login, password, email, reg_time, lastlogin, lastlogout, lastclantime, loc_time, cure_time, bot, clan, dismiss, nochat, siluet}  //all available params that can be accessed via get/setParam()
    public enum GetMeParams {time, tdt, login, email, loc_time, cure_time, god, hint, exp, pro, propwr, rank_points, clan, clr, img, alliance, man, HP, psy, maxHP, maxPsy, stamina, str, dex, INT, pow, acc, intel, X, Y, Z, hz}

    private static final Logger logger = LogManager.getFormatterLogger();

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generator_sequence")
    @SequenceGenerator(name = "generator_sequence", sequenceName = "users_id_seq", allocationSize = 1)
    private Integer id;

    @Embedded
    private final UserParams params = new UserParams();

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
    public boolean isCop() {return getParam(Params.clan).equals("police");}                                                                 //user is a cop (is a member of police clan)
    public boolean isInBattle() {return false;}                                                                                             //just a stub yet

    public String getLogin() {return getParamStr(Params.login);}                                                                            //just a shortcut
    private Long getParam_time() {return Instant.now().getEpochSecond();}                                                                   //always return epoch time is seconds
    private Integer getParam_tdt() {return Calendar.getInstance().getTimeZone().getOffset(Instant.now().getEpochSecond() / 3600L);}         //user time zone, used in user history log
    public Integer getParamInt(Params param) {return NumberUtils.toInt(getParamStr(param));}
    public Long getParamLong(Params param) {return NumberUtils.toLong(getParamStr(param));}
    public Double getParamDouble(Params param) {return NumberUtils.toDouble(getParamStr(param));}
    public String getParamStr(Params param) {return getParam(param).toString();}
    private Object getParam(Params param) {                                                                                                 //get user param value (param must be in Params enum)
        String paramName = param.name();                                                                                                    //param name as a String
        try {                                                                                                                               //try to find param in UserParam instance
            return params.getParam(paramName);
        } catch (Exception e) {logger.debug("cannot find param in UserParams params, gonna look for a special method to compute that param");}

        String methodName = String.format("getParam_%s", paramName);
        try {                                                                                                                               //if not found in params. try to compute the param value via the dedicated method
            Method method = this.getClass().getDeclaredMethod(methodName);
            return (method.invoke(this));                                                                                                   //always return string value
        } catch (Exception e) {
            logger.warn("cannot find or compute param %s, neither in User params nor by a dedicated method: %s", paramName, e.getMessage());
        }
        return new Object();
    }
    public void setParam(Params paramName, String paramValue) {setParam(paramName, (Object)paramValue);}                                    //set param to String value
    public void setParam(Params paramName, Integer paramValue) {setParam(paramName, (Object)paramValue);}                                   //set param to Integer value
    public void setParam(Params paramName, Long paramValue) {setParam(paramName, (Object)paramValue);}                                      //set param to Long value
    public void setParam(Params paramName, Double paramValue) {setParam(paramName, (Object)paramValue);}                                    //set param to Double value
    private void setParam(Params param, Object value) {this.params.setParam(param.name(), value);}                                          //universal method to set a param


    synchronized void onlineGame(Channel ch) {
        logger.debug("setting user %s game channel online", getLogin());

        this.gameChannel = ch;                                                                                                              //set user game channel
        this.gameChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.GAME);                                                        //set the user channel type to GAME
        this.gameChannel.attr(AttributeKey.valueOf("chStr")).set("user " + getLogin());                                                     //replace a client representation string to 'user <login>' instead of IP:port
        setParam(Params.lastlogin, Instant.now().getEpochSecond());                                                                         //set user last login time, needed to compute loc_time
        String resultMsg = String.format("<OK l=\"%s\" ses=\"%s\"/>", getLogin(), ch.attr(AttributeKey.valueOf("encKey")).get());           //send OK with a chat auth key in ses attribute (using already existing key)
        sendMsgGame(resultMsg);
        return;
    }

    synchronized void offlineGame() {
        logger.debug("setting user %s game channel offline", getLogin());
        sendErrMsgChat(StringUtil.EMPTY_STRING);
        this.gameChannel = null;
        notifyAll();                                                                                                                        //awake all threads waiting for the user to get offline
        logger.info("user %s game channel logged of the game", getLogin());
        return;
    }

    synchronized void onlineChat(Channel ch) {
        logger.debug("turning user %s chat on", getLogin());

        this.chatChannel = ch;
        this.chatChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.CHAT);
        this.chatChannel.attr(AttributeKey.valueOf("chStr")).set("user " + getLogin() + " (chat)");
        return;
    }
    synchronized void offlineChat() {
        logger.info("turning user %s chat off", getLogin());
        this.chatChannel = null;
        notifyAll();
        return;
    }

    public void com_MYPARAM() {
        logger.info("processing <GETME/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());
        String xml = String.format("<MYPARAM login=\"%s\" X=\"0\" Y=\"0\" Z=\"0\"></MYPARAM>", getParam(Params.login));
        sendMsgGame(xml);
        return;
    }
    public void com_GOLOC() {
        logger.info("processing <GOLOC/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());
        String xml = String.format("<GOLOC><L/></GOLOC>");
        sendMsgGame(xml);
        return;
    }

    public void com_SILUET(String slt, String set) {
        logger.info("processing <SILUET/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")));
        logger.info("slt = %s, set = %s", slt, set);
        setParam(Params.siluet, set);
        String response = String.format("<SILUET code=\"0\"/><MYPARAM siluet=\"%s\"/>",  set);
        sendMsgGame(response);
        return;
    }


    public void sendMsgGame(String msg) {sendMsg(gameChannel, msg, false);}
    public void sendErrMsgGame(String msg) {sendMsg(gameChannel, msg, true);}                                                               //sendErr will disconnect a corresponding channel
    public void sendMsgChat(String msg) { sendMsg(chatChannel, msg, false);}
    public void sendErrMsgChat(String msg) {sendMsg(chatChannel, msg, true);}
    private void sendMsg(Channel ch, String msg, boolean close) {
        try {
            ChannelFuture cf = gameChannel.writeAndFlush(msg);
            if (close)                                                                                                                      //closing the channel after sending a message
                cf.addListener(ChannelFutureListener.CLOSE).await();                                                                        //wait for the channel to be closed
        }catch (Exception e) {
            logger.warn("cant send message %s to %s: %s", msg, getLogin(), e.getMessage());
        }
        return;
    }
}

