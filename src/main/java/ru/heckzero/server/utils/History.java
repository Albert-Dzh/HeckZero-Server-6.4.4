package ru.heckzero.server.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.DiscriminatorFormula;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "History")
@Table(name = "history")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "History_Region")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorFormula("case when user_id is not null then 'HistoryUser' " +
        "else ( case when b_id is not null then 'HistoryBuilding' else 'HistoryBankCell' end ) " +
        "end ")

public abstract class History {
    private static final Logger logger = LogManager.getFormatterLogger();

    protected History() { }

    public History(int ims, long dt, int code) {
        this.ims = ims;
        this.dt = dt;
        this.code = code;
    }

    public History(int ims, long dt, int code, String param1) {
        this(ims, dt, code);
        this.param1 = param1;
    }

    public History(int ims, long dt, int code, String param1, String param2) {
        this(ims, dt, code, param1);
        this.param2 = param2;
    }

    public History(int ims, long dt, int code, String param1, String param2, String param3) {
        this(ims, dt, code, param1, param2);
        this.param3 = param3;
    }

    public History(int ims, long dt, int code, String param1, String param2, String param3, String param4) {
        this(ims, dt, code, param1, param2, param3);
        this.param4 = param4;
    }

    public History(int ims, long dt, int code, String param1, String param2, String param3, String param4, String param5) {
        this(ims, dt, code, param1, param2, param3, param4);
        this.param5 = param5;
    }


    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "history_sequence_generator")
    @SequenceGenerator(name = "history_sequence_generator", sequenceName = "history_id_seq", allocationSize = 1)
    private Integer id;

    private int ims;                                                                                                                        //will this event be sent as an IMS
    private long dt = Instant.now().getEpochSecond();                                                                                       //time (epoch)
    private int code;                                                                                                                       //history code
    private String param1 = StringUtils.EMPTY;
    private String param2 = StringUtils.EMPTY;
    private String param3 = StringUtils.EMPTY;
    private String param4 = StringUtils.EMPTY;
    private String param5 = StringUtils.EMPTY;

    public boolean sync() {return ServerMain.sync(this);}
}
