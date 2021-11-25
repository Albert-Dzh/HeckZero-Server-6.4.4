package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ParamUtils;
import ru.heckzero.server.ServerMain;

import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import javax.persistence.Transient;
import java.util.EnumSet;
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

    @Transient private String key;
    private int tkey;                                                                                                                       //does bank offer cells for free
    private int cost;                                                                                                                       //new cell cost
    private int cost2;                                                                                                                      //monthly cell rent
    private int cost3;                                                                                                                      //new cell section cost
    private int free;                                                                                                                       //number of available cells in bank

    protected Bank() { }

    public boolean setCost(int cost, int cost2) {this.cost = cost; this.cost2 = cost2; return sync();}                                      //set cost settings
    public boolean setFree(int free) {this.free = free; return sync();}
    public void setKey(String key) {this.key = key; }

    @Override
    protected String getParamXml(Params param) {return ParamUtils.getParamXml(this, param.toString()).transform(s -> s.startsWith("cash") ? s.replace("cash", "cash1") : s);}

    public String bkXml() {                                                                                                                 //XML formatted bank data
        StringJoiner sj = new StringJoiner("", "", "</BK>");
        sj.add(bankParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<BK ", ">")));   //add XML bank params
        return sj.toString();
    }
}
