package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;

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

    @Column(name = "\"ROOM\"") private int ROOM;                                                                                            //room inside the building this route goes to
    private double cost;                                                                                                                    //1000 weight units teleportation price
    private boolean bigmap_shown;                                                                                                           //include this route into bigmap data

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "p_id")
    private Portal srcPortal;                                                                                                               //Portal id this route goes from (foreign key to portals)

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "d_id")
    private Portal dstPortal;                                                                                                               //Building id this route goes to (foreign key to location_b)

    public PortalRoute() {  }

    public String getBigMapData() {return bigmap_shown ? String.format("%d,%d", dstPortal.getBuilding().getLocation().getLocalX(), dstPortal.getBuilding().getLocation().getLocalY()) : StringUtils.EMPTY; }
}
