package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;

import javax.persistence.*;
import java.util.*;
import java.util.stream.Collectors;

@Entity(name = "Portal")
@Table(name = "portals")
@PrimaryKeyJoinColumn(name = "b_id")
public class Portal extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> portalParams = EnumSet.of(Params.cash, Params.ds, Params.city, Params.p1, Params.p2);

//    private int cash;                                                                                                                     //portal cash
    private int ds;                                                                                                                         //discount (%) for citizens arriving to this portal
    private String city;                                                                                                                    //of that city
    private String p1;                                                                                                                      //resources needed to teleport 1000 weight units ?
    private String p2;                                                                                                                      //corsair clone price
    private String bigmap_city;                                                                                                             //city on bigmap this portal represents
    private boolean bigmap_enabled;                                                                                                         //should this portal be shown on a bigmap

    @OneToMany(mappedBy = "srcPortal", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private final List<PortalRoute> routes = new ArrayList<>();

    public String getCity() {return city;}                                                                                                  //these citizens have a discount when they are flying TO this portal
    public int getDs()      {return ds;}                                                                                                    //discount for citizens of the 'city' which are flying to that portal

    public static Portal getPortal(int id) {                                                                                                //try to get Portal by building id from a database
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Portal> query = session.createQuery("select p from Portal p left join fetch p.routes pr left join fetch pr.dstPortal p_dst left join fetch p_dst.location where p.id = :id order by p_dst.txt", Portal.class).setParameter("id", id).setCacheable(true);
            Portal portal = query.getSingleResult();
            if (portal != null)
                portal.ensureInitialized();
            return portal;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load portal with id %d from database: %s", id, e.getMessage());
        }
        return new Portal();                                                                                                                //in case of portal was not found or database error return a default portal instance
    }

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

    private static List<PortalRoute> getComeinRoutes(int p_dst_id) {                                                                         //get routes for the given destination portal id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<PortalRoute> query = session.createQuery("select pr from PortalRoute pr inner join fetch pr.srcPortal p_src inner join fetch pr.dstPortal p_dst inner join fetch p_src.location where p_dst.id = :id order by p_src.txt", PortalRoute.class).setParameter("id", p_dst_id).setCacheable(true);
            List<PortalRoute> comeinRoutes = query.list();
            comeinRoutes.forEach(r -> Hibernate.initialize(r.getSrcPortal().getLocation()));                                                //initialize included fields on subsequent queries from L2 cache
            return comeinRoutes;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get incoming routes for destination portal id %d: %s:%s", p_dst_id, e.getClass().getSimpleName(), e.getMessage());
        }
        return Collections.emptyList();
    }

    protected Portal() { }

    private void ensureInitialized() {                                                                                                      //initialize portal fields from L2 cache
        Hibernate.initialize(getLocation());
        routes.forEach(r -> Hibernate.initialize(r.getDstPortal().getLocation()));
        return;
    }
    private String getXmlRoutes() {return routes.stream().filter(PortalRoute::isEnabled).map(PortalRoute::getXml).collect(Collectors.joining());} //portal routes XML formatted in <O />

    public String bigMapXml() {                                                                                                             //generate an object for the bigmap (city and/or portal)
        StringBuilder sb = new StringBuilder();
        if (StringUtils.isNotBlank(bigmap_city))                                                                                            //this portal "represents" a city on bigmap
            sb.append(String.format("<city name=\"%s\" xy=\"%s,%s\"/>", bigmap_city, getLocation().getLocalX(), getLocation().getLocalY()));

        String routes = this.routes.stream().filter(PortalRoute::isBigmapEnabled).map(PortalRoute::getBigMap).collect(Collectors.joining(";")); //get portal routes (from this portal)
        if (!routes.isEmpty())
            sb.append(String.format("<portal name=\"%s\" xy=\"%d,%d\" linked=\"%s;\"/>", getParamStr(Building.Params.txt), getLocation().getLocalX(), getLocation().getLocalY(), routes));
        return sb.toString();
    }

    public String cominXml() {                                                                                                              //get incoming routes as XML string
        StringJoiner sj = new StringJoiner("", "<PR comein=\"1\">", "</PR>");
        List<PortalRoute> comeinRoutes = getComeinRoutes(getId());                                                                          //get incoming routes for the current portal
        comeinRoutes.forEach(r -> sj.add(r.getXmlComein()));                                                                                //add each route as XML string
        return sj.toString();
    }

    public String prXml() {                                                                                                                 //XML formatted portal data including routes and warehouse items
        StringJoiner sj = new StringJoiner("", "", "</PR>");
        sj.add(portalParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<PR ", ">"))); //add XML portal params
        sj.add(getXmlRoutes());                                                                                                             //add XML portal routes
        sj.add(getItemBox().getXml());                                                                                                      //add portal warehouse items (resources)
        return sj.toString();
    }

    public boolean consumeRes(int userWeight) {                                                                                             //consume portal resources for the teleportation
        String [] res = p1.split(",");                                                                                                      //p1 - resources needed to teleport 1000 weight units
        Map<Item, Integer> itemsToConsume = new HashMap<>();                                                                                //found items to consume with consume

        for (int i = 0; i < res.length; i++) {
            if (res[i].isEmpty())                                                                                                           //res doesn't contain resource of number i
                continue;
            Item item = getItemBox().findItemByType(Double.parseDouble(String.format("0.19%d", i + 1)));                                    //find needed resource on portal warehouse
            int requiredRes = NumberUtils.toInt(res[i]) * userWeight / 1000;                                                                //needed resource count to teleportation
            if (item == null || item.getCount() < requiredRes) {
                logger.warn("portal id %d %s has got not enough resources of type %d, (needed %d, has got %d)", getId(), getTxt(), i + 1, requiredRes, item == null ? 0 : item.getCount());
                return false;
            }
            itemsToConsume.put(item, requiredRes);                                                                                          //item to consume and a count to decrease
        }
        itemsToConsume.forEach((k, v) -> logger.info("consuming resource %s of count %d in portal %d %s", k.getParamStr(Item.Params.txt), v, getId(), getTxt()));
        itemsToConsume.forEach((k, v) -> getItemBox().delItem(k.getId(), v));
        return true;
    }

}
