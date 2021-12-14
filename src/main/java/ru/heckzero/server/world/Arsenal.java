package ru.heckzero.server.world;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.ArsenalLoot;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.items.ItemTemplate;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;

@Entity(name = "Arsenal")
@Table(name = "arsenals")
@PrimaryKeyJoinColumn(name = "b_id")
public class Arsenal extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();

    public static Arsenal getArsenal(int id) {                                                                                              //try to get an Arsenal instance by id from database
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Arsenal> query = session.createQuery("select a from Arsenal a inner join fetch a.arsenalLoot al inner join fetch al.itemTemplate it where a.id = :id", Arsenal.class).setParameter("id", id).setCacheable(true);
            return query.getSingleResult();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load arsenal with id %d from database: %s", id, e.getMessage());
        }
        return new Arsenal();
    }

    private ItemBox loadItemBox() {                                                                                                         //load an arsenal item box from arsenal_loot and item_templates tables
        ItemBox itemBox = new ItemBox();
        List<Long> ids = Item.getNextGlobalId(arsenalLoot.size());                                                                          //get result.size() numbers from main_id_seq

        for (ArsenalLoot loot: arsenalLoot) {
            ItemTemplate itemTemplate = loot.getItemTemplate();                                                                             //ItemTemplate instance
            Item item = new Item(itemTemplate);                                                                                             //create an Item by an ItemTemplate instance
            item.setParam(Item.Params.count, loot.getCount(), false);                                                                       //set new item count from arsenal loot
            item.setId(ids.get(arsenalLoot.indexOf(loot)), false);                                                                          //set a new next global id from 'ids' pool
            if (item.getId() != -1L)                                                                                                        //add the item to new item box
                itemBox.addItem(item);
        }
        return itemBox;                                                                                                                     //return the item box with the items
    }
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_ONLY)
    @OneToMany(mappedBy = "aid", fetch = FetchType.LAZY)
    private List<ArsenalLoot> arsenalLoot = new ArrayList<>();

    protected Arsenal() { }


    @Override
    public ItemBox getItemBox() {                                                                                                           //load an ItemBox from items_template
        return itemBox == null ? (itemBox = loadItemBox()) : itemBox;
    }

    public String lootXml() {                                                                                                               //return XML formatted ItemBox
        return String.format("<AR>%s</AR>", getItemBox().getXml());
    }

    @Override
    public String toString() {
        return "Arsenal{" +
                "id=" + id +
                ", txt='" + txt + '\'' +
                ", arsenalLoot=" + arsenalLoot +
                '}';
    }
}
