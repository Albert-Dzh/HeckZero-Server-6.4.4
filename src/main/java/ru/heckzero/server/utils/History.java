package ru.heckzero.server.utils;

import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.NativeQuery;
import org.hibernate.query.Query;
import org.hibernate.type.StringType;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.time.Instant;
import java.util.Collections;
import java.util.List;

@org.hibernate.annotations.NamedNativeQueries({
        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryAccountAttack", query = "select param1, count(param1) from history where dt > (select lastlogout from users where id = :user_id) and code = 3 and sbj_type = 0 and sbj_id = :user_id group by param1 having count(param1) > 5"),
        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryGivenDate", query = "select * from history h where h.sbj_id = :sbj_id and sbj_type = :sbj_type and h.dt >= :dt and h.dt < :dt + 86400 order by h.id"),
        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryPrevDate", query = "with tmp_table as (select extract(epoch from date_trunc('day', to_timestamp(max(dt)))) as min_dt from history where sbj_id = :sbj_id and sbj_type = :sbj_type and dt < :dt) select * from history where sbj_id = :sbj_id and sbj_type = :sbj_type and dt >= (select min_dt from tmp_table) and dt < (select min_dt + 86400 from tmp_table) order by id"),
        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryNextDate", query = "with tmp_table as (select extract(epoch from date_trunc('day', to_timestamp(min(dt)))) as max_dt from history where sbj_id = :sbj_id and sbj_type = :sbj_type and dt >= :dt + 86400) select * from history where dt >= (select max_dt from tmp_table) and dt < (select max_dt + 86400 from tmp_table) order by id"),

        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryGivenDateDZ", query ="SELECT * FROM history WHERE sbj_id = :sbj_id AND sbj_type = :sbj_type AND CAST(to_timestamp(dt) AS DATE) = CAST(:date AS DATE) ORDER BY id"),
        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryPrevDateDZ", query ="WITH aux AS (SELECT MAX(CAST(to_timestamp(dt) AS DATE)) AS dt_preceding FROM history WHERE sbj_id = :sbj_id AND sbj_type = :sbj_type AND CAST(to_timestamp(dt) AS DATE) < CAST(:date AS DATE)) SELECT * FROM history WHERE sbj_id = :sbj_id AND sbj_type = :sbj_type AND CAST(to_timestamp(dt) AS DATE) = (SELECT dt_preceding FROM aux) ORDER BY id"),
        @org.hibernate.annotations.NamedNativeQuery(name = "HistoryNextDateDZ", query ="WITH aux AS (SELECT MIN(CAST(to_timestamp(dt) AS DATE)) AS dt_following FROM history WHERE sbj_id = :sbj_id AND sbj_type = :sbj_type AND CAST(to_timestamp(dt) AS DATE) > CAST(:date AS DATE)) SELECT * FROM history WHERE sbj_id = :sbj_id AND sbj_type = :sbj_type AND CAST(to_timestamp(dt) AS DATE) = (SELECT dt_following FROM aux) ORDER BY id")
    }
)

@Entity(name = "History")
@Table(name = "history")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "History_Region")
public class History {
    public enum Subject {USER, BUILDING, CELL, CLAN}
    private static final Logger logger = LogManager.getFormatterLogger();

    public static void add(int sbj_id, Subject sbj_type, int code, String...params) {                                                       //add a history record for some subject
        new History(sbj_id, sbj_type, 0, code, params).sync();
        return;
    }
    public static void addIms(int user_id, int code, String...params) {                                                                     //add a history record for user that will be sent as IMS (shown upon each user login)
        new History(user_id, Subject.USER, 1, code, params).sync();
        return;
    }

    public static void clIMS(int user_id) {                                                                                                 //clear IMS flag for the messages of user id user_id
        Transaction tx = null;
        try (Session session = ServerMain.sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.createQuery("update History set ims = 0 where sbj_id = :user_id and sbj_type = 0 and ims = 1").setParameter("user_id", user_id).executeUpdate();
            tx.commit();
            return;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't update history  (delete IMS) for user id %d: %s:%s", user_id, e.getClass().getSimpleName(), e.getMessage());
            if (tx != null && tx.isActive())
                tx.rollback();
        }
        return;
    }

    public static List<History> getIMS(int user_id) {                                                                                       //select IMS messages for user by user_id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<History> query = session.createQuery("select h from History h where h.sbj_id = :user_id and h.sbj_type = 0 and h.ims = 1 order by h.id", History.class).setParameter("user_id", user_id).setCacheable(false);
            return query.list();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get history IMS logs: %s", e.getMessage());
        }
        return Collections.emptyList();
    }

    public static List<History> getHistory(Subject sbj_type, int sbj_id, String date, String dx) {                                          //date - the requested date "dd.MM.YY"; dx - "+/-"
        String queryName = dx == null ? "HistoryGivenDateDZ" : (dx.equals("-") ? "HistoryPrevDateDZ" : "HistoryNextDateDZ");
        try (Session session = ServerMain.sessionFactory.openSession()) {
            NativeQuery query = session.getNamedNativeQuery(queryName).setParameter("sbj_id", sbj_id).setParameter("sbj_type", sbj_type.ordinal()).setParameter("date", date).addEntity(History.class).setCacheable(true);
            return (List<History>) query.list();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get history logs: %s", e.getMessage());
        }
        return Collections.emptyList();
    }

    public static List<String> checkAccountAttack(int user_id) {
        try (Session session = ServerMain.sessionFactory.openSession()) {
            NativeQuery query = session.getNamedNativeQuery("HistoryAccountAttack").setParameter("user_id", user_id).addScalar("param1", StringType.INSTANCE).setCacheable(false);
            return (List<String>) query.list();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get history logs for account attacks: %s", e.getMessage());
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
    protected String param6 = StringUtils.EMPTY;

    protected History() { }

    private History(int sbj_id, Subject sbj_type, int ims, int code, String...param) {                                                      //a private constructor, use add() methods to add a history record
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
    public String getParam6() {return param6;}

    private void sync() {ServerMain.sync(this);}
}
