package ru.heckzero.server.world;

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

    private Integer pid_to;
    @Column(name = "\"ROOM\"") private Integer ROOM;
    private Double cost;

    public PortalRoute() {  }

    @ManyToOne(fetch = FetchType.EAGER)
    @JoinColumn(name = "pid_from")
    private Portal portal;                                                                                                                  //portal association

}
