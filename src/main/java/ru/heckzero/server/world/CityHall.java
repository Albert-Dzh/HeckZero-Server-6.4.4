package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemTemplate;
import ru.heckzero.server.user.User;
import ru.heckzero.server.utils.HistoryCodes;

import javax.persistence.*;
import java.time.Instant;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "CityHall")
@Table(name = "city_hall")
@PrimaryKeyJoinColumn(name = "ch_id")
public class CityHall extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> cityHallParams = EnumSet.of(Params.p1, Params.p2, Params.d1, Params.ds, Params.cash, Params.m1, Params.o, Params.vip, Params.t, Params.sv /* Params.mod, Params.paint, Params.color, Params.bot*/);

    public static CityHall getCityHall(int id) {                                                                                            //try to get a CityHall instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<CityHall> query = session.createQuery("select ch from CityHall ch left join fetch ch.chLoot where ch.id = :id", CityHall.class).setParameter("id", id).setCacheable(true);
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
    private int ds;                                                                                                                         //license discount for the citizens
    private int t;                                                                                                                          //trademark cost
    private int o;                                                                                                                          //sell licenses only to citizens
    private String sv;                                                                                                                      //city name
    @Column(name = "mayor")
    private String m1;                                                                                                                      //mayor and mayor deputy name
    private int mod;
    private int vip;
//    int paint;
//    String color;

    @OneToMany(mappedBy = "cityHall", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE)
    private final List<CityHallLoot> chLoot = new ArrayList<>();

    protected CityHall() { }

    private void setCityHallParams(int p1, int p2, int d1, int ds, String m1, int o) {                                                      //save city hall params
        this.p1 = p1;  this.p2 = p2;   this.d1 = d1;  this.ds = ds;    this.m1 = m1;  this.o = o;
        sync();
        return;
    }

    public String chXml() {                                                                                                                 //XML formatted city hall data
        StringJoiner sj = new StringJoiner("", "", "</MR>");
        sj.add(cityHallParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<MR ", ">")));//add XML bank params
        return sj.toString();
    }

    public void processCmd(User user, int p1, int p2, int d1, int ds, String m1, int o, int vip, int citizenship, int img, int lic, int mod, int paint, String color) {
        if (p1 != -1 && p2 != -1) {                                                                                                         //set the CityHall options
            setCityHallParams(p1, p2, d1, ds, m1, o);                                                                                       //set and save new CityHall params
            addHistory(HistoryCodes.LOG_CITY_HALL_CHANGE_PARAMS, user.getLogin());
            logger.info("city hall options have been changed by user %s", user.getLogin());
            return;
        }

        if (citizenship == 1) {
            if (!user.decMoney(this.p1)) {                                                                                                  //try to charge a user for a passport
                user.sendMsg("<MR code=\"1\"/>");
                return;
            }
            Item passport = ItemTemplate.getTemplateItem(ItemTemplate.PASSPORT);                                                            //generate a new passport item based on template
            if (passport == null)
                return;

            passport.setParam(Item.Params.user_id,  user.getId());                                                                          //user id
            passport.setParam(Item.Params.txt, String.format("%s %s", passport.getParamStr(Item.Params.txt), this.sv));                     //add city name to passport(item) name
            passport.setParam(Item.Params.res,  this.sv);                                                                                   //city name
            passport.setParam(Item.Params.dt, Instant.now().getEpochSecond() + ServerMain.ONE_MES);                                         //set passport expiration date to one month
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

        if (lic != -1) {                                                                                                                    //buying licenses
            logger.warn(this.chLoot);
            StringJoiner sj = new StringJoiner("", "<MR lic=\"1\">", "</MR>");
            for (CityHallLoot loot : chLoot) {
                Item lootItem = ItemTemplate.getTemplateItem(loot.getTemplate_loot_id());
                lootItem.setParam(Item.Params.res, loot.getRes());
                lootItem.setParam(Item.Params.cost, loot.getCost());
                lootItem.setParam(Item.Params.count, loot.getCount());
                sj.add(lootItem.getXml());
            }
            user.sendMsg(sj.toString());
            return;
        }

        user.sendMsg(chXml());
        return;
    }

}
