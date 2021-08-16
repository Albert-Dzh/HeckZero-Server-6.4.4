package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.math.BigDecimal;
import java.math.RoundingMode;

@Converter
class StringToIntegerConverter implements AttributeConverter<String, Integer> {
    @Override
    public Integer convertToDatabaseColumn(String value) {
        boolean isNumeric = value.chars().filter(ch -> ch != '.').allMatch(Character::isDigit);
        return isNumeric ? Math.toIntExact(Math.round(Double.parseDouble(value))) : 0;
    }
    @Override
    public String convertToEntityAttribute(Integer dbData) {
        return dbData.toString();
    }
}
@Converter
class StringToDoubleConverter implements AttributeConverter<String, Double> {
    @Override
    public Double convertToDatabaseColumn(String value) {
        boolean isNumeric = value.chars().filter(ch -> ch != '.').allMatch(Character::isDigit);
        return isNumeric ? Math.round(Double.parseDouble(value) * 1000.0) / 1000.0 : 0D;
    }
    @Override
    public String convertToEntityAttribute(Double dbData) {
        return dbData.toString();
    }
}

@Embeddable
class UserParams {
    private static Logger logger = LogManager.getFormatterLogger();

    @Transient
    private String nochat;                                                                                                                  //user chat status 1,0

    @Column(name = "\"X\"")
    @Convert(converter = StringToIntegerConverter.class)
    private String X;

    @Column(name = "\"Y\"")
    @Convert(converter = StringToIntegerConverter.class)
    private String Y;

    @Column(name = "\"Z\"")
    @Convert(converter = StringToIntegerConverter.class)
    private String Z;                                                                                                                       //Z - house number on a location

    @Convert(converter = StringToIntegerConverter.class)
    private String hz;                                                                                                                      //hz - house type

    @Convert(converter = StringToIntegerConverter.class)
    private String exp;                                                                                                                     //user experience

    @Convert(converter = StringToIntegerConverter.class)
    private String lastlogin;                                                                                                               //last user login time in seconds, needed for computing loc_time

    @Convert(converter = StringToIntegerConverter.class)
    private String lastlogout;                                                                                                              //last user logout time in epoch seconds

    @Convert(converter = StringToDoubleConverter.class)
    private String propwr;

    @Column(name = "int")
    @Convert(converter = StringToIntegerConverter.class)
    private String intu;

    private String login, password;                                                                                                         //user login
    private String dismiss;                                                                                                                 //user block reason, we treat the user as blocked if it's not empty


    void setParam(User.Params paramName, String paramValue) {                                                                               //set user param value
        try {
            Field field = this.getClass().getDeclaredField(paramName.toString());                                                           //find a field of name paramName
            field.set(this, paramValue);
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
            logger.warn("cannot set param %s to value %s, a corresponding field is not found or an error occurred: %s", paramName, paramValue, e.getMessage());
        }
        return;
    }

    String getParam(User.Params paramName) throws NoSuchFieldException, IllegalAccessException {
        Field field = this.getClass().getDeclaredField(paramName.toString());
        return (String)field.get(this);
    }
}
