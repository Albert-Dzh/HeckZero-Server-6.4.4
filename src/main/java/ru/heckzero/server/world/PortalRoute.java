package ru.heckzero.server.world;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.util.Collections;
import java.util.List;

@Entity(name = "PortalRoute")
@Table(name = "portal_routes")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class PortalRoute {
    private static final Logger logger = LogManager.getFormatterLogger();

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "portal_routes_generator_sequence")
    @SequenceGenerator(name = "portal_routes_generator_sequence", sequenceName = "portal_routes_id_seq", allocationSize = 1)
    private Integer id;

    @Column(name = "\"ROOM\"") private int ROOM;                                                                                            //room inside the building this route goes to
    private double cost;                                                                                                                    //1000 weight units teleportation price
    private boolean enabled;                                                                                                                //this route is enabled
    private boolean bigmap_enabled;                                                                                                         //include this route into bigmap data

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "p_id_src")
    private Portal srcPortal;                                                                                                               //Portal id this route goes from (foreign key to portals b_id)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "p_id_dst")
    private Portal dstPortal;                                                                                                               //Portal id this route goes to (foreign key to portals b_id)

    public static PortalRoute getRoute(int route_id) {                                                                                      //get PortalRoute by the given id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<PortalRoute> query = session.createQuery("select pr from PortalRoute pr inner join fetch pr.dstPortal p_dst inner join p_dst.location loc_dst where pr.id = :id", PortalRoute.class).setParameter("id", route_id).setCacheable(true);
            PortalRoute route = query.getSingleResult();
            if (route != null) {
                Hibernate.initialize(route.getDstPortal().getLocation());
                return route;
            }else
                logger.error("can't find portal route with id %d in database. Check it out!", route_id);
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load portal route with id %d from database: %s", route_id, e.getMessage());
        }
        return null;
    }

    public static List<PortalRoute> getComeinRoutes(int p_dst_id) {                                                                         //get routes with the given destination portal id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<PortalRoute> query = session.createQuery("select pr from PortalRoute pr inner join fetch pr.srcPortal p_src inner join fetch pr.dstPortal p_dst inner join fetch p_src.location where p_dst.id = :id", PortalRoute.class).setParameter("id", p_dst_id).setCacheable(true);
            List<PortalRoute> comeinRoutes = query.list();
            comeinRoutes.forEach(r -> Hibernate.initialize(r.srcPortal.getLocation()));                                                     //initialize included fields on subsequent queries from L2 cache
            return comeinRoutes;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get incoming routes for destination portal id %d: %s", p_dst_id, e.getMessage());
        }
        return Collections.emptyList();
    }

    public PortalRoute() {  }

    public boolean isEnabled() {return enabled;}
    public boolean isBigmapEnabled() {return bigmap_enabled;}

    public int getROOM() {return ROOM;}
    public Portal getDstPortal() {return dstPortal;}

    public void setCost(double cost) {this.cost = cost;}

    public void sync() { ServerMain.sync(this); }

    public String getXml() {return String.format("<O id=\"%d\" txt=\"%s\" X=\"%d\" Y=\"%d\" cost=\"%.1f\" ds=\"%d\" city=\"%s\"/>", id, dstPortal.getTxt(), dstPortal.getLocation().getLocalX(), dstPortal.getLocation().getLocalY(), cost, dstPortal.getDs(), dstPortal.getCity());}
    public String getXmlComein() {return String.format("<O id=\"%d\" txt=\"%s [%d/%d]\" cost=\"%.1f\"/>", id, srcPortal.getTxt(), srcPortal.getLocation().getLocalX(), srcPortal.getLocation().getLocalY(), cost);}
    public String getBigMap(){return String.format("%d,%d", dstPortal.getLocation().getLocalX(), dstPortal.getLocation().getLocalY());}     //get route coordinates for the BigMap
}
