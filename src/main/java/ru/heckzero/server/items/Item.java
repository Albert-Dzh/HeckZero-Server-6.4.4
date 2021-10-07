package ru.heckzero.server.items;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.Transaction;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ParamUtils;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.time.Instant;
import java.util.EnumSet;
import java.util.StringJoiner;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.stream.Collectors;

@org.hibernate.annotations.NamedQuery(name = "ItemBox_USER", query = "select i from Item i where i.user_id = :id order by i.id", cacheable = false)
//@org.hibernate.annotations.NamedQuery(name = "ItemBox_BUILDING", query = "select i from Item i where i.b_id = :id order by i.id", cacheable = false)
@org.hibernate.annotations.NamedNativeQuery(name = "Item_GetNextMainId", query = "select nextval('main_id_seq')  as nextval", cacheable = false)
@org.hibernate.annotations.NamedQuery(name = "Item_DeleteItemById", query = "delete from Item i where i.id = :id or i.pid = :id")

@Entity(name = "Item")
@Table(name = "items_inventory")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "default")
public class Item {
    private static final Logger logger = LogManager.getFormatterLogger();

    public enum Params {id, pid,    section, slot,     name, txt, massa, st, made, min, protect, quality, maxquality, OD, rOD, type, damage, calibre, shot, nskill, max_count, up, grouping, range, nt, build_in, c, radius, cost, cost2, s1, s2, s3, s4, count, lb, dt, hz, res, owner, tm, ln}
    private static final EnumSet<Params> itemParams = EnumSet.of(Params.id, Params.section, Params.slot, Params.name, Params.txt, Params.massa, Params.st, Params.min, Params.protect, Params.quality, Params.maxquality, Params.OD, Params.rOD, Params.type, Params.damage, Params.calibre, Params.shot, Params.nskill, Params.max_count, Params.up, Params.grouping, Params.range, Params.nt, Params.build_in, Params.c, Params.radius, Params.cost, Params.cost2, Params.s1, Params.s2, Params.s3, Params.s4, Params.count, Params.lb, Params.dt, Params.hz, Params.res, Params.owner, Params.tm, Params.ln);

    public static void delItem(long id) {                                                                                                   //delete item from database
        Transaction tx = null;
        try (Session session = ServerMain.sessionFactory.openSession()) {
            tx = session.beginTransaction();
            session.createNamedQuery("Item_DeleteItemById").setParameter("id", id).executeUpdate();
            tx.commit();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't delete item id %d: %s:%s", id, e.getClass().getSimpleName(), e.getMessage());
            if (tx != null && tx.isActive())
                tx.rollback();
        }
        return;
    }

    @Transient AtomicBoolean needSync = new AtomicBoolean(false);

    @Id
    private Long id;

    private long pid = -1;                                                                                                                  //parent item id (-1 means no parent, a master item)
    private String name = "foobar";                                                                                                         //item name in a client (.swf and sprite)
    private String txt = "Unknown";                                                                                                         //item text representation for human
    private int massa;                                                                                                                      //item weight
    private String st;                                                                                                                      //slots- appropriate slots this item can be wear on
    private String made;                                                                                                                    //made by
    private String min;                                                                                                                     //minimal requirements
    private String protect;                                                                                                                 //item protection properties
    private String quality, maxquality;                                                                                                     //current item quality
    @Column(name = "\"OD\"") private String OD;                                                                                             //OD needed to use an item
    @Column(name = "\"rOD\"") private String rOD;                                                                                           //OD needed for reload
    private String type;
    private String damage;
    private String calibre;
    private String shot;
    private String nskill;                                                                                                                  //category (perk?) name-skill?
    private String max_count;                                                                                                               //max number of included items allowed
    private String up;                                                                                                                      //user parameters this item does influence on
    private String grouping;
    private String range;                                                                                                                   //range of action
    private String nt = "1";                                                                                                                //no transfer, this item can't be transferred
    private String build_in;
    private String c;                                                                                                                       //item category
    private String radius;
    private String cost, cost2;                                                                                                             //something related to shop sales
    private String s1, s2, s3, s4;                                                                                                          //s4 - LANG[perk_group_1]
    private String count;                                                                                                                   //item count
    private String lb;
    private String dt;                                                                                                                      //expiry date
    private String hz;
    private String res;
    private String owner;                                                                                                                   //item owner
    private String tm;
    private String ln;                                                                                                                      //long name text

    private int user_id;                                                                                                                    //user id this item belongs to
    private String section;                                                                                                                 //the section number in user box or building warehouse
    private String slot;                                                                                                                    //user's slot this item is on

    @Transient private ItemBox included = new ItemBox();                                                                                    //included items have pid = this.id

    public boolean isIncluded() {return pid != -1;}
    public boolean needCreateNewId(int count) {return count < getParamInt(Params.count) || getParamInt(Params.count) > 0 && getParamDouble(Params.calibre) > 0;}

    public Long getId() { return id; }
    public long getPid() {return pid; }
    public ItemBox getIncluded() {return included;}

    public void setParam(Params paramName, Object paramValue) {                                                                             //set an item param to paramValue
        if (ParamUtils.setParam(this, paramName.toString(), paramValue))                                                                    //delegate param setting to ParamUtils
            needSync.compareAndSet(false, true);
        return;
    }


    public String getParamStr(Params param) {return ParamUtils.getParamStr(this, param.toString());}
    public int getParamInt(Params param) {return ParamUtils.getParamInt(this, param.toString());}
    public long getParamLong(Params param) {return ParamUtils.getParamLong(this, param.toString());}
    public double getParamDouble(Params param) {return ParamUtils.getParamDouble(this, param.toString());}
    private String getParamXml(Params param) {return ParamUtils.getParamXml(this, param.toString());}                                      //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false

    public String getXml() {return getXml(true);}
    public String getXml(boolean withIncluded) {
        StringJoiner sj = new StringJoiner("", "", "</O>");
        sj.add(itemParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<O ", ">")));
        sj.add(withIncluded ? included.getXml() : StringUtils.EMPTY);
        return sj.toString();
    }

    public ItemBox getExpired() {
        ItemBox expired = new ItemBox();
        long dt = getParamLong(Params.dt);
        if (dt > 0 && dt <= Instant.now().getEpochSecond())
            expired.add(this);
        expired.add(included.getExpired());
        return expired;
    }
    public Item findById(long id) {
        return this.id == id ? this : included.findItemById(id);
    }

    public void unload() {setParam(Params.pid, -1);}

    public void decrease(int num) {
        int count = getParamInt(Params.count);
        if (count > num)
            setParam(Params.count, count - num);
        else
            logger.error("can't decrease item id %d by num %d, current item count = %d", count);
        return;
    }

    public void insertItem(Item sub) {
        included.add(sub);
        return;
    }

    public void sync() {sync(false);}
    public void sync(boolean force) {
        if (needSync.compareAndSet(true, false) || force) {                                                                                 //sync if needSync or force is true
            logger.info("syncing item %s", this);
            ServerMain.sync(this);
        } else
            logger.debug("skipping syncing item id %d cause it hasn't been changed", id);
        included.sync();                                                                                                                    //sync included items
        return;
    }

    @Override
    public String toString() {
        return "Item{" +
                "id=" + id +
                ", count=" + count +
                ", pid=" + pid +
                ", txt='" + txt + '\'' +
                ", included=" + included +
                "}\n";
    }
}
