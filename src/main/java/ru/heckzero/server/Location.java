package ru.heckzero.server;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;
import ru.heckzero.server.user.User;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.EnumSet;
import java.util.stream.Collectors;

@Entity(name = "Location")
@Table(name = "locations")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class Location {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);

    public enum Params {X, Y, tm, t, m, n, r, name, b, z, battlemap_f, danger, o, p, repair, monsters};
    private static final EnumSet<Params> golocParams = EnumSet.of(Params.X, Params.Y, Params.tm, Params.t, Params.m, Params.n, Params.r, Params.name, Params.b, Params.z, Params.o, Params.p, Params.repair);

    private static final int DEF_LOC_TIME = 5;
    private static final int [][] dxdy = { {-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1},     {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2},    {2, -1}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {-1, 2}, {-2, 2}, {-2, 1}, {-2, 0}, {-2, -1}  };
//    private static final int [][] locNums = { {10, 11, 12, 13, 14}, {25, 1, 2, 3, 15}, {24, 4, 5, 6, 16}, {23, 7, 8, 9, 17}, {22, 21, 20, 19, 18} };
//    public static int  normalLocToLocal(int value) {return  value > 180 ? value - 360 : (value <= -180 ? value + 360 : value);}
    private static int normalizeLoc(int val) {return val < 0 ? val + 360 : (val > 359 ? val - 360 : val);}
    private static int shiftCoordinate(int currCoordinate, int shift) {return normalizeLoc(currCoordinate + shift);}

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loc_generator_sequence")
    @SequenceGenerator(name = "loc_generator_sequence", sequenceName = "locations_id_seq", allocationSize = 1)
    private Integer id;

    @Column(name = "\"X\"")    private Integer X;                                                                                           //X,Y coordinate (server format) (0-359)
    @Column(name = "\"Y\"")    private Integer Y;
    private Integer tm = DEF_LOC_TIME;                                                                                                      //location wait time (loc_time) sec
    private String t = "A";                                                                                                                 //location surface type 1 symbol (A-Z)
    private String m ="t1:19:8,t1:26:12";                                                                                                   //location map itself
    private String n = StringUtils.EMPTY;                                                                                                   //location music
    private String r = StringUtils.EMPTY;                                                                                                   //Is there a road on the location (0 - no, 1 - yes)
    private String name = StringUtils.EMPTY;                                                                                                //name for the mini map and chat
    private String b = "1";                                                                                                                 //Is there a cupola (0 - yes, 1 - no) (can user battle here)
    private String z = "9";                                                                                                                 //number of artefacts (taken from world.swf)
    private String battlemap_f = "A";                                                                                                       //type of battle map for the location
    private String danger = StringUtils.EMPTY;                                                                                              //danger level for the player (0-low, 1 - location is rangers protected, 2 - high)
    private String o = "";                                                                                                                  //radiation level if o < 999 or not accessible if o >=999
    private String p = StringUtils.EMPTY;                                                                                                   //location road condition ?
    private String repair = StringUtils.EMPTY;                                                                                              //location road is being repairing  ?
    private String monsters = "2,2,2;3,3,3";                                                                                                //monster type (attribute m in <GOLOC/> reply)  from the loc_x.swf (monstersX_Y sprite) (see the function AddMonsters() in client)

    protected Location() { }

    private Location (Integer X, Integer Y) {                                                                                               //generate a default location
        this.X = X;
        this.Y = Y;

        return;
    }

    public boolean isLocationAcrossTheBorder(User user, int shift) {
        int userX = user.getParamInt(User.Params.X);
        int userY = user.getParamInt(User.Params.Y);
        return  ((userX == 181 && (shift == 1 || shift == 4 || shift == 7)) || (userY == 181 && (shift == 1 || shift == 2 || shift == 3)) || (userX == 180 && (shift == 3 || shift == 6 || shift == 9)) ||  (userY == 180 && (shift == 7 || shift == 8 || shift == 9)));
    }

    public static Location getLocation(User user) {return getLocation(user.getParamInt(User.Params.X), user.getParamInt(User.Params.Y));}
    public static Location getLocation(User user, int shift) {return getLocation(user.getParamInt(User.Params.X), user.getParamInt(User.Params.Y), shift);}
    public static Location getLocation(int X, int Y, int shift) {return getLocation(shiftCoordinate(X, dxdy[shift - 1][0]), shiftCoordinate(Y, dxdy[shift - 1][1]));}
    public static Location getLocation(Integer X, Integer Y) {                                                                              //try to get location from database
        Session session = ServerMain.sessionFactory.openSession();
        Query<Location> query = session.createQuery("select l from Location l where X=:X and Y = :Y", Location.class).setParameter("X", X).setParameter("Y", Y);
        try (session) {
            Location location = query.uniqueResult();
            return (location == null) ? new Location(X, Y) : location;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't execute query %s: %s", query.getQueryString(), e.getMessage());
        }
        return new Location(X, Y);                                                                                                          //in case of database error return a default location
    }


    public String getParamStr(Params param) {return strConv.convert(String.class, getParam(param));}                                        //get user param value as different type
    public Integer getParamInt(Params param) {return intConv.convert(Integer.class, getParam(param));}
    private String getParamXml(Params param) {return getParamStr(param).transform(s -> !s.isEmpty() ? String.format("%s=\"%s\"", param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    public String getLocationXml() {return golocParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" "));}

    private Object getParam(Params paramName) {                                                                                             //try to find a field with the name equals to paramName
        try {
            Field field = this.getClass().getDeclaredField(paramName.toString());
            return field.get(this);                                                                                                         //and return it (or an empty string if null)
        } catch (Exception e) {logger.error("can't get location param %s: %s", paramName.toString(), e.getMessage()); }
        return StringUtils.EMPTY;
    }

}
