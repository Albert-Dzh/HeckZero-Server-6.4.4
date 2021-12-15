package ru.heckzero.server.world;

import io.netty.util.AttributeKey;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ParamUtils;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemTemplate;
import ru.heckzero.server.items.ItemsDct;
import ru.heckzero.server.user.User;

import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.EnumSet;
import java.util.Map;
import java.util.Set;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "Bank")
@Table(name = "banks")
@PrimaryKeyJoinColumn(name = "b_id")
public class Bank extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> bankParams = EnumSet.of(Params.cash, Params.cost, Params.cost2, Params.cost3, Params.free, Params.tkey, Params.key);

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
    volatile private int free;                                                                                                              //number of available cells in a bank

    protected Bank() { }

    public int getCost() {return cost;}
    public int getCost3() {return cost3;}

    public boolean setCost(int cost, int cost2) {this.cost = cost; this.cost2 = cost2; return sync();}                                      //set cost settings
    public void setKey(String key) {this.key = key;}                                                                                        //set an encryption key

    synchronized public boolean decrementFreeCells() {return free-- > 0 && sync();}                                                         //decrement free cells count

    @Override
    protected String getParamXml(Params param) {return ParamUtils.getParamXml(this, param.toString()).transform(s -> s.startsWith("cash") ? s.replace("cash", "cash1") : s);}

    public Item createCell(int user_id, String password) {                                                                                  //create a bank cell and return a key item for that cell
        BankCell bankCell = new BankCell(this.getId(), user_id, password);                                                                  //create a new bank cell
        if (!bankCell.sync())
            return null;
        Item key = ItemTemplate.getTemplateItem(ItemTemplate.BANK_KEY);                                                                     //generate a bank cell key item
        if (key == null)
            return null;
        key.setParam(Item.Params.txt, key.getParamStr(Item.Params.txt) + bankCell.getId(), false);                                          //set the key item params to make client display the key hint properly
        key.setParam(Item.Params.made, String.format("%s%d_%d_%d",  key.getParamStr(Item.Params.made), getX(), getY(), getZ()), false);
        key.setParam(Item.Params.dt, bankCell.getDt(), false);
        key.setParam(Item.Params.hz, bankCell.getId(), false);
        key.setParam(Item.Params.res, getTxt(), false);
        key.setParam(Item.Params.user_id, user_id, false);                                                                                  //user id this key belongs to
        key.setParam(Item.Params.section, 0, false);                                                                                        //user box sections this key will be placed to
        return key;
    }

    public String bkXml() {                                                                                                                 //XML formatted bank data
        StringJoiner sj = new StringJoiner("", "", "</BK>");
        sj.add(bankParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<BK ", ">")));   //add XML bank params
        return sj.toString();
    }

    public void processCmd(int put, int get, int cost, int cost2, int buy, String p, String newpsw, String newemail, int go, int sell, long d, int s, int c, long f, long a, int newkey, User user) {
        BankCell cell = null;
        if (sell >= 0 && StringUtils.isNotBlank(p)) {                                                                                       //opening a cell
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
            int cashTaken = user.getBuilding().decMoney(get);                                                                               //take money from building
            user.addMoney(ItemsDct.MONEY_COPP, cashTaken);                                                                                  //add money  from bank cash to the user
            user.sendMsg("<BK code=\"0\"/>");
            return;
        }

        if (put > 0) {                                                                                                                      //put money to bank's cash
            user.decMoney(put);                                                                                                             //get money from user
            if (!user.getBuilding().addMoney(put))                                                                                          //add money to building
                user.disconnect();
            user.sendMsg("<BK code=\"0\"/>");
            return;
        }

        if (cost >= 0 && cost2 >= 0) {                                                                                                      //save bank cost-related settings
            if (!setCost(cost, cost2))
                user.disconnect();
            return;
        }

        if (buy == 1 && StringUtils.isNotBlank(p)) {                                                                                        //user buys a new cell
            if (user.getMoney().getCopper() < getCost()) {
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
            Item key = createCell(user.getId(), p);                                                                                         //create a cell and a new key item for that cell
            if (key == null) {
                user.disconnect();
                return;
            }
            user.addSendItem(key);                                                                                                          //add the cell key to the user item box and send the key-item description to him
            user.sendMsg(bkXml());                                                                                                          //update bank information to the client
            return;
        }

        if (go == 1 && cell != null) {                                                                                                      //opening a cell id 'sell'
            user.sendMsg(cell.cellXml());
            return;
        }

        if (sell >= 0 && d >= 0 && s >=0 && cell != null) {                                                                                 //move an item from user to cell
            Set<Item.Params> resetParams = Set.of(Item.Params.user_id);                                                                     //reset param list
            Map<Item.Params, Object> setParams = Map.of(Item.Params.b_id, getId(), Item.Params.cell_id, sell, Item.Params.section, s);      //set param - values list
            if (!getItemBox().joinMoveItem(d, c, false, user::getNewId, cell.getItemBox(), resetParams, setParams)) {
                logger.error("can't put an item id %d to bank cell id %d", d, sell);
                user.disconnect();
            }
            return;
        }

        if (sell >= 0 && a >=0 && s >= 0 && cell != null) {                                                                                 //user takes an item from cell to his item box
            Set<Item.Params> resetParams = Set.of(Item.Params.b_id, Item.Params.cell_id);
            Map<Item.Params, Object> setParams = Map.of(Item.Params.user_id, user.getId(), Item.Params.section, s);                         //params that need to be set to an item before moving to user
            if (!cell.getItemBox().moveItem(a, c, false, user::getNewId, getItemBox(), resetParams, setParams)) {
                logger.error("can't move an item id %d from bank cell to user %s", a, user.getLogin());
                user.disconnect();
            }
            return;
        }
        if (sell >= 0 && f >= 0 && s >= 0 && cell != null) {                                                                                //item is being moving between sections within a cell
            if (!cell.getItemBox().changeOne(f, Item.Params.section, s))
                user.disconnect();
            return;
        }

        if (newpsw != null && newpsw.length() >= 6 && cell != null) {                                                                       //change cell's psw
            if (!cell.setPassword(newpsw))
                user.disconnect();
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
            if (user.getMoney().getCopper() < getCost3()) {
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
            user.addSendItem(keyCopy);                                                                                                      //send a key item to the client
            user.sendMsg("<BK code=\"0\"/>");
            user.sendMsg(bkXml());                                                                                                          //update bank information to the client
            return;
        }
        setKey((String)user.getGameChannel().attr(AttributeKey.valueOf("encKey")).get());
        user.sendMsg(bkXml());
        return;
    }

}
