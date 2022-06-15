package ru.heckzero.server.items;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.NativeQuery;
import ru.heckzero.server.ServerMain;

import java.util.EnumSet;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.function.Predicate;
import java.util.function.Supplier;
import java.util.stream.Collectors;

public class ItemBox implements Iterable<Item> {
    private static final Logger logger = LogManager.getFormatterLogger();
    public enum BoxType {USER, BUILDING, BANK_CELL, PARCEL}
    private EnumSet<Item.Params> resetParams = EnumSet.of(Item.Params.user_id, Item.Params.section, Item.Params.slot, Item.Params.b_id, Item.Params.cell_id, Item.Params.rcpt_id, Item.Params.rcpt_dt, Item.Params.owner);

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
    public List<Long> itemsIds() {return items.stream().mapToLong(Item::getId).boxed().toList();}                                           //get items IDs of the 1st level items

    public int getMass()     {return items.stream().mapToInt(Item::getMass).sum();}                                                         //get the weight of all items in the itembox
    public String getXml()   {return items.stream().map(Item::getXml).collect(Collectors.joining());}                                       //get XML list of items as a list of <O/> nodes with the included items
    public ItemBox getAllItems() {return items.stream().map(Item::getAllItems).collect(ItemBox::new, ItemBox::addAll, ItemBox::addAll);}

    public Item findFirst() {return items.stream().findFirst().orElse(null);}
    public ItemBox findItems(Predicate<Item> predicate) {return getAllItems().items.stream().filter(predicate).collect(ItemBox::new, ItemBox::addItem, ItemBox::addAll);}
    public ItemBox findItems(long[] ids) {return findItems(i-> ArrayUtils.contains(ids, i.getId()));}                                       //find items by the set of ids
    public Item findItem(long id) {return findItems(i -> i.getId() == id).findFirst();}                                                     //find an item recursively inside the item box

    public Item findItemByType(double type) {return findItems(i -> i.getParamDouble(Item.Params.type) == type).findFirst();}                //find an Item by type
    private Item findSameItem(Item sample) {                                                                                                //find a joinable item in the item box by a sample item
        Predicate<Item> isResEquals = i -> sample.isRes() && i.getParamInt(Item.Params.massa) == sample.getParamInt(Item.Params.massa);     //the sample is res and items weight is equals
        Predicate<Item> isDrugEquals = i -> sample.isDrug() && i.getParamDouble(Item.Params.type) == sample.getParamInt(Item.Params.type);
        Predicate<Item> isSameName = i -> i.getParamStr(Item.Params.name).equals(sample.getParamStr(Item.Params.name));                     //items have the same name parameter value
        Predicate<Item> isJoinable = isSameName.and(isResEquals.or(isDrugEquals));                                                          //can items be joined
        return findItems(isJoinable).findFirst();                                                                                           //iterate over the 1st level items
    }

    public Item addItem(Item item)   {this.items.add(item); return (!needSync || item.sync()) ? item : null;}                               //add one item to the ItemBox
    public Item addItem(Item item, Map<Item.Params, Object> setParams) {
        if (setParams != null)
            item.setParams(setParams);
        return addItem(item);
    }

    public boolean addAll(ItemBox box)  {this.items.addAll(box.items); return !needSync || sync();}                                         //add all items from itembox


    public Item delItem(long id) {
        Item item = findItem(id);
        if (item == null) {
            logger.error("can't delete item id %d because it was not found in itembox", id);
            return null;
        }

        if (!item.isExpired() && (item.isNoTransfer() || !item.getIncluded().findItems(i -> !i.isExpired() && i.isNoTransfer()).isEmpty())) { //deleting item is forbidden, item or one of its included has nt set to 1
            logger.info("can't delete item id %d, because it or one of its included has no transfer flag set", id);
            return null;
        }

        if (item.isIncluded()) {
            if (!findItem(item.getPid()).getIncluded().items.removeIf(i -> i.getId() == item.getId())) {
                logger.error("can't remove %s from the item box because it was not found in parent included collection", item);
                return null;
            }
        } else
            if (!items.removeIf(i -> i.getId() == item.getId())) {                                                                          //it's a 1st level item, remove it from this item box
                logger.error("can't remove %s from the item box because it was not found in items collection", item);
                return null;
            }
        return (!needSync || item.delFromDB()) ? item : null;                                                                               //delete the item from database with its included
    }

