package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import java.util.List;
import java.util.Objects;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Consumer;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ItemBox {
    private static final Logger logger = LogManager.getFormatterLogger();
    public enum boxType {USER, BUILDING}
    private final CopyOnWriteArrayList<Item> items = new CopyOnWriteArrayList<>();
    private boolean needSync = false;

    public static ItemBox getItemBox(boxType boxType, int id, boolean needSync) {
        ItemBox itemBox = new ItemBox(needSync);                                                                                            //needSync - if a returned ItemBox has to sync its items with a db
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Item> query = session.createNamedQuery(String.format("ItemBox_%s", boxType.toString()), Item.class).setParameter("id", id).setCacheable(true);
            List<Item> items = query.list();
            itemBox.load(items);
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load ItemBox type %s by id %d from database: %s:%s, an empty ItemBox will be returned", boxType, id, e.getClass().getSimpleName(), e.getMessage());
        }
        return itemBox;
    }

    public ItemBox() { }
    public ItemBox(boolean needSync) {this.needSync = needSync;}
    public ItemBox(ItemBox box) {                                                                                                           //create an ItemBox by doing deep copy of items from box
        box.items.forEach(i -> {
            try {
                this.add((Item) i.clone());
            }catch (CloneNotSupportedException e) { logger.error("can't create item box: %s", e.getMessage());}});
        return;
    }
    public ItemBox(ItemBox box, boolean needSync) {this(box); this.needSync = needSync; }

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
    public void addAll(ItemBox box) {this.items.addAllAbsent(box.items);}                                                                   //add all items(shallow copy) from box to this ItemBox

    public List<Item> getItems()    {return items;}                                                                                         //get just the 1st level items
    public List<Long> getItemsIds() {return items.stream().mapToLong(Item::getId).boxed().toList();}                                        //get just items IDs of the 1st level items


    public String getXml()        {return items.stream().map(Item::getXml).collect(Collectors.joining());}                                  //get XML list of items as a list of <O/> nodes with the included items
    public Item findItem(long id) {return items.stream().map(i -> i.findItem(id)).filter(Objects::nonNull).findFirst().orElse(null);}       //find an item recursively inside the item box
    public ItemBox findExpired()  {return items.stream().map(Item::findExpired).collect(ItemBox::new, ItemBox::addAll, ItemBox::addAll);}   //get all expired items with included ones placed in a 1st level

    public void del(Predicate<Item> p) {
        items.stream().filter(p).forEach(i -> del(i.getId()));
    }

    public void del(long id) {                                                                                                              //delete an item from the box or from the parent's included item box
        Item item = findItem(id);                                                                                                           //try to find an item by id
        if (item == null) {
            logger.error("can't delete an item: the item id %d has not been found in the itembox", id);
            return;
        }
        if (item.isIncluded()) {                                                                                                            //the item is a child item
            Item parent = findItem(item.getPid());                                                                                          //try to find an item's master item by pid
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
    public Item getSplitItem(long id, int count, boolean noSetNewId, Supplier<Long> newId) {                                                //find an Item and split it by count or just return it back, may be with a new id, which depends on noSetNewId argument and the item type
        Item item = findItem(id);                                                                                                           //find an item by id
        if (item == null) {                                                                                                                 //we couldn't find an item by id
            logger.error("can't find item id %d in the item box", id);
            return null;
        }
        if (count > 0 && count < item.getCount()) {                                                                                         //split the item
            logger.debug("splitting the item %d by count %d", id, count);
            return item.split(count, noSetNewId, newId);                                                                                    //and return a cloned one with a new ID and count
        }
        logger.debug("get the entire item stack");
        items.remove(item);                                                                                                                 //delete an item from the item box
        if (needSync)
            Item.delItem(item.getId(), true);                                                                                               //delete the item from database
        if (item.getCount() > 0 && !noSetNewId && item.getParamDouble(Item.Params.calibre) > 0)                                             //set a new id for the ammo
            item.setId(newId.get());

        logger.debug("returning item %s", item);
        return item;
    }

    public Item findJoinableItem(Item sample) {                                                                                             //find a joinable item in the itembox
        Predicate<Item> isResEquals = i -> sample.isRes() && i.getParamInt(Item.Params.massa) == sample.getParamInt(Item.Params.massa);     //the sample is res and items weight is equals
        Predicate<Item> isDrugEquals = i -> sample.isDrug()&& i.getParamDouble(Item.Params.type) == sample.getParamInt(Item.Params.type);
        Predicate<Item> isSameName = i -> i.getParamStr(Item.Params.name).equals(sample.getParamStr(Item.Params.name));                     //items have the same name parameter value
        Predicate<Item> isJoinable = isSameName.and(isResEquals.or(isDrugEquals));                                                          //can items be joined

        return items.stream().filter(isJoinable).findFirst().orElse(null);                                                                  //iterate over the 1st level items
    }

    public void forEach(Consumer<Item> action) {
        items.forEach(action);
        return;
    }
    public void sync() {items.forEach(Item::sync);}                                                                                         //recursively sync all items in the ItemBox

    @Override
    public String toString() {return "ItemBox{" + "items=" + items + '}'; }
}
