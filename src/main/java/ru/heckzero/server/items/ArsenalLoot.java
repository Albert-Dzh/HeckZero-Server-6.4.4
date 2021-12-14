package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;

import javax.persistence.*;

@Immutable
@Entity(name = "ArsenalLoot")
@Table(name = "arsenal_loot")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY, region = "ArsenalLoot_Region")
public class ArsenalLoot {
    private static final Logger logger = LogManager.getFormatterLogger();

    @Id
    private int id;                                                                                                                         //arsenal loot id
    private int aid;                                                                                                                        //arsenal id
    private String count;                                                                                                                   //item template override count

    @OneToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "loot_id")
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    private ItemTemplate itemTemplate;

    protected ArsenalLoot() { }

    public ItemTemplate getItemTemplate() {return itemTemplate;}
    public String getCount() {return count;}

    @Override
    public String toString() {
        return "ArsenalLoot{" +
                "id=" + id +
                ", count='" + count + '\'' +
                ", itemTemplate=" + itemTemplate +
                '}';
    }
}
