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
    private int cost;                                                                                                                       //1000 weight units teleportation price
    private boolean enabled;                                                                                                                //this route is enabled
    private boolean bigmap_enabled;                                                                                                         //include this route into bigmap data

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "p_id_src")
    private Portal srcPortal;                                                                                                               //Portal id this route goes from (foreign key to portals b_id)

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "p_id_dst")
    private Portal dstPortal;                                                                                                               //Portal id this route goes to (foreign key to portals b_id)

    public static List<PortalRoute> getComeinRoutes(int p_id) {                                                                             //incoming routes for portal p_id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<PortalRoute> query = session.createQuery("select pr from PortalRoute pr inner join fetch pr.srcPortal p_src inner join fetch pr.dstPortal p_dst inner join fetch p_src.location where p_dst.id = :id", PortalRoute.class).setParameter("id", p_id).setCacheable(true);
            List<PortalRoute> comeinRoutes = query.list();
            comeinRoutes.forEach(r -> Hibernate.initialize(r.srcPortal.getLocation()));                                                     //initialize included fields on subsequent queries
            return comeinRoutes;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get incoming routes for portal id %d: %s", p_id, e.getMessage());
        }
        return Collections.emptyList();
    }

    public PortalRoute() {  }

    public boolean isEnabled() {return enabled;}
    public boolean isBigmapEnabled() {return bigmap_enabled;}

    public Portal getDstPortal() {return dstPortal; }

    public String getXmlPortal() {return String.format("<O id=\"%d\" txt=\"%s\" X=\"%d\" Y=\"%d\" cost=\"%d\" ds=\"%d\" city=\"%s\"/>", dstPortal.getId(), dstPortal.getTxt(), dstPortal.getLocation().getLocalX(), dstPortal.getLocation().getLocalY(), cost, dstPortal.getDs(), dstPortal.getCity());}
    public String getXmlComein() {return String.format("<O id=\"%d\" txt=\"%s [%d/%d]\" cost=\"%d\"/>", id, srcPortal.getTxt(), srcPortal.getLocation().getLocalX(), srcPortal.getLocation().getLocalY(), cost);}
    public String getBigMapData(){return String.format("%d,%d", dstPortal.getLocation().getLocalX(), dstPortal.getLocation().getLocalY());}  //get route coordinates for the BigMap
}
