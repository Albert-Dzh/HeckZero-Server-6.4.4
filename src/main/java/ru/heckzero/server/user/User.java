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
import ru.heckzero.server.ParamUtils;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.Item;
import ru.heckzero.server.items.ItemBox;
import ru.heckzero.server.world.*;

import javax.persistence.*;
import java.time.Instant;
import java.util.*;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.function.Predicate;
import java.util.stream.Collectors;

@Entity(name = "User")
@Table(name = "users")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class User {
    private static final Logger logger = LogManager.getFormatterLogger();

    public enum ChannelType {NOUSER, GAME, CHAT}                                                                                            //user channel type, which is set on login by onlineGame() and onlineChat() methods
    public enum Params {time, tdt, owner, level, predlevel, nextlevel, maxHP, maxPsy, nochat, kupol, battleid, group, login, password, email, reg_time, lastlogin, lastlogout, lastclantime, loc_time, cure_time, god, hint, exp, pro, propwr, rank_points, clan, clan_img, clr, img, alliance, man, HP, psy, stamina, str, dex, intu, pow, acc, intel, sk0, sk1, sk2, sk3, sk4, sk5, sk6, sk7, sk8, sk9, sk10, sk11, sk12, X, Y, Z, hz, ROOM, id1, id2, i1, ne, ne2, cup_0, cup_1, cup_2, silv, gold, p78money, acc_flags, siluet, bot, name, city, about, note, list, plist, ODratio, virus, brokenslots, poisoning, ill, illtime, sp_head, sp_left, sp_right, sp_foot, eff1, eff2, eff3, eff4, eff5, eff6, eff7, eff8, eff9, eff10, rd, rd1, t1, t2, dismiss, chatblock, forumblock}  //all possible params that can be accessed via get/setParam()
    private static final EnumSet<Params> getmeParams = EnumSet.of(Params.time, Params.tdt, Params.owner, Params.level, Params.predlevel, Params.nextlevel, Params.maxHP, Params.maxPsy, Params.kupol, Params.login, Params.email, Params.loc_time, Params.god, Params.hint, Params.exp, Params.pro, Params.propwr, Params.rank_points, Params.clan, Params.clan_img, Params.clr, Params.img, Params.alliance, Params.man, Params.HP, Params.psy, Params.stamina, Params.str, Params.dex, Params.intu, Params.pow,  Params.acc, Params.intel, Params.sk0, Params.sk1, Params.sk2, Params.sk3, Params.sk4, Params.sk5, Params.sk6, Params.sk7, Params.sk8, Params.sk9, Params.sk10, Params.sk11, Params.sk12, Params.X, Params.Y, Params.Z, Params.hz, Params.ROOM, Params.id1, Params.id2, Params.i1, Params.ne, Params.ne2, Params.cup_0, Params.cup_1, Params.cup_2, Params.silv, Params.gold, Params.p78money, Params.acc_flags, Params.siluet, Params.bot, Params.name, Params.city, Params.about, Params.note, Params.list, Params.plist, Params.ODratio, Params.virus, Params.brokenslots, Params.poisoning, Params.ill, Params.illtime, Params.sp_head, Params.sp_left, Params.sp_right, Params.sp_foot, Params.eff1, Params.eff2, Params.eff3, Params.eff4, Params.eff5, Params.eff6, Params.eff7, Params.eff8, Params.eff9, Params.eff10, Params.rd, Params.rd1, Params.t1, Params.t2, Params.dismiss, Params.chatblock, Params.forumblock);   //params sent in <MYPARAM/>
    private static final int DB_SYNC_INTERVAL = 180;                                                                                        //user database sync interval in seconds

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

    @Transient private Arsenal arsenal = null;                                                                                              //current user arsenal
    @Transient private Portal portal = null;                                                                                                //current user portal

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

    public Channel getGameChannel() {return this.gameChannel;}
    public Channel getChatChannel() {return this.chatChannel;}
    public String getLogin() {return getParamStr(Params.login);}                                                                            //just a shortcut
    private String getParam_battleid() {return StringUtils.EMPTY;}
    private String getParam_group() {return StringUtils.EMPTY;}

    private long getParam_time() {return Instant.now().getEpochSecond();}                                                                   //always return epoch time is seconds
    private int getParam_tdt() {return Calendar.getInstance().getTimeZone().getOffset(Instant.now().getEpochSecond() / 3600L);}             //user time zone, used in user history log
    private int getParam_level() {return UserLevelData.getLevel(this);}                                                                     //compute the user level by its experience value
    private int getParam_predlevel() {return UserLevelData.getExpLastLvl(this);}                                                            //get the experience value of current level beginning
    private int getParam_nextlevel() {return UserLevelData.getExpNextLvl(this);}                                                            //get the experience value of current level end
    private int getParam_maxHP() {return UserLevelData.getMaxHP(this);}
    private int getParam_maxPsy() {return UserLevelData.getMaxPsy(this);}
    private int getParam_nochat() {return isOnlineChat() ? 0 : 1;}                                                                          //user chat status, whether he has his chat channel off (null)
    private int getParam_kupol() {return getLocation().getParamInt(Location.Params.b) ^ 1;}                                                 //is a user under the kupol - his current location doesn't allow battling
    private int getParam_owner() {return getParamInt(Params.Z) == 0 ? 0 : BooleanUtils.toInteger(isBuildMaster(getBuilding()));}            //is a user under the kupol - his current location doesn't allow battling

    public String getParamStr(Params param) {return ParamUtils.getParamStr(this, param.toString());}                                        //get user param value as different type
    public int getParamInt(Params param) {return ParamUtils.getParamInt(this, param.toString());}                                           //get user param value as different type
    public long getParamLong(Params param) {return ParamUtils.getParamLong(this, param.toString());}                                        //get user param value as different type
    public double getParamDouble(Params param) {return ParamUtils.getParamDouble(this, param.toString());}                                  //get user param value as different type

    private String getParamXml(Params param, boolean appendEmpty) {return getParamStr(param).transform(s -> (!s.isEmpty() || appendEmpty) ? String.format("%s=\"%s\"", param == Params.intu ? "int" : param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    private String getParamsXml(EnumSet<Params> params, boolean appendEmpty) {return params.stream().map(p -> getParamXml(p, appendEmpty)).filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));}

    public Location getLocation() {return Location.getLocation(getParamInt(Params.X), getParamInt(Params.Y));}                              //get the location the user is now at
    public Location getLocation(int btnNum) {return Location.getLocation(getParamInt(Params.X), getParamInt(Params.Y), btnNum);}            //get the location for minimap button number
    public Building getBuilding() {return getLocation().getBuilding(getParamInt(Params.Z));}                                                //get the building the user is now in

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

    public ItemBox getItemBox() {                                                                                                           //return user itembox or get it
        return itemBox != null ? itemBox : (itemBox = ItemBox.init(ItemBox.boxType.USER, id, true));
    }

    synchronized void onlineGame(Channel ch) {                                                                                              //the user game channel connected
        logger.debug("setting user '%s' game channel online", getLogin());
        this.gameChannel = ch;                                                                                                              //set user game channel
        this.gameChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.GAME);                                                        //set the user channel type to GAME
        this.gameChannel.attr(AttributeKey.valueOf("chStr")).set("user '" + getLogin() + "'");                                              //replace a channel representation string to 'user <login>' instead of IP:port
        this.gameChannel.pipeline().replace("socketIdleHandler", "userIdleHandler", new ReadTimeoutHandler(ServerMain.hzConfiguration.getInt("ServerSetup.MaxUserIdleTime", ServerMain.DEF_MAX_USER_IDLE_TIME))); //replace read timeout handler to a new one with a longer timeout defined for authorized user
        setParam(Params.lastlogin, Instant.now().getEpochSecond());                                                                         //set user last login time, needed to compute loc_time
        this.lastsynctime = Instant.now().getEpochSecond();                                                                                 //set last db sync time to now
        setParam(Params.loc_time, Math.min(Instant.now().getEpochSecond() + 180, getParamLong(Params.loc_time) != 0L ? getParamLong(Params.loc_time) + getParamLong(Params.lastlogin) - getParamLong(Params.lastlogout) : getParamLong(Params.reg_time))); //compute client loc_time - time when user is allowed to leave his current location
        String resultMsg = String.format("<OK l=\"%s\" ses=\"%s\"/>", getLogin(), ch.attr(AttributeKey.valueOf("encKey")).get());           //<OK/> message with a chat auth key in ses attribute (using already existing key)
        sendMsg(resultMsg);                                                                                                                 //send login <OK/> message to the user
        chat.updateMyStatus();                                                                                                              //will add user to his current room, so others will be able to see him
        return;
    }

    synchronized void offlineGame() {                                                                                                       //the user game channel disconnected
        logger.debug("setting user '%s' game channel offline", getLogin());
        setParam(Params.lastlogout, Instant.now().getEpochSecond());                                                                        //set lastlogout to now
        this.gameChannel = null;                                                                                                            //a marker that user is offline now
        disconnectChat();                                                                                                                   //chat without a game channel is ridiculous, so shut the chat down
        chat.updateMyStatus();                                                                                                              //will remove user from room
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

    public void com_DROP(String id, String count) {
        if (!getItemBox().delItem(NumberUtils.toLong(id), NumberUtils.toInt(count)))
            disconnect();
        return;
    }

    public void com_TO_SECTION(String id, String section) {                                                                                 //change item section in user box
        if (!getItemBox().changeOne(NumberUtils.toLong(id), Item.Params.section, section))
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
                logger.warn("user %s tried to move at loc_time < now() (%d < %d) Check it out!", getLogin(), getParamLong(Params.loc_time), Instant.now().getEpochSecond());
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

    public void com_PR(String comein, String id, String new_cost, String to, String d, String a, String s, String c) {                      //portal workflow
        if (comein != null) {                                                                                                               //incoming routes list request
            sendMsg(portal.cominXml());                                                                                                     //get and send incoming routes for the current portal
            return;
        }

        if (id != null && new_cost != null) {                                                                                               //changing an incoming route cost
            PortalRoute route = PortalRoute.getRoute(NumberUtils.toInt(id));                                                                //get the route to change cost for
            if (route == null) {
                disconnect();
                return;
            }
            route.setCost(NumberUtils.toDouble(new_cost));
            route.sync();                                                                                                                   //sync the route with a database
            return;
        }

        if (to != null) {                                                                                                                   //flight request to = routeId where user wants to fly to
            PortalRoute route = PortalRoute.getRoute(NumberUtils.toInt(to));
            if (route == null) {                                                                                                            //can't get the route user wants flying to
                disconnect();
                return;
            }
            Portal dstPortal = route.getDstPortal();
            int X = dstPortal.getLocation().getX();
            int Y = dstPortal.getLocation().getY();
            int Z = dstPortal.getZ();
            int hz = dstPortal.getName();
            int ROOM = route.getROOM();

            setRoom(X, Y, Z, hz, ROOM);
            sendMsg(String.format("<MYPARAM kupol=\"%d\"/><PR X=\"%d\" Y=\"%d\" Z=\"%d\" hz=\"%d\" ROOM=\"%d\"/>", getParamInt(Params.kupol), X, Y, Z, hz, ROOM));
            return;
        }

        if (d != null) {                                                                                                                    //user puts resource-item to portal's warehouse
            Item item = getItemBox().findItem(NumberUtils.toLong(d));                                                                       //get the item he wants to put to a warehouse
            int count = NumberUtils.toInt(c);                                                                                               //item count he has selected
            if (item == null) {                                                                                                             //we couldn't find an item the user wants to put in the user Item box
                logger.error("item id %s is not found for user %s", d, getLogin());
                disconnect();
                return;
            }
            logger.info("try to find joinable item inside portal item box");
            Item joinable = portal.getItemBox().findJoinableItem(item);                                                                     //try to find a joinable item in a portal warehouse
            if (joinable != null) {                                                                                                         //we've found it
                logger.info("joinable item found: %s", joinable);
                joinable.increase(count);                                                                                                   //increase joinable item count by count
                joinable.sync();                                                                                                            //update joinable before deletion invalidates our L2 cache to prevent redundant select of joinable
                if (count > 0 && count < item.getCount())                                                                                   //check if we should decrease or delete the item from users item box
                    item.decrease(count);
                else {
                    getItemBox().delItem(NumberUtils.toLong(d));                                                                                //item will be deleted from user item box and db, which invalidates L2 cache
                    Item.delFromDB(NumberUtils.toLong(d), false);
                }
                return;
            }
                                                                                                                                            //joinable item hasn't been found
            logger.info("can't find an item to join our item into, will split the item");
            item = getItemBox().getSplitItem(NumberUtils.toLong(d), count, false, this::getNewId);                                          //get (split) an item from user itembox the user wants to put to warehouse
            item.setParam(Item.Params.user_id, null);                                                                                       //reset user specific params before putting an item to the building item box
            item.setParam(Item.Params.b_id, portal.getId());
            portal.getItemBox().addItem(item);                                                                                             //put an item to building item box
            portal.getItemBox().sync();                                                                                                     //add the item to database
            return;
        }

        if (a != null) {                                                                                                                    //user takes an item from warehouse
            int count = NumberUtils.toInt(c);                                                                                               //item count he has selected
            Item takenItem = portal.getItemBox().getClonnedItem(NumberUtils.toLong(a), count, this::getNewId);
            if (takenItem == null) {                                                                                                        //item doesn't exist on warehouse already
                sendMsg("<PR a1=\"0\" a2=\"0\"/>");                                                                                         //let the client know if it failed taking an item
                return;
            }

            takenItem.resetParam(Item.Params.b_id);
            takenItem.setParam(Item.Params.user_id, this.id);
            takenItem.setParam(Item.Params.section, s);
            getItemBox().addItem(takenItem);

            Item item = portal.getItemBox().findItem(NumberUtils.toLong(a));
            int a2 = item == null ? 0 : item.getCount();
            sendMsg(String.format("<PR a1=\"%d\" a2=\"%d\"/>", takenItem.getCount(), a2));
            return;
        }

        portal = Portal.getPortal(getBuilding().getId());                                                                                   //init the portal the user is entering
        sendMsg(portal.prXml());                                                                                                            //user entered a portal, sending info about that portal, its routes and warehouse items
        return;
    }

    public void com_AR(String a, String d, String s, String c) {                                                                            //arsenal workflow
        if (a != null) {                                                                                                                    //get an item from arsenal
            Item item = arsenal.getItem(NumberUtils.toLong(a), NumberUtils.toInt(c), this::getNewId);
            if (item == null) {                                                                                                             //we couldn't find an item in arsenal item box
                disconnect();
                return;
            }
            item.setParam(Item.Params.user_id, this.id);                                                                                    //assign the item to the user by setting user-specific params
            item.setParam(Item.Params.section, s);
            Map<Item.Params, Object> userParams = Map.of(Item.Params.user_id, id, Item.Params.section, s);
            getItemBox().addItem(item);                                                                                                     //add the item to the user's itembox
            return;                                                                                                                         //we don't have to send <ADD_ONE/> because client adds an item by itself
        }

        if (d != null) {                                                                                                                    //put an item to arsenal
            Item item = getItemBox().getSplitItem(NumberUtils.toLong(d), NumberUtils.toInt(c), false, this::getNewId);
            if (item == null) {                                                                                                             //we couldn't find an item from user
                disconnect();
                return;
            }
            arsenal.putItem(item);
            return;
        }

        arsenal = new Arsenal(getBuilding().getId());                                                                                       //get an arsenal loot XML list and send it to th user
        sendMsg(arsenal.lootXml());
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
        upgradeUserParams(item, true);
        item.setParam(Item.Params.slot, slot);
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
        item.setParam(Item.Params.slot, "");
        return;
    }

    private void upgradeUserParams(Item item, boolean isEquipping) {
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
            if (stat == null) {
                logger.error("unknown param %s for item id %d in item.up field, check the database data", item.getParamStr(Item.Params.up), item.getId());
                disconnect();
                return;
            }
            int influence = Integer.parseInt(bonusStat.split("=")[1]);                                                                      //get the influence value
            setParam(stat, getParamInt(stat) + (isEquipping ? influence : influence * -1));                                                 //update the corresponding user stat param
            return;
        }
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
        String s_id1 = getParamStr(Params.id1);                                                                                             //s_idx -> server side values
        String s_id2 = getParamStr(Params.id2);
        String s_i1 = getParamStr(Params.i1);

        if ((id1 != null && !id1.equals(s_id1)) || (id2 != null && !id2.equals(s_id2)) || (i1 != null && !i1.equals(s_i1))) {               //compare id values if they are not null
            logger.error("%s !!!!!!!!MISTIMING!!!!!!!! id1 = %s s_id1 = %s, id2 = %s s_id2 = %s, i1 = %s s_i1 = %s", getLogin(), id1, s_id1, id2, s_id2, i1, s_i1);
            disconnect();
        }
//        if (Instant.now().getEpochSecond() - lastsynctime > DB_SYNC_INTERVAL)
            sync();
//        if (Instant.now().getEpochSecond() - lastsynctime > DB_SYNC_INTERVAL * 2)
            com_CHECK();
        return;
    }

    public boolean isBuildMaster(Building bld) {return isBuildMaster(bld.getX(), bld.getY(), bld.getZ());}
    private boolean isBuildMaster(int x, int y, int z) {                                                                                    //check if user has a master key to the building with given coordinates
        String keyName = String.format("$key%d_%d_%d", x, y, z);
        Predicate<Item> predicate = (i -> !i.isExpired() && i.getParamDouble(Item.Params.type) == 782 && (i.getParamStr(Item.Params.made).equals(keyName) || i.getParamStr(Item.Params.made).equals("$key_all")));
        return !getItemBox().findItems(predicate).isEmpty() || isGod();
    }

    public void addSendItems(ItemBox box) {box.forEach(this::addSendItem);}                                                                 //add and send all items from box to the user's Item Box
    public void addSendItem(Item item) {
        if (!getItemBox().addItem(item))
            logger.error("can't add item %s", item);
        sendMsg(String.format("<ADD_ONE>%s</ADD_ONE>", item.getXml()));
        return;
    }
    public void delSendItems(ItemBox box) {box.forEach(this::delSendItem);}
    public void delSendItem(Item item) {
        getItemBox().delItem(item.getId());
        sendMsg(String.format("<DEL_ONE id=\"%d\"/>", item.getId()));
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
        ItemBox expired = getItemBox().findExpired();                                                                                       //all user expired items are here at a 1st level
        if (expired.size() > 0)
            logger.info("found %d expired items for user %s, %s", expired.size(), getLogin(), expired);

        expired.forEach(item -> {
            logger.info("deleting expired item %s for user %s", item, getLogin());
            delSendItem(item);

            ItemBox included = item.getIncluded();
            if (!included.isEmpty()) {
                logger.info("item %d contains %d included items: %s, unloading and adding them to user %s", item.getId(), included.size(), included.itemsIds(), getLogin());
                included.forEach(Item::unload);
                addSendItems(included);                                                                                                     //add all included items to the user as a 1st level items
            }
        });
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
    public void disconnectChat() {disconnect(chatChannel);}                                                                                 //disconnects user's chat channel
    public void disconnectChat(String msg) {sendMsgChat(msg); disconnectChat();}
    private void disconnect(Channel ch) {
        if (ch == null || !ch.isActive())                                                                                                   //nothing to do
            return;
        ch.close();
        return;
    }
}
