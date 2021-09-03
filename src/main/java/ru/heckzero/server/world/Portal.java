package ru.heckzero.server.world;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "Portal")
@Table(name = "portals")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")

public class Portal {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);

    public enum Params {cash, ds, city, p1, p2, bigmap_city, bigmap_shown};
    private static final EnumSet<Params> portalParams = EnumSet.of(Params.cash, Params.ds, Params.city, Params.p1, Params.p2, Params.bigmap_city, Params.bigmap_shown);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "portal_generator_sequence")
    @SequenceGenerator(name = "portal_generator_sequence", sequenceName = "portals_id_seq", allocationSize = 1)
    private Integer id;

    private Integer cash;
    private String ds;
    private String city;
    private String p1;
    private String p2;
    private String bigmap_city;
    private Integer bigmap_shown;

    @OneToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "b_id")
    private Building building;

    @OneToMany(mappedBy = "portal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private final List<PortalRoute> portalRoutes = new ArrayList<>();

    public Portal() { }

    public String getParamStr(Params param) {return strConv.convert(String.class, getParam(param));}                                        //get user param value as different type
    public Integer getParamInt(Params param) {return intConv.convert(Integer.class, getParam(param));}
    private String getParamXml(Params param) {return getParamStr(param).transform(s -> !s.isEmpty() ? String.format("%s=\"%s\"", param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
//    public String getPortalXml() {return portalParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<B ", "/>"));}

    public String getBigMapXml() {
        StringJoiner sj = new StringJoiner("");
        if (StringUtils.isNotBlank(bigmap_city))
            sj.add(String.format("<city name=\"%s\" xy=\"%s,%s\"/>", bigmap_city, building.getLocation().getLocalX(), building.getLocation().getLocalY()));

        String routes = portalRoutes.stream().filter(PortalRoute::isBigMapEnabled).map(PortalRoute::getBigMapData).collect(Collectors.joining(";"));
        sj.add(String.format("<portal name=\"%s\" xy=\"%d,%d\"%s/>", building.getParamStr(Building.Params.txt), building.getLocation().getLocalX(), building.getLocation().getLocalY(), routes.isEmpty() ? "" : String.format(" linked=\"%s;\"", routes)));
        return sj.toString();
    }

    private Object getParam(Params paramName) {                                                                                             //try to find a field with the name equals to paramName
        try {
            Field field = this.getClass().getDeclaredField(paramName.toString());
            return field.get(this);                                                                                                         //and return it (or an empty string if null)
        } catch (Exception e) {logger.error("can't get portal param %s: %s", paramName.toString(), e.getMessage()); }
        return StringUtils.EMPTY;
    }


    @Override
    public String toString() {
        return "Portal{" +
                "id=" + id +
                ", cash=" + cash +
                ", ds='" + ds + '\'' +
                ", city='" + city + '\'' +
                ", p1='" + p1 + '\'' +
                ", p2='" + p2 + '\'' +
                ", bigmap_city='" + bigmap_city + '\'' +
                ", bigmap_shown='" + bigmap_shown + '\'' +
//                ", building=" + building +
                '}';
    }
}
