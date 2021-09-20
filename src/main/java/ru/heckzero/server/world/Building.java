package ru.heckzero.server.world;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.*;
import java.util.stream.Collectors;

@Entity(name = "Building")
@Table(name = "locations_b")
@Inheritance(strategy = InheritanceType.JOINED)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);

    public enum Params {X, Y, Z, txt, maxHP, HP, name, upg, maxl, repair, clan,      cash, ds, city, p1, p2, clon, bigmap_city, bigmap_shown};
    private static final EnumSet<Params> bldParams = EnumSet.of(Params.X, Params.Y, Params.Z, Params.txt, Params.maxHP, Params.HP, Params.name, Params.upg, Params.maxl, Params.repair, Params.clan);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loc_b_generator_sequence")
    @SequenceGenerator(name = "loc_b_generator_sequence", sequenceName = "locations_b_seq", allocationSize = 1)
    private Integer id;

    @Column(name = "\"X\"") private int X = 20;                                                                                             //X,Y coordinate within a location
    @Column(name = "\"Y\"") private int Y = 8;
    @Column(name = "\"Z\"") private int Z = 0;                                                                                              //unique building number withing a location

    private String txt = "!!!STUB!!!";                                                                                                      //the building visible name
    @Column(name = "\"maxHP\"") private String maxHP;
    @Column(name = "\"HP\"") private String HP;
    private int name = 188;                                                                                                                 //building type - ruins by default
    private String upg;
    private String maxl;
    private String repair;
    private String clan;                                                                                                                    //clan which owns the building

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "l_id")
    private Location location;                                                                                                              //location association

    protected Building() { }

    public boolean isEmpty() {return id == null;}

    public Integer getId() { return id; }
    public int getZ() {return Z; }
    public int getName() { return name; }
    public String getTxt() { return txt; }

    public Location getLocation() {return location;}                                                                                        //get the location this Building belongs to
    public String getParamStr(Params param) {return strConv.convert(String.class, getParam(param));}                                        //get user param value as different type
    public int getParamInt(Params param) {return intConv.convert(Integer.class, getParam(param));}
    protected String getParamXml(Params param) {return getParamStr(param).transform(s -> !s.isEmpty() ? String.format("%s=\"%s\"", param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    protected String getXml() {return bldParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<B ", "/>"));}

    private Object getParam(Params paramName) {                                                                                             //try to find a field with the name equals to paramName
        List<Field> fields = getAllFields(this.getClass());                                                                                 //get all declared fields from parent and child classes
        Field field = fields.stream().filter(f -> f.getName().equals(paramName.toString())).findFirst().orElse(null);                       //try to find needed field by its name
        if (field == null) {                                                                                                                //field is not found
            logger.warn("can't get param %s, the field is not defined is class %s", paramName.toString(), this.getClass().getSimpleName());
            return StringUtils.EMPTY;                                                                                                       //return a default value - empty string
        }
        try {
            return field.get(this);                                                                                                         //and return the field value or an empty string in case of some error
        } catch (Exception e) {
            logger.error("can't get building (%s) param %s: %s", this.getClass().getSimpleName(), paramName.toString(), e.getMessage());
        }
        return StringUtils.EMPTY;
    }

    private List<Field> getAllFields(Class clazz) {                                                                                         //recursively get all declared field from a child and parent classes
        if (clazz == null)
            return Collections.emptyList();
        List<Field> result = new ArrayList<>(getAllFields(clazz.getSuperclass()));
        result.addAll(Arrays.asList(clazz.getDeclaredFields()));
        return result;
    }

}
