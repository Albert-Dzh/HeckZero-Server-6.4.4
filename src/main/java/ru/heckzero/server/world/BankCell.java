package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.items.ItemTemplate;
import ru.heckzero.server.items.ItemsDct;
import ru.heckzero.server.user.UserManager;
import ru.heckzero.server.utils.History;

import javax.persistence.*;
import java.time.Instant;
import java.util.StringJoiner;

@Entity(name = "BankCell")
@Table(name = "bank_cells")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "BankCell_Region")
public class BankCell {
    private static final Logger logger = LogManager.getFormatterLogger();

    public static BankCell getBankCell(int id) {                                                                                            //try to get a Bank cell instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<BankCell> query = session.createQuery("select c from BankCell c where c.id = :id", BankCell.class).setParameter("id", id).setCacheable(true);
            return query.getSingleResult();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.info("can't load bank cell id %d from database: %s", id, e.getMessage());
        }
        return null;
    }

    @Transient private ItemBox itemBox = null;

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bank_cell_generator_sequence")
    @SequenceGenerator(name = "bank_cell_generator_sequence", sequenceName = "bank_cells_id_seq", allocationSize = 1)
    private int id;                                                                                                                         //cell id generated by database sequence
    private int bank_id;                                                                                                                    //bank id
    private int user_id;                                                                                                                    //user id
    private String password;                                                                                                                //cell password
    private String email;                                                                                                                   //email for cell password restoration
    private long dt;                                                                                                                        //valid till date (epoch)
    private int block;                                                                                                                      //cell is blocked
    private int bookmark_add;                                                                                                               //cell's tabs amount
    private int capacity;                                                                                                                   //cell's capacity (in items)

    protected BankCell() { }

    public BankCell(int bank_id, int user_id, String password) {
        this.bank_id = bank_id;
        this.user_id = user_id;
        this.password = password;
        this.email = StringUtils.EMPTY;
        this.dt = Instant.now().getEpochSecond() + ServerMain.ONE_MES * 4;                                                                  //now + 4 month. Lease time in client will be shown as now + 1 month
        this.block = 0;                                                                                                                     //new cell is now blocked by default
        return;
    }

    public boolean isBlocked() {return this.block == 1;}                                                                                    //is the cell blocked
    public boolean isExpired() {return this.dt > (Instant.now().getEpochSecond() - ServerMain.ONE_MES * 3);}                                //is the cell expired

    public int getId()  {return this.id;}
    public long getDt() {return this.dt;}
    public boolean checkPass(String key, String ecnryptedPass) {return ecnryptedPass.equals(UserManager.encrypt(key, password));}           //validate cell's password

    public ItemBox getItemBox() {return itemBox == null ? (itemBox = ItemBox.init(ItemBox.BoxType.BANK_CELL, id)) : itemBox;}               //get the building itembox, initialize if needed
    public int getBookmark_add() {return bookmark_add;}
    public int getCapacity() {return capacity;}
    public int getUser_id() {return user_id;}

    public boolean block() {this.block = 1; return sync();}                                                                                 //block the cell
    public boolean unblock() {this.block = 0; return sync();}                                                                               //unblock the cell

    public boolean setCapacity(int capacity) {
        this.capacity = capacity;
        return sync();
    }

    public boolean setBookmark_add(int bookmark_add) {
        this.bookmark_add = bookmark_add;
        return sync();
    }

    public boolean setPassword(String password) {
        this.password = password;
        return sync();
    }
    public boolean setEmail(String email) {
        this.email = email;
        return sync();
    }

    public Item makeKeyCopy() {
        Item key = ItemTemplate.getTemplateItem(ItemsDct.TYPE_BANK_KEY_COPY);                                                               //generate a bank cell key item
        if (key == null)
            return null;
        Bank bank = Bank.getBank(bank_id);

        key.setParam(Item.Params.txt, key.getParamStr(Item.Params.txt) + getId());                                                           //set the key item params to make client display the key hint properly
        key.setParam(Item.Params.made, String.format("%s%d_%d_%d",  key.getParamStr(Item.Params.made), bank.getX(), bank.getY(), bank.getZ()));
        key.setParam(Item.Params.hz, getId());
        key.setParam(Item.Params.res, bank.getTxt());
        key.setParam(Item.Params.user_id, user_id);                                                                                         //user id this key belongs to
        key.setParam(Item.Params.section, 0);                                                                                               //user box sections this key will be placed to
        return key;
    }

    public void addHistory(int code, String ... params) {                                                                                   //add a record to the cell history log
        History.add(getId(), History.Subject.CELL, code, params);
        return;
    }

    public String cellXml() {                                                                                                               //XML formatted cell data included item box
        StringJoiner sj = new StringJoiner("", "<BK sell=\"1\" bookmark_add=\"" + bookmark_add + "\"" + " capacity=\"" + capacity +"\">", "</BK>");
        sj.add(getItemBox().getXml());
        return sj.toString();
    }

    public boolean sync() {                                                                                                                 //sync the bank cell
        if (!ServerMain.sync(this)) {
            logger.error("can't sync bank cell %s", this);
            return false;
        }
        logger.info("synced bank cell %s", this);
        return true;
    }

    @Override
    public String toString() {return "BankCell{" + "id=" + id + ", bank_id=" + bank_id + ", user_id=" + user_id + '}'; }
}
