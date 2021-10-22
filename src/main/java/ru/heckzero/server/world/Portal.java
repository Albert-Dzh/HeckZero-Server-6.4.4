package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

@Entity(name = "Portal")
@Table(name = "portals")
@PrimaryKeyJoinColumn(name = "b_id")
public class Portal extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> portalParams = EnumSet.of(Params.cash, Params.ds, Params.city, Params.p1, Params.p2);

    private int cash;                                                                                                                       //portal cash
    private int ds;                                                                                                                         //discount (%) for citizens arriving to this portal
    private String city;                                                                                                                    //of that city
    private String p1;                                                                                                                      //resources needed to teleport 1000 weight units ?
    private String p2;                                                                                                                      //corsair clone price
    private String bigmap_city;                                                                                                             //city on bigmap this portal represents
    private boolean bigmap_enabled;                                                                                                         //should this portal be shown on a bigmap

    @OneToMany(mappedBy = "srcPortal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private final List<PortalRoute> routes = new ArrayList<>();

    @Transient private ItemBox itemBox = null;                                                                                              //portal Item box

    public String getCity() {return city;}                                                                                                  //these citizens have a discount when they are flying TO this portal
    public int getDs()      {return ds;}                                                                                                    //discount for citizens

    public static List<Portal> getBigmapPortals() {                                                                                         //generate world map data - cities, portal and routes
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Portal> query = session.createQuery("select p from Portal p inner join fetch p.location l left join fetch p.routes pr where p.bigmap_enabled = true", Portal.class).setCacheable(true).setReadOnly(true);
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
            logger.error("can't load portal with id %d from database: %s", id, e.getMessage());
        }
        return null;                                                                                                                        //in case of portal was not found or database error return a default portal instance
    }

    protected Portal() { }

    private void ensureInitialized() {                                                                                                      //initialize portal fields from L2 cache
        Hibernate.initialize(getLocation());
        routes.forEach(r -> Hibernate.initialize(r.getDstPortal().getLocation()));
        return;
    }
    private String getXmlRoutes() {return routes.stream().filter(PortalRoute::isEnabled).map(PortalRoute::getXml).collect(Collectors.joining()); }

    public String getXmlBigmap() {                                                                                                          //generate an object for the bigmap (city and/or portal)
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(bigmap_city))                                                                                            //this portal "represents" a city on bigmap
            sb.append(String.format("<city name=\"%s\" xy=\"%s,%s\"/>", bigmap_city, getLocation().getLocalX(), getLocation().getLocalY()));

        String routes = this.routes.stream().filter(PortalRoute::isBigmapEnabled).map(PortalRoute::getBigMap).collect(Collectors.joining(";")); //get portal routes (from this portal)
        if (!routes.isEmpty())
            sb.append(String.format("<portal name=\"%s\" xy=\"%d,%d\" linked=\"%s;\"/>", getParamStr(Building.Params.txt), getLocation().getLocalX(), getLocation().getLocalY(), routes));
        return sb.toString();
    }

    public String getXmlPR() {
        StringJoiner sj = new StringJoiner("", "", "</PR>");
        sj.add(portalParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<PR ", ">"))); //add XML portal params
        sj.add(getXmlRoutes());                                                                                                             //add XML portal routes

        ItemBox tmpBox = new ItemBox(getItemBox());                                                                                         //we need to create a temporary itembox
        tmpBox.del(i -> i.getCount() == 1);                                                                                                 //delete all items with count=1
        tmpBox.forEach(Item::decrement);                                                                                                    //that is all for mimic client behaviour
        sj.add(tmpBox.getXml());                                                                                                            //add portal item list

        return sj.toString();
    }

    public ItemBox getItemBox() {return itemBox == null ? (itemBox = ItemBox.getItemBox(ItemBox.boxType.BUILDING, getId(), true)) : itemBox;} //get portal itembox, initialize if needed
}
