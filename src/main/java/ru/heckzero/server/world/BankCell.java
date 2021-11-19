package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.time.Instant;

@Entity(name = "BankCell")
@Table(name = "bank_cells")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "BankCell_Region")
public class BankCell {
    private static final Logger logger = LogManager.getFormatterLogger();

    /*public static void createCell(int bank_id, int user_id) {                                                                                                    //try to get a Bank instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Bank> query = session.createQuery("select b from Bank b where b.id = :id", Bank.class).setParameter("id", id).setCacheable(true);
            return query.getSingleResult();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load bank with id %d from database: %s", id, e.getMessage());
        }
        return new Bank();
    }
*/
    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "bank_cell_generator_sequence")
    @SequenceGenerator(name = "bank_cell_generator_sequence", sequenceName = "bank_cells_cell_id_seq", allocationSize = 1)
    private int id;                                                                                                                         //cell id
    private int bank_id;                                                                                                                    //bank id
    private int user_id;                                                                                                                    //user id
    private String password;                                                                                                                //cell password
    private String email;                                                                                                                   //email for password restoration
    private long dt;                                                                                                                        //valid till date (epoch)
    private int block ;                                                                                                                     //cell is blocked

    protected BankCell() { }

    public BankCell(int bank_id, int user_id, String password) {
        this.bank_id = bank_id;
        this.user_id = user_id;
        this.password = password;
        this.email = StringUtils.EMPTY;
        this.dt = Instant.now().getEpochSecond() + 2678400;
        this.block = 0;
        return;
    }

    public int getId() {return id;}
    public int getBlock() {return block;}

    public void setBlock(int block) {this.block = block; }

    public boolean sync() {                                                                                                                 //sync the bank cell
        if (!ServerMain.sync(this)) {
            logger.error("can't sync bank cell %s", this);
            return false;
        }
        logger.info("synced bank cell %s", this);
        return true;
    }


/*    public String bkXml() {                                                                                                                 //XML formatted bank data
        StringJoiner sj = new StringJoiner("", "", "</BK>");
        sj.add(bankParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<BK ", ">")));   //add XML bank params
        return sj.toString();
    }*/

    @Override
    public String toString() {return "BankCell{" + "id=" + id + ", bank_id=" + bank_id + ", user_id=" + user_id + '}'; }
}
