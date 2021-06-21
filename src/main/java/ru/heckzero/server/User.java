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

    private final Map<String, Object> userParams;
    private Channel gameChannel = null;
    private Channel chatChannel = null;
    public boolean isEmpty() {return userParams.isEmpty();}                                                                                 //if this user doesn't exist and is just a stub
    public boolean isOnline() {return gameChannel != null && gameChannel.isActive();}                                                       //if this user is online, have it's channel up and running

    public Channel getChannel() { return this.gameChannel;}

    public User() {                                                                                                                         //create an empty User instance
        this(new HashMap<>(0));

        return;
    }
    public User(Map<String, Object> userParams) {                                                                                           //create a User instance with a given params (taken from database_
        this.userParams = new ConcurrentHashMap<>(userParams);
        return;
    }

    public String getParam(String param) {
        if (userParams.containsKey(param)) 																		                        	//requested param exists in userParams
            return String.valueOf(userParams.get(param));			    													                //simply return param value

        String handlerMethodName = String.format("getParam_%s", param);																		//handler method name to compute a param which doesn't exit in userParams
        try {
            Method handlerMethod = this.getClass().getDeclaredMethod(handlerMethodName, new Class[0]);								    	//find a method with name handlerMethodName
            return (String)handlerMethod.invoke(this);																			            //try to call this method
        } catch (Exception e) {logger.error("getParam: can't call method %s: %s ", handlerMethodName, e.getMessage()); }	                    //such method was not found or got wrong arguments
        return StringUtils.EMPTY;														                    								//returns an empty string if the param is not set and no getParam_% method exists
    }

    public void setOnline(Channel ch) {
        this.gameChannel = ch;
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
