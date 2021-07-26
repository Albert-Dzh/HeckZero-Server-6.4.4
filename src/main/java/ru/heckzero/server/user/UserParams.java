package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.persistence.Embeddable;
import java.lang.reflect.Field;

@Embeddable
class UserParams {
    private static Logger logger = LogManager.getFormatterLogger();

    private String login;
    private String password;
    private String exp;

    void setParam(String paramName, String paramValue) {
        try {
            Field field = this.getClass().getDeclaredField(paramName);
            field.set(this, paramValue);
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException ex) {
            logger.warn("cannot find field %s to set value", paramName);
        }
        return;
    }

    String getParam(String paramName) throws Exception{
        Field field = this.getClass().getDeclaredField(paramName);
        return (String) field.get(this);
    }


    @Override
    public String toString() {
        return "PersonParams{" +
                "login='" + login + '\'' +
                ", password='" + password + '\'' +
                '}';
    }
}
