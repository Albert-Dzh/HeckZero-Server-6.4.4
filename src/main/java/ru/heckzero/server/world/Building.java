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

    public enum Params {X, Y, Z, txt, maxHP, HP, name, upg, maxl, repair, clan};
    private static final EnumSet<Params> bldParams = EnumSet.of(Params.X, Params.Y, Params.Z, Params.txt, Params.maxHP, Params.HP, Params.name, Params.upg, Params.maxl, Params.repair, Params.clan);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loc_b_generator_sequence")
    @SequenceGenerator(name = "loc_b_generator_sequence", sequenceName = "locations_b_seq", allocationSize = 1)
    private Integer id;

    @Column(name = "\"X\"") private int X = 20;                                                                                             //X,Y coordinate within a location
    @Column(name = "\"Y\"") private int Y = 8;
    @Column(name = "\"Z\"") private int Z = 0;                                                                                              //unique building number withing a location

    private String txt = "!!!STUB!!!";                                                                                                      //building visible name
    @Column(name = "\"maxHP\"") private String maxHP;
    @Column(name = "\"HP\"") private String HP;
    private int name = 188;                                                                                                                 //building type - ruins by default
    private String upg;
    private String maxl;
    private String repair;
    private String clan;                                                                                                                    //clan which owns the building

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "l_id")
    protected Location location;                                                                                                              //location association

    protected Building() { }

    public boolean isEmpty() {return id == null;}

    public Integer getId() {
        return id;
    }

    public Location getLocation() {return location;}                                                                                        //get the location this Building belongs to
    public String getParamStr(Params param) {return strConv.convert(String.class, getParam(param));}                                        //get user param value as different type
    public int getParamInt(Params param) {return intConv.convert(Integer.class, getParam(param));}
    private String getParamXml(Params param) {return getParamStr(param).transform(s -> !s.isEmpty() ? String.format("%s=\"%s\"", param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    public String getBuildingXml() {return bldParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<B ", "/>"));}

    private Object getParam(Params paramName) {                                                                                             //try to find a field with the name equals to paramName
        List<Field> fields = getAllFields(this.getClass());
        Field field = fields.stream().filter(f -> f.getName().equals(paramName.toString())).findFirst().orElse(null);
        if (field == null) {
            logger.error("can't get building (%s) param %s: such field does not exist", this.getClass().getSimpleName(), paramName.toString());
            return StringUtils.EMPTY;
        }
        try {
            return field.get(this);                                                                                                         //and return it (or an empty string if null)
        } catch (Exception e) {logger.error("can't get building (%s) param %s: %s", this.getClass().getSimpleName(), paramName.toString(), e.getMessage());}

        return StringUtils.EMPTY;
    }

    private List<Field> getAllFields(Class clazz) {
        if (clazz == null)
            return Collections.emptyList();
        List<Field> result = new ArrayList<>(getAllFields(clazz.getSuperclass()));
        result.addAll(Arrays.asList(clazz.getDeclaredFields()));
        return result;
    }

}
