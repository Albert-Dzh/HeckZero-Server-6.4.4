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

    double template_type;                                                                                                                   //item template type
    int cost;                                                                                                                               //license cost
    String res;                                                                                                                             //license supplementary data
    int count;                                                                                                                              //num of available license
    int ch_id;                                                                                                                              //city hall id

    protected CityHallLoot() { }
    public boolean sync() {return ServerMain.sync(this);}
}
