package ru.heckzero.server;

import org.apache.commons.beanutils.converters.DoubleConverter;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.LongConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.lang.reflect.Field;
import java.lang.reflect.Method;
import java.util.List;
import java.util.Objects;

public class ParamUtils {
    private static final Logger logger = LogManager.getFormatterLogger();

    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);
    private static final LongConverter longConv = new LongConverter(0L);
    private static final DoubleConverter doubleConv = new DoubleConverter(0D);

    public static String getParamStr(Object obj, String param) {return Objects.toString(getParam(obj, param), StringUtils.EMPTY);}          //get user param value as different type
    public static int getParamInt(Object obj, String param) {return intConv.convert(int.class, getParam(obj, param));}
    public static long getParamLong(Object obj, String param) {return longConv.convert(long.class, getParam(obj, param));}
    public static double getParamDouble(Object obj, String param) {return doubleConv.convert(double.class, getParam(obj, param));}
    public static String getParamXml(Object obj, String param) {return getParamStr(obj, param).transform(s -> !s.isEmpty() ? String.format("%s=\"%s\"", param, s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false

    public static boolean setParam(Object obj, String paramName, Object paramValue) {                                                       //set a paramName to paramValue for the obj instance
        try {
            Field field = FieldUtils.getField(obj.getClass(), paramName, true);                                                             //find a field with a name paramName
            Class<?> fieldType = field.getType();                                                                                           //found field type

            if (paramValue == null || fieldType.equals(paramValue.getClass())) {                                                            //if they are equals, just set the value
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
        } catch (SecurityException | IllegalAccessException e) {
            logger.warn("error setting param %s to value %s for class %s: %s:%s", paramName, paramValue, obj.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getMessage());
        }
        return false;
    }


    private static Object getParam(Object obj, String paramName) {                                                                          //try to find a field with the name equals to paramName
        List<Field> fieldList = FieldUtils.getAllFieldsList(obj.getClass());                                                                //gets all fields of the given class and its parents (if any)
        logger.debug("phase1: try to find a param %s from the list of the fields", paramName);
        Field paramField = fieldList.stream().filter(f -> f.getName().equals(paramName)).findFirst().orElse(null);                          //try to find a field with a name of paramName in the fieldList
        if (paramField != null)                                                                                                             //Field found in obj;
            return readField(obj, paramField);                                                                                              //try to read value of that field or return null in case of read exception

        logger.debug("phase2: try to find a field %s inside a params object", paramName);
        Field paramsField = fieldList.stream().filter(f -> f.getName().equals("params")).findFirst().orElse(null);                          //get a field by name "params" from the fieldList
        if (paramsField != null) {                                                                                                          //the field "params" is found
            Object paramsObject = readField(obj, paramsField);                                                                              //get the params object this field refers to
            if (paramsObject == null)                                                                                                       //we couldn't get the params object
                return null;
            paramField = FieldUtils.getAllFieldsList(paramsObject.getClass()).stream().filter(f -> f.getName().equals(paramName)).findFirst().orElse(null); //search a field of paramName in a list of paramsObject fields
            if (paramField != null)                                                                                                         //found
                return readField(paramsObject, paramField);                                                                                 //try to read and return its value
        }

        return getParamByInvokingMethod(obj, paramName);                                                                                    //try to get the param by invoking a method of getParam_paramName() on the obj
    }

    private static Object getParamByInvokingMethod(Object obj, String paramName) {                                                          //get param by calling getParam_param() method of obj
        String methodName = String.format("getParam_%s", paramName);
        try {
            Method method = obj.getClass().getDeclaredMethod(methodName);                                                                   //try to get a method and invoke it
            method.setAccessible(true);
            return method.invoke(obj);
        } catch (Exception e) {
            logger.warn("can't get or compute param %s, of the class %s: %s:%s", paramName, obj.getClass().getSimpleName(), e.getClass().getSimpleName(), e.getMessage());
        }
        return null;
    }

    private static Object readField(Object target, Field field) {
        try {
            return FieldUtils.readField(field, target, true);
        } catch (IllegalAccessException e) {
            logger.error("can't read a field %s of object %s: %s", field.getName(), target, e.getMessage());
        }
        return null;
    }

}
