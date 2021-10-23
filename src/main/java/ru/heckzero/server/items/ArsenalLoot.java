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
    private int id;

    private int aid;
    private int loot_id;
    private String count;

    protected ArsenalLoot() { }

    public String getCount() { return count;}
}
