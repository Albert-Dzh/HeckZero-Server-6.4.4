package ru.heckzero.server.items;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import ru.heckzero.server.ServerMain;

import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ItemBox implements Iterable<Item> {
    private static final Logger logger = LogManager.getFormatterLogger();
    public enum BoxType {USER, BUILDING, BANK_CELL, PARCEL}

    private final CopyOnWriteArrayList<Item> items = new CopyOnWriteArrayList<>();
    private boolean needSync = false;

    public static ItemBox init(BoxType boxType, long id) {
        ItemBox itemBox = new ItemBox(true);                                                                                                //needSync - if a returned ItemBox has to sync its items with a db
        try (Session session = ServerMain.sessionFactory.openSession()) {
            NativeQuery query = session.getNamedNativeQuery(String.format("ItemBox_%s", boxType.toString())).setParameter("id", id).addEntity(Item.class).setCacheable(true);
            List<Item> loadedItems = query.list();

            List<Item> masters = loadedItems.stream().filter(Item::isMaster).toList();                                                      //master (1st-level) items
            itemBox.items.addAll(masters);

            if (loadedItems.removeAll(masters))                                                                                             //find and add included items to master items
                masters.forEach(i -> i.getIncluded().items.addAll(loadedItems.stream().filter(inc -> i.getId() == inc.getPid()).toList()));
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load ItemBox type %s by id %d from database: %s:%s, an empty ItemBox will be returned", boxType.toString(), id, e.getClass().getSimpleName(), e.getMessage());
        }
        return itemBox;
    }

    public ItemBox() { }                                                                                                                    //an empty UNsynchronized ItemBox
    public ItemBox(boolean needSync) {this.needSync = needSync;}

    public int size()        {return items.size();}                                                                                         //number of 1-st level items in the box
    public boolean isEmpty() {return items.isEmpty();}

    public String getXml()   {return items.stream().map(Item::getXml).collect(Collectors.joining());}                                       //get XML list of items as a list of <O/> nodes with the included items

    public Item findItem(long id) {return items.stream().filter(i -> i.getId() == id).findFirst().orElse(null);}                            //find an item recursively inside the item box
    public ItemBox findItems(Predicate<Item> predicate) {return items.stream().filter(predicate).collect(ItemBox::new, ItemBox::addItem, ItemBox::addAll);}
    public Item findItemByType(double type) {                                                                                               //find an Item by type
        Predicate<Item> isTypeEquals = i -> i.getParamDouble(Item.Params.type) == type;
        return items.stream().filter(isTypeEquals).findFirst().orElse(null);
    }
    public Item findSameItem(Item sample) {                                                                                                 //find a joinable item in the item box by a sample item
        Predicate<Item> isResEquals = i -> sample.isRes() && i.getParamInt(Item.Params.massa) == sample.getParamInt(Item.Params.massa);     //the sample is res and items weight is equals
        Predicate<Item> isDrugEquals = i -> sample.isDrug() && i.getParamDouble(Item.Params.type) == sample.getParamInt(Item.Params.type);
        Predicate<Item> isSameName = i -> i.getParamStr(Item.Params.name).equals(sample.getParamStr(Item.Params.name));                     //items have the same name parameter value
        Predicate<Item> isJoinable = isSameName.and(isResEquals.or(isDrugEquals));                                                          //can items be joined
        return items.stream().filter(isJoinable).findFirst().orElse(null);                                                                  //iterate over the 1st level items
    }

    public boolean addItem(Item item)   {this.items.add(item); return (!needSync || item.sync());}                                          //add one item to the ItemBox
    public boolean addAll(ItemBox box) {this.items.addAll(box.items);  return !needSync || sync();}

    public boolean delItem(long id, boolean withIncluded) {                                                                                 //delete an item from the box or from the parent's included item box
        Item item = findItem(id);                                                                                                          //try to find an item by id
        if (item == null) {
            logger.error("can't delete item id %d because it was not found in the itembox", id);
            return false;
        }
        if (item.isNoTransfer() || !item.getIncluded().findItems(Item::isNoTransfer).isEmpty()) {                                          //deleting item is forbidden, item or one of its included has nt set to 1
            logger.info("can't delete item id %d, because it or one of its included has no transfer flag set");
            return false;
        }

        if (!items.removeIf(i -> i.getId() == item.getId())) {                                                                              //it's a 1st level item, remove it from this item box
            logger.error("can't remove %s from the item box because it was not found in 1st level item list", item);
            return false;
        }
        return !needSync || item.delFromDB(withIncluded);                                                                                   //delete the item from database with its included
    }

    public boolean delItem(long id, int count) {
        Item item = findItem(id);
        if (item == null) {
            logger.error("can't delete item id %d because it was not found in itembox", id);
            return false;
        }
        if (count > 0 && count < item.getCount()) {
            item.decrease(count);                                                                                                           //we should decrease an Item by count
            return !needSync || item.sync();
        }
        return delItem(id, false);                                                                                                          //delete the entire item
    }

    public boolean moveItem(long id, int count, boolean noSetNewId, Supplier<Long> newId, ItemBox dstBox) {return moveItem(id, count, noSetNewId, newId, dstBox, null, null); }
    public boolean moveItem(long id, int count, boolean noSetNewId, Supplier<Long> newId, ItemBox dstBox, Set<Item.Params> resetParams, Map<Item.Params, Object> setParams) { //move an item from this ItemBox to dstBox
        Item item = getSplitItem(id, count, newId);
        if (item == null)
            return false;
        if (resetParams != null)
            item.resetParams(resetParams);
        if (setParams != null)
            item.setParams(setParams);
        return dstBox.addItem(item);
    }

    public boolean changeOne(long id, Item.Params paramName, Object paramValue) {                                                           //used in User.TO_SECTION()
        Item item = findItem(id);
        if (item == null) {                                                                                                                 //we couldn't find an item by id
            logger.error("can't find item id %d in the item box", id);
            return false;
        }
        item.setParam(paramName, paramValue);
        return !needSync || item.sync();
    }


  /*
    public List<Long> itemsIds() {return items.stream().mapToLong(Item2::getId).boxed().toList();}                                           //get items IDs of the 1st level items

    public boolean joinMoveItem(long id, int count, boolean noSetNewId, Supplier<Long> newId, ItemBox2 dstBox) {return joinMoveItem(id, count, noSetNewId,newId, dstBox, null, null);}
    public boolean joinMoveItem(long id, int count, boolean noSetNewId, Supplier<Long> newId, ItemBox2 dstBox, Set<Item2.Params> resetParams, Map<Item2.Params, Object> setParams) {
        Item2 item = findItem(id);                                                                                                           //get an item by id
        if (item == null) {
            logger.error("item id %d was not found in the item box", id);
            return false;
        }
        logger.info("try to find joinable item inside the item box");
        Item2 joinable = dstBox.findSameItem(item);                                                                                          //try to find a joinable item in the item box
        if (joinable != null) {                                                                                                             //we've found it
            logger.info("joinable item found: %s", joinable);
            dstBox.changeOne(joinable.getId(), Item2.Params.count, joinable.getCount() + (count > 0 ? count : item.getCount()));             //increase joinable item by count before deletion invalidates our L2 cache to prevent redundant select of joinable
            if (count > 0 && count < item.getCount())                                                                                       //check if we should decrease or delete the source item from user's item box
                return item.decrease(count, needSync);
            else
                return delItem(id);                                                                                                         //item will be deleted from user item box and db, which invalidates L2 cache
        }

        logger.info("can't find an item to join our item with, will split the item id %d by count %d", id, count);
        item = getSplitItem(id, count, noSetNewId, newId);
        if (item == null)
            return false;
        if (resetParams != null)
            item.resetParams(resetParams, false);
        if (setParams != null)
            item.setParams(setParams, false);
        return dstBox.addItem(item);
    }




    public Item2 getSplitItem(long id, int count, boolean noSetNewId, Supplier<Long> newId) {                                                //find an Item and split it by count or just return it back, may be with a new id, which depends on noSetNewId argument and the item type
        Item2 item = findItem(id);                                                                                                           //find an item by id
        if (item == null) {                                                                                                                 //we couldn't find an item by id
            logger.error("can't find item id %d in the item box", id);
            return null;
        }
        if (count > 0 && count < item.getCount()) {                                                                                         //split the item
            logger.debug("splitting the item %d by cloning and decreasing by count %d", id, count);
            return item.split(count, noSetNewId, newId, needSync);                                                                          //get a cloned item with a new ID and requested count
        }
        logger.debug("get the entire item id %d stack of count %d", id, item.getCount());
        if (!delItem(id))
            return null;
        if (item.getCount() > 0 && !noSetNewId && item.getParamDouble(Item2.Params.calibre) > 0)                                             //set a new id for the ammo
            item.setId(newId.get(), false);

        logger.debug("returning item %s", item);
        return item;
    }

    public Item2 getClonnedItem(long id, int count, Supplier<Long> newId) {                                                                  //used by portal
        Item2 item = findItem(id);
        if (item == null || !ServerMain.refresh(item)) {                                                                                    //after refreshing we know if the item exists in database and the actual item count
            logger.info("can't find an item id %d in the item box, the item doesn't exist anymore", id);
            return null;
        }
        if (count > 0 && count < item.getCount()) {                                                                                         //split the item
            logger.debug("splitting the item %d by cloning and decreasing by count %d", id, count);
            return item.split(count, false, newId, needSync);                                                                               //get a cloned item with a new ID and requested count
        }
                                                                                                                                            //we are taking the entire item
        logger.info("will take a whole stack and delete item %d", item.getId());
        if (!delItem(id) || !item.setId(newId.get(), false))                                                                                //del the source item from item box
            return null;
       return item;
    }

*/

    public int getMass() {return items.stream().mapToInt(Item::getMass).sum();}

    @Override
    public Iterator<Item> iterator() {return items.iterator();}

    private boolean sync() {items.forEach(Item::sync); return true;}

    @Override
    public String toString() {return "ItemBox{" + "items=" + items + '}'; }
}
