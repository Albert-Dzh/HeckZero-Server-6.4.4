package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ItemBox {
    private static final Logger logger = LogManager.getFormatterLogger();
    public enum boxType  {USER, BUILDING}
    private final List<Item> items = new CopyOnWriteArrayList<>();

    public static ItemBox getItemBox(boxType boxType, int id) {
        ItemBox itemBox = new ItemBox();
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Item> query = session.createNamedQuery(String.format("ItemBox_%s", boxType.toString()), Item.class).setParameter("id", id);
            List<Item> items = query.list();
            itemBox.load(items);
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load itembox of type %s by id %d from database: %s:%s, generating a default empty ItemBox", boxType, id, e.getClass().getSimpleName(), e.getMessage());
        }
        return itemBox;
    }

    public ItemBox() { }

    public boolean isEmpty() {return items.isEmpty();}

    public void load(List<Item> items) {
        List <Item> included = items.stream().filter(Item::isIncluded).toList();                                                             //get all child items in the list
        for (Item child : included) {
            Item parent = items.stream().filter(i -> i.getId() == child.getPid()).findFirst().orElseGet(Item::new);                         //find parent item for each child item
            parent.getIncluded().add(child);                                                                                                //add child into parent included
        }
        items.removeAll(included);                                                                                                          //remove all child items from the list course they all are included in their parents
        this.items.addAll(items);
        return;
    }

    public void add(Item item) {this.items.add(item);}                                                                                      //add one item to this ItemBox
    public void add(ItemBox box) {this.items.addAll(box.items);}                                                                            //add all items from box to this ItemBox

    public List<Item> getItems() {return items;}                                                                                            //sad but true

    public Item findItemById(long id) {return items.stream().filter(i -> i.getId() == id).findFirst().orElse(null);}
    public Item findItemById2(long id) {return items.stream().map(i -> i.findById(id)).filter(Objects::nonNull).findFirst().orElse(null);}

    public String getXml() {return items.stream().map(Item::getXml).collect(Collectors.joining());}

    public ItemBox getExpired() {                                                                                                           //recursively get the expired items
        ItemBox expired = new ItemBox();
        items.forEach(i -> expired.add(i.getExpired()));
        return expired;
    }

    public void deleteItem(long id) {
        if (items.removeIf(i -> i.getId() == id))
            Item.delItem(id);
        else
            logger.error("can't find item id %d to delete from itembox", id);
        return;
    }

    public void sync() {items.forEach(Item::sync);}                                                                                         //recursively sync all items in ItemBox

    @Override
    public String toString() {
        return "ItemBox{" +
                "items=" + items +
                '}';
    }
}
