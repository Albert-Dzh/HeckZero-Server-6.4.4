package ru.heckzero.server.user;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import io.netty.util.internal.StringUtil;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

@Entity(name = "User")
@Table(name = "users")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class User {
    public enum Params {LOGIN, PASSWORD, DISMISS, BOT, CLAN}

    private static final Logger logger = LogManager.getFormatterLogger();
    private static List<String> attrsDb ;//= ((ArrayList<DataRow>)db.executeQuery("select column_name from information_schema.columns where table_name='users'")).stream().map(row -> ((Map<String,String>)row.getDataAsMap()).get("column_name")).collect(Collectors.toList());

    @Id
    private Integer id;

    @Embedded
    private UserParams params = new UserParams();

    @Transient private Channel gameChannel = null;                                                                                          //user's game socket
    @Transient private Channel chatChannel = null;                                                                                          //user's chat socket

    public Channel getGameChannel() { return this.gameChannel;}
    public Channel getChatChannel() { return this.chatChannel;}

    public User() { }                                                                                                                       //default constructor

    public boolean isEmpty() {return getParam(Params.LOGIN).isEmpty();}                                                                     //user is empty (having empty params)
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


    /*
    public String getParam(String param) {
        if (params.containsKey(param)) 																		                             	//requested param exists in userParams
            return String.valueOf(params.get(param));			    													                    //simply return param value

        String handlerMethodName = String.format("getParam_%s", param);																		//handler method name to compute a param which doesn't exit in userParams
        try {
            Method handlerMethod = this.getClass().getDeclaredMethod(handlerMethodName);	            							    	//find a method with name handlerMethodName
//            Method handlerMethod = this.getClass().getDeclaredMethod(handlerMethodName, new Class[0]);								    	//find a method with name handlerMethodName
            return (String)handlerMethod.invoke(this);																			            //try to call this method
        } catch (Exception e) {logger.error("getParam: can't call method %s: %s ", handlerMethodName, e.getMessage()); }	                //such method was not found or got wrong arguments
        return StringUtils.EMPTY;														                    								//returns an empty string if the param is not set and no getParam_% method exists
    }
*/
    private void setGameChannel(Channel ch) {
        this.gameChannel = ch;
    }
    private void setChatChannel(Channel ch) {
        this.chatChannel = ch;
    }
    public void setOnline(Channel ch) {
        setGameChannel(ch);
        return;
    }
    public void setOffline() {
        if(isOnline()) {
            this.gameChannel.close();
            this.gameChannel = null;
        }
    }
/*
    public void dbsync() {												                    												//sync (update) user with a database. Sync only attrsDb params
        StringJoiner sj = new StringJoiner(", ", "update users set ", " where id = " + getParam("id"));
        attrsDb.forEach(attr -> sj.add("\"" + attr + "\"='" + getParam(attr) + "'"));
//        db.executeCommand(sj.toString());
        return;
    }
*/


    public ChannelFuture sendMsg(String msg) {
        return gameChannel.writeAndFlush(msg);
    }
}
