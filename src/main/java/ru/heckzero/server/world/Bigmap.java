package ru.heckzero.server.world;

import org.hibernate.annotations.CacheConcurrencyStrategy;

import javax.persistence.*;

@Entity(name = "Bigmap")
@Table(name = "bigmap")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY, region = "default")

public class Bigmap {
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bigmap_generator_sequence")
    @SequenceGenerator(name = "bigmap_generator_sequence", sequenceName = "bigmap_id_seq", allocationSize = 1)
    private Integer id;

    private String type;
    private String name;
    private String xy;
    private String linked;
    private Integer enabled;

    public Bigmap() {   }
}
