package ru.heckzero.server.user;

import io.netty.channel.Channel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.BooleanUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.NativeQuery;
import org.hibernate.type.LongType;
import ru.heckzero.server.Chat;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.items.ItemsDct;
import ru.heckzero.server.utils.History;
import ru.heckzero.server.utils.HistoryCodes;
import ru.heckzero.server.utils.ParamUtils;
import ru.heckzero.server.world.*;

import javax.persistence.*;
import java.text.SimpleDateFormat;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@org.hibernate.annotations.NamedQuery(name = "UserById", query = "select u from User u where id = ?1")
@org.hibernate.annotations.NamedQuery(name = "UserByLogin", query = "select u from User u where lower(u.params.login) = lower(?1)")
@Entity(name = "User")
@Table(name = "users")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class User {
    private static final Logger logger = LogManager.getFormatterLogger();

    public enum ChannelType {NOUSER, GAME, CHAT}                                                                                            //user channel type, which is set on login by onlineGame() and onlineChat() methods
    public enum Params {login, password, serverid, confattack, freeexchange, crypt_pass, email, reg_time, lastlogin, boxp, lastlogout, lastclantime, loc_time, cure_time, god, hint, exp, pro, propwr, rank_points, clan, clan_img, alliance, clr, img, man, HP, psy, str, dex, intu, pow, acc, intel, sk0, sk1, sk2, sk3, sk4, sk5, sk6, sk7, sk8, sk9, sk10, sk11, sk12, X, Y, Z, hz, ROOM, id1, id2, i1, ne, ne2, cup_0, cup_1, cup_2, silv, gold, p78money, acc_flags, siluet, bot, name, city, about, note, list, plist, ODratio, brokenslots, poisoning, virus, ill, illtime, sp_head, sp_left, sp_right, sp_foot, df_eff, eff1, eff2, eff3, eff4, eff5, eff6, eff7, eff8, eff9, eff10, rd, rd1, t1, t2, dismiss, chatblock, forumblock, dl, time, tdt, owner, level, predlevel, nextlevel, maxHP, maxPsy, nochat, kupol, battleid, ft, group, stamina}  //all possible params that can be accessed via get/setParam()
    private static final EnumSet<Params> getmeParams = EnumSet.of(Params.time, Params.tdt, Params.owner, Params.level, Params.predlevel, Params.nextlevel, Params.maxHP, Params.maxPsy, Params.kupol, Params.login, Params.email, Params.loc_time, Params.god, Params.hint, Params.exp, Params.pro, Params.propwr, Params.rank_points, Params.clan, Params.clan_img, Params.clr, Params.img, Params.alliance, Params.man, Params.HP, Params.psy, Params.stamina, Params.str, Params.dex, Params.intu, Params.pow,  Params.acc, Params.intel, Params.sk0, Params.sk1, Params.sk2, Params.sk3, Params.sk4, Params.sk5, Params.sk6, Params.sk7, Params.sk8, Params.sk9, Params.sk10, Params.sk11, Params.sk12, Params.X, Params.Y, Params.Z, Params.hz, Params.ROOM, Params.id1, Params.id2, Params.i1, Params.ne, Params.ne2, Params.cup_0, Params.cup_1, Params.cup_2, Params.silv, Params.gold, Params.p78money, Params.acc_flags, Params.siluet, Params.bot, Params.name, Params.city, Params.about, Params.note, Params.list, Params.plist, Params.ODratio, Params.virus, Params.brokenslots, Params.poisoning, Params.ill, Params.illtime, Params.sp_head, Params.sp_left, Params.sp_right, Params.sp_foot, Params.eff1, Params.eff2, Params.eff3, Params.eff4, Params.eff5, Params.eff6, Params.eff7, Params.eff8, Params.eff9, Params.eff10, Params.rd, Params.rd1, Params.t1, Params.t2, Params.dismiss, Params.chatblock, Params.forumblock);   //params sent in <MYPARAM/>
    private static final EnumSet<Params> getinfoParams = EnumSet.of(Params.login, Params.serverid, Params.nochat, Params.battleid, Params.confattack, Params.ft, Params.pro, Params.propwr, Params.clan, Params.clan_img, Params.rank_points, Params.img, Params.man, Params.HP, Params.psy, Params.str, Params.dex, Params.intu, Params.pow, Params.acc, Params.intel, Params.siluet, Params.name, Params.city, Params.about, Params.brokenslots, Params.poisoning, Params.virus, Params.ill, Params.dismiss, Params.chatblock, Params.forumblock, Params.maxHP, Params.maxPsy, Params.kupol, Params.level);
    private static final int DB_SYNC_INTERVAL = 180;                                                                                        //user database sync interval in seconds
    private static final int TRANSFER_RATE = 5;                                                                                             //transfer rate percentage

    /*class UserMoney {
        private final int transferTax = 5;                                                                                                  //transfer tax percentage
        private final int copper;
        private final double silver;
        private final double gold;

        public UserMoney() {
           this.copper = getParamInt(Params.cup_0);
           this.silver = getParamDouble(Params.silv);
           this.gold = getParamDouble(Params.gold);
        }

        public int getTransferTax() {return transferTax;}
        public int getCopper()      {return copper;}
        public double getSilver()   {return silver;}
        public double getGold()     {return gold;}
    }*/

    public int getMoneyCop() {return getParamInt(Params.cup_0);}
    public double getMoneySilv() {return getParamDouble(Params.silv);}
    public double getMoneyGold() {return getParamDouble(Params.gold);}
    public int getMoneyErgon() {return 0;}

    private static long getId2() {                                                                                                          //compute next id2 value for the user
        try (Session session = ServerMain.sessionFactory.openSession()) {
            NativeQuery<Long> query = session.createSQLQuery("select setval('main_id_seq', nextval('main_id_seq') + 100, false) - 100 as id2").addScalar("id2", LongType.INSTANCE);
            return query.getSingleResult();
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't get id2: %s:%s", e.getClass().getSimpleName(), e.getMessage());
        }
        return -1;
    }
    @Transient volatile private Channel gameChannel = null;                                                                                 //user game channel
    @Transient volatile private Channel chatChannel = null;                                                                                 //user chat channel
    @Transient private final AtomicBoolean needSync = new AtomicBoolean(false);                                                             //user need to be synced if some params have been modified
    @Transient private final Chat chat = new Chat(this);
    @Transient private long lastsynctime;
    @Transient private long lastSentId2 = -1;                                                                                               //last id2 value sent to user by com_MYPARAM() or com_NEWID()
    @Transient private ItemBox itemBox = null;                                                                                              //users item box will be initialized upon a first access

    @Transient private Building currBld = null;                                                                                             //current user building

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_generator_sequence")
    @SequenceGenerator(name = "user_generator_sequence", sequenceName = "users_id_seq", allocationSize = 1)
    private Integer id;

    @Embedded
    private final UserParams params = new UserParams();                                                                                     //user params that can be set (read-write) are placed there

    public User() { }

    public boolean isEmpty() {return id == null;}                                                                                           //user is a stub with empty params
    public boolean isOnlineGame() {return gameChannel != null;}                                                                             //this user has a game channel assigned
    public boolean isOnlineChat() {return chatChannel != null;}                                                                             //this user has a chat channel assigned
    public boolean isInBattle()   {return getParamInt(Params.battleid) > 0;}                                                                //is user in a battle
    public boolean isInGame()  {return isOnlineGame() || isInBattle();}                                                                     //user is treated as in game when he is online or is in a battle
    public boolean isInClaim() {return false;}                                                                                              //user is in battle (arena) claim (waiting for a battle to begin)
    public boolean isBot() {return !getParamStr(Params.bot).isEmpty();}                                                                     //user is a bot (not a human)
    public boolean isGod() {return getParamInt(Params.god) == 1;}                                                                           //this is a privileged user (admin)
    public boolean isCop() {return getParamStr(Params.clan).equals("police");}                                                              //user is a cop (is a member of police clan)
    public boolean isInVault() {return false;}

    public Integer getId() {return id;}
    public Channel getGameChannel() {return this.gameChannel;}
    public Channel getChatChannel() {return this.chatChannel;}
    public String getLogin() {return getParamStr(Params.login);}                                                                            //just a shortcut
    private String getParam_battleid() {return StringUtils.EMPTY;}
    private String getParam_group() {return StringUtils.EMPTY;}

    private long getParam_time() {return Instant.now().getEpochSecond();}                                                                   //always return epoch time is seconds
    private int getParam_tdt() {return Calendar.getInstance().getTimeZone().getRawOffset() / 1000 / 3600;}                                  //server timezone offset in hours (must be user timezone?)
    private int getParam_level() {return UserLevelData.getLevel(this);}                                                                     //compute the user level by its experience value
    private int getParam_predlevel() {return UserLevelData.getExpLastLvl(this);}                                                            //get the experience value of current level beginning
    private int getParam_nextlevel() {return UserLevelData.getExpNextLvl(this);}                                                            //get the experience value of current level end
    private int getParam_maxHP() {return UserLevelData.getMaxHP(this);}
    private int getParam_maxPsy() {return UserLevelData.getMaxPsy(this);}
    private int getParam_nochat() {return isOnlineGame() && !isOnlineChat() ? 1 : 0;}                                                       //user chat status, whether he has his chat channel off (null)
    private int getParam_kupol() {return getLocation().getParamInt(Location.Params.b) ^ 1;}                                                 //is a user under the kupol - his current location doesn't allow battling
    private int getParam_owner() {return getParamInt(Params.Z) == 0 ? 0 : BooleanUtils.toInteger(isBuildMaster(getBuilding()));}            //is a user under the kupol - his current location doesn't allow battling
    private int getParam_confattack() {return isOnlineGame() && !isInBattle() ? 1 : 0; }                                                    //if user is available for duel
    private int getParam_freechallenge() {return isOnlineGame() && getBuilding().getParamInt(Building.Params.name) == 1 ? 1 : 0; }          //if user is available for arena battle // challenge_t=10       -- таймаут арены (сек) // challenge_m=10       -- ставка (если бой на деньги) медных монет // challenge_a='В?[0-3]' -- типа сражения: B-кровавый бой, 0-на ножах, 1-легкое оружие, 2-среднее, 3-тяжёлое
    private int getParam_boxp() {return (int)((double)getMass().get("tk") * 100 / getMass().get("max"));}                                   //get user box load (percentage)
    private int getParam_freeexchange() {return 1; }                                                                                        //if user is available for items exchanging

    private int getMaxLoad() {                                                                                                              //user max load capacity
        int str = getParamInt(Params.str);
        double profRate = switch (getParamInt(Params.pro)) {                                                                                //rate limit depends on prof
            case 3 -> 1.2;                                                                                                                  //miner
            case 2 -> 0.7;                                                                                                                  //stalker
            case 6 -> 1.8;                                                                                                                  //merchant
            default -> 1;                                                                                                                   //bum
        };
        return (int)(str * 500 * profRate);
    }
    public String getParamStr(Params param) {return ParamUtils.getParamStr(this, param.toString());}                                        //get user param value as different type
    public int getParamInt(Params param) {return ParamUtils.getParamInt(this, param.toString());}                                           //get user param value as different type
    public long getParamLong(Params param) {return ParamUtils.getParamLong(this, param.toString());}                                        //get user param value as different type
    public double getParamDouble(Params param) {return ParamUtils.getParamDouble(this, param.toString());}                                  //get user param value as different type

    private String getParamXml(Params param, boolean appendEmpty) {return getParamStr(param).transform(s -> (!s.isEmpty() || appendEmpty) ? String.format("%s=\"%s\"", param == Params.intu ? "int" : param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    private String getParamsXml(EnumSet<Params> params, boolean appendEmpty) {return params.stream().map(p -> getParamXml(p, appendEmpty)).filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));}

    public Location getLocation() {return Location.getLocation(getParamInt(Params.X), getParamInt(Params.Y));}                              //get the location the user is now at
    public Location getLocation(int btnNum) {return Location.getLocation(getParamInt(Params.X), getParamInt(Params.Y), btnNum);}            //get the location for minimap button number
    public Building getBuilding() {return getLocation().getBuilding(getParamInt(Params.Z));}                                                //get the building the user is now in
//    public UserMoney getMoney() {return new UserMoney();}

    public long getNewId() {                                                                                                                //get a new id for an item
        long id1 = getParamLong(Params.id1);                                                                                                //get current user id1, id2, i1
        long id2 = getParamLong(Params.id2);
        long i1 = getParamLong(Params.i1);

        long newId = id1 + i1;                                                                                                              //compute a new id

        if (++i1 == 100) {                                                                                                                  //increment i1 and if it becomes 100, get a new id2
            logger.info("i1 has become 100 for user %s, will set a new id2", getLogin());
            i1 = 0;
            long newId2 = getId2();                                                                                                         //get a new id2 from database
            if (newId2 == -1) {
                disconnect();
                return -1;
            }
            setParam(Params.id1, id2);
            setParam(Params.id2, newId2);
        }
        setParam(Params.i1, i1);
        logger.info("computed NEW ID: %d for user %s (id1 = %d, id2 = %d, i1 = %d)", newId, getLogin(), getParamLong(Params.id1), getParamLong(Params.id2), getParamInt(Params.i1));
        return newId;
    }

    public void setParam(Params paramName, Object paramValue) {                                                                             //set a user param
        if (ParamUtils.setParam(params, paramName.toString(), paramValue))                                                                  //delegate param setting to ParamUtils class
            needSync.compareAndSet(false, true);                                                                                            //set needSync to true to get this user to be synced on a nex sync() call
        if (!isInGame())                                                                                                                    //sync the user immediately if he is offline and  not in a battle
            sync();
        return;
    }

    public ItemBox getItemBox() {return itemBox != null ? itemBox : (itemBox = ItemBox.init(ItemBox.BoxType.USER, id));}                    //return user itembox, init it if necessary
    public Map<String, Integer> getMass() {return Map.of("tk", getItemBox().getMass(), "max", getMaxLoad());}                               //get user weight tk - current, max - maximum, considering user profession influence
    public Item getPassport() {return getItemBox().findItemByType(ItemsDct.BASE_TYPE_PASS); }

    synchronized void onlineGame(Channel ch) {                                                                                              //the user game channel connected
        logger.debug("setting user '%s' game channel online", getLogin());
        this.gameChannel = ch;                                                                                                              //set user game channel
        this.gameChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.GAME);                                                        //set the user channel type to GAME
        this.gameChannel.attr(AttributeKey.valueOf("chStr")).set("user '" + getLogin() + "'");                                              //replace a channel representation string to 'user <login>' instead of IP:port
        this.gameChannel.pipeline().replace("socketIdleHandler", "userIdleHandler", new ReadTimeoutHandler(ServerMain.hzConfiguration.getInt("ServerSetup.MaxUserIdleTime", ServerMain.DEF_MAX_USER_IDLE_TIME))); //replace read timeout handler to a new one with a longer timeout defined for authorized user
        setParam(Params.lastlogin, Instant.now().getEpochSecond());                                                                         //set user last login time, needed to compute loc_time
        this.lastsynctime = Instant.now().getEpochSecond();                                                                                 //set last db sync time to now
        setParam(Params.loc_time, Math.min(Instant.now().getEpochSecond() + 12, getParamLong(Params.loc_time) != 0L ? getParamLong(Params.loc_time) + getParamLong(Params.lastlogin) - getParamLong(Params.lastlogout) : getParamLong(Params.reg_time))); //compute client loc_time - time when user is allowed to leave his current location
        String resultMsg = String.format("<OK l=\"%s\" ses=\"%s\"/>", getLogin(), ch.attr(AttributeKey.valueOf("encKey")).get());           //<OK/> message with a chat auth key in ses attribute (using already existing key)
        sendMsg(resultMsg);                                                                                                                 //send login <OK/> message to the user
        addHistory(HistoryCodes.LOG_LOGIN, (String)ch.attr(AttributeKey.valueOf("sockStr")).get());                                         //Вход в игру с IP=%s
        List<String> failedLoginsIP = History.checkAccountAttack(this.id);
        failedLoginsIP.forEach(ip -> History.addIms(this.id, HistoryCodes.LOG_WRONG_MANY_TIMES, ip));                                       //C IP адреса {%s} был более 5 раз введен неправильный пароль к вашему персонажу
        addHistory(HistoryCodes.LOG_BALANCE_INFO_2, String.valueOf(getMoneyCop()), String.valueOf(getMoneySilv()), String.valueOf(getMoneyGold()), String.valueOf(getMoneyErgon()));     // "На счету %s медных монет, %s серебряных, %s золотых и %s эргона.",
        chat.updateMyStatus();                                                                                                              //add user to his current room, so others will be able to see him
        return;
    }

    synchronized void offlineGame() {                                                                                                       //the user game channel disconnected
        logger.debug("setting user '%s' game channel offline", getLogin());
        setParam(Params.lastlogout, Instant.now().getEpochSecond());                                                                        //set lastlogout to now
        this.gameChannel = null;                                                                                                            //a marker that user is offline now
        disconnectChat();                                                                                                                   //chat without a game channel is ridiculous, so shut the chat down
        chat.updateMyStatus();                                                                                                              //will remove user from room
        addHistory(HistoryCodes.LOG_LOGOUT);                                                                                                //Выход из игры
        addHistory(HistoryCodes.LOG_BALANCE_INFO_2, String.valueOf(getMoneyCop()), String.valueOf(getMoneySilv()), String.valueOf(getMoneyGold()), String.valueOf(getMoneyErgon()));     // "На счету %s медных монет, %s серебряных, %s золотых и %s эргона.",
        sync();                                                                                                                             //update the user in database
        notifyAll();                                                                                                                        //awake all threads waiting for the user to get offline
        logger.info("user '%s' game channel logged out", getLogin());
        return;
    }

    synchronized void onlineChat(Channel ch) {
        logger.debug("turning user '%s' chat on", getLogin());
        this.chatChannel = ch;
        this.chatChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.CHAT);
        this.chatChannel.attr(AttributeKey.valueOf("chStr")).set("user '" + getLogin() + "' (chat)");
        this.chatChannel.pipeline().replace("socketIdleHandler", "userIdleHandler", new ReadTimeoutHandler(ServerMain.hzConfiguration.getInt("ServerSetup.MaxUserIdleTime", ServerMain.DEF_MAX_USER_IDLE_TIME)));
        chat.start();
        return;
    }
    synchronized void offlineChat() {                                                                                                       //user chat channel has been disconnected
        logger.debug("turning user '%s' chat off", getLogin());
        this.chatChannel = null;
        chat.updateMyStatus();
        notifyAll();
        logger.info("user '%s' chat channel logged out", getLogin());
        return;
    }

    public void com_DROP(long id, int count) {                                                                                              //user drops an item from its box
        Item dropped = getItemBox().getSplitItem(id, count, false, this::getNewId);
        if (dropped != null)
            addHistory(HistoryCodes.LOG_DROP_ITEMS_IN_LOCATION, dropped.getLogDescription(), getLocation().getLogDescription());
        return;
    }

    public void com_TO_SECTION(long id, String section) {                                                                                   //change item section in user box
        if (getItemBox().changeOne(id, Item.Params.section, section) == null)
            disconnect();
        return;
    }

    public void com_MYPARAM() {                                                                                                             //provision the client initial params as a reply for <GETME/>
        logger.info("processing <GETME/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());

        StringJoiner sj = new StringJoiner("", "<MYPARAM ", "</MYPARAM>");
        sj.add(getParamsXml(getmeParams, false)).add(">");
        sj.add(getItemBox().getXml());

        lastSentId2 = getParamLong(Params.id2);
        sendMsg(sj.toString());
        checkIMS();
        return;
    }

    public void com_GETINFO(String login, int details) {                                                                                    //get and send information about a user
        logger.info("processing <GETINFO/> from %s", getLogin());
        User user = login == null ? this : UserManager.getUser(login);                                                                      //login might be null when user clicks on his own avatar
        if (user.isEmpty()) {                                                                                                               //user is not found
            sendMsg(String.format("<NOUSER login=\"%s\"/>", login));
            return;
        }

        StringJoiner sj = new StringJoiner(" ", "<USERPARAM ", "</USERPARAM>");
        if (details != -1) sj.add("details=\"1\"");
        sj.add(user.getParamsXml(getinfoParams, false));                                                                                    //send a list of getinfo params

        if (user.isOnlineGame()) {                                                                                                          //user is online
            sj.add(String.format("X=\"%d\"", getParamInt(Params.X)));                                                                       //"X" is always sent if user is online
            if (user.getParamInt(Params.kupol) > 0)                                                                                         //"Y" is sent only if user is in city
                sj.add(String.format("Y=\"%d\"", getParamInt(Params.Y)));
            sj.add("freeattack=\"1\"");                                                                                                     //напасть на этого персонажа
            sj.add(String.format("boxp=\"%d\"", getParamInt(Params.boxp)));                                                                 //user box load in percentage (for corsairs)
        }
        sj.add(">");

        Predicate<Item> dressed = i -> !i.getParamStr(Item.Params.slot).isEmpty();                                                          //dressed items
        Predicate<Item> other = i -> i.getBaseType() == 802;                                                                                //other items we have to send in GETINFO
        Predicate<Item> getInfoItems = dressed.or(other);

        ItemBox dressedItems = user.getItemBox().findItems(getInfoItems);                                                                   //get user items by the predicate getInfoItems
        sj.add(dressedItems.getXml());
        sendMsg(sj.toString());
        return;
    }

    public void com_GOLOC(String n, String d, String slow, String force, String pay, String t1, String t2) {
        logger.debug("processing <GOLOC/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());

        int btnNum = NumberUtils.toInt(n, 5);                                                                                               //button number user has pressed on minimap (null -> 5 - means there was no movement made and this is just a locations request)
        if (btnNum < 1 || btnNum > 9) {                                                                                                     //bnt num must within 1-9
            logger.warn("user %s tried to move to illegal location (btn num: %d)", getLogin(), btnNum);
            disconnect();                                                                                                                   //TODO should we ban the motherfecker cheater
            return;
        }
        Location locationToGo = getLocation(btnNum);                                                                                        //get the location data user wants move to or the current user location if there was no movement requested
        StringJoiner sj = new StringJoiner("", "<GOLOC", "</GOLOC>");                                                                       //start formatting a <GOLOC> reply
        if (btnNum != 5) {                                                                                                                  //user moves to another location
            if (locationToGo.getLocBtnNum(this) != btnNum) {                                                                                //getLocBtnNum() must return the same btnNum as user has tapped
                logger.warn("user %s tried to move to inapplicable location from %d/%d to %d/%d", getLogin(), getParamInt(Params.X), getParamInt(Params.Y), locationToGo.getParamInt(Location.Params.X), locationToGo.getParamInt(Location.Params.Y));
                sendMsg("<ERRGO />");                                                                                                       //Вы не смогли перейти в этом направлении
                return;
            }
            if (getParamLong(Params.loc_time) > Instant.now().getEpochSecond() + 1 && !isGod()) {                                           //TODO why do we have to add 1 to now()?
                logger.warn("user %s tried to move at loc_time > now() (%d > %d) Check it out!", getLogin(), getParamLong(Params.loc_time), Instant.now().getEpochSecond());
                sendMsg(String.format("<MYPARAM time=\"%d\"/><ERRGO code=\"5\"/>", Instant.now().getEpochSecond()));                        //Вы пока не можете двигаться, отдохните
                return;
            }
            if (locationToGo.getParamInt(Location.Params.o) >= 999 && !isGod()) {                                                           //an impassable location, trespassing is not allowed
                sendMsg("<ERRGO code=\"1\"/>");                                                                                             //Локация, в которую вы пытаетесь перейти, непроходима
                return;
            }

            sj.add(String.format(" n=\"%d\"", btnNum));                                                                                     //add n="shift" if we have moved to some location

            Long locTime = Instant.now().getEpochSecond() + Math.max(locationToGo.getParamInt(Location.Params.tm), 5);                      //compute a new loc_time for user(now + the location loc_time (location tm parameter))
            setParam(Params.loc_time, locTime);                                                                                             //set the new loc_time for a user
            setLocation(locationToGo.getParamInt(Location.Params.X), locationToGo.getParamInt(Location.Params.Y));                          //actually change user coordinates to new location
            String reply = String.format("<MYPARAM loc_time=\"%d\" kupol=\"%d\"/>", locTime, getParamInt(Params.kupol));
            sendMsg(reply);
        }
        sj.add(locationToGo.getParamStr(Location.Params.monsters).transform(s -> s.isEmpty() ? ">" : String.format(" m=\"%s\">", s)));      //add m (monster) to <GOLOC> from the current location

        if (d != null) {                                                                                                                    //user requests nearest location description
            List<Location> locations = Arrays.stream(d.split("")).mapToInt(NumberUtils::toInt).mapToObj(btn -> btn == 5 ? locationToGo : getLocation(btn)).filter(l -> l.getLocBtnNum(this) != ArrayUtils.INDEX_NOT_FOUND).collect(Collectors.toList()); //get the list if requested location (for each number in "d")
            locations.forEach(l -> sj.add(l.getXml()));
        }
        sendMsg(sj.toString());                                                                                                             //send a <GOLOC/> reply
        return;
    }

    public void com_MMP(String param) {
        StringJoiner sj = new StringJoiner("", "<MMP>", "</MMP>");                                                                          //MMP - Big map (5x5) request
        List<Location> locations = Arrays.stream(param.split(",")).mapToInt(NumberUtils::toInt).mapToObj(this::getLocation).filter(l -> l.getLocBtnNum(this) != ArrayUtils.INDEX_NOT_FOUND).collect(Collectors.toList()); //get the list if requested location (for each number in "param")
        locations.forEach(l -> sj.add(l.getXml()));
        sendMsg(sj.toString());
        return;
    }

    public void com_BIGMAP() {                                                                                                              //The world map - cities portals and routes
        StringJoiner sj = new StringJoiner("", "<BIGMAP>", "</BIGMAP>");
        List<Portal> portals = Portal.getBigmapPortals();                                                                                   //get all portal with their routes and locations
        portals.forEach(p -> sj.add(p.bigMapXml()));
        sendMsg(sj.toString());
        return;
    }

    public void com_GOBLD(int n) {                                                                                                          //user entering or exiting a building
        logger.info("processing <GOBLD/> from %s", getLogin());
        if (n == 0) {                                                                                                                       //user comes out of building
            setRoom();                                                                                                                      //set Z, hz and ROOM params to 0
            sendMsg("<OKGO/>");                                                                                                             //allow the user to go out
            currBld = null;
            return;
        }
        Building bld = getLocation().getBuilding(n);                                                                                        //get the building info
        if (bld.isEmpty()) {                                                                                                                //suck building does not exist
            sendMsg("<ERRGO code=\"20\"/>");
            return;
        }

        String bldClan = bld.getParamStr(Building.Params.clan);                                                                             //clan name this building belongs to
        if (!bldClan.isBlank() && !getParamStr(Params.clan).equals(bldClan)) {                                                              //TODO check if user has a temporary building pass
            sendMsg("<ERRGO code=\"20\"/>");                                                                                                //user can't go to a private building
            return;
        }

        int bldType = bld.getParamInt(Building.Params.name);                                                                                //building type
        setBuilding(n, bldType);                                                                                                            //place the user inside the building (set proper params)
        String resultXML = String.format("<GOBLD n=\"%s\" hz=\"%d\" owner=\"%d\"/>", n, bldType, getParamInt(Params.owner));
        sendMsg(resultXML);
        return;
    }

    public void com_PR(int comein, int id, double new_cost, int to, long d, long a, int s, int c, int get, int ds) {                        //portal workflow
        Portal portal = currBld instanceof Portal && Objects.equals(currBld.getId(), getBuilding().getId()) ? (Portal)currBld : (Portal)(currBld = Portal.getPortal(getBuilding().getId()));          //init a currBld if necessary
        portal.processCmd(comein, id, new_cost, to, d, a, s, c, get, ds, this);
        return;
    }

    public void com_AR(long a, long d, int s, int c) {                                                                                      //arsenal workflow
        Arsenal arsenal = currBld instanceof Arsenal ? (Arsenal)currBld : (Arsenal)(currBld = Arsenal.getArsenal(getBuilding().getId()));
        if (arsenal != null)
            arsenal.processCmd(a, d, s, c, this);
        return;
    }

    public void com_BK(int put, int get, int cost, int cost2, int buy, String p, String newpsw, String newemail, int go, int sell, long d, int s, int c, long f, long a, int newkey, int addsection, int extend, int check_sell, int tr, int sell2) {    //bank workflow
        Bank bank = currBld instanceof Bank ? (Bank)currBld : (Bank) (currBld = Bank.getBank(getBuilding().getId()));
        bank.processCmd(this, put, get,cost, cost2, buy, p, newpsw, newemail, go, sell, d, s, c, f, a, newkey, addsection, extend, check_sell, tr, sell2);
        return;
    }

    public void com_PT(int get, int me, int p1, int p2, int d1, long a, int c, int s, String login, String wire, String parcel, String itm, int fast) {                     //PT workflow
        PostOffice postOffice = currBld instanceof PostOffice ? (PostOffice) currBld : (PostOffice) (currBld = PostOffice.getPostOffice(getBuilding().getId()));
        postOffice.processCmd(this, get, me, p1, p2, d1, a, c, s, login, wire, parcel, itm, fast);
        return;
    }

    public void com_HISTORY(String login, String date, String dx, String b) {                                                               //log request
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy"), timeFormat = new SimpleDateFormat("HH:mm");
        Calendar cReq = Calendar.getInstance();
        cReq.set(2000 + Integer.parseInt(date.split("\\.")[2]), Integer.parseInt(date.split("\\.")[1]) - 1, Integer.parseInt(date.split("\\.")[0]), 0, 0, 0);

        History.Subject subject = (login == null) ? History.Subject.USER : (NumberUtils.isDigits(login) ? History.Subject.CELL : (login.equals("$building") ? History.Subject.BUILDING : History.Subject.CLAN)); //what is this request for: User, building, cell, clan.
        int subjectId = switch (subject) {                                                                                                  //determine subject id based on subject
            case USER -> {User u = login == null ? this : UserManager.getUser(login); yield !u.isEmpty() ? u.getId() : -1;}                 //user id (self or requested)
            case BUILDING -> getBuilding().getId();                                                                                         //building id
            case CELL, CLAN -> NumberUtils.toInt(login);                                                                                    //cell or clan id
        };

        List<History> historyLogs = History.getHistory(subject, subjectId, date, dx);                                                       //this will query the database
        if (historyLogs.isEmpty() && dx != null)                                                                                            //no records for the previous or next date
            com_HISTORY(login, date, null, b);                                                                                              //return records for the given date without any shifting
        if (!historyLogs.isEmpty())                                                                                                         //there are records in a result set
            cReq.setTimeInMillis(historyLogs.get(0).getDt() * 1000);                                                                        //setting the returning date to the date of the first history record

        StringJoiner sj = new StringJoiner("\n", String.format("<HISTORY date=\"%s\">", dateFormat.format(cReq.getTime())), "</HISTORY>");
        historyLogs.forEach(hl -> sj.add(String.format("%s %s\t%d\t%s\t%s\t%s\t%s\t%s", dateFormat.format(new Date(hl.getDt() * 1000)), timeFormat.format(new Date(hl.getDt() * 1000)), hl.getCode(), hl.getParam1(), hl.getParam2(), hl.getParam3(), hl.getParam4(), hl.getParam5())));
        sendMsg(sj.toString());
        return;
    }

    public void com_TAKE_ON(String id, String slot) {                                                                                       //user takes on an item on a specified slot
        Item item = getItemBox().findItem(NumberUtils.toLong(id));
        if (item == null) {
            logger.error("can't find an item id %s to perform TAKE_ON command from user %s", id, getLogin());
            disconnect();
            return;
        }
        String currentSlot = item.getParamStr(Item.Params.slot);
        if (currentSlot.equals(slot)) {
            logger.warn("can't take on an item %s to slot %s for user %s, item is already on this slot", id, slot, getLogin());
            disconnect();
            return;
        }
        String[] st = item.getParamStr(Item.Params.st).split(",");
        if (!ArrayUtils.contains(st, slot)) {
            logger.warn("can't take on an item %s to slot %s for user %s, the slot is not in an allowed list: %s", id, slot, getLogin(), st);
            disconnect();
            return;
        }
        upgradeUserParams(item, true);                                                                                                      //user takes on the item, upgrade his params
        getItemBox().changeOne(item.getId(), Item.Params.slot, slot);
        return;
    }

    public void com_TAKE_OFF(String id) {                                                                                                   //user takes on an item on a specified slot
        Item item = getItemBox().findItem(NumberUtils.toLong(id));
        if (item == null) {
            logger.error("can't find an item id %s to perform TAKE_OFF command from user %s", id, getLogin());
            disconnect();
            return;
        }

        upgradeUserParams(item, false);
        getItemBox().changeOne(item.getId(), Item.Params.slot, "");
        return;
    }

    private void upgradeUserParams(Item item, boolean isEquipping) {                                                                        //isEquipping - take on or take off the item
        String[] bonusStats = item.getParamStr(Item.Params.up).split(",");                                                                  //all item influence (Item.Params.up)

        for (String bonusStat : bonusStats) {                                                                                               //траверс по параметрам
            Params stat = switch (bonusStat.split("=")[0]) {                                                                                //get each stat this items influence to
                case "HP"   -> Params.HP;
                case "psy"  -> Params.psy;
                case "str"  -> Params.str;
                case "dex"  -> Params.dex;
                case "int"  -> Params.intel;
                case "pow"  -> Params.pow;
                case "acc"  -> Params.acc;
                case "intu" -> Params.intu;
                default -> null;
            };

            if (stat == null)                                                                                                               //current stat is not valid
                continue;

            int influence = Integer.parseInt(bonusStat.split("=")[1]);                                                                      //get the stat influence value
            setParam(stat, getParamInt(stat) + (isEquipping ? influence : influence * -1));                                                 //update the corresponding user stat param
        }
        return;
    }

    public void com_TRANSFER(String login, String msg, int t, int c) {                                                                      //transfer money between users
        if (login.isEmpty() || msg.isEmpty() || t == -1 || c == -1) {                                                                       //if first time opened
            sendMsg(String.format("<TRANSFER p=\"%d\"/>", TRANSFER_RATE));
            return;
        }

        User receiver = UserManager.getUser(login);
        if (receiver == null || receiver.isEmpty()) {                                                                                       //if target user not found
            sendMsg("<TRANSFER err=\"3\"/>");                                                                                               //Такого персонажа не существует
            return;
        }
        if (receiver.getLogin().equals(getLogin())) {                                                                                       //if sending money to myself
            sendMsg("<TRANSFER err=\"2\"/>");                                                                                               //Операция недоступна
            return;
        }
        if (receiver.isInVault()) {                                                                                                         //if receiver is in Vault city
            sendMsg("<TRANSFER err=\"4\"/>");                                                                                               //Переводы персонажам в закрытых городах запрещены",
            return;
        }
                                                                                                                                            //TODO implement dedicated method for calculating distance between two players
        int dstX = getParamInt(Params.X) - receiver.getParamInt(Params.X);                                                                  //distance between sender and receiver X-axis
        int dstY = getParamInt(Params.Y) - receiver.getParamInt(Params.Y);                                                                  //distance between sender and receiver Y-axis
        if (dstX * dstX > 225 || dstY * dstY > 225) {                                                                                       //if sender is 15-cells far from receiver
            sendMsg("<TRANSFER err=\"5\"/>");                                                                                               //Персонаж находится слишком далеко
            return;
        }

        int tax = (int) (Math.ceil(c * (TRANSFER_RATE / 100f)));                                                                            //transfer tax
        int withdrawSum = c + tax;                                                                                                          //transfer amount with taxes

        if (getMoneyCop() < withdrawSum) {                                                                                                  //if sender is money-cheating
            sendMsg("<TRANSFER err=\"1\"/>");                                                                                               //Недостаточно денег
            return;
        }
        decMoney(withdrawSum);                                                                                                              //decrease money from sender
        addHistory(HistoryCodes.LOG_MONEY_TRANSFER_TO, String.valueOf(ItemsDct.MONEY_COPP), String.valueOf(c), receiver.getLogin(), String.valueOf(tax), String.valueOf(getMoneyCop()));       //Перевёл {%s[%s]} персонажу '%s'. Стоимость отправки %s мнт. Осталось %s мнт. %s

        receiver.addMoney(c);                                                                                                               //add money to receiver
        receiver.addHistory(HistoryCodes.LOG_MONEY_TRANSFER_FROM, String.valueOf(ItemsDct.MONEY_COPP), String.valueOf(c), getLogin(), String.valueOf(receiver.getMoneyCop()));//Получено {%s[%s]} от персонажа \'%s\'. Всего на счету: %s мнт. %s",
        sendMsg("<TRANSFER />");
        return;
    }


    public void com_SILUET(String slt, String set) {                                                                                        //set user body type
        logger.debug("processing <SILUET/> from %s", getLogin());
        setParam(Params.siluet, set);
        String response = String.format("<SILUET code=\"0\"/><MYPARAM siluet=\"%s\"/>", set);
        sendMsg(response);
        return;
    }

    public void com_POST(String t) {                                                                                                        //chat POST - the message from user
        if (StringUtils.isNotBlank(t))
            chat.post(t);
        else
            logger.warn("invalid <POST/> command received from user %s, t = %s, got nothing to process", getLogin(), t);
        return;
    }

    public void com_NEWID() {                                                                                                               //client has just created a new ID for an item
        long userId2 = getParamLong(Params.id2);                                                                                            //last id2 value was sent to user
        if (lastSentId2 != userId2) {                                                                                                       //id2 has been changed recently
            sendMsg(String.format("<ID2 id=\"%d\"/>", lastSentId2 = userId2));                                                              //send a new id2 value to user
        }
        return;
    }

    public void com_N(String id1, String id2, String i1) {                                                                                  //compare items id counters between server and a client
        String s_id1 = getParamStr(Params.id1);                                                                                             //s_idx -> server side id values
        String s_id2 = getParamStr(Params.id2);
        String s_i1 = getParamStr(Params.i1);

        if ((id1 != null && !id1.equals(s_id1)) || (id2 != null && !id2.equals(s_id2)) || (i1 != null && !i1.equals(s_i1))) {               //compare id values if they are not null
            logger.error("%s !!!!!!!!MISTIMING!!!!!!!! id1 = %s s_id1 = %s, id2 = %s s_id2 = %s, i1 = %s s_i1 = %s", getLogin(), id1, s_id1, id2, s_id2, i1, s_i1);
            disconnect();
        }
        if (Instant.now().getEpochSecond() - lastsynctime > DB_SYNC_INTERVAL)                                                               //user db sync check interval
            sync();                                                                                                                         //sync the user with db
        if (Instant.now().getEpochSecond() - lastsynctime > DB_SYNC_INTERVAL * 2)                                                           //user items expiration check interval
            com_CHECK();                                                                                                                    //check and delete user's expired items

        return;
    }


    public boolean isBuildMaster() {return isBuildMaster(getBuilding());}
    public boolean isBuildMaster(Building bld) {return isBuildMaster(bld.getX(), bld.getY(), bld.getZ());}
    private boolean isBuildMaster(int x, int y, int z) {                                                                                    //check if user has a master key to the building with given coordinates
        String keyName = String.format("$key%d_%d_%d", x, y, z);                                                                            //key name is composed of building coordinates
        Predicate<Item> predicate = (i -> !i.isExpired() && i.isBldKey() && (i.getParamStr(Item.Params.made).equals(keyName) || i.getParamStr(Item.Params.made).equals("$key_all")));
        return !getItemBox().findItems(predicate).isEmpty() || isGod();
    }

    public void addSendItems(ItemBox box) {box.forEach(this::addSendItem);}                                                                 //add and send all items from box to the user's Item Box
    public void addSendItem(Item item) {
        if (getItemBox().addItem(item) == null)
            logger.error("can't add item %s", item);
        sendMsg(String.format("<ADD_ONE>%s</ADD_ONE>", item.getXml()));
        return;
    }

    public void setRoom() {setRoom(-1, -1, 0, 0, 0);}                                                                                       //coming out
    public void setLocation(int X, int Y) {setRoom(X, Y, -1, -1, -1);}                                                                      //only change a location
    public void setBuilding(int Z, int hz) {setRoom(-1, -1, Z, hz, 0);}                                                                     //enter to building
    public void setRoom(int ROOM) {setRoom(-1, -1, -1, -1, ROOM);}                                                                          //set room inside the building
    public void setRoom(int X, int Y, int Z, int hz, int ROOM) {                                                                            //actually change user coordinates and other parameters
        chat.removeMe();                                                                                                                    //remove user from chat room

        if (X >= 0)
            setParam(Params.X, X);
        if (Y >= 0)
            setParam(Params.Y, Y);
        if (Z >= 0)
            setParam(Params.Z, Z);
        if (hz >= 0)
            setParam(Params.hz, hz);
        if (ROOM >= 0)
            setParam(Params.ROOM, ROOM);

        chat.updateMyStatus();
        chat.showMeRoom();
        return;
    }

    public void com_CHECK() {
        logger.debug("checking for the expired items for user %s", getLogin());
        ItemBox expired = getItemBox().findItems(Item::isExpired);                                                                          //all user expired items are here at a 1st level
        if (expired.isEmpty())
            return;

        logger.info("found %d expired items for user %s, %s", expired.size(), getLogin(), expired);
        expired.forEach(item -> {
            logger.info("deleting expired item %s for user %s", item, getLogin());

            getItemBox().delItem(item.getId());
            sendMsg(String.format("<DEL_ONE id=\"%d\"/>", item.getId()));

            ItemBox included = item.getIncluded();
            if (!included.isEmpty()) {
                logger.info("item %d contains %d included items: %s, unloading and adding them to user %s", item.getId(), included.size(), included.itemsIds(), getLogin());
                included.forEach(i -> i.resetParam(Item.Params.pid));
                included.forEach(i -> i.setParams(Map.of(Item.Params.user_id, id, Item.Params.section,0)));
                addSendItems(included);                                                                                                     //add all included items to the user as a 1st level items
            }
        });
        return;
    }

    public void decMoney(double amount) {decMoney(ItemsDct.MONEY_COPP, amount);}
    public void decMoney(int type, double amount) {addMoney(type, amount * -1);}                                                            //decrease user money
    public void addMoney(int amount) {addMoney(ItemsDct.MONEY_COPP, amount);}
    synchronized public void addMoney(int type, double amount) {                                                                            //add money to user
        if (amount == 0)                                                                                                                    //nothing to do
            return;
        Params moneyParam = switch (type) {                                                                                                 //money type copper, silver, gold
            default -> Params.cup_0;
            case ItemsDct.MONEY_SILV -> Params.silv;
            case ItemsDct.MONEY_GOLD -> Params.gold;
        };
        double money = getParamDouble(moneyParam);                                                                                          //current user money
        money += amount;                                                                                                                    //new money value
        if (money < 0) {
            logger.warn("can't adjust user %s money by %f, because it become negative", getLogin(), amount);
            return;
        }
        setParam(moneyParam, money);                                                                                                        //set new money value
        if (amount < 0)
            sendMsg(String.format("<DM c=\"%.2f\" m=\"%d\" />", amount * -1, type));
        else
            sendMsg(String.format("<MYPARAM %s=\"%s\"/>", moneyParam.name(), getParamStr(moneyParam)));
        return;
    }

    public void sync() {sync(false);}
    public void sync(boolean force) {                                                                                                       //update the user in database
        if (needSync.compareAndSet(true, false) || force) {                                                                                 //sync if one of needSync or force = true
            logger.info("syncing user %s", getLogin());
            ServerMain.sync(this);
            lastsynctime = Instant.now().getEpochSecond();                                                                                  //reset lastsynctime(set it to now)
        } else
            logger.info("skipping syncing user %s cause he hasn't been changed (AFK?)", getLogin());
        return;
    }

    public void addHistory(int code, String ... params) {
        History.add(getId(), History.Subject.USER, code, params);                                                                           //add a record to the history log
        return;
    }

    public void com_CLIMS() {                                                                                                               //clear IMS - set ims flag to 0 for user history records
        History.clIMS(getId());
        return;
    }
    public void checkIMS() {                                                                                                                //send all unread IMS to the user
        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy"), timeFormat = new SimpleDateFormat("HH:mm");
        List<History> historyLogs = History.getIMS(getId());                                                                                //get all IMS messages for the user (history records having ims=1)
        if (historyLogs.isEmpty())                                                                                                          //no IMS found for the user, so nothing to send
            return;

        StringJoiner sj = new StringJoiner("\n", "<IMS m=\"", "\" />");
        historyLogs.forEach(hl -> sj.add(String.format(" &#13;%s %s\t%d\t%s\t%s\t%s\t%s\t%s", dateFormat.format(new Date(hl.getDt() * 1000)), timeFormat.format(new Date(hl.getDt() * 1000)), hl.getCode(), hl.getParam1(), hl.getParam2(), hl.getParam3(), hl.getParam4(), hl.getParam5())));
        sendMsg(sj.toString());
        return;
    }

    public void sendIMS(int code, String... params) {                                                                                       //send an IMS to user and add history record
        long now = Instant.now().getEpochSecond();                                                                                          //format date time
        History.addIms(getId(), code, params);                                                                                              //add history record with IMS flag set

        SimpleDateFormat dateFormat = new SimpleDateFormat("dd.MM.yy"), timeFormat = new SimpleDateFormat("HH:mm");
        String date = String.format("%s %s", dateFormat.format(new Date(now * 1000L)), timeFormat.format(new Date(now * 1000L)));
        StringJoiner sj = new StringJoiner("\t", "<IMS m=\" &#13;" + date + "\t" + code + "\t", "\n\"/>");                                        //format and send IMS to the user if he is online
        Arrays.stream(params).forEach(sj::add);
        sendMsg(sj.toString());
        return;
    }

    public void sendMsg(String msg) {sendMsg(gameChannel, msg);}                                                                            //send a message to the game socket
    public void sendMsgChat(String msg) {sendMsg(chatChannel, msg);}                                                                        //send a message to the chat socket
    private void sendMsg(Channel ch, String msg) {
        if (ch == null || !ch.isActive())
            return;
        ch.writeAndFlush(msg);
        return;
    }

    public void disconnect() {disconnect(gameChannel);}                                                                                     //disconnect (close) the game channel
    public void disconnect(String msg) {sendMsg(msg); disconnect();}                                                                        //send message and disconnect the channel
    public void disconnect(UserManager.ErrCodes errCode) {disconnect(String.format("<ERROR code=\"%d\"/>", errCode.ordinal()));}            //send the error code and disconnect the user
    public void disconnectChat() {disconnect(chatChannel);}                                                                                 //disconnects user's chat channel
    public void disconnectChat(String msg) {sendMsgChat(msg); disconnectChat();}
    private void disconnect(Channel ch) {
        if (ch == null || !ch.isActive())                                                                                                   //nothing to do
            return;
        ch.close();
        return;
    }
}
