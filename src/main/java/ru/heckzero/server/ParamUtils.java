package ru.heckzero.server;

import org.apache.commons.beanutils.converters.DoubleConverter;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.LongConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.heckzero.server.user.UserParams;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.Optional;

public class ParamUtils {
    private static final Logger logger = LogManager.getFormatterLogger();

    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);
    private static final LongConverter longConv = new LongConverter(0L);
    private static final DoubleConverter doubleConv = new DoubleConverter(0D);

    public static String getParamStr(Object objField, Object objMethod, String param) {return strConv.convert(String.class, getParam(objField, param).orElseGet(() -> getParam2(objMethod, param).orElse(null)));}
    public static int getParamInt(Object objField, Object objMethod, String param) {return intConv.convert(Integer.class, getParam(objField, param).orElseGet(() -> getParam2(objMethod, param).orElse(null)));}
    public static long getParamLong(Object objField, Object objMethod, String param) {return longConv.convert(Long.class, getParam(objField, param).orElseGet(() -> getParam2(objMethod, param).orElse(null)));}
    public static double getParamDouble(Object objField, Object objMethod, String param) {return doubleConv.convert(Double.class, getParam(objField, param).orElseGet(() -> getParam2(objMethod, param).orElse(null)));}

    public static String getParamStr(Object obj, String param) {return strConv.convert(String.class, getParam(obj, param).orElse(null));}   //get user param value as different type
    public static int getParamInt(Object obj, String param) {return intConv.convert(Integer.class, getParam(obj, param).orElse(null));}
    public static long getParamLong(Object obj, String param) {return longConv.convert(Long.class, getParam(obj, param).orElse(null));}
    public static double getParamDouble(Object obj, String param) {return doubleConv.convert(Double.class, getParam(obj, param).orElse(null));}

    public static String getParamXml(Object obj, String param) {return getParamStr(obj, param).transform(s -> !s.isEmpty() ? String.format("%s=\"%s\"", param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false

    public static boolean setParam(Object obj, String paramName, Object paramValue) {                                                       //set a paramName to paramValue for the obj instance
        try {
            Field field = obj.getClass().getDeclaredField(paramName);                                                                       //find a field with a name paramName
            field.setAccessible(true);
            Class<?> fieldType = field.getType();                                                                                           //found field type
            Class<?> valueType = paramValue.getClass();                                                                                     //value type
            if (fieldType.equals(valueType)) {                                                                                              //if they are equals, just set the value
                field.set(obj, paramValue);
                return true;
            }
            String strValue = paramValue instanceof String ? (String)paramValue : paramValue.toString();                                    //cast or convert paramValue to String
            return switch (fieldType.getSimpleName()) {                                                                                     //a short field type name (Integer, String, etc.)
                case "String" -> {field.set(obj, strValue); yield true;}                                                                    //just set a String value to String field type
                case "int", "Integer" -> {field.set(obj, NumberUtils.isParsable(strValue) ? Math.toIntExact(Math.round(Double.parseDouble(strValue))) : 0); yield true;} //convert String value to field type
                case "long", "Long" -> {field.set(obj, NumberUtils.isParsable(strValue) ? Math.round(Double.parseDouble(strValue)) : 0L); yield true;}
                case "double", "Double" -> {field.set(obj, NumberUtils.isParsable(strValue) ? Math.round(Double.parseDouble(strValue) * 1000D) / 1000D  : 0D); yield true;}
                default -> {logger.error("can't set param %s for object of type %s, param type '%s' is not supported", paramName, obj.getClass().getSimpleName(), fieldType.getSimpleName()); yield false;}
            };
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
            logger.warn("error setting param %s to value %s for class %s: %s:%s", paramName, paramValue, obj.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getMessage());
        }
        return false;
    }

    private static Optional<Object> getParam(Object obj, String paramName) {                                                                //try to find a field with the name equals to paramName
        try {
            Field field = obj.getClass().getDeclaredField(paramName);
            field.setAccessible(true);                                                                                                      //set private field accessible by the reflection
            return Optional.of(field.get(obj));                                                                                             //return a value of the field
        }catch (NoSuchFieldException | IllegalAccessException e) {
            if (e.getClass().equals(NoSuchFieldException.class) && !obj.getClass().equals(UserParams.class))
                logger.error("can't get param %s of class %s: %s:%s", paramName, obj.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getMessage());
        }
        return Optional.empty();
    }

    private static Optional<Object> getParam2(Object obj, String paramName) {
        String methodName = String.format("getParam_%s", paramName);
        try {                                                                                                                               //if not found in params. try to compute the param value via the dedicated method
            Method method = obj.getClass().getDeclaredMethod(methodName);
            method.setAccessible(true);
            return Optional.of(method.invoke(obj));
        } catch (Exception e) {
            logger.warn("can't get or compute param %s, neither in User.UserParams nor by a dedicated method: %s", paramName, e.getMessage());
        }
        return Optional.empty();
    }

}
