package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

@Entity(name = "Portal")
@Table(name = "portals")
@PrimaryKeyJoinColumn(name = "b_id")
public class Portal extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> portalParams = EnumSet.of(Params.cash, Params.ds, Params.city, Params.p1, Params.p2);

    protected int cash;                                                                                                                     //portal cash
    protected int ds;                                                                                                                       //discount (%) for citizens arriving to this portal
    protected String city;                                                                                                                  //of that city
    protected String p1;                                                                                                                    //resources needed to teleport 1000 weight units ?
    protected String p2;                                                                                                                    //corsair clone price
    private String bigmap_city;                                                                                                             //city on bigmap this portal represents
    private boolean bigmap_enabled;                                                                                                         //should this portal be shown on a bigmap

    @OneToMany(mappedBy = "srcPortal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private final List<PortalRoute> routes = new ArrayList<>();

    public String getCity() { return city; }
    public int getDs() { return ds;}
    public List<PortalRoute> getRoutes() {return routes;}

    public static List<Portal> getBigmapPortals() {
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Portal> query = session.createQuery("select p from Portal p inner join fetch p.location l left join fetch p.routes pr where p.bigmap_enabled = true", Portal.class).setCacheable(true);
            List<Portal> portals = query.list();
            portals.forEach(Portal::ensureInitialized);                                                                                     //initialize location and routes on subsequent queries from L2 cache
            return portals;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load bigmap data: %s", e.getMessage());
            e.printStackTrace();
        }
        return Collections.emptyList();
    }

    public static Portal getPortal(int id) {                                                                                                //try to get Portal by building id from a database
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Portal> query = session.createQuery("select p from Portal p left join fetch p.routes pr left join fetch pr.dstPortal p_dst left join fetch p_dst.location where p.id = :id", Portal.class).setParameter("id", id).setCacheable(true);
            Portal portal = query.getSingleResult();
            if (portal != null)
                portal.ensureInitialized();
            return portal;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load portal with id %d from database: %s, generating a default Portal instance", id, e.getMessage());
        }
        return null;                                                                                                                        //in case of portal was not found or database error return a default portal instance
    }

    protected Portal() { }

    protected void ensureInitialized() {                                                                                                    //initialize portal fields from L2 cache
        Hibernate.initialize(getLocation());
        routes.forEach(r -> Hibernate.initialize(r.getDstPortal().getLocation()));
        return;
    }

    public String getXmlBigmap() {                                                                                                          //generate an object for the bigmap (city and/or portal)
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(bigmap_city))                                                                                            //this portal "represents" a city on bigmap
            sb.append(String.format("<city name=\"%s\" xy=\"%s,%s\"/>", bigmap_city, getLocation().getLocalX(), getLocation().getLocalY()));

        String routes = this.routes.stream().filter(PortalRoute::isBigmapEnabled).map(PortalRoute::getBigMap).collect(Collectors.joining(";")); //get portal routes (from this portal)
        if (!routes.isEmpty())
            sb.append(String.format("<portal name=\"%s\" xy=\"%d,%d\" linked=\"%s;\"/>", getParamStr(Building.Params.txt), getLocation().getLocalX(), getLocation().getLocalY(), routes));
        return sb.toString();
    }

    public String getXmlPR() {return portalParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<PR ", ">"));}
    public String getXmlRoutesPR() {return routes.stream().filter(PortalRoute::isEnabled).map(PortalRoute::getXmlPR).collect(Collectors.joining()); }

}
