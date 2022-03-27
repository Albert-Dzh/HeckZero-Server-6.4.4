package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.items.ItemsDct;
import ru.heckzero.server.user.User;
import ru.heckzero.server.user.UserManager;
import ru.heckzero.server.utils.HistoryCodes;
import ru.heckzero.server.utils.ParamUtils;

import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import java.time.Instant;
import java.util.Arrays;
import java.util.EnumSet;
import java.util.Map;
import java.util.StringJoiner;
import java.util.concurrent.TimeUnit;
import java.util.stream.Collectors;

@Entity(name = "PostOffice")
@Table(name = "post_offices")
@PrimaryKeyJoinColumn(name = "b_id")
public class PostOffice extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final int DELIVERY_TIME_MIN_SEC = 300, DELIVERY_TIME_MAX_SEC = 1800;                                                     //delivery time in seconds
    private static final EnumSet<Params> postParams = EnumSet.of(Params.cash, Params.p1, Params.p2, Params.d1);

    public static PostOffice getPostOffice(int id) {                                                                                        //try to get a PostOffice instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<PostOffice> query = session.createQuery("select b from PostOffice b where b.id = :id", PostOffice.class).setParameter("id", id).setCacheable(true);
            PostOffice postOffice = query.getSingleResult();
            Hibernate.initialize(postOffice.getLocation());                                                                                 //need by bank cells to get bank coordinates
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

    public void processCmd(User user, int get, int me, int p1, int p2, int d1, long a, int c, int s, String login, String wire, String parcel, String itm, int fast) {
        if (get > 0 && user.isBuildMaster()) {                                                                                              //take money from post office's cash
            int cashTaken = decMoney(get);                                                                                                  //take money from building
            user.addMoney(ItemsDct.MONEY_COPP, cashTaken);                                                                                  //add money from postOffice cash to the user
            this.addHistory(HistoryCodes.LOG_POST_GET_MONEY, user.getLogin(), String.valueOf(cashTaken));                                   //Владелец почты '%s' забрал из кассы %s мнт.
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
            this.addHistory(HistoryCodes.LOG_POST_CHANGE_PARAMS, user.getLogin());                                                          //Персонаж '%s' изменил настройки
        }

        if (login != null && wire != null) {                                                                                                //sending a wire to user login
            User rcptUser = UserManager.getUser(login);
            if (rcptUser == null || rcptUser.isEmpty()) {                                                                                   //recipient is not found
                user.sendMsg("<PT err=\"1\"/>");
                return;
            }
            wire = wire.replace("\"", "&quot;").replace("'", "&apos;").replace("\r", "&#xD;");                                              //replace quotes in a wire by the XML equivalent

            user.decMoney(this.p1);                                                                                                         //charge the user for the wire sending
            this.addMoney(this.p1);                                                                                                         //add money to the post office cash
            user.addHistory(HistoryCodes.LOG_PAY_AND_BALANCE, "Coins[" + this.p1 + "]", String.format("%s,%s,%s,%s", getTxt(), getLocalX(), getLocalY(), getZ()), HistoryCodes.ULOG_FOR_WIRE, String.valueOf(user.getMoneyCop()));
            this.addHistory(HistoryCodes.LOG_POST_PAY_FOR_WIRE, user.getLogin(), String.valueOf(this.p1));                                  //Персонаж 'User' заплатил XX мнт. за отправку телеграммы
            user.sendMsg("<PT ok=\"1\"/>");
            rcptUser.sendIMS(HistoryCodes.LOG_WIRE, user.getLogin(), wire);                                                                 //send a wire as an IMS to the recipient
        }

        if (parcel != null && itm != null) {                                                                                                //user sends a parcel, recipient is in parcel argument
            User rcptUser = UserManager.getUser(parcel);
            if (rcptUser == null || rcptUser.isEmpty()) {                                                                                   //parcel recipient is not found
                user.sendMsg("<PT err=\"1\"/>");
                return;
            }

            int deliveryTime = fast == 1 ? 1 : DELIVERY_TIME_MIN_SEC + (int)(Math.random() * ((DELIVERY_TIME_MAX_SEC - DELIVERY_TIME_MIN_SEC) + 1));    //parcel delivery time - get random number of seconds between MIN and MAX delivery time
            long rcpt_dt = Instant.now().getEpochSecond() + deliveryTime;                                                                   //the time (epoch) when parcel can be delivered

            Map<Item.Params, Object> setParams = Map.of(Item.Params.rcpt_id, rcptUser.getId(), Item.Params.owner, user.getLogin(), Item.Params.rcpt_dt, rcpt_dt);      //set param - value list

            ItemBox parcelBox = new ItemBox(true);                                                                                          //new synchronised Item Box to place parcel items into
            long[ ] itemsIdsCount = Arrays.stream(itm.split(",")).mapToLong(Long::parseLong).toArray();                                     //items ids along with their count that was placed in a parcel by player
            for (int i = 0; i < itemsIdsCount.length; i += 2) {                                                                             //move items to parcelBox including database changes
                if (user.getItemBox().moveItem(itemsIdsCount[i], (int)itemsIdsCount[i + 1], Item::getNextGlobalId, false, parcelBox, setParams) == null) { // new id will be set to the next global id if the item is going to be split, or leave the id unchanged otherwise
                    logger.error("can't put an item id %d to post office", itemsIdsCount[i]);
                    user.disconnect();
                    return;
                }
            }
            int parcelWeight = parcelBox.getMass();                                                                                         //parcel weight
            int parcelCost = (int)Math.ceil(parcelWeight * (fast == 1 ? this.d1 : this.p2) / 100.0);                                        //parcel delivery cost

            user.decMoney(parcelCost);                                                                                                      //charge the user for the parcel sending
            this.addMoney(parcelCost);                                                                                                      //add money to the post office cash
            user.addHistory(HistoryCodes.LOG_SEND_ITEMS, parcelBox.getLogDescription(), rcptUser.getLogin());								//Переслал: {%s} персонажу \'%s\'
            user.addHistory(HistoryCodes.LOG_PAY_AND_BALANCE, "Coins[" + parcelCost + "]", String.format("%s,%s,%s,%s", getTxt(), getLocalX(), getLocalY(), getZ()), HistoryCodes.ULOG_FOR_PARCEL, String.valueOf(user.getMoneyCop()));
            this.addHistory(HistoryCodes.LOG_POST_PAY_FOR_PARCEL, user.getLogin(), String.valueOf(parcelCost));                             //Персонаж 'User' заплатил XX мнт. за отправку посылки

            user.sendMsg("<PT ok=\"2\"/>");                                                                                                 //send ok to the Post office
            user.getGameChannel().eventLoop().schedule(() -> rcptUser.sendIMS(HistoryCodes.LOG_PARCEL_ARRIVED, user.getLogin()), deliveryTime, TimeUnit.SECONDS);//send IMS notification to the recipient about the parcel in deliveryTime seconds
        }

        if (me == 1) {                                                                                                                      //check if there is a parcel ready for delivery for the user
            ItemBox parcelBox = ItemBox.init(ItemBox.BoxType.PARCEL, user.getId());
            user.sendMsg(String.format("<PT me=\"1\">%s</PT>", parcelBox.getXml()));                                                        //send parcel ItemBox context to the user
            return;
        }

        if (a != -1) {                                                                                                                      //user takes an item from a parcel
            ItemBox parcelBox = ItemBox.init(ItemBox.BoxType.PARCEL, user.getId());
            String sender = parcelBox.findItem(a).getParamStr(Item.Params.owner);
            Item item = parcelBox.moveItem(a, c, user::getNewId, false, user.getItemBox(), Map.of(Item.Params.user_id, user.getId(), Item.Params.section, s));
            if (item == null) {
                logger.error("cannot get item id %d[%d] from parcel itembox for user id %d (%s)", a, c, user.getId(), user.getLogin());
                user.disconnect();
                return;
            }
            user.addHistory(HistoryCodes.LOG_RECEIVE_ITEMS, item.getLogDescription(), sender);                                              //Почтой получены предметы: {%s} от персонажа \'%s\'
            return;
        }

        user.sendMsg(ptXml());
        return;
    }
}
