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

    @Column(name = "\"ROOM\"") private Integer ROOM;
    private Double cost;                                                                                                                    //1000 weight units price
    private Boolean bigmap_shown;                                                                                                           //include this route to bigmap data

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "p_id")
    private Portal portal;                                                                                                                  //Portal this route goes from

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "b_id")
    private Building dstBuilding;                                                                                                           //Building this route goes to

    public PortalRoute() {  }

    public String getBigMapData() {return bigmap_shown ? String.format("%s,%s", dstBuilding.getLocation().getLocalX(), dstBuilding.getLocation().getLocalY()) : StringUtils.EMPTY; }
}
