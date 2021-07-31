package ru.heckzero.server.user;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.internal.StringUtil;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.user.UserParams;

import javax.persistence.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.List;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Entity(name = "User")
@Table(name = "users")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class User {
    public enum Params {LOGIN, PASSWORD, EMAIL, REG_TIME, LASTLOGIN, LASTLOGOUT, LASTCLANTIME, LOC_TIME, CURE_TIME, BOT, CLAN, DISMISS, NOCHAT, SILUET}                                                                             //params that can be accessed via get/setParam()
    public enum GetMeParams {TIME, TDT, LOGIN, EMAIL, LOC_TIME, CURE_TIME, GOD, HINT, EXP, PRO, PROPWR, RANK_POINTS, CLAN, CLR, IMG, ALLIANCE, MAN, HP, PSY, MAX_HP, MAX_PSY, STAMINA, STR, DEX, INT, POW, ACC, INTEL, X, Y, Z}
    public enum Commands {MYPARAM, GOLOC, SILUET}                                                                                                   //client commands that can be executed by execCommand(), must have corresponding com_COMMAND() method

    private static final Logger logger = LogManager.getFormatterLogger();

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "generator_sequence")
    @SequenceGenerator(name = "generator_sequence", sequenceName = "users_id_seq", allocationSize = 1)
    private Integer id;

    @Embedded
    private final UserParams params = new UserParams();

    @Transient private Channel gameChannel = null;                                                                                          //user's game socket
    @Transient private Channel chatChannel = null;                                                                                          //user's chat socket
    @Transient private ExecutorService mainExecutor;

    public Channel getGameChannel() { return this.gameChannel;}
    public Channel getChatChannel() { return this.chatChannel;}


    public User() { }                                                                                                                       //default constructor
    public void setId(Integer id) {this.id = id;}

    public boolean isEmpty() {return id == null;}                                                                                           //user is empty (having empty params)
    public boolean isOnline() {return gameChannel != null && gameChannel.isActive();}                                                       //this user is online and it's game channel is up and running
    public boolean isChatOn() {return isOnline() && chatChannel != null && chatChannel.isActive();}                                         //this user is online and it's game channel is up and running
    public boolean isBot() {return !getParam(Params.BOT).isEmpty();}                                                                        //user is a bot (no!t a human)
    public boolean isCop() {return getParam(Params.CLAN).equals("police");}                                                                 //user is a cop (member of police clan)
    public boolean isInBattle() {return false;}                                                                                             //just a stub yet


    public void setParam(Params paramName, String paramValue) {setParam(paramName, (Object)paramValue);}                                    //set param to String value
    public void setParam(Params paramName, Integer paramValue) {setParam(paramName, (Object)paramValue);}                                   //set param to Integer value
    public void setParam(Params paramName, Long paramValue) {setParam(paramName, (Object)paramValue);}                                      //set param to Long value
    public void setParam(Params paramName, Double paramValue) {setParam(paramName, (Object)paramValue);}                                    //set param to Double value

    private void setParam(Params param, Object value) {                                                                                     //universal method to set a param
        this.params.setParam(param.name().toLowerCase(), value);
    }

    private Integer getParamInteger(Params param) {
        String val = getParam(param);
        return val.chars().allMatch(Character::isDigit) ? Integer.parseInt(val) : 0;
    }
    public Long getParamLong(Params param) {
        String val = getParam(param);
        return val.chars().allMatch(Character::isDigit) ? Long.parseLong(val) : 0;
    }
    public Double getParamDouble(Params param) {
        String val = getParam(param);
        return val.chars().filter(ch -> ch != '.').allMatch(Character::isDigit) ? Double.parseDouble(val) : 0.0D;
    }
    public String getParam(Params param) {                                                                                                  //get user param value (param must be in Params enum)
        String paramName = param.name().toLowerCase();                                                                                      //convert param to a lowercase string
        String methodName = String.format("getParam_%s", paramName);

        try {                                                                                                                               //try to find param in UserParam instance
            return params.getParam(paramName);
        } catch (Exception e) {
            logger.debug("cannot find param in UserParams params, gonna look for a special method to compute that param");
        }
        try {                                                                                                                               //if not found in params. try to compute the param value via the dedicated method
            Method method = this.getClass().getDeclaredMethod(methodName);
            return (String.valueOf(method.invoke(this)));                                                                                   //always return string value
        } catch (Exception e) {
            logger.warn("cannot find or compute param %s, neither in params nor by a dedicated method: %s", paramName, e.getMessage());
        }
        return StringUtil.EMPTY_STRING;
    }

    public Long getParamTime() {return Instant.now().getEpochSecond();}                                                                     //always return epoch time is seconds

    public void setOnline(Channel ch) {
        this.gameChannel = ch;                                                                                                              //set user's game socket (channel)
        setParam(Params.LASTLOGIN, Instant.now().getEpochSecond());                                                                         //set user last login time, needed to compute loc_time
        setParam(Params.NOCHAT, 1);                                                                                                         //set initial user chat status to off, until 2nd chat connection completed
        mainExecutor = Executors.newSingleThreadExecutor();                                                                                 //create an executor service for this user
        return;
    }
    public void setOffline() {
        if(isOnline()) {
            this.gameChannel.close();
            this.gameChannel = null;
        }
    }

    public void chatOn(Channel ch) {
        chatChannel = ch;
        sendChatMsg("<Z t=\"jopa\"/>");
        sendChatMsg("<R t=\"Location[0/0] jopa" + "\t" + "0/0/0/0" + "\"/>");
        return;
    }

    public void com_MYPARAM() {
        logger.info("processing <GETME/> from %s", gameChannel.attr(ServerMain.userStr).get());
        String xml = String.format("<MYPARAM login=\"%s\" X=\"0\" Y=\"0\"></MYPARAM>", getParam(Params.LOGIN));
        sendMsg(xml);
        return;
    }
    public void com_GOLOC() {
        logger.info("processing <GOLOC/> from %s", gameChannel.attr(ServerMain.userStr).get());
        String xml = String.format("<GOLOC><L/></GOLOC>");
        sendMsg(xml);
        return;
    }

    public void com_SILUET(String slt, String set) {
        logger.info("processing <SILUET/> from %s", gameChannel.attr((ServerMain.userStr)));
        logger.info("slt = %s, set = %s", slt, set);
        String response = String.format("<SILUET code=\"1\"/><MYPARAM siluet=\"%s\"/>",  set);
        sendMsg(response);
        return;
    }
    public ChannelFuture sendMsg(String msg) {
        return gameChannel.writeAndFlush(msg);
    }

    public ChannelFuture sendChatMsg(String msg) {
        return gameChannel.writeAndFlush(msg);
    }

    @Override
    public String toString() {
        return "User{" +
                "id=" + id +
                ", params=" + params +
                ", gameChannel=" + gameChannel +
                ", chatChannel=" + chatChannel +
                ", mainExecutor=" + mainExecutor +
                '}';
    }
}
