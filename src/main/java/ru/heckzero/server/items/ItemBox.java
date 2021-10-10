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
    private final CopyOnWriteArrayList<Item> items = new CopyOnWriteArrayList<>();

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
        List <Item> included = items.stream().filter(Item::isIncluded).toList();                                                            //get all child items in the list
        for (Item child : included) {
            Item parent = items.stream().filter(i -> i.getId() == child.getPid()).findFirst().orElseGet(Item::new);                         //find parent item for each child item
            parent.getIncluded().add(child);                                                                                                //add child into parent included
        }
        items.removeAll(included);                                                                                                          //remove all child items from the list course they all are included in their parents
        this.items.addAll(items);
        return;
    }
    public int size() {return items.size();}

    public void add(Item item)      {this.items.addIfAbsent(item);}                                                                         //add one item to this ItemBox
    public void addAll(ItemBox box) {this.items.addAllAbsent(box.items);}                                                                   //add all items from box to this ItemBox

    public List<Item> getItems()    {return items;}                                                                                         //get just the 1st level items
    public List<Long> getItemsIds() {return items.stream().mapToLong(Item::getId).boxed().toList();}                                        //get just items IDs of the 1st level items


    public String getXml() {return items.stream().map(Item::getXml).collect(Collectors.joining());}                                         //get XML list of items as a list of <O/> nodes with the included items
    public Item findItem(long id) {return items.stream().map(i -> i.findItem(id)).filter(Objects::nonNull).findFirst().orElse(null);}
    public ItemBox findExpired()  {return items.stream().map(Item::findExpired).collect(ItemBox::new, ItemBox::addAll, ItemBox::addAll);}   //get all expired items with included ones as a 1st level items

    public void del(long id) {                                                                                                              //delete an item from the box or from the parent's included items
        Item item = findItem(id);                                                                                                           //try to find an item by id
        if (item == null) {
            logger.error("can't delete an item: the item id %d has not been found in the itembox", id);
            return;
        }
        if (item.isIncluded()) {                                                                                                            //the item is a child item
            Item parent = findItem(item.getPid());                                                                                          //try to find an item's parent item by pid
            if (parent == null) {
                logger.error("can't find a parent item id %d for item %d", item.getPid(), id);
                return;
            }
            if (!parent.getIncluded().getItems().remove(item))                                                                              //remove the item from the parent's included item box
                logger.error("can't delete an item id %d from parent item id %d, the parent included items do not contain the item", id, parent.getId());
            return;
        }
        if (!items.remove(item))                                                                                                            //it's a 1st level item, remove it from this item box
            logger.error("can't remove an item id %d from the item box because the item is not found there");
        return;
    }

    public void sync() {items.forEach(Item::sync);}                                                                                         //recursively sync all items in the ItemBox

    @Override
    public String toString() {return "ItemBox{" + "items=" + items + '}'; }
}
