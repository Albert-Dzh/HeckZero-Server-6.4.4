package ru.heckzero.server.items;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.List;
import java.util.stream.Collectors;

@Entity(name = "Item")
@Table(name = "items_inventory")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class Item {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);

    public enum Params {id,   user_id, section, slot,     name, txt, massa, st, made, min, protect, quality, maxquality, OD, rOD, type, damage, calibre, shot, nskill, max_count, up, grouping, range, nt, build_in, c, radius, cost1, cost2, s1, s2, s3, s4, count, lb, dt, hz, res, owner, tm, ln}
    private static final EnumSet<Params> itemParams = EnumSet.of(Params.id, Params.section, Params.slot, Params.name, Params.txt, Params.massa, Params.st, Params.min, Params.protect, Params.quality, Params.maxquality, Params.OD, Params.rOD, Params.type, Params.damage, Params.calibre, Params.shot, Params.nskill, Params.max_count, Params.up, Params.grouping, Params.nt, Params.build_in, Params.c, Params.radius, Params.cost1, Params.cost2, Params.s1, Params.s2, Params.s3, Params.s4, Params.count, Params.lb, Params.dt, Params.hz, Params.res, Params.owner, Params.tm, Params.ln);

    @Id
    private Integer id;

    int pid;
    String name;                                                                                                                            //item name in a client (.swf and sprite)
    String txt;                                                                                                                             //item text representation
    int massa;
    String st;                                                                                                                              //appropriate slots this item can be wear on
    String made;                                                                                                                            //made by
    String min;                                                                                                                             //minimal requirement
    String protect;                                                                                                                         //item protection properties
    String quality, maxquality;                                                                                                             //current quality
    @Column(name = "\"OD\"") String OD;                                                                                                     //OD needed for using
    @Column(name = "\"rOD\"") String rOD;                                                                                                   //OD needed for reload
    String type;
    String damage;
    String calibre;
    String shot;
    String nskill;                                                                                                                          //category (perk?) name-skill?
    String max_count;                                                                                                                       //max number of included items allowed
    String up;
    String grouping;
    String range;
    String nt;                                                                                                                              //no transfer
    String build_in;
    String c;                                                                                                                               //item category
    String radius;
    String cost, cost2;
    String s1, s2, s3, s4;
    String count;
    String lb;
    String dt;                                                                                                                              //expiry date
    String hz;
    String res;
    String owner;                                                                                                                           //item owner
    String tm;
    String ln;                                                                                                                              //long name text

    int user_id;                                                                                                                            //user id this item belongs to
    int section;                                                                                                                            //the section number in user box or building warehouse
    String slot;                                                                                                                            //user slot this item is on

    protected Item() { }

    public static void getItem() {    }

    public String getParamStr(Params param) {return strConv.convert(String.class, getParam(param));}                                        //get user param value as different type
    public int getParamInt(Params param) {return intConv.convert(Integer.class, getParam(param));}
    protected String getParamXml(Params param) {return getParamStr(param).transform(s -> !s.isEmpty() ? String.format("%s=\"%s\"", param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    protected String getXml() {return itemParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<O ", "/>"));}

    private Object getParam(Params paramName) {                                                                                             //try to find a field with the name equals to paramName
        List<Field> fields = Arrays.stream(Item.class.getDeclaredFields()).toList();                                                        //get all declared fields from this class
        Field field = fields.stream().filter(f -> f.getName().equals(paramName.toString())).findFirst().orElse(null);                       //try to find needed field by its name
        if (field == null) {                                                                                                                //field is not found
            logger.warn("can't get param %s, the field is not defined is class %s", paramName.toString(), this.getClass().getSimpleName());
            return StringUtils.EMPTY;                                                                                                       //return a default value - empty string
        }
        try {
            return field.get(this);                                                                                                         //and return the field value or an empty string in case of some error
        } catch (Exception e) {
            logger.error("can't get (%s) param %s: %s", this.getClass().getSimpleName(), paramName.toString(), e.getMessage());
        }
        return StringUtils.EMPTY;
    }

    @Override
    public String toString() {
        return "Item{" +
                "id=" + id +
                ", pid=" + pid +
                ", name='" + name + '\'' +
                ", txt='" + txt + '\'' +
                '}';
    }
}