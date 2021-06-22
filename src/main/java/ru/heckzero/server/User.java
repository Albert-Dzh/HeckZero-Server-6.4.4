package ru.heckzero.server;

import io.netty.channel.Channel;
import io.netty.channel.ChannelFuture;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Method;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.ConcurrentHashMap;

class User {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static List<String> attrsDb ;//= ((ArrayList<DataRow>)db.executeQuery("select column_name from information_schema.columns where table_name='users'")).stream().map(row -> ((Map<String,String>)row.getDataAsMap()).get("column_name")).collect(Collectors.toList());

    private final Map<String, Object> params;
    private Channel gameChannel = null;                                                                                                     //user game socket
    private Channel chatChannel = null;                                                                                                     //user chat socket
    public Channel getGameChannel() { return this.gameChannel;}
    public Channel getChatChannel() { return this.chatChannel;}

    public User() {                                                                                                                         //create an empty User instance
        this(new HashMap<>(0));
        return;
    }
    public User(Map<String, Object> params) {                                                                                               //create a User instance with a given params (taken from database_
        this.params = new ConcurrentHashMap<>(params);
        return;
    }

    public boolean isEmpty() {return params.isEmpty();}                                                                                     //this user doesn't exist and is just a stub
    public boolean isOnline() {return gameChannel != null && gameChannel.isActive();}                                                       //this user is online and it's game channel is up and running
    public boolean isChatOn() {return chatChannel != null && chatChannel.isActive();}                                                       //this user is online and it's game channel is up and running
    public boolean isBot() {return !getParam("bot").isEmpty();}
    public boolean isInBattle() {return false;}                                                                                             //just a stub yet


    public String getParam(String param) {
        if (params.containsKey(param)) 																		                             	//requested param exists in userParams
            return String.valueOf(params.get(param));			    													                    //simply return param value

        String handlerMethodName = String.format("getParam_%s", param);																		//handler method name to compute a param which doesn't exit in userParams
        try {
            Method handlerMethod = this.getClass().getDeclaredMethod(handlerMethodName, new Class[0]);								    	//find a method with name handlerMethodName
            return (String)handlerMethod.invoke(this);																			            //try to call this method
        } catch (Exception e) {logger.error("getParam: can't call method %s: %s ", handlerMethodName, e.getMessage()); }	                //such method was not found or got wrong arguments
        return StringUtils.EMPTY;														                    								//returns an empty string if the param is not set and no getParam_% method exists
    }

    public void setGameChannel(Channel ch) {
        this.gameChannel = ch;
    }
    public void setChatChannel(Channel ch) {
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
    public void dbsync() {												                    												//sync (update) user with a database. Sync only attrsDb params
        StringJoiner sj = new StringJoiner(", ", "update users set ", " where id = " + getParam("id"));
        attrsDb.forEach(attr -> sj.add("\"" + attr + "\"='" + getParam(attr) + "'"));
//        db.executeCommand(sj.toString());
        return;
    }


    public ChannelFuture sendMsg(String msg) {
        return gameChannel.writeAndFlush(msg);
    }
}
