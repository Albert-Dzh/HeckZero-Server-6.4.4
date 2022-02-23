package ru.heckzero.server.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@org.hibernate.annotations.NamedQuery(name = "History_USER", query = "select h from HistoryUser h where h.user.id = :id order by h.id", readOnly = true)
@Entity(name = "History")
@Table(name = "history")
@Inheritance(strategy = InheritanceType.SINGLE_TABLE)
@DiscriminatorColumn(name = "dtype", discriminatorType = DiscriminatorType.INTEGER)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "History_Region")
public abstract class History {
    public enum Subject {USER, BUILDING, CELL, CLAN}
    private static final Logger logger = LogManager.getFormatterLogger();

    public static List<History> getHistory(Subject subject, int id) {
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<History> query = session.createNamedQuery(String.format("History_%s", subject.toString()), History.class).setParameter("id", id).setCacheable(true);
            List<History> historyLogs = query.list();
            return historyLogs;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get history logs: %s", e.getMessage());
        }
        return Collections.emptyList();
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "history_sequence_generator")
    @SequenceGenerator(name = "history_sequence_generator", sequenceName = "history_id_seq", allocationSize = 1)
    private Integer id;

    private long dt;                                                                                                                        //event time (epoch sec)
    private int code;                                                                                                                       //history code
    protected String param1 = StringUtils.EMPTY;
    protected String param2 = StringUtils.EMPTY;
    protected String param3 = StringUtils.EMPTY;
    protected String param4 = StringUtils.EMPTY;
    protected String param5 = StringUtils.EMPTY;

    protected History() { }

    protected History(int code, String...param) {
        this.dt = Instant.now().getEpochSecond();
        this.code = code;
        try { setParams(param); }
            catch (IllegalAccessException e) { e.printStackTrace(); }
        return;
    }

    public long getDt() {return dt;}                                                                                                        //regular getters
    public int getCode() {return code;}
    public String getParam1() {return param1;}
    public String getParam2() {return param2;}
    public String getParam3() {return param3;}
    public String getParam4() {return param4;}
    public String getParam5() {return param5;}

    private void setParams(String... params) throws IllegalAccessException {                                                                //set params1-params5 if any
        for (int i = 0; i < params.length; i++) {
            String fieldName = String.format("param%d", i + 1);
            Field f = FieldUtils.getDeclaredField(History.class, fieldName, true);
            f.set(this, params[i]);
        }
        return;
    }

    protected boolean sync() {return ServerMain.sync(this);}
}
