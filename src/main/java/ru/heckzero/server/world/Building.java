package ru.heckzero.server.world;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.stream.Collectors;

@Entity(name = "Building")
@Table(name = "locations_b")
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
    @Column(name = "\"Z\"")private int Z;                                                                                                   //unique building number withing a location

    private String txt = "a stub building";                                                                                         //building description
    @Column(name = "\"maxHP\"")
    private String maxHP;
    @Column(name = "\"HP\"")
    private String HP;
    private int name = 0;                                                                                                                   //building type 0 - no building
    private String upg;
    private String maxl;
    private String repair;
    private String clan;

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "l_id")
    private Location location;                                                                                                              //location association

    protected Building() { }
    public Building(int Z) {this.Z = Z;}                                                                                                    //constructor with a certain Z coordinate

    public boolean isEmpty() {return name == 0;}

    public Location getLocation() {                                                                                                         //get the location this Building belongs to
        return location;
    }

    public String getParamStr(Params param) {return strConv.convert(String.class, getParam(param));}                                        //get user param value as different type
    public int getParamInt(Params param) {return intConv.convert(Integer.class, getParam(param));}
    private String getParamXml(Params param) {return getParamStr(param).transform(s -> !s.isEmpty() ? String.format("%s=\"%s\"", param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    public String getBuildingXml() {return bldParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<B ", "/>"));}

    private Object getParam(Params paramName) {                                                                                             //try to find a field with the name equals to paramName
        try {
            Field field = this.getClass().getDeclaredField(paramName.toString());
            return field.get(this);                                                                                                         //and return it (or an empty string if null)
        } catch (Exception e) {logger.error("can't get building param %s: %s", paramName.toString(), e.getMessage()); }
        return StringUtils.EMPTY;
    }
}
