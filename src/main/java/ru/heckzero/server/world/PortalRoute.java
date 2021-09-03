package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;

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

    private Integer b_id;
    @Column(name = "\"ROOM\"") private Integer ROOM;
    private Double cost;                                                                                                                    //1000 weight units price
    private Integer bigmap_shown;                                                                                                           //show this route on a bigmap

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "p_id")
    private Portal portal;                                                                                                                  //portal association

    public PortalRoute() {  }

    public boolean isBigMapEnabled() {return bigmap_shown == 1;}

    public String getBigMapData() {
        Session session = ServerMain.sessionFactory.openSession();

        try (session) {
            Building bldTo = session.get(Building.class, b_id);
            Integer X = bldTo.getLocation().getLocalX();
            Integer Y = bldTo.getLocation().getLocalY();
            return String.format("%d,%d", X, Y);
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load building data by bid %d: %s", b_id, e.getMessage());
            e.printStackTrace();
        }
        return StringUtils.EMPTY;
    }
}
