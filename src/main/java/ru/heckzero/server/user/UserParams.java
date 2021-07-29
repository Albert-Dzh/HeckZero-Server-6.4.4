package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.persistence.*;
import java.lang.reflect.Field;

@Converter
class IntegerToStringConverter implements AttributeConverter<String, Integer> {
    @Override
    public String convertToEntityAttribute(Integer value) {return Integer.toString(value); }
    @Override
    public Integer convertToDatabaseColumn(String value) {return value.chars().allMatch(Character::isDigit) ? Integer.parseInt(value) : 0;}
}

@Embeddable
class UserParams {
    private static Logger logger = LogManager.getFormatterLogger();

    private String login;                                                                                                                   //user login
    private String password;                                                                                                                //you won't believe

    @Column(name = "\"X\"")
    @Convert(converter = IntegerToStringConverter.class)
    private String X;

    @Column(name = "\"Y\"")
    @Convert(converter = IntegerToStringConverter.class)
    private String Y;

//    private String Z, hz;                                                                                                                   //coordinates, Z - house number on a location, hz - house type

    private String exp;                                                                                                                     //experience
    private String dismiss;                                                                                                                 //user is blocked

    void setParam(String paramName, String paramValue) { setParam(paramName, paramValue, String.class);}                                    //set param to value
    void setParam(String paramName, Integer paramValue) { setParam(paramName, paramValue, Integer.class);}                                  //set param to value
    void setParam(String paramName, Double paramValue) { setParam(paramName, paramValue, Double.class);}                                    //set param to value

    private void setParam(String paramName, Object paramValue, Class paramType) {                                                                                    //set param to value
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
