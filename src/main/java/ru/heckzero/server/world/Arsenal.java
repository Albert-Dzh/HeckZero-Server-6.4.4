package ru.heckzero.server.world;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.*;
import ru.heckzero.server.user.User;
import ru.heckzero.server.utils.HistoryCodes;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.List;
import java.util.Map;

@Entity(name = "Arsenal")
@Table(name = "arsenals")
@PrimaryKeyJoinColumn(name = "b_id")
public class Arsenal extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();

    public static Arsenal getArsenal(int id) {                                                                                              //try to get an Arsenal instance by id from database
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Arsenal> query = session.createQuery("select a from Arsenal a inner join fetch a.arsenalLoot al inner join fetch al.itemTemplate it where a.id = :id", Arsenal.class).setParameter("id", id).setCacheable(true);
            Arsenal arsenal = query.getSingleResult();
            Hibernate.initialize(arsenal.getLocation());                                                                                    //need by bank cells to get bank coordinates
            arsenal.arsenalLoot.forEach(l -> Hibernate.initialize(l.getItemTemplate()));
            return arsenal;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load arsenal id %d from database: %s", id, e.getMessage());
        }
        return null;
    }

    private ItemBox loadItemBox() {                                                                                                         //load an arsenal item box from arsenal_loot and item_templates tables
        ItemBox itemBox = new ItemBox();
        List<Long> ids = Item.getNextGlobalId(arsenalLoot.size());                                                                          //get result.size() numbers from main_id_seq

        for (ArsenalLoot loot: arsenalLoot) {
            ItemTemplate itemTemplate = loot.getItemTemplate();                                                                             //ItemTemplate instance
            Item item = new Item(itemTemplate);                                                                                             //create an Item by an ItemTemplate instance
            item.setParam(Item.Params.count, loot.getCount());                                                                              //set new item count from arsenal loot
            item.setParam(Item.Params.id, ids.get(arsenalLoot.indexOf(loot)));                                                              //set a new next global id from 'ids' pool
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

    public void processCmd(long a, long d, int s, int c, User user) {
        if (a != -1) {                                                                                                                      //user gets an item from an arsenal
            Map<Item.Params, Object> setParams = Map.of(Item.Params.user_id, user.getId(), Item.Params.section, s);                         //params that need to be set to the item before moving it to user
            Item takenItem = this.getItemBox().moveItem(a, c, user::getNewId, false, user.getItemBox(), setParams);
            if (takenItem == null) {
                logger.error("can't move an item id %d from arsenal to user %s", a, user.getLogin());
                user.disconnect();
                return;
            }
            user.addHistory(HistoryCodes.LOG_GET_ITEMS_IN_HOUSE, takenItem.getLogDescription(), this.getLogDescription());
            return;
        }
        if (d != -1) {                                                                                                                      //put an item to arsenal
            Item givenItem = user.getItemBox().moveItem(d, c, user::getNewId, false, this.getItemBox(), null);
            if (givenItem == null) {
                logger.error("can't move an item id %d from user %s to arsenal", d, user.getLogin());
                user.disconnect();
                return;
            }
            user.addHistory(HistoryCodes.LOG_PUT_ITEMS_IN_HOUSE, givenItem.getLogDescription(), this.getLogDescription());
            return;
        }
        user.sendMsg(lootXml());
        return;
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
