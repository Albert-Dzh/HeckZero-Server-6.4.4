package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import java.lang.reflect.Field;

@Embeddable
class UserParams {
    private static Logger logger = LogManager.getFormatterLogger();

    private String login;                                                                                                                   //user login
    private String password;                                                                                                                //you won't believe

//    @Column(name = "\"X\"")
//    private String X;

//            , Y, Z, hz;                                                                                                             //coordinates, Z - house number on a location, hz - house type
//    private String exp;                                                                                                                     //experience
    private String dismiss;                                                                                                                 //user is blocked

    void setParam(String paramName, Integer paramValue) { setParam(paramName, String.valueOf(paramValue));}                                                                                   //set param to value
    void setParam(String paramName, String paramValue) {                                                                                    //set param to value
        try {
            Field field = this.getClass().getDeclaredField(paramName);
            field.set(this, paramValue);
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException ex) {
            logger.warn("cannot find field %s to set value", paramName);
        }
        return;
    }

    String getParam(String paramName) throws Exception {
        Field field = this.getClass().getDeclaredField(paramName);
        return (String) field.get(this);
    }

}
