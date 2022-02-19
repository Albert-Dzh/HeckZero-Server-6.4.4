package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.ItemsDct;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;
import ru.heckzero.server.utils.HistoryCodes;
import ru.heckzero.server.utils.ParamUtils;

import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import java.util.EnumSet;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "PostOffice")
@Table(name = "post_offices")
@PrimaryKeyJoinColumn(name = "b_id")
public class PostOffice extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> postParams = EnumSet.of(Params.cash, Params.p1, Params.p2, Params.d1);

    public static PostOffice getPostOffice(int id) {                                                                                        //try to get a PostOffice instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<PostOffice> query = session.createQuery("select b from PostOffice b where b.id = :id", PostOffice.class).setParameter("id", id).setCacheable(true);
            PostOffice postOffice = query.getSingleResult();
//            Hibernate.initialize(postOffice.getLocation());                                                                                 //need by bank cells to get bank coordinates
            return postOffice;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load post office with id %d from database: %s", id, e.getMessage());
        }
        return new PostOffice();
    }

    private int p1;                                                                                                                         //wire cost
    private int p2;                                                                                                                         //parcel cost
    private int d1;                                                                                                                         //urgent parcel cost

    protected PostOffice() { }

    public int getP1()  {return p1;}

    public void setP1(int p1) {this.p1 = p1; }
    public void setP2(int p2) {this.p2 = p2;}
    public void setD1(int d1) {this.d1 = d1;}

    @Override
    protected String getParamXml(Params param) {return ParamUtils.getParamXml(this, param.toString());}

    public String ptXml() {                                                                                                                 //XML formatted post office data
        StringJoiner sj = new StringJoiner("", "", "</PT>");
        sj.add(postParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<PT ", ">")));   //add XML post office params
        return sj.toString();
    }

    public void processCmd(User user, int get, int me, int p1, int p2, int d1, String login, String wire) {
        if (get > 0 && user.isBuildMaster()) {                                                                                              //take money from post office's cash
            int cashTaken = decMoney(get);                                                                                                  //take money from building
            user.addMoney(ItemsDct.MONEY_COPP, cashTaken);                                                                                  //add money from postOffice cash to the user
            return;
        }

        if (p1 != -1 && p2 != -1 && d1 != -1) {                                                                                             //set(change) post office settings
            this.p1 = p1;
            this.p2 = p2;
            this.d1 = d1;
            if (!sync()) {
                user.disconnect();
                return;
            }
        }

        if (login != null && wire != null) {                                                                                                //sending a wire to user login
            User rcptUser = UserManager.getUser(login);
            if (rcptUser == null || rcptUser.isEmpty()) {                                                                                   //recipient is not found
                user.sendMsg("<PT err=\"1\"/>");
                return;
            }
            wire = wire.replace("\"", "&quot;").replace("'", "&apos;").replace("\r", "&#xD;");                                              //replace quotes in a wire by the XML equivalent
            rcptUser.sendIMS(HistoryCodes.LOG_WIRE, "Vacya", wire);                                                                         //send a wire as an IMS to the recipient
            user.sendMsg("<PT ok=\"1\"/>");
        }

        user.sendMsg(ptXml());
        return;
    }

}
