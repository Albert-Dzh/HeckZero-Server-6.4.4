package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import java.util.ArrayList;
import java.util.List;
import java.util.stream.Collectors;

public class ItemBox {
    private static final Logger logger = LogManager.getFormatterLogger();
    public enum boxType  {USER, ITEM, BUILDING}
    private List<Item> items = new ArrayList<>();

    public static ItemBox getItemBox(boxType boxType, int id) {
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Item> query = session.createNamedQuery(String.format("ItemBox_%s", boxType.name()), Item.class).setParameter("id", id);
            List<Item> items = query.list();

            return new ItemBox(items);
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load itembox of type %s by id %d from database: %s:%s, generating a default empty ItemBox", boxType, id, e.getClass().getSimpleName(), e.getMessage());
        }
        return new ItemBox();
    }

    public ItemBox() { }

    private ItemBox(List<Item> items) {
        for (Item item: items) {
            int pid = item.getPid();
            if (pid == 0)
                continue;
//            logger.info("item %s is a child", item);
            Item parent = items.stream().filter(i -> i.getId() == pid).findFirst().orElse(null);
            if (parent == null)
                logger.warn("can't find parent item with id %d for Item id %d", pid, item.getId());
            else{
                parent.getIncluded().addItem(item);
//                logger.info("found parent item %s", parent);
            }
        }
        items.removeIf(i -> !i.isParent());
        this.items = items;
    }

    public boolean isEmpty() {return items.isEmpty();}

    public void addItem(Item item) {
        this.items.add(item);
        return;
    }

    public String getXml() {
        return items.stream().map(Item::getXml).collect(Collectors.joining());
    }

    @Override
    public String toString() {
        return "ItemBox{" +
                "items=" + (items.isEmpty() ? "[]" : items) +
                '}';
    }
}
