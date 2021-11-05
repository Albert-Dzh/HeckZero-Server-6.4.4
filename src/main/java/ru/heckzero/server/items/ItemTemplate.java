package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;

@Immutable
@Entity(name = "ItemTemplate")
@Table(name = "items_template")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY, region = "default")
public class ItemTemplate {
    private static final Logger logger = LogManager.getFormatterLogger();

    @Id
    private long id;

    private String name = "foobar";                                                                                                         //item name in a client (.swf and sprite)
    private String txt = "Unknown";                                                                                                         //item text representation for a human
    private int massa;                                                                                                                      //item weight
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

    public Long getId()  {return id; }

    @Override
    public String toString() {return "Item{" + "id=" + id + ", txt='" + txt + '\'' + ", count=" + count + "}"; }
}
