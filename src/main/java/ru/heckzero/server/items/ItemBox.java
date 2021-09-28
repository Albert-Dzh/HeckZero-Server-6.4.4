package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import java.util.Collections;
import java.util.List;
import java.util.stream.Collectors;

public class ItemBox {
    private static final Logger logger = LogManager.getFormatterLogger();
    public enum boxType  {USER, ITEM, BUILDING}
    private List<Item> items = Collections.emptyList();

    public static ItemBox getItemBox(boxType boxType, int id) {
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Item> query = session.createNamedQuery(String.format("ItemBox_%s", boxType.name()), Item.class).setParameter("id", id);
            List<Item> items = query.list();

            return new ItemBox(items);
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load itembox of type %s by id %d from database: %s, generating a default empty ItemBox", boxType, id, e.getMessage());
        }
        return new ItemBox();
    }

    public ItemBox() { }

    private ItemBox(List<Item> items) {
        this.items = items;
    }

    public String getXml() {
        return items.stream().map(Item::getXml).collect(Collectors.joining());
    }

    @Override
    public String toString() {
        return "ItemBox{" +
                "items=" + items +
                '}';
    }
}
