package ru.heckzero.server.user;

import io.netty.channel.Channel;
import io.netty.handler.timeout.ReadTimeoutHandler;
import io.netty.util.AttributeKey;
import org.apache.commons.beanutils.converters.DoubleConverter;
import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.LongConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.Location;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.lang.reflect.Method;
import java.time.Instant;
import java.util.*;
import java.util.stream.Collectors;

@Entity(name = "User")
@Table(name = "users")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class User {
    private static final Logger logger = LogManager.getFormatterLogger();

    public enum ChannelType {NOUSER, GAME, CHAT}                                                                                            //user channel type, set on login by onlineGame() and onlineChat() methods
    public enum Params {time, tdt, nochat, login, password, email, reg_time, lastlogin, lastlogout, lastclantime, loc_time, cure_time, god, hint, exp, pro, propwr, rank_points, clan, clan_img, clr, img, alliance, man, HP, psy, stamina, str, dex, intu, pow, acc, intel, sk0, sk1, sk2, sk3, sk4, sk5, sk6, sk7, sk8, sk9, sk10, sk11, sk12, X, Y, Z, hz, ROOM, id1, id2, i1, ne, ne2, cup_0, cup_1, cup_2, silv, acc_flags, siluet, bot, name, city, about, note, list, plist, ODratio, virus, brokenslots, poisoning, ill, illtime, sp_head, sp_left, sp_right, sp_foot, eff1, eff2, eff3, eff4, eff5, eff6, eff7, eff8, eff9, eff10, rd, rd1, t1, t2, dismiss, chatblock, forumblock}  //all possible params that can be accessed via get/setParam()
    public static final EnumSet<Params> getmeParams = EnumSet.of(Params.time, Params.tdt, Params.login, Params.email, Params.loc_time, Params.god, Params.hint, Params.exp, Params.pro, Params.propwr, Params.rank_points, Params.clan, Params.clan_img, Params.clr, Params.img, Params.alliance, Params.man, Params.HP, Params.psy, Params.stamina, Params.str, Params.dex, Params.intu, Params.pow,  Params.acc, Params.intel, Params.sk0, Params.sk1, Params.sk2, Params.sk3, Params.sk4, Params.sk5, Params.sk6, Params.sk7, Params.sk8, Params.sk9, Params.sk10, Params.sk11, Params.sk12, Params.X, Params.Y, Params.Z, Params.hz, Params.ROOM, Params.id1, Params.id2, Params.i1, Params.ne, Params.ne2, Params.cup_0, Params.cup_1, Params.cup_2, Params.silv, Params.acc_flags, Params.siluet, Params.bot, Params.name, Params.city, Params.about, Params.note, Params.list, Params.plist, Params.ODratio, Params.virus, Params.brokenslots, Params.poisoning, Params.ill, Params.illtime, Params.sp_head, Params.sp_left, Params.sp_right, Params.sp_foot, Params.eff1, Params.eff2, Params.eff3, Params.eff4, Params.eff5, Params.eff6, Params.eff7, Params.eff8, Params.eff9, Params.eff10, Params.rd, Params.rd1, Params.t1, Params.t2, Params.dismiss, Params.chatblock, Params.forumblock);   //params sent in GETME - MYPARAM

    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);
    private static final LongConverter longConv = new LongConverter(0L);
    private static final DoubleConverter doubleConv = new DoubleConverter(0D);

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "user_generator_sequence")
    @SequenceGenerator(name = "user_generator_sequence", sequenceName = "users_id_seq", allocationSize = 1)
    private Integer id;

    @Embedded
    private final UserParams params = new UserParams();                                                                                     //user params that can be set (read-write) are placed there

    @Transient volatile private Channel gameChannel = null;                                                                                 //user game channel
    @Transient volatile private Channel chatChannel = null;                                                                                 //user chat channel

    public Channel getGameChannel() { return this.gameChannel;}
    public Channel getChatChannel() { return this.chatChannel;}

    public User() { }                                                                                                                       //default empty constructor

    public boolean isEmpty() {return id == null;}                                                                                           //user is empty (having empty params)
    public boolean isOnlineGame() {return gameChannel != null;}                                                                             //this user has a game channel assigned
    public boolean isOnlineChat() {return chatChannel != null;}                                                                             //this user has a chat channel assigned
    public boolean isOffline() {return !(isOnlineGame() || isOnlineChat());}                                                                //user is offline
    public boolean isBot() {return !getParamStr(Params.bot).isEmpty();}                                                                     //user is a bot (not a human)
    public boolean isCop() {return getParamStr(Params.clan).equals("police");}                                                              //user is a cop (is a member of police clan)
    public boolean isInBattle() {return false;}                                                                                             //just a stub yet, take some cognac when you are about to change this method

    public String getLogin() {return getParamStr(Params.login);}                                                                            //just a shortcut
    private Long getParam_time() {return Instant.now().getEpochSecond();}                                                                   //always return epoch time is seconds
    private Integer getParam_tdt() {return Calendar.getInstance().getTimeZone().getOffset(Instant.now().getEpochSecond() / 3600L);}         //user time zone, used in user history log

    public String getParamStr(Params param) {return strConv.convert(String.class, getParam(param));}                                        //get user param value as different type
    public Integer getParamInt(Params param) {return intConv.convert(Integer.class, getParam(param));}
    public Long getParamLong(Params param) {return longConv.convert(Long.class, getParam(param));}
    public Double getParamDouble(Params param) {return doubleConv.convert(Double.class, getParam(param));}
    private String getParamXml(Params param, boolean appendEmpty) {return getParamStr(param).transform(s -> (!s.isEmpty() || appendEmpty) ? String.format("%s=\"%s\"", param == Params.intu ? "int" : param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    private String getParamsXml(EnumSet<Params> params, boolean appendEmpty) {return params.stream().map(p -> getParamXml(p, appendEmpty)).filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));}

    private Object getParam(Params param) {                                                                                                 //get user param (param must be in Params enum)
        try {                                                                                                                               //try to find param in UserParam instance
            return params.getParam(param);
        } catch (Exception e) {logger.debug("cannot find param %s in User.Params params, looking for a special method which computes that param", param.toString());}

        String paramName = param.toString();                                                                                                //param name as a String
        String methodName = String.format("getParam_%s", paramName);
        try {                                                                                                                               //if not found in params. try to compute the param value via the dedicated method
            Method method = this.getClass().getDeclaredMethod(methodName);
            return method.invoke(this);
        } catch (Exception e) {
            logger.warn("cannot find or compute param %s, neither in User params nor by a dedicated method: %s", paramName, e.getMessage());
        }
        return StringUtils.EMPTY;                                                                                                           //return an empty string as a default value
    }
    public void setParam(Params paramName, String paramValue) {params.setParam(paramName, paramValue);}                                     //set param to String value
    public void setParam(Params paramName, Integer paramValue) {params.setParam(paramName, paramValue);}                                    //set param to Integer value
    public void setParam(Params paramName, Long paramValue) {params.setParam(paramName, paramValue);}                                       //set param to Long value
    public void setParam(Params paramName, Double paramValue) {params.setParam(paramName, paramValue);}                                     //set param to Double value


    synchronized void onlineGame(Channel ch) {
        logger.debug("setting user '%s' game channel online", getLogin());

        this.gameChannel = ch;                                                                                                              //set user game channel
        this.gameChannel.attr(AttributeKey.valueOf("chType")).set(ChannelType.GAME);                                                        //set the user channel type to GAME
        this.gameChannel.attr(AttributeKey.valueOf("chStr")).set("user '" + getLogin() + "'");                                              //replace a client representation string to 'user <login>' instead of IP:port
        this.gameChannel.pipeline().replace("socketIdleHandler", "userIdleHandler", new ReadTimeoutHandler(ServerMain.hzConfiguration.getInt("ServerSetup.MaxUserIdleTime", ServerMain.DEF_MAX_USER_IDLE_TIME))); //replace read timeout handler to a new one with a longer timeout defined for authorized user
        setParam(Params.lastlogin, Instant.now().getEpochSecond());                                                                         //set user last login time, needed to compute loc_time
        setParam(Params.loc_time, getParamLong(Params.loc_time) != 0L ? getParamLong(Params.loc_time) + getParamLong(Params.lastlogin) - getParamLong(Params.lastlogout) : getParamLong(Params.reg_time)); //compute client loc_time - time he is allowed to leave his location
        setParam(Params.nochat, 1);                                                                                                         //user has no chat socket connected upon login. He will be treated as chat off by his ROOM neighbours
        String resultMsg = String.format("<OK l=\"%s\" ses=\"%s\"/>", getLogin(), ch.attr(AttributeKey.valueOf("encKey")).get());           //send OK with a chat auth key in ses attribute (using already existing key)
        sendMsg(resultMsg);
        return;
    }

    synchronized void offlineGame() {
        logger.debug("setting user '%s' game channel offline", getLogin());
        this.gameChannel = null;                                                                                                            //a marker that user is offline now
        disconnectChat();                                                                                                                   //chat without a game is ridiculous
        setParam(Params.lastlogout, Instant.now().getEpochSecond());                                                                        //set lastlogout to now
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
        return;
    }
    synchronized void offlineChat() {
        logger.debug("turning user '%s' chat off", getLogin());
        this.chatChannel = null;
        notifyAll();
        logger.info("user '%s' chat channel logged out", getLogin());
        return;
    }

    public void com_MYPARAM() {                                                                                                             //provision the client initial params as a reply for <GETME/>
        logger.info("processing <GETME/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());
        StringJoiner xmlGetme = new StringJoiner(" ", "<MYPARAM ",   "></MYPARAM>").add(getParamsXml(getmeParams, false));
        sendMsg(xmlGetme.toString());
        return;
    }

    public void com_GOLOC(String n, String d, String slow, String force, String pay, String t1, String t2) {
        logger.info("processing <GOLOC/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")).get());

        int btnNum = NumberUtils.toInt(n, 5);                                                                                               //button number user has pressed on minimap (null -> 5 - means there was no movement made and this is just a locations request)
        if (btnNum < 1 || btnNum > 9) {                                                                                                     //bnt num must within 1-9
            logger.warn("user %s tried to move to illegal location num: %d", getLogin(), btnNum);
            disconnect();                                                                                                                   //TODO should we ban the motherfecker cheater
            return;
        }
        Location locationToGo = Location.getLocation(this, btnNum);                                                                         //get the location data user wants move to or the current user location if there was no movement requested

        StringJoiner sj = new StringJoiner("", "<GOLOC", "</GOLOC>");                                                                       //start formatting a <GOLOC> reply

        if (btnNum != 5) {                                                                                                                  //user moves to another location
            if (locationToGo.getLocBtnNum(this) != btnNum) {
                logger.warn("user %s tried to move to inapplicable location from %d/%d to %d/%d", getLogin(), getParam(Params.X), getParam(Params.Y), locationToGo.getParamInt(Location.Params.X), locationToGo.getParamInt(Location.Params.Y));
                sendMsg("<ERRGO />");                                                                                                       //Вы не смогли перейти в этом направлении
                return;
            }
            if (getParamLong(Params.loc_time) > Instant.now().getEpochSecond() + 1) {                                                       //TODO why do we have to add 1 to now()?
                logger.warn("user %s tried to move at loc_time < now() (%d < %d) Check it out!", getLogin(), getParamLong(Params.loc_time), Instant.now().getEpochSecond());
                sendMsg(String.format("<MYPARAM time=\"%d\"/><ERRGO code=\"5\"/>", Instant.now().getEpochSecond()));                        //Вы пока не можете двигаться, отдохните
                return;
            }
            if (locationToGo.getParamInt(Location.Params.o) >= 999) {                                                                       //an impassable location, no trespassing allowed
                sendMsg("<ERRGO code=\"1\"/>");                                                                                             //Локация, в которую вы пытаетесь перейти, непроходима
                return;
            }

            sj.add(String.format(" n=\"%d\"", btnNum));                                                                                     //add n="shift" if we have moved to some location

            Long locTime = Instant.now().getEpochSecond() + Math.max(locationToGo.getParamInt(Location.Params.tm), 5);                      //compute a loc_time for a user(now + the location loc_time (location tm parameter))
            setParam(Params.loc_time, locTime);                                                                                             //set new loc_time for a user
            setRoom(locationToGo.getParamInt(Location.Params.X), locationToGo.getParamInt(Location.Params.Y));                              //change user coordinates to new location

            String reply = String.format("<MYPARAM loc_time=\"%d\" kupol=\"%d\"/>", locTime, locationToGo.getParamInt(Location.Params.b) ^ 1);
            sendMsg(reply);
        }
        sj.add(locationToGo.getParamStr(Location.Params.monsters).transform(s -> s.isEmpty() ? ">" : String.format(" m=\"%s\">", s)));      //add m (monster) to <GOLOC> from the current location

        if (d != null) {                                                                                                                    //user requests nearest location description
            List<Location> locations = Arrays.stream(d.split("")).mapToInt(NumberUtils::toInt).mapToObj(btn -> btn == 5 ? locationToGo : Location.getLocation(this, btn)).filter(l -> l.getLocBtnNum(this) != -1).collect(Collectors.toList()); //get the list if requested location (for each number in "d")
            locations.forEach(l -> sj.add(l.getLocationXml()));
        }
        sendMsg(sj.toString());                                                                                                             //send a <GOLOC/> reply
        return;
    }

    public void com_MMP(String param) {
        StringJoiner sj = new StringJoiner("", "<MMP>", "</MMP>");                                                                          //MMP - Big map (5x5) request
        List<Location> locations = Arrays.stream(param.split(",")).mapToInt(NumberUtils::toInt).mapToObj(c -> Location.getLocation(this, c)).filter(l -> l.getLocBtnNum(this) != -1).collect(Collectors.toList()); //get the list if requested location (for each number in "param")
        locations.forEach(l -> sj.add("<L ").add(l.getLocationXml()).add("/>"));
        sendMsg(sj.toString());
        return;
    }

    public void com_SILUET(String slt, String set) {
        logger.info("processing <SILUET/> from %s", gameChannel.attr(AttributeKey.valueOf("chStr")));
        logger.info("slt = %s, set = %s", slt, set);
        setParam(Params.siluet, set);
        String response = String.format("<SILUET code=\"0\"/><MYPARAM siluet=\"%s\"/>",  set);
        sendMsg(response);
        return;
    }

    public void setRoom(int X, int Y) {setRoom(X, Y, 0, 0); }
    public void setRoom(int X, int Y, int Z, int ROOM) {
        setParam(Params.X, X);
        setParam(Params.Y, Y);
        setParam(Params.Z, Z);
        setParam(Params.ROOM, ROOM);
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
    public void disconnectChat() {disconnect(chatChannel);}
    public void disconnectChat(String msg) {sendMsgChat(msg); disconnectChat();}
    private void disconnect(Channel ch) {
        if (ch == null || !ch.isActive())                                                                                                   //nothing to do
            return;
        ch.close();
        return;
    }
}
