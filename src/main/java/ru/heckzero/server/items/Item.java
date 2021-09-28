package ru.heckzero.server.items;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

@Entity(name = "Item")
@Table(name = "items_inventory")
@Inheritance(strategy = InheritanceType.JOINED)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class Item {
    @Id
    private Integer id;

    int pid;
    String name;
    String txt;
    int massa;
    String st;
    String made;
    String min;
    String protect;
    String quality;
    String maxquality;
    @Column(name = "\"OD\"") String OD;
    String type;
    String damage;


}
