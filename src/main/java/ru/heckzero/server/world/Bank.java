package ru.heckzero.server.world;

import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.reflect.FieldUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.items.ItemTemplate;
import ru.heckzero.server.items.ItemsDct;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;
import ru.heckzero.server.utils.HistoryCodes;
import ru.heckzero.server.utils.ParamUtils;

import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.EnumSet;
import java.util.Map;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "Bank")
@Table(name = "banks")
@PrimaryKeyJoinColumn(name = "b_id")
public class Bank extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> bankParams = EnumSet.of(Params.cash, Params.cost, Params.cost2, Params.cost3, Params.cost_to200, Params.cost_to300, Params.cost_to400, Params.cost_to500,Params.free, Params.tkey, Params.key);

    public static Bank getBank(int id) {                                                                                                    //try to get a Bank instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Bank> query = session.createQuery("select b from Bank b where b.id = :id", Bank.class).setParameter("id", id).setCacheable(true);
            Bank bank = query.getSingleResult();
            Hibernate.initialize(bank.getLocation());                                                                                       //need by bank cells to get bank coordinates
            return bank;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load bank with id %d from database: %s", id, e.getMessage());
        }
        return new Bank();
    }

    @Transient private String key;                                                                                                          //encryption key used by client to encrypt cell's password
    private int tkey;                                                                                                                       //does bank offer cells for free
    private int cost;                                                                                                                       //new cell cost
    private int cost2;                                                                                                                      //monthly cell rent cost
    private int cost3;                                                                                                                      //cell key duplicate and an additional cell section cost
    private int cost_to200;                                                                                                                 //increase cell capacity up to 200 items
    private int cost_to300;
    private int cost_to400;
    private int cost_to500;

    volatile private int free;                                                                                                              //number of available cells in a bank

    protected Bank() { }

    public int getCost()  {return cost;}
    public int getCost3() {return cost3;}

    public boolean setCost(int cost, int cost2) {this.cost = cost; this.cost2 = cost2; return sync();}                                      //set cost settings
    public void setKey(String key) {this.key = key;}                                                                                        //set an encryption key

    synchronized public boolean decrementFreeCells() {return free-- > 0 && sync();}                                                         //decrement free cells count

    @Override
    protected String getParamXml(Params param) {return ParamUtils.getParamXml(this, param.toString()).transform(s -> s.startsWith("cash") ? s.replace("cash", "cash1") : s);}

    public Item createCell(User user, String password) {                                                                                    //create a bank cell and return a key item for that cell
        BankCell bankCell = new BankCell(this.getId(), user.getId(), password);                                                             //create a new bank cell
        if (!bankCell.sync())
            return null;
        bankCell.addHistory(HistoryCodes.LOG_CELL_WAS_BOUGHT_BY, user.getLogin());                                                          //Персонаж '%s' купил эту ячейку

        Item key = ItemTemplate.getTemplateItem(ItemTemplate.BANK_KEY);                                                                     //generate a bank cell key item
        if (key == null)
            return null;
        key.setParam(Item.Params.txt, key.getParamStr(Item.Params.txt) + bankCell.getId());                                                 //set the key item params to make client display the key hint properly
        key.setParam(Item.Params.made, String.format("%s%d_%d_%d",  key.getParamStr(Item.Params.made), getX(), getY(), getZ()));
        key.setParam(Item.Params.dt, bankCell.getDt());
        key.setParam(Item.Params.hz, bankCell.getId());
        key.setParam(Item.Params.res, getTxt());
        key.setParam(Item.Params.user_id, user.getId());                                                                                    //user id this key belongs to
        key.setParam(Item.Params.section, 0);                                                                                               //user box sections this key will be placed to
        return key;
    }

    public String bkXml() {                                                                                                                 //XML formatted bank data
        StringJoiner sj = new StringJoiner("", "", "</BK>");
        sj.add(bankParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<BK ", ">")));   //add XML bank params
        return sj.toString();
    }

    public void processCmd(User user, int put, int get, int cost, int cost2, int buy, String p, String newpsw, String newemail, int go, int sell, long d, int s, int c, long f, long a, int newkey, int addsection, int extend, int check_sell, int tr, int cell2) {
        BankCell cell = null;
        if (sell >= 0 && StringUtils.isNotBlank(p)) {                                                                                       //opening a cell p - cell password
            cell = BankCell.getBankCell(sell);                                                                                              //get cell data by id from database
            if (cell == null) {
                user.disconnect();
                return;
            }
            if (!cell.checkPass(key, p)) {                                                                                                  //check the cell password
                user.sendMsg("<BK code=\"1\"/>");                                                                                           //wrong cell password
                return;
            }
        }

        if (get > 0) {                                                                                                                      //take money from bank's cash
            int cashTaken = decMoney(get);                                                                                                  //take money from building
            user.addMoney(ItemsDct.MONEY_COPP, cashTaken);                                                                                  //add money  from bank cash to the user
            addHistory(HistoryCodes.LOG_BANK_GET_MONEY_FROM_CASH, user.getLogin(), String.valueOf(get));                                    //Владелец банка '%s' забрал из кассы %s мнт.
            user.addHistory(HistoryCodes.LOG_GET_MONEY_FROM_CASH, String.valueOf(ItemsDct.MONEY_COPP), String.valueOf(get), getLogDescription(), String.valueOf(user.getMoneyCop()));
            user.sendMsg("<BK code=\"0\"/>");
            return;
        }

        if (put > 0) {                                                                                                                      //put money to bank's cash
            user.decMoney(put);                                                                                                             //get money from user
            if (!user.getBuilding().addMoney(put))                                                                                          //add money to building
                user.disconnect();
            addHistory(HistoryCodes.LOG_BANK_PUT_MONEY_TO_CASH, user.getLogin(), String.valueOf(put));							            //Владелец банка '%s' положил в кассу %s мнт.
            user.addHistory(HistoryCodes.LOG_PUT_MONEY_TO_CASH, String.valueOf(ItemsDct.MONEY_COPP), String.valueOf(put), getLogDescription(), String.valueOf(user.getMoneyCop()));
            user.sendMsg("<BK code=\"0\"/>");
            return;
        }

        if (cost >= 0 && cost2 >= 0) {                                                                                                      //save the bank cost-related settings
            if (!setCost(cost, cost2)) {
                user.disconnect();
                return;
            }
            addHistory(HistoryCodes.LOG_BANK_CHANGE_PARAMS, user.getLogin());
            return;
        }

        if (buy == 1 && StringUtils.isNotBlank(p)) {                                                                                        //user buys a new cell
            if (user.getMoneyCop() < getCost()) {
                user.sendMsg("<BK code=\"4\"/>");
                return;
            }
            if (!decrementFreeCells()) {                                                                                                    //decrement bank free cells count
                user.sendMsg("<BK code=\"8\"/>");
                return;
            }
            user.decMoney(getCost());                                                                                                       //decrease user money by cell cost
            if (!addMoney(getCost())) {                                                                                                     //add money to bank cash
                user.disconnect();
                return;
            }
            Item key = createCell(user, p);                                                                                                 //create a cell and a new key item for that cell
            if (key == null) {
                user.disconnect();
                return;
            }

            addHistory(HistoryCodes.LOG_BANK_PROFIT_FOR_CELL, String.valueOf(this.cost));                                                   //Доход банка %s мнт. за продажу ячейки
            user.addHistory(HistoryCodes.LOG_PAY_AND_BALANCE, "Coins[" + this.cost + "]", getLogDescription(), HistoryCodes.ULOG_FOR_BANK_CELL_PURCHASE, String.valueOf(user.getMoneyCop()));
            user.addSendItem(key);                                                                                                          //add the cell key to the user item box and send the key-item description to him
            user.sendMsg("<BK code=\"0\"/>");
            user.sendMsg(bkXml());                                                                                                          //update bank information to the client
            return;
        }

        if (cell != null && addsection == 1) {                                                                                              //add a tab to the cell
            if (cell.getBookmark_add() >= 8) {
                user.sendMsg("<BK code=\"29\"/>");
                return;
            }
            if (!cell.setBookmark_add(cell.getBookmark_add() + 1)) {
                user.sendMsg("<BK code=\"9\"/>");
                return;
            }
            if (user.getMoneyCop() < getCost3()) {
                user.sendMsg("<BK code=\"4\"/>");
                return;
            }
            user.decMoney(getCost3());                                                                                                      //decrease user money
            if (!addMoney(getCost3())) {                                                                                                    //add money to bank cash
                user.disconnect();
                return;
            }
            String cellUpdate = "<BK sell=\"1\" update=\"1\" bookmark_add=\"" + cell.getBookmark_add() + "\"" + " capacity=\"" + cell.getCapacity() +"\">";
            user.sendMsg(cellUpdate);
            return;
        }

        if (go == 1 && cell != null) {                                                                                                      //opening a cell id 'sell'
            user.sendMsg(cell.cellXml());
            return;
        }

        if (cell != null && extend == 1) {                                                                                                  //increase cell capacity
            int currCapacity = cell.getCapacity();                                                                                          //get cell current capacity
            if (currCapacity == 500) {                                                                                                      //current cell capacity is already maximum
                user.sendMsg("<BK code=\"30\"/>");
                return;
            }

            int nextCapacityCost;
            try {
                nextCapacityCost = (int)FieldUtils.readDeclaredField(this, String.format("cost_to%d", currCapacity + 100), true);
            } catch (IllegalAccessException | IllegalArgumentException e) {
                logger.error("can't increase cell capacity: %s", e.getMessage());
                user.sendMsg("<BK code=\"9\"/>");
                return;
            }
            if (user.getMoneyCop() < nextCapacityCost) {                                                                                    //user doesn't have enough money
                user.sendMsg("<BK code=\"4\"/>");
                return;
            }
            user.decMoney(nextCapacityCost);                                                                                                //decrease user money by cell cost
            if (!(addMoney(nextCapacityCost) && cell.setCapacity(cell.getCapacity() + 100))) {                                              //add money to bank cash and actually increase cell capacity
                user.disconnect();
                return;
            }
            String cellUpdate = "<BK sell=\"1\" update=\"1\" bookmark_add=\"" + cell.getBookmark_add() + "\"" + " capacity=\"" + cell.getCapacity() +"\">";
            user.sendMsg(cellUpdate);
            return;
        }

        if (sell >= 0 && d >= 0 && s >=0 && cell != null) {                                                                                 //move an item from user to cell
            Map<Item.Params, Object> setParams = Map.of(Item.Params.b_id, getId(), Item.Params.cell_id, sell, Item.Params.section, s);      //set param - values list
            Item item = user.getItemBox().joinMoveItem(d, c, user::getNewId, cell.getItemBox(), setParams);
            if (item == null) {
                logger.error("can't put an item id %d to bank cell id %d", d, sell);
                user.disconnect();
                return;
            }
            cell.addHistory(HistoryCodes.LOG_CELL_PUT_ITEMS, user.getLogin(), item.getLogDescription());                                    //Персонаж '%s' положил в ячейку предметы: {%s}
            user.addHistory(HistoryCodes.LOG_CELL_PUT_ITEMS, user.getLogin(), item.getLogDescription());
            return;
        }

        if (sell >= 0 && a >= 0 && s >= 0 && cell != null) {                                                                                //user takes an item from cell to his item box
            Map<Item.Params, Object> setParams = Map.of(Item.Params.user_id, user.getId(), Item.Params.section, s);                         //params that need to be set to an item before moving to user
            Item item = cell.getItemBox().moveItem(a, c, user::getNewId, false, user.getItemBox(), setParams);
            if (item == null) {
                logger.error("can't move an item id %d from bank cell to user %s", a, user.getLogin());
                user.disconnect();
                return;
            }
            cell.addHistory(HistoryCodes.LOG_CELL_GET_ITEMS, user.getLogin(), item.getLogDescription());                                    //Персонаж '%s' забрал из ячейки предметы: {%s}
            user.addHistory(HistoryCodes.LOG_CELL_GET_ITEMS, user.getLogin(), item.getLogDescription());
            return;
        }

        if (sell >= 0 && f >= 0 && s >= 0 && cell != null) {                                                                                //item is being moving between sections within a cell
            if (cell.getItemBox().changeOne(f, Item.Params.section, s) == null)
                user.disconnect();
            return;
        }

        if (newpsw != null && newpsw.length() >= 6 && cell != null) {                                                                       //change cell's password
            if (!cell.setPassword(newpsw))
                user.disconnect();
            cell.addHistory(HistoryCodes.LOG_CELL_PSWD_CHANGE, user.getLogin());                                                            //Персонаж '%s' сменил пароль от ячейки
            user.sendMsg("<BK code=\"0\"/>");
            return;
        }

        if (newemail != null && newemail.contains("@") && cell != null) {                                                                   //change cell's holder email
            if (!cell.setEmail(newemail))
                user.disconnect();
            user.sendMsg("<BK code=\"0\"/>");
            return;
        }

        if (newkey >= 0 && cell != null) {                                                                                                  //cell key duplicate request
            if (user.getMoneyCop() < getCost3()) {                                                                                          //user hasn't got enough money to make a key
                user.sendMsg("<BK code=\"4\"/>");
                return;
            }
            user.decMoney(getCost3());                                                                                                      //decrease user money by cell cost
            if (!addMoney(getCost3())) {                                                                                                    //add money to bank cash
                user.disconnect();
                return;
            }
            Item keyCopy = cell.makeKeyCopy();                                                                                              //create a key copy from item template
            if (keyCopy == null) {
                user.disconnect();
                return;
            }
            cell.addHistory(HistoryCodes.LOG_CELL_KEY_DUBLICATE, user.getLogin());                                                          //Персонаж '%s' изготовил дубликат ключа от ячейки
            user.addHistory(HistoryCodes.LOG_PAY_AND_BALANCE, "Coins[" + this.cost + "]", String.format("%s,%s,%s,%s", getTxt(), getLocalX(), getLocalY(), getZ()), HistoryCodes.ULOG_FOR_CELL_KEY_DUBLICATE, String.valueOf(user.getMoneyCop()));

            user.addSendItem(keyCopy);                                                                                                      //send a key item to the client
            user.sendMsg("<BK code=\"0\"/>");
            user.sendMsg(bkXml());                                                                                                          //update bank information to the client
            return;
        }

        if (check_sell >= 0) {                                                                                                              //check the cell by id and return the cell's owner login or an empty string
            BankCell checkCell = BankCell.getBankCell(check_sell);
            if (checkCell == null) {
                user.sendMsg("<BK login_cell=\"\"/>");                                                                                      //cell's owner was not found
                return;
            }
            user.sendMsg(String.format("<BK login_cell=\"%s\"/>", UserManager.getUser(checkCell.getUser_id()).getLogin()));
            return;
        }

        if (tr > 0 && cell2 > 0 && c > 0) {                                                                                                 //transfer sources between cells
            BankCell dstCell = BankCell.getBankCell(cell2);
            if (cell == null || dstCell == null) {
                logger.error("can't transfer resources from cell id %d to cell id %d, one of the cells is null", cell, cell2);
                user.disconnect();
                return;
            }
            ItemBox srcBox = cell.getItemBox();
            ItemBox dstBox = dstCell.getItemBox();

            Map<Item.Params, Object> setParams = Map.of(Item.Params.cell_id, cell2, Item.Params.section, 0);
            Item trItem = srcBox.joinMoveItem(tr, c, Item::getNextGlobalId, dstBox, setParams);                                             //tr - item it to transfer
            if (trItem == null) {
                logger.error("can't transfer item id %d from cell id %d to cell id %d", tr, cell, cell2);
                user.disconnect();
                return;
            }
            trItem.setParam(Item.Params.count, c);
            cell.addHistory(HistoryCodes.LOG_CELL_RES_TRANSFERED_TO, user.getLogin(), String.valueOf(cell2), trItem.getLogDescription());   //Персонаж '%s' перевёл на счет {%s} ресурсы: {%s}
            dstCell.addHistory(HistoryCodes.LOG_CELL_RES_TRANSFERED_FROM, user.getLogin(), String.valueOf(cell.getId()), trItem.getLogDescription());//Персонаж '%s' перевёл со счёта {%s} ресурсы: {%s}
            user.sendMsg("<BK code=\"0\"/>");                                                                                               //sources have been transferred successfully
            return;
        }

        setKey((String)user.getGameChannel().attr(AttributeKey.valueOf("encKey")).get());
        user.sendMsg(bkXml());
        return;
    }

}
