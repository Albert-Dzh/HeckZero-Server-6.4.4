package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemTemplate;
import ru.heckzero.server.user.User;
import ru.heckzero.server.utils.HistoryCodes;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import java.time.Instant;
import java.util.EnumSet;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "CityHall")
@Table(name = "city_hall")
@PrimaryKeyJoinColumn(name = "ch_id")
public class CityHall extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> cityHallParams = EnumSet.of(Params.p1, Params.p2, Params.d1, Params.ds, Params.cash, Params.m1, Params.o, Params.vip /* Params.mod, Params.paint, Params.color*/);

    public static CityHall getCityHall(int id) {                                                                                            //try to get a CityHall instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<CityHall> query = session.createQuery("select ch from CityHall ch where ch.id = :id", CityHall.class).setParameter("id", id).setCacheable(true);
            CityHall cityHall = query.getSingleResult();
            Hibernate.initialize(cityHall.getLocation());                                                                                   //need by bank cells to get bank coordinates
            return cityHall;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load cityHall with id %d from database: %s", id, e.getMessage());
        }
        return new CityHall();
    }

    private int p1;                                                                                                                         //passport cost
    private int p2;                                                                                                                         //citizenship monthly fee
    private int d1;                                                                                                                         //holiday outfit rent cost
    private int ds;
    private int o;
    private String sv;
    @Column(name = "mayor")
    private String m1;
    private int mod;
    private int vip;
//    int paint;
//    String color;

    protected CityHall() { }

    public String chXml() {                                                                                                                 //XML formatted city hall data
        StringJoiner sj = new StringJoiner("", "", "</MR>");
        sj.add(cityHallParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<MR ", ">")));   //add XML bank params
        return sj.toString();
    }

    public void processCmd(User user, int p1, int p2, int d1, int ds, String m1, int o, int vip, int citizenship, int img, int mod, int paint, String color) {
        if (citizenship == 1) {
            if (!user.decMoney(this.p1)) {                                                                                                  //try to charge a user for a passport
                user.sendMsg("<MR code=\"1\"/>");
                return;
            }
            Item passport = ItemTemplate.getTemplateItem(ItemTemplate.PASSPORT);                                                            //generate a new passport item based on template
            if (passport == null)
                return;
            passport.setParam(Item.Params.txt, String.format("%s %s", passport.getParamStr(Item.Params.txt), this.sv));                     //add city name to passport(item) name
            passport.setParam(Item.Params.res,  this.sv);                                                                                   //city name
            passport.setParam(Item.Params.dt, Instant.now().getEpochSecond() + ServerMain.ONE_MES);
            user.addSendItem(passport);
            if (img != -1) {                                                                                                                //set the user image if defined
                String imgName = String.format("%s%d", user.getParamInt(User.Params.man) == 1 ? "man" : "girl", img);
                user.setParam(User.Params.img, imgName);
                user.sendMsg(String.format("<MYPARAM img=\"%s\"/>", imgName));
            }
            user.addHistory(HistoryCodes.LOG_PAY_AND_BALANCE, "Coins[" + this.p1 + "]", getLogDescription(), HistoryCodes.ULOG_FOR_PASSPORT, String.valueOf(user.getMoneyCop()));
            user.addHistory(HistoryCodes.LOG_GET_ITEMS_IN_HOUSE, passport.getLogDescription(), this.getLogDescription());
            addHistory(HistoryCodes.LOG_CITY_HALL_BUY_PASSPORT, user.getLogin(), String.valueOf(this.p1));
            user.sendMsg("<MR code=\"0\"/>");

            return;
        }

        user.sendMsg(chXml());
        return;
    }

}
