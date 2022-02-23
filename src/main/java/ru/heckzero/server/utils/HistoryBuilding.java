package ru.heckzero.server.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.user.User;
import ru.heckzero.server.world.BankCell;
import ru.heckzero.server.world.Building;

import javax.persistence.*;

@Entity(name = "HistoryBuilding")
@Table(name = "history")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "History_Region")

public class HistoryBuilding {
    private static final Logger logger = LogManager.getFormatterLogger();

    protected HistoryBuilding() { }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "history_sequence_generator")
    @SequenceGenerator(name = "history_sequence_generator", sequenceName = "history_id_seq", allocationSize = 1)
    private Integer id;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;                                                                                                                      //User this record relates to

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "b_id")
    private Building building;

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "cell_id")
    private BankCell bankCell;

    private int ims;                                                                                                                        //will this event be sent as an IMS
    private long dt;                                                                                                                        //time (epoch)
    private int code;                                                                                                                       //history code
    private String param1;
    private String param2;
    private String param3;
    private String param4;
    private String param5;

    public boolean sync() {return ServerMain.sync(this);}
}
