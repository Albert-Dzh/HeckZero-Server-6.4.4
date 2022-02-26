package ru.heckzero.server.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.NativeQuery;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@org.hibernate.annotations.NamedNativeQueries({
        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryCertainDate", query = "select * from history h where h.sbj_id = :sbj_id and sbj_type = :sbj_type and h.dt >= :dt and h.dt <  :dt + 86400 order by h.id"),
        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryPrevDate", query = "with tmp_table as (select extract(epoch from date_trunc('day', to_timestamp(max(dt)))) as min_dt from history where sbj_id = :sbj_id and sbj_type = :sbj_type and dt < :dt) select * from history where sbj_id = :sbj_id and sbj_type = :sbj_type and dt >= (select min_dt from tmp_table) and dt < (select min_dt + 86400 from tmp_table) order by id"),
        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryNextDate", query = "with tmp_table as (select extract(epoch from date_trunc('day', to_timestamp(min(dt)))) as max_dt from history where sbj_id = :sbj_id and sbj_type = :sbj_type and dt >= :dt + 86400) select * from history where dt >= (select max_dt from tmp_table) and dt < (select max_dt + 86400 from tmp_table) order by id")
    }
)

@Entity(name = "History")
@Table(name = "history")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "History_Region")
public class History {
    public enum Subject {USER, BUILDING, CELL, CLAN}
    private static final Logger logger = LogManager.getFormatterLogger();

    public static void add(int sbj_id, Subject sbj_type, int code, String...params) {                                                       //add a history record
        new History(sbj_id, sbj_type, 0, code, params).sync();
        return;
    }
    public static void addIms(int user_id, int code, String...params) {                                                                     //add a history record for user that will be sent as IMS
        new History(user_id, Subject.USER, 1, code, params).sync();
        return;
    }
    public static List<History> getHistory(Subject sbj_type, int sbj_id, long date, String dx) {                                            //date - the requested date with time reset to 00:00:00
        String queryName = dx == null ? "HistoryCertainDate" : (dx.equals("-") ? "HistoryPrevDate" : "HistoryNextDate");
        try (Session session = ServerMain.sessionFactory.openSession()) {
            NativeQuery query = session.getNamedNativeQuery(queryName).setParameter("sbj_id", sbj_id).setParameter("sbj_type", sbj_type.ordinal()).setParameter("dt", date).addEntity(History.class).setCacheable(true);
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

    private int sbj_id;                                                                                                                     //subject id this record is related to
    private Subject sbj_type;                                                                                                               //subject type (user, building, bank cell, etc..)
    private int ims;                                                                                                                        //if this event is gonna be sent as an IMS on every user login
    private long dt;                                                                                                                        //event time (epoch sec)
    private int code;                                                                                                                       //the code (history message type)
    protected String param1 = StringUtils.EMPTY;
    protected String param2 = StringUtils.EMPTY;
    protected String param3 = StringUtils.EMPTY;
    protected String param4 = StringUtils.EMPTY;
    protected String param5 = StringUtils.EMPTY;

    protected History() { }

    private History(int sbj_id, Subject sbj_type, int ims, int code, String...param) {
        this.sbj_id = sbj_id;
        this.sbj_type = sbj_type;
        this.ims = ims;
        this.dt = Instant.now().getEpochSecond();
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

    public long getDt() {return dt;}                                                                                                        //regular getters
    public int getCode() {return code;}
    public String getParam1() {return param1;}
    public String getParam2() {return param2;}
    public String getParam3() {return param3;}
    public String getParam4() {return param4;}
    public String getParam5() {return param5;}

    private void sync() {ServerMain.sync(this);}
}