    public Item moveItem(long id, int count, Supplier<Long> newId, boolean alwaysSetNewId, ItemBox dstBox, Map<Item.Params, Object> setParams) {  //move an item from this ItemBox to dstBox
        Item item = getSplitItem(id, count, alwaysSetNewId, newId);
        if (item == null)
            return null;
        item.resetParams(resetParams);
        if (setParams != null)
            item.setParams(setParams);
        return dstBox.addItem(item);
    }

    public Item changeOne(long id, Item.Params paramName, Object paramValue) {                                                              //set param of the item
        Item item = findItem(id);
        if (item == null) {                                                                                                                 //we couldn't find an item by id
            logger.error("can't find item id %d in the item box", id);
            return null;
        }
        item.setParam(paramName, paramValue);
        return (!needSync || item.sync()) ? item : null;
    }

    public Item getSplitItem(long id, int count, boolean alwaysSetNewId, Supplier<Long> newId) {                                            //find an Item and split it by count or just return it back, may be with a new id, which depends on noSetNewId argument and the item type
        Item item = findItem(id);                                                                                                           //find an item by id
        if (item == null) {                                                                                                                 //we couldn't find an item by id
            logger.error("can't find item id %d in the item box", id);
            return null;
        }
        if (count > 0 && count < item.getCount()) {                                                                                         //split the item
            logger.debug("splitting the item %d by cloning and decreasing by count %d", id, count);
            Item spiltted = item.split(count, newId);                                                                                       //get a cloned item with a new ID and requested count
            if (needSync)
                item.sync();
            return spiltted;
        }
        logger.debug("get the entire item id %d of count %d", id, item.getCount());
        if (delItem(id) == null)                                                                                                            //delete the item from database
            return null;
        if (alwaysSetNewId || item.getCount() > 0 && item.getParamDouble(Item.Params.calibre) > 0)                                          //set a new id for the item
            item.setParam(Item.Params.id, newId.get());
        logger.debug("returning item %s", item);
        return item;
    }

    public Item joinMoveItem(long id, int count, Supplier<Long> newId, ItemBox dstBox, Map<Item.Params, Object> setParams) {
        Item item = findItem(id);                                                                                                           //get an item by id
        if (item == null) {
            logger.error("item id %d was not found in the item box", id);
            return null;
        }
        logger.info("try to find joinable item inside the item box");
        Item joinable = dstBox.findSameItem(item);                                                                                          //try to find a joinable item in the item box
        if (joinable != null) {                                                                                                             //we've found it
            logger.info("joinable item found: %s", joinable);
            dstBox.changeOne(joinable.getId(), Item.Params.count, joinable.getCount() + (count > 0 ? count : item.getCount()));             //increase joinable item by count before deletion invalidates our L2 cache to prevent redundant select of joinable
            if (count > 0 && count < item.getCount())                                                                                       //check if we should decrease or delete the source item from user's item box
                return changeOne(id, Item.Params.count, item.getCount() - count);
            else
                return delItem(id);                                                                                                         //item will be deleted from the item box and db, which invalidates L2 cache
        }

        logger.info("can't find an item to join our item with, will split the item id %d by count %d", id, count);
        item = getSplitItem(id, count, false, newId);
        if (item == null)
            return null;
        item.resetParams(resetParams);
        if (setParams != null)
            item.setParams(setParams);

        return dstBox.addItem(item);
    }

    public String getLogDescription() {return items.stream().map(Item::getLogDescription).collect(Collectors.joining(","));}

    @Override
    public Iterator<Item> iterator() {return items.iterator();}

    public boolean sync() {return items.stream().allMatch(Item::sync);}                                                                     //sync all items in ItemBox and check that all sync was successful

    @Override
    public String toString() {return "ItemBox{" + "items=" + items + '}'; }
}
