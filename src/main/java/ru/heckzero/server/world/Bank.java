package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;

import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import java.util.EnumSet;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "Bank")
@Table(name = "banks")
@PrimaryKeyJoinColumn(name = "b_id")
public class Bank extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> bankParams = EnumSet.of(Params.cash, Params.cost, Params.cost2, Params.free, Params.tkey);

    public static Bank getBank(int id) {                                                                                                    //try to get a Bank instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Bank> query = session.createQuery("select b from Bank b where b.id = :id", Bank.class).setParameter("id", id).setCacheable(true);
            Bank bank = query.getSingleResult();
//            if (bank != null)
//                bank.ensureInitialized();
            return bank;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load portal with id %d from database: %s", id, e.getMessage());
        }
        return new Bank();
    }

    private int tkey;
    private int cost;
    private int cost2;
//    private int cost3;
    private int free;                                                                                                                       //number of available cells in bank


    protected Bank() { }

    public String bkXml() {                                                                                                                 //XML formatted bank data
        StringJoiner sj = new StringJoiner("", "", "</BK>");
        sj.add(bankParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<BK ", ">"))); //add XML bank params
        return sj.toString();
    }

}
