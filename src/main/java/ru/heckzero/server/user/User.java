package ru.heckzero.server.user;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;

@Entity(name = "User")
@Table(name = "users")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class User {
    public enum Params {LOGIN, PASSWORD, DISMISS, BOT, CLAN}
    public enum Commands {MYPARAM}

    private static final Logger logger = LogManager.getFormatterLogger();
    private static List<String> attrsDb ;//= ((ArrayList<DataRow>)db.executeQuery("select column_name from information_schema.columns where table_name='users'")).stream().map(row -> ((Map<String,String>)row.getDataAsMap()).get("column_name")).collect(Collectors.toList());

    @Id
    private Integer id;

    @Embedded
    private UserParams params = new UserParams();

    @Transient private Channel gameChannel = null;                                                                                          //user's game socket
    @Transient private Channel chatChannel = null;                                                                                          //user's chat socket
    @Transient private ExecutorService mainExecutor;

    public Channel getGameChannel() { return this.gameChannel;}
    public Channel getChatChannel() { return this.chatChannel;}

    public User() { }                                                                                                                       //default constructor

    public boolean isEmpty() {return id == null;}                                                                                           //user is empty (having empty params)
    public boolean isOnline() {return gameChannel != null && gameChannel.isActive();}                                                       //this user is online and it's game channel is up and running
    public boolean isChatOn() {return chatChannel != null && chatChannel.isActive();}                                                       //this user is online and it's game channel is up and running
    public boolean isBot() {return !getParam(Params.BOT).isEmpty();}
    public boolean isCop() {return getParam(Params.CLAN).equals("police");}                                                                 //user is a cop
    public boolean isInBattle() {return false;}                                                                                             //just a stub yet

    public String getParam(Params param) {
        String paramName = param.name().toLowerCase();
        String methodName = String.format("getParam_%s", paramName);

        try {
            return params.getParam(paramName);
        } catch (Exception e) {
            logger.debug("cannot find param in params");
        }

        try {
            Method method = this.getClass().getDeclaredMethod(methodName);
            return (String) method.invoke(this);
        } catch (Exception e) {
            logger.warn("cannot find or compute param %s", paramName);
        }
        return StringUtil.EMPTY_STRING;
    }

    public void setParam(Params param, String value) {
        this.params.setParam(param.name().toLowerCase(), value);
    }

    public void setOnline(Channel ch) {
        this.gameChannel = ch;
        mainExecutor = Executors.newSingleThreadExecutor();
        return;
    }
    public void setOffline() {
        if(isOnline()) {
            this.gameChannel.close();
            this.gameChannel = null;
        }
    }

    public void execCommand(Commands cmdName, Object...params) {
        try {
            Method method = this.getClass().getDeclaredMethod(String.format("com_%s", cmdName));
            mainExecutor.execute(() -> {
                try { method.invoke(this, params); }
                catch (IllegalAccessException | InvocationTargetException e) { e.printStackTrace(); }
            });
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    private void com_MYPARAM() {
        String xml = String.format("<MYPARAM login=\"%s\" />", getParam(Params.LOGIN));
        sendMsg(xml);
        return;
    }

    public ChannelFuture sendMsg(String msg) {
        return gameChannel.writeAndFlush(msg);
    }
}
