package ru.heckzero.server.world;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.items.ItemTemplate;

import javax.persistence.Cacheable;
import javax.persistence.Entity;
import javax.persistence.Id;
import javax.persistence.Table;
import java.util.ArrayList;
import java.util.List;

@Immutable
@Entity(name = "ArsenalLoot")
@Table(name = "arsenal_loot")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY, region = "default")
public class ArsenalLoot {
    private static final Logger logger = LogManager.getFormatterLogger();

    @Id
    private int id;

    private int aid;
    private int loot_id;
    private String count;

    public ArsenalLoot() { }

    public static ItemBox getLoot(int aid) {
        List<Object[]> result = new ArrayList<>();
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Object[]> query = session.createQuery("select a, it from ArsenalLoot a inner join ItemTemplate it on a.loot_id = it.id where a.aid = :aid order by a.id").setParameter("aid", aid).setCacheable(true);
            result.addAll(query.list());
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load arsenal loot: %s:%s", e.getClass().getSimpleName(), e.getMessage());
            return new ItemBox();
        }

        ItemBox box = new ItemBox();                                                                                                        //create a new Item box for arsenal items
        List<Long> ids = Item.getNextGlobalId(result.size());                                                                               //get result.size() numbers from main_id_seq
        for (int i = 0; i < result.size(); i++) {
            ArsenalLoot arLoot = (ArsenalLoot)result.get(i)[0];                                                                             //ArsenalLoot instance
            ItemTemplate itemTemplate = (ItemTemplate) result.get(i)[1];                                                                    //ItemTemplate instance

            Item item = new Item(itemTemplate);                                                                                             //create an Item by an ItemTemplate instance
            item.setParam(Item.Params.count, arLoot.count);                                                                                 //set new item count from arsenal loot
            item.setId(ids.get(i));                                                                                                         //set a new next global id from 'ids' pool
            if (item.getId() != -1L)                                                                                                        //add the item to new item box
                box.add(item);
        }
        return box;                                                                                                                         //return the item box with the items
    }

}
