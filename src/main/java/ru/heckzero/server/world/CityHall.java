package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.HibernateException;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.items.ItemTemplate;
import ru.heckzero.server.items.ItemsDct;
import ru.heckzero.server.user.User;
import ru.heckzero.server.utils.HistoryCodes;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.Semaphore;
import java.util.stream.Collectors;

@Entity(name = "CityHall")
@Table(name = "city_hall")
@PrimaryKeyJoinColumn(name = "ch_id")
public class CityHall extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> cityHallParams = EnumSet.of(Params.p1, Params.p2, Params.d1, Params.ds, Params.cash, Params.m1, Params.o, Params.vip, Params.t, Params.sv /* Params.mod, Params.paint, Params.color, Params.bot*/);
    private static final Semaphore semBuyLic = new Semaphore(1, true);                                                                      //license buying semaphore
    private static final String [][] weddingItems = {{"bk2-f11", "bk2-f14", "bk2-f15", "bk2-f15", "bk2-f16", "bk2-f17"}, {"bk2-f1", "bk2-f2", "bk2-f3", "bk2-f4"}};	//women's[0] and man's[1] list of template items names

    public static CityHall getCityHall(int id) {                                                                                            //try to get a CityHall instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<CityHall> query = session.createQuery("select ch from CityHall ch where ch.id = :id", CityHall.class).setParameter("id", id).setCacheable(true);
            CityHall cityHall = query.getSingleResult();
            Hibernate.initialize(cityHall.getLocation());                                                                                   //need by bank cells to get bank coordinates
            return cityHall;
        } catch (HibernateException e) {                                                                                                    //database problem occurred
            logger.error("can't load cityHall with id %d from database: %s", id, e.getMessage());
        }
        return new CityHall();
    }

    private List<CityHallLoot> getLicences() {                                                                                              //get available licenses for the city hall
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<CityHallLoot> query = session.createQuery("select ch_loot from CityHallLoot ch_loot where ch_loot.ch_id = :id order by ch_loot.id", CityHallLoot.class).setParameter("id", this.id).setCacheable(true);
            return query.list();
        } catch (HibernateException e) {                                                                                                    //database problem occurred
            logger.error("can't load cityHall licences with ch id %d from database: %s", this.id, e.getMessage());
        }
        return new ArrayList<>();
    }

    private int checkTrademark(String trade) {                                                                                              //check if the such trademark exists
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<Item> query = session.createQuery("select i from Item i where i.type = :type and lower(i.made) = :made", Item.class).setParameter("type", ItemsDct.TYPE_TRADEMARK).setParameter("made", trade.toLowerCase()).setCacheable(true);
            return query.list().size();
        } catch (HibernateException e) {                                                                                                    //database problem occurred
            logger.error("can't check if trademark '%s' exists: %s", trade, e.getMessage());
        }
        return 1;
    }

    private int p1;                                                                                                                         //passport cost
    private int p2;                                                                                                                         //citizenship monthly fee
    private int d1;                                                                                                                         //wedding dress rent cost
    private int ds;                                                                                                                         //license discount for the citizens
    private int t;                                                                                                                          //trademark cost
    private int o;                                                                                                                          //if the CityHall sells licenses only to citizens
    private String sv;                                                                                                                      //city name
    @Column(name = "mayor")
    private String m1;                                                                                                                      //mayor and mayor deputy names (coma separated)
    private int mod;
    private int vip;                                                                                                                        //vip card price (silver coins)

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

    public void buyLicsense(int licId, int numLic, User user) {                                                                             //runs in a separate thread to avoid blocking precious netty threads
        logger.info("buying %d licences for user %s in cityHall %d", numLic, user.getLogin(), this.id);
        semBuyLic.acquireUninterruptibly();                                                                                                 //lock all others threads
        List<CityHallLoot> chLoot = getLicences();                                                                                          //get the available licenses for the city hall
        CityHallLoot licLoot = chLoot.stream().filter(i -> i.getId() == licId).findFirst().orElse(null);                                    //find the given license by id
        if (licLoot == null) {                                                                                                              //can't found the license
            logger.warn("lic (loot) id %d is not found in the CityHall id %d", licId, getId());
            user.sendMsg("<MR code=\"6\"/>");                                                                                               //Закончились лицензии
            semBuyLic.release();
            return;
        }
        numLic = Math.min(numLic, licLoot.getCount());                                                                                      //set the actual number of licenses the user can buy
        if (user.getMoneyCop() < numLic * licLoot.getCost()) {                                                                              //user hasn't enough money
            logger.warn("user %s hasn't enough money to buy %d licences", user.getLogin(), numLic);
            user.sendMsg("<MR code=\"0\" count=\"0\"/>");
            semBuyLic.release();
            return;
        }

        licLoot.setCount(licLoot.getCount() - numLic);                                                                                      //update licenses count in db
        licLoot.sync();
        semBuyLic.release();                                                                                                                //release the semaphore

        Item licItem = ItemTemplate.getTemplateItem(licLoot.getTemplate_type());                                                            //form a license item from loot
        licItem.setParam(Item.Params.res, licLoot.getRes());
        licItem.setParam(Item.Params.count, numLic);
        user.addSendItem(licItem);
        user.sendMsg(String.format("<MR code=\"0\" count=\"%d\"/>", numLic));

        user.decMoney(numLic * licLoot.getCost());
        user.addHistory(HistoryCodes.LOG_PAY_AND_BALANCE, "Coins[" + licLoot.getCost() * numLic + "]", getLogDescription(), "", String.valueOf(user.getMoneyCop()));                     //Оплатил {%s} в \'%s\' %s. В рюкзаке осталось %s мнт.
        user.addHistory(HistoryCodes.LOG_GET_ITEMS_IN_HOUSE, licItem.getLogDescription(), getLogDescription());                             //Получены предметы: {%s} в здании \'%s\'

        this.addMoney(numLic * licLoot.getCost());                                                                                          //add money tom the city hall's cache
        this.addHistory(HistoryCodes.LOG_CITY_HALL_BUY_LIC, user.getLogin(), licItem.getLogDescription(), String.valueOf(licLoot.getCost() * numLic));//Персонаж '%s' купил лицензию {%s} за %s мнт."
        return;
    }

    public void processCmd(User user, int p1, int p2, int d1, int ds, String m1, int o, int vip, int citizenship, int img, int lic, int buy, int count, int mod, String paint, String color, int tax, int ch, int cost, int w, String trade, int get) {
        if (p1 != -1 && p2 != -1) {                                                                                                         //set the CityHall options
            setCityHallParams(p1, p2, d1, ds, m1, o);                                                                                       //set and save new CityHall params
            addHistory(HistoryCodes.LOG_CITY_HALL_CHANGE_PARAMS, user.getLogin());
            logger.info("city hall options have been changed by user %s", user.getLogin());
            return;
        }

        if (citizenship == 1) {                                                                                                             //user applies for a citizenship
            if (!user.decMoney(this.p1)) {                                                                                                  //try to charge a user for a passport
                user.sendMsg("<MR code=\"1\"/>");
                return;
            }
            addMoney(this.p1);                                                                                                              //add money to city hall cash
            Item passport = ItemTemplate.getTemplateItem(ItemsDct.TYPE_PASSPORT);                                                           //generate a new passport item based on template

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
            user.addHistory(HistoryCodes.LOG_GET_ITEMS_IN_HOUSE, passport.getLogDescription(), this.getLogDescription());                   //Получены предметы: {%s} в здании \'%s\'
            addHistory(HistoryCodes.LOG_CITY_HALL_BUY_PASSPORT, user.getLogin(), String.valueOf(this.p1));                                  //Персонаж '%s' купил паспорт: %s мнт.
            user.sendMsg("<MR code=\"0\"/>");

            return;
        }
        if (tax != -1) {                                                                                                                    //passport renewal
            Item passport = user.getPassport();
            if (passport == null) {
                logger.warn("can't find passport item of user %s", user.getLogin());
                user.sendMsg("<MR code=\"19\"/>");                                                                                          //Системная ошибка
                return;
            }
            if (!user.decMoney(this.p2)) {                                                                                                  //try to charge a user for a passport renewal
                user.sendMsg("<MR code=\"1\"/>");                                                                                           //Недостаточно монет
                return;
            }
            addMoney(this.p2);                                                                                                              //add money to the city hall cash

            user.getItemBox().changeOne(passport.getId(), Item.Params.dt, passport.getParamLong(Item.Params.dt) + ServerMain.ONE_MES);
            user.addHistory(HistoryCodes.LOG_PAY_AND_BALANCE, "Coins[" + this.p2 + "]", getLogDescription(), HistoryCodes.ULOG_FOR_CITIZEN_TAX, String.valueOf(user.getMoneyCop()));//Оплатил {%s} в \'%s\' %s. В рюкзаке осталось %s мнт.
            addHistory(HistoryCodes.LOG_CITY_HALL_PAY_TAX, user.getLogin(), String.valueOf(this.p2));                                       //Персонаж '%s' заплатил налоги: %s мнт."
            user.sendMsg("<MR code=\"0\"/>");
            return;
        }

        if (w != -1) {                                                                                                                      //wedding dress loan
            doWeddingRent(user);
            return;
        }

        if (vip != -1) {                                                                                                                    //buy a vip card
            doVIP(user);
            return;
        }

        if (!paint.isEmpty() && !color.isEmpty()) {                                                                                         //paint items
            doPaint(user, paint, color);
            return;
        }

        if (!trade.isEmpty()) {                                                                                                             //user registers a trademark
            doTradeMark(user, trade);
            return;
        }

        if (get != -1) {                                                                                                                    //withdraw money from CityHall cash
            int cashTaken = this.decMoney(get);
            user.addMoney(ItemsDct.MONEY_COPP, cashTaken);
            addHistory(HistoryCodes.LOG_CITY_HALL_GET_MONEY_FROM_CASH, user.getLogin(), String.valueOf(get));                               //Владелец мэрии '%s' забрал из кассы %s мнт.
            user.addHistory(HistoryCodes.LOG_GET_MONEY_FROM_CASH, String.valueOf(ItemsDct.MONEY_COPP), String.valueOf(get), getLogDescription(), String.valueOf(user.getMoneyCop()));//Забрал {%s[%s]} из кассы %s. В рюказке стало %s мнт.
        }

        if (buy != -1 && count > 0) {                                                                                                       //buying licences
            Thread buyingThread = new Thread(() -> this.buyLicsense(buy, count, user));                                                     //run in a separate thread for the sake of synchronization and avoid blocking of netty threads
            buyingThread.start();
            try {
                buyingThread.join(10000);                                                                                                   //wait for the buying thread to finish to prevent any further client's commands processing by the netty thread
            } catch (InterruptedException e) {
                throw new RuntimeException(e);
            }
        }

        if (lic != -1) {                                                                                                                    //show available licenses
            List<CityHallLoot> chLoot = getLicences();
            if (!user.isBuildMaster())
                chLoot = chLoot.stream().filter(i -> i.getCost() != 0).toList();

            StringJoiner sj = new StringJoiner("", "<MR lic=\"1\">", "</MR>");
            for (CityHallLoot loot : chLoot) {
                Item lootItem = ItemTemplate.getTemplateItem(loot.getTemplate_type());
                lootItem.setParam(Item.Params.id, loot.getId());
                lootItem.setParam(Item.Params.res, loot.getRes());
                lootItem.setParam(Item.Params.cost, loot.getCost());
                lootItem.setParam(Item.Params.count, loot.getCount());
                sj.add(lootItem.getXml());
            }
            user.sendMsg(sj.toString());
            return;
        }

        if (ch != -1 && cost != -1) {                                                                                                       //license cost changing
            List<CityHallLoot> chLoot = getLicences();
            CityHallLoot licLoot = chLoot.stream().filter(i -> i.getId() == ch).findFirst().orElse(null);                                   //find the given license by id
            if (licLoot == null) {                                                                                                          //can't found the license
                logger.warn("lic (loot) id %d is not found in the CityHall id %d", ch, getId());
                user.sendMsg("<MR code=\"19\"/>");                                                                                          //Системная ошибка
                return;
            }
            Item licItem = ItemTemplate.getTemplateItem(licLoot.getTemplate_type());                                                     //a license item from a loot
            int minCost = licItem.getParamInt(Item.Params.cost2);                                                                           //minimum license cost
            if (cost < minCost && cost != 0)                                                                                                //can't set cost which is less than a minimum
                logger.warn("cant set new license cost to %d because it's less than minimum cost (%d < %d)", cost, minCost);
            licLoot.setCost(cost);
            licLoot.sync();
            return;
        }

        user.sendMsg(chXml());
        return;
    }

    private void doWeddingRent(User user) {                                                                                                 //wedding dress rent
        if (!user.getParam_citizen().equals(this.sv)) {
            logger.warn("user %s is not a citizen of %s, and is not eligible for the wedding dress rent", user.getLogin(), this.sv);        //check if user is a citizen of this city
            user.sendMsg("<MR code=\"13\"/>");																				                //Прокат работает только для граждан этого города
            return;
        }
        String [ ] itemNames = weddingItems[user.getParamInt(User.Params.man)];			                        		    				//choose a list of wedding items names by player gender

        ItemBox wBox = new ItemBox();				                    																	//a temporary item box for the wedding items
        Arrays.stream(itemNames).map(ItemTemplate::getTemplateItem).filter(item -> item.checkMinRequirement(user)).forEach(wBox::addItem);  //get template items and check the minimum requirements
        if (wBox.size() != itemNames.length) {																					            //some items failed minimum requirement checks
            user.sendMsg("<MR code=\"14\"/>");																				                //На складе нет одежды вашего размера
            return;
        }
        wBox.forEach(item -> item.setParam(Item.Params.dt, Instant.now().getEpochSecond() + 86400));                                        //set dt to 24 hours

        if (!user.decMoney(this.d1)) {                                                                                                      //user hasn't got enough money
            logger.warn("user %s hasn't got enough money to rent wedding clothes", user.getLogin());
            user.sendMsg("<MR code=\"1\"/>");                                                                                               //Недостаточно монет
            return;
        }
        addMoney(this.d1);	                                    																			//add money to the cityHall cash

        user.addHistory(HistoryCodes.LOG_PAY_AND_BALANCE, "Coins[" + this.d1 + "]", getLogDescription(), HistoryCodes.ULOG_FOR_WEDDING_RENT, String.valueOf(user.getMoneyCop()));//Оплатил {%s} в \'%s\' %s. В рюкзаке осталось %s мнт.
        user.addHistory(HistoryCodes.LOG_GET_ITEMS_IN_HOUSE, wBox.getLogDescription(), this.getLogDescription());                           //Получены предметы: {%s} в здании \'%s\'
//        addHistory(HistoryCodes.LOG_CITY_HALL_WEDDING_RENT, user.getLogin(), String.valueOf(this.d1));                                    //here must be a proper CityHall log message for the wedding rent, but it's absent in the LANG file

        user.addSendItems(wBox);                                                                                                            //add items to user box and send <ADD_ONE/> for every item to the user
        user.sendMsg("<MR code=\"0\"/>");
        return;
    }

    private void doVIP(User user) {                                                                                                         //user buys a VIP card or renew an old one
        if (!user.decMoney(ItemsDct.MONEY_SILV, this.vip)) {                                                                                //try to charge a user for a VIP card
            user.sendMsg("<MR code=\"1\"/>");
            return;
        }

        Item vip = user.getVipCard();                                                                                                       //try to find an existing VIP card from user
        if (vip != null) {                                                                                                                  //found
            logger.info("renewing a VIP card id %d of user %s", vip.getId(), user.getLogin());
            vip.setParam(Item.Params.dt, vip.getParamLong(Item.Params.dt) + ServerMain.ONE_MES);                                            //renew - extend dt param by 1 month
            user.sendMsg(String.format("<CHANGE_ONE id=\"%d\" dt=\"%d\"/>", vip.getId(), vip.getParamLong(Item.Params.dt)));
        }else {                                                                                                                             //create a new VIP card item
            vip = ItemTemplate.getTemplateItem(ItemsDct.TYPE_VIP_CARD);                                                                     //create a new VIP card item based on a template item
            logger.info("created a VIP card id %d for user %s", vip.getId(), user.getLogin());
            vip.setParam(Item.Params.dt, Instant.now().getEpochSecond() + ServerMain.ONE_MES);                                              //set the vip card expiration date
            user.addSendItem(vip);                                                                                                          //send a vip card to user
        }

        user.addHistory(HistoryCodes.LOG_PAY, "Silver[" + this.vip + "]", getLogDescription(), HistoryCodes.ULOG_FOR_ALPHA_CODE);           //Оплатил {%s} в \'%s\' %s
        user.addHistory(HistoryCodes.LOG_BALANCE_INFO, String.valueOf(user.getMoneyCop()), String.valueOf(user.getMoneySilv()));            //На счету %s медных монет и %s серебряных.
        user.addHistory(HistoryCodes.LOG_GET_ITEMS_IN_HOUSE, vip.getLogDescription(), this.getLogDescription());                            //Получены предметы: {%s} в здании \'%s\'
        addHistory(HistoryCodes.LOG_CITY_HALL_BUY_VIP_CARD, user.getLogin(), String.valueOf(this.vip));                                     //Персонаж '%s' купил VIP-card: %s с.м.

        user.sendMsg("<MR code=\"0\"/>");
        return;
    }

    private void doPaint(User user, String paint, String color) {                                                                           //paint items
        long[ ] itemIds = Arrays.stream(paint.split(",")).mapToLong(Long::valueOf).toArray();                                               //items ids that need to be painted
        ItemBox paintBox = user.getItemBox().findItems(itemIds);                                                                            //item box containing items to be painted

        if (paintBox.size() != itemIds.length) {                                                                                            //something went wrong
            logger.error("can't find some items to be painted (%s) from user %s", itemIds, user.getLogin());
            user.sendMsg("<MR code=\"19\"/>");                                                                                              //Системная ошибка
            return;
        }

        if (!user.decMoney(ItemsDct.MONEY_SILV, paintBox.size())) {                                                                         //try to charge a user for painting
            user.sendMsg("<MR code=\"1\"/>");
            return;
        }

        paintBox.forEach(i -> user.getItemBox().changeOne(i.getId(), Item.Params.s1, color));                                               //paint the items - set s1 param of each item to color
        user.addHistory(HistoryCodes.LOG_PAY, "Silver[" + paintBox.size() + "]", getLogDescription(), HistoryCodes.ULOG_FOR_ITEMS_PAINT);   //Оплатил {%s} в \'%s\' %s
        user.addHistory(HistoryCodes.LOG_BALANCE_INFO, String.valueOf(user.getMoneyCop()), String.valueOf(user.getMoneySilv()));            //На счету %s медных монет и %s серебряных.

        user.sendMsg("<MR code=\"0\"/>");
        return;
    }

    private void doTradeMark(User user, String trade) {                                                                                     //register a new trademark
        trade = trade.trim();
        if (checkTrademark(trade) != 0) {
            user.sendMsg("<MR code=\"15\"/>");                                                                                              //Такая или подобная торговая марка уже зарегистрирована
            return;
        }

        if (!user.decMoney(this.t)) {                                                                                                       //user hasn't got enough money
            logger.warn("user %s hasn't got enough money to buy a trademark", user.getLogin());
            user.sendMsg("<MR code=\"1\"/>");                                                                                               //Недостаточно монет
            return;
        }
        this.addMoney(this.t);                                                                                                              //add money to city hall cache

        Item tm = ItemTemplate.getTemplateItem(ItemsDct.TYPE_TRADEMARK);                                                                    //create a new TradeMark item
        tm.setParam(Item.Params.made, trade);
        tm.setParam(Item.Params.owner, user.getLogin());
        tm.setParam(Item.Params.tm, Instant.now().getEpochSecond());
        logger.info("created a trademark %s id %d for user %s", trade, tm.getId(), user.getLogin());

        user.addHistory(HistoryCodes.LOG_PAY_AND_BALANCE, "Coins[" + this.t + "]", getLogDescription(), HistoryCodes.ULOG_FOR_TRADEMARK, String.valueOf(user.getMoneyCop()));//Оплатил {%s} в \'%s\' %s. В рюкзаке осталось %s мнт.
        user.addHistory(HistoryCodes.LOG_GET_ITEMS_IN_HOUSE, tm.getLogDescription(), this.getLogDescription());                             //Получены предметы: {%s} в здании \'%s\'
        addHistory(HistoryCodes.LOG_CITY_HALL_REG_TRADEMARK, user.getLogin(), trade);               	    			                    //Персонаж '%s' зарегистрировал торговую марку {%s}

        user.addSendItem(tm);                                                                                                               //send a trademark item to the user
        user.sendMsg("<MR code=\"0\"/>");
        return;
    }
}
