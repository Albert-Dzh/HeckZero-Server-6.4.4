package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;
import java.lang.reflect.Field;

@Embeddable
class UserParams {
    private static Logger logger = LogManager.getFormatterLogger();

    @Transient
    private Integer nochat;                                                                                                                 //user chat status 1,0

    private String login, password;                                                                                                         //user login

    @Column(name = "\"X\"")
    private Integer X;
    @Column(name = "\"Y\"")
    private Integer Y;
    @Column(name = "\"Z\"")
    private Integer Z;                                                                                                                      //Z - house number on a location

    private Integer hz;                                                                                                                     //hz - house type
    private Double exp;                                                                                                                     //user experience
    private String dismiss;                                                                                                                 //user block reason, we treat the user as blocked if it's not empty

    private Long lastlogin;                                                                                                                 //last user login time in seconds, needed for computing loc_time
    private Long lastlogout;                                                                                                                //last user logout time in epoch seconds


    void setParam(String paramName, Object paramValue) {                                                                                    //set user param value
        try {
            Field field = this.getClass().getDeclaredField(paramName);                                                                      //find a field of name paramName
            if (field.getType().equals(paramValue.getClass()))                                                                               //field and param type must match each other
                field.set(this, paramValue);
            else
                logger.warn("cannot set param %s, param type: %s differs from field type: %s", paramName, paramValue.getClass().getSimpleName(), field.getType().getSimpleName());
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
            logger.warn("cannot set param %s to value %s, a corresponding field is not found or an error occurred: %s", paramName, paramValue, e.getMessage());
        }
        return;
    }

    Object getParam(String paramName) throws NoSuchFieldException, IllegalAccessException {
        Field field = this.getClass().getDeclaredField(paramName);
        return field.get(this);
    }
}
