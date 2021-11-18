package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;

@Immutable
@Entity(name = "ItemTemplate")
@Table(name = "items_template")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY, region = "ItemTemplate_Region")
public class ItemTemplate {
    private static final Logger logger = LogManager.getFormatterLogger();

    public static final int BANK_KEY = 403;
    public static final int BANK_KEY_COPY = 670;

    public static Item getTemplateItem(int templateId) {
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<ItemTemplate> query = session.createQuery("select it from ItemTemplate it where id = :id", ItemTemplate.class).setParameter("id", templateId).setCacheable(true);
            ItemTemplate itemTemplate = query.getSingleResult();
            Item newItem = new Item(itemTemplate);
            newItem.setGlobalId(false);
            return newItem;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't clone template item with id %d: %s", templateId, e.getMessage());
        }
        return null;
    }

    @Id
    private int id;
    private String name;                                                                                                                    //item name in a client (.swf and sprite)
    private String txt;                                                                                                                     //item text representation for a human
    private String massa;                                                                                                                   //item weight
    private String st;                                                                                                                      //slots - appropriate slots this item can be wear on
    private String made;                                                                                                                    //made by
    private String min;                                                                                                                     //minimal requirements to be able to take an item on
    private String protect;                                                                                                                 //item protection properties
    private String quality, maxquality;                                                                                                     //current item quality
    @Column(name = "\"OD\"") private String OD;                                                                                             //OD needed to use an item
    @Column(name = "\"rOD\"") private String rOD;                                                                                           //OD needed for reload
    private double type;                                                                                                                    //item's type
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
    private String dt;                                                                                                                      //expiry time in epoch sec
    private String hz;
    private String res;
    private String owner;                                                                                                                   //item owner
    private String tm;
    private String ln;                                                                                                                      //long name text

    @Override
    public String toString() {return "Item{" + "id=" + id + ", txt='" + txt + '\'' + ", count=" + count + "}"; }
}
