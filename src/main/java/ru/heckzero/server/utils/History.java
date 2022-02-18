package ru.heckzero.server.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.time.Instant;

@Entity(name = "History")
@Table(name = "history")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.INTEGER)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "History_Region")
public abstract class History {
    private static final Logger logger = LogManager.getFormatterLogger();

    protected History() { }

    public History(int code, String...param) {
        this.code = code;
        try { setParams(param); }
            catch (IllegalAccessException e) { e.printStackTrace(); }
        return;
    }

    private void setParams(String... params) throws IllegalAccessException {                                                                //set params1-params5 if any
        for (int i = 0; i < params.length; i++) {
            String fieldName = String.format("param%d", i + 1);
            Field f = FieldUtils.getDeclaredField(History.class, fieldName, true);
            f.set(this, params[i]);
        }
        return;
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
