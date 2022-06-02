package ru.heckzero.server.world;

import lombok.Getter;
import lombok.Setter;
import lombok.ToString;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;

@Entity(name = "CityHallLoot")
@Table(name = "city_hall_loot")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "CityHallLoot_Region")
@ToString
@Getter
@Setter
public class CityHallLoot {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "city_hall_loot_gen")
    @SequenceGenerator(name = "city_hall_loot_gen", sequenceName = "city_hall_loot_id_seq", allocationSize = 1)
    private Integer id;

    int template_loot_id;
    int cost;
    String res;
    int count;
    int ch_id;
/*
    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "ch_id")
    private CityHall cityHall;                                                                                                              //City hall
*/

    protected CityHallLoot() { }
    public boolean sync() {return ServerMain.sync(this);}
}
