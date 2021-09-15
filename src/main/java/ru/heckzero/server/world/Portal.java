package ru.heckzero.server.world;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "Portal")
@Table(name = "portals")
@PrimaryKeyJoinColumn(name = "b_id")
public class Portal extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);

    public enum PortalParams {cash, ds, city, p1, p2, bigmap_city, bigmap_shown};
    private static final EnumSet<PortalParams> portalParams = EnumSet.of(PortalParams.cash, PortalParams.ds, PortalParams.city, PortalParams.p1, PortalParams.p2, PortalParams.bigmap_city, PortalParams.bigmap_shown);

    private int cash;                                                                                                                       //portal cash
    private String ds;                                                                                                                      //discount (%) for citizens
    private String city;                                                                                                                    //of that city
    private String p1;                                                                                                                      //resources needed to teleport 1000 weight units ?
    private String p2;                                                                                                                      //corsair clone price
    private String bigmap_city;                                                                                                             //city on bigmap this portal represents
    private boolean bigmap_shown;                                                                                                           //should this portal be shown on a bigmap

    @OneToMany(mappedBy = "srcPortal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private final List<PortalRoute> routes = new ArrayList<>();

    public List<PortalRoute> getRoutes() { return routes; }

    public static Portal getPortal(int id) {                                                                                                //try to get Portal by building id from a database
        Session session = ServerMain.sessionFactory.openSession();
        try (session) {
            Portal portal = session.get(Portal.class, id);
            return (portal == null) ? new Portal() : portal;                                                                                //return portal data from db or a default portal if it was not found in database
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load portal with id %d from database: %s, generating a default Portal instance", id, e.getMessage());
        }
        return new Portal();                                                                                                                //in case of database error return a default portal
    }
    protected Portal() { }

    public String getBigMapXml() {                                                                                                          //generate an object for the bigmap (city and/or portal)
        StringJoiner sj = new StringJoiner("");
        if (StringUtils.isNotBlank(bigmap_city))                                                                                            //this portal "represents" a city on bigmap
            sj.add(String.format("<city name=\"%s\" xy=\"%s,%s\"/>", bigmap_city, getLocation().getLocalX(), getLocation().getLocalY()));

        String routes = this.routes.stream().map(PortalRoute::getBigMapData).filter(StringUtils::isNoneBlank).collect(Collectors.joining(";")); //get portal routes (from this portal)
        if (!routes.isEmpty())
            sj.add(String.format("<portal name=\"%s\" xy=\"%d,%d\" linked=\"%s;\"/>", getParamStr(Building.Params.txt), getLocation().getLocalX(), getLocation().getLocalY(), routes));
        return sj.toString();
    }

    @Override
    public String toString() {
        return "Portal{" +
                "cash=" + cash +
                ", ds='" + ds + '\'' +
                ", city='" + city + '\'' +
                ", p1='" + p1 + '\'' +
                ", p2='" + p2 + '\'' +
                ", bigmap_city='" + bigmap_city + '\'' +
                ", bigmap_shown=" + bigmap_shown +
                ", portalRoutes=" + routes +
                '}';
    }
}
