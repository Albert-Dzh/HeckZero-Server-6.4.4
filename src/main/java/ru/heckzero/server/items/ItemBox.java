package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.stream.Collectors;

public class ItemBox {
    private static final Logger logger = LogManager.getFormatterLogger();
    public enum boxType  {USER, BUILDING}
    private List<Item> items = new CopyOnWriteArrayList<>();

    public static ItemBox getItemBox(boxType boxType, int id) {
        ItemBox itemBox = new ItemBox();
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Item> query = session.createNamedQuery(String.format("ItemBox_%s", boxType.name()), Item.class).setParameter("id", id);
            List<Item> items = query.list();
            itemBox.init(items);
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load itembox of type %s by id %d from database: %s:%s, generating a default empty ItemBox", boxType, id, e.getClass().getSimpleName(), e.getMessage());
        }
        return itemBox;
    }

    public ItemBox() { }


    public void init(List<Item> items) {
        List <Item> childItems = items.stream().filter(Item::isChild).toList();
        for (Item child : childItems) {
            Item parent = items.stream().filter(i -> i.getId() == child.getPid()).findFirst().orElseGet(Item::new);
            parent.insertItem(child);
        }
        items.removeAll(childItems);
        this.items = items;
        return;
    }

    public boolean isEmpty() {return items.isEmpty();}

    public void add(Item item) {
        this.items.add(item);
        return;
    }

    public Item getItem(int id) { return items.stream().filter(i -> i.getId() == id).findFirst().orElse(null); }                            //get an Item by id

    public String getXml() { return items.stream().map(Item::getXml).collect(Collectors.joining()); }

    @Override
    public String toString() {
        return "ItemBox{" +
                "items=" + items +
                '}';
    }
}
