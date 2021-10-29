package ru.heckzero.server.world;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.ArsenalLoot;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.items.ItemTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.function.Supplier;

public class Arsenal {
    private static final Logger logger = LogManager.getFormatterLogger();

    private final int aid;
    private ItemBox itemBox;

    private static ItemBox loadItemBox(int aid) {                                                                                           //load an arsenal item box from arsenal_loot and item_templates tables
        List<Object[]> result;

        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Object[]> query = session.createQuery("select a, it from ArsenalLoot a inner join ItemTemplate it on a.loot_id = it.id where a.aid = :aid order by a.id").setParameter("aid", aid).setCacheable(true);
            result = new ArrayList<>(query.list());
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load arsenal loot for arsenal id %d: %s:%s", aid, e.getClass().getSimpleName(), e.getMessage());
            return new ItemBox();
        }
        ItemBox itemBox = new ItemBox();
        List<Long> ids = Item.getNextGlobalId(result.size());                                                                               //get result.size() numbers from main_id_seq

        for (int i = 0; i < result.size(); i++) {
            ArsenalLoot loot = (ArsenalLoot)result.get(i)[0];                                                                               //ArsenalLoot instance
            ItemTemplate itemTemplate = (ItemTemplate) result.get(i)[1];                                                                    //ItemTemplate instance

            Item item = new Item(itemTemplate);                                                                                             //create an Item by an ItemTemplate instance
            item.setParam(Item.Params.count, loot.getCount());                                                                              //set new item count from arsenal loot
            item.setId(ids.get(i));                                                                                                         //set a new next global id from 'ids' pool
            if (item.getId() != -1L)                                                                                                        //add the item to new item box
                itemBox.addItem(item);
        }
        return itemBox;                                                                                                                     //return the item box with the items
    }

    public Arsenal(int aid) {
        this.aid = aid;
        return;
    }

    private ItemBox getItemBox() {                                                                                                          //load an ItemBox from items template
        return itemBox == null ? (itemBox = loadItemBox(aid)) : itemBox;
    }

    public String lootXml() {                                                                                                               //return XML formatted ItemBox
        return String.format("<AR>%s</AR>", getItemBox().getXml());
    }
    public Item getItem(long id, int count, Supplier<Long> newId) {                                                                         //withdraw an item from arsenal
        return itemBox.getSplitItem(id, count, false, newId);
    }

    public void putItem(Item item) {                                                                                                        //user puts an item to arsenal
        itemBox.addItem(item);
    }
}
