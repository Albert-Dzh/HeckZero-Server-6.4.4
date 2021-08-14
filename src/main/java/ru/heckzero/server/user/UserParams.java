package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.persistence.*;
import java.lang.reflect.Field;

@Embeddable
class UserParams {
    private static Logger logger = LogManager.getFormatterLogger();

    private String login, password;                                                                                                         //user login

    @Column(name = "\"X\"")
    private Short X;
    @Column(name = "\"Y\"")
    private Short Y;
    @Column(name = "\"Z\"")
    private Short Z;                                                                                                                        //Z - house number on a location
    private Short hz;                                                                                                                       //hz - house type
    private Double exp;                                                                                                                     //user experience
    private String dismiss;                                                                                                                 //user block reason, we treat the user as blocked if it's not empty

    private Long lastlogin;                                                                                                                 //last user login time in seconds, needed for computing loc_time
    private Long lastlogout;                                                                                                                //last user logout time in epoch seconds

    @Transient
    private Integer nochat;                                                                                                                 //user chat status 1,0

    void setParam(String paramName, Object paramValue) {                                                                                    //set user param value
        try {
            Field field = this.getClass().getDeclaredField(paramName);                                                                      //find a field with name paramName
            String fType = field.getType().getSimpleName();                                                                                 //field type String representation
            String vType = paramValue.getClass().getSimpleName();                                                                           //value type String representation
            if (fType.equals(vType))                                                                                                        //field and column type must match
                field.set(this, paramValue);
            else
                logger.warn("cannot set param %s, param type: %s differs from a database column type: %s", paramName, vType, fType);
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException ex) {
            logger.warn("cannot set param %s to value %s, a corresponding field is not found or an error occurred: %s", paramName, paramValue, ex.getMessage());
        }

        return;
    }

    Object getParam(String paramName) throws NoSuchFieldException, IllegalAccessException {
        Field field = this.getClass().getDeclaredField(paramName);
        return field.get(this);
    }

}
