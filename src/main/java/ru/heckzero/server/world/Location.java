package ru.heckzero.server.world;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import org.hibernate.annotations.NaturalIdCache;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.user.User;

import javax.persistence.*;
import java.lang.reflect.Field;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "Location")
@Table(name = "locations")
@Cacheable
@NaturalIdCache
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class Location {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);

    public enum Params {X, Y, tm, t, m, n, r, name, b, z, battlemap_f, danger, o, p, repair, monsters};
    private static final EnumSet<Params> golocParams = EnumSet.of(Params.X, Params.Y, Params.tm, Params.t, Params.m, Params.n, Params.r, Params.name, Params.b, Params.z, Params.o, Params.p, Params.repair);

    private static final int SPAN = 360;                                                                                                    //the world's horizontal and vertical dimension
    private static final int DEF_LOC_TIME = 5;                                                                                              //default location wait time in sec.
    private static final int [][] dxdy = { {-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1},     {-2, -2}, {-1, -2}, {0, -2}, {1, -2}, {2, -2},    {2, -1}, {2, 0}, {2, 1}, {2, 2}, {1, 2}, {0, 2}, {-1, 2}, {-2, 2}, {-2, 1}, {-2, 0}, {-2, -1}  };
    private static final Integer [][] locNums = { {10, 11, 12, 13, 14}, {25, 1, 2, 3, 15}, {24, 4, 5, 6, 16}, {23, 7, 8, 9, 17}, {22, 21, 20, 19, 18} }; //map for computing button number by location coordinate

    public static int  normalLocToLocal(int value) {return  value > 180 ? value - 360 : (value <= -180 ? value + 360 : value);}             //transform normalized coordinates to local (human adapted)
    private static int normalizeLoc(int val) {return val < 0 ? val + 360 : (val > 359 ? val - 360 : val);}                                  //make a coordinate normalized (after shift)
    private static int shiftCoordinate(int coordinate, int shift) {return Math.floorMod(coordinate + shift, SPAN);}                         //compute shifted coordinate

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loc_generator_sequence")
    @SequenceGenerator(name = "loc_generator_sequence", sequenceName = "locations_id_seq", allocationSize = 1)
    private Integer id;

    @Column(name = "\"X\"")
    @NaturalId                                                                                                                              //locations are queried by Natural Id API
    private int X;                                                                                                                          //X,Y coordinate (server format) (0-359)

    @Column(name = "\"Y\"")
    @NaturalId
    private int Y;

    private int tm = DEF_LOC_TIME;                                                                                                          //location wait time (loc_time) in sec
    private String t = "A";                                                                                                                 //location surface type 1 symbol (A-Z)
    private String m ="t1:19:8,t1:26:12";                                                                                                   //location map itself
    private String n = StringUtils.EMPTY;                                                                                                   //location music
    private String r = StringUtils.EMPTY;                                                                                                   //Is there a road at the location (0 - no, 1 - yes)
    private String name = StringUtils.EMPTY;                                                                                                //name for the mini map and chat
    private String b = "1";                                                                                                                 //Is there a cupola (0 - yes, 1 - no) (can user battle here)
    private String z = "9";                                                                                                                 //number of artefacts (taken from world.swf)
    private String battlemap_f = "A";                                                                                                       //type of battle map for the location
    private String danger = StringUtils.EMPTY;                                                                                              //danger level for the player (0-low, 1 - location is rangers protected, 2 - high)
    private String o = "";                                                                                                                  //radiation level if o < 999 or not accessible if o >=999
    private String p = StringUtils.EMPTY;                                                                                                   //location road condition ?
    private String repair = StringUtils.EMPTY;                                                                                              //location road is being repairing  ?
    private String monsters = "2,2,2;3,3,3";                                                                                                //monster type (attribute "m" in <GOLOC/> reply)  from the loc_x.swf (monstersX_Y sprite) (see the function AddMonsters() in client)

    @OneToMany(mappedBy = "location", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.EAGER)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private final List<Building> buildings = new ArrayList<>();

    public static Location getLocation(int X, int Y, int btnNum) {return getLocation(shiftCoordinate(X, dxdy[btnNum - 1][0]), shiftCoordinate(Y, dxdy[btnNum - 1][1]));}
    public static Location getLocation(int X, int Y) {                                                                                      //try to get location from a database
        Session session = ServerMain.sessionFactory.openSession();
        try (session) {
            Location location = session.byNaturalId(Location.class).using("X", X).using("Y", Y).load();
            return (location == null) ? new Location(X, Y) : location;                                                                      //return location data from db or a default if location was not found in database
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load location %d/%d from database: %s, generating a default location", X, Y, e.getMessage());
        }
        return new Location(X, Y);                                                                                                          //in case of database error return a default location
    }

    protected Location() { }
    private Location (int X, int Y) {                                                                                                       //generate a default location
        this.X = X;
        this.Y = Y;
        return;
    }

    public Building getBuilding(int Z) {return buildings.stream().filter(b -> b.getParamInt(Building.Params.Z) == Z).findFirst().orElseGet(Building::new); } //return building by Z coordinate

    public int getX() {return getParamInt(Params.X);}                                                                                       //get location X coordinate (shortcut)
    public int getY() {return getParamInt(Params.Y);}                                                                                       //get location Y coordinate (shortcut)
    public int getLocalX() {return normalLocToLocal(getParamInt(Params.X));}                                                                //get location local coordinates X,Y (for the client)
    public int getLocalY() {return normalLocToLocal(getParamInt(Params.Y));}

    public int getLocBtnNum(User user) {                                                                                                    //return the minimap button number this location can be placed on
        int userX = user.getParamInt(User.Params.X);                                                                                        //current user coordinates
        int userY = user.getParamInt(User.Params.Y);

        int col = normalLocToLocal(this.X) - normalLocToLocal(userX) + 2;                                                                   //get locNum[] indexes
        int row = normalLocToLocal(this.Y) - normalLocToLocal(userY) + 2;
        return ArrayUtils.get(ArrayUtils.get(locNums, row), col, ArrayUtils.INDEX_NOT_FOUND);                                               //get locNum value or -1 if indexes are out of bounds
    }

    public String getParamStr(Params param) {return strConv.convert(String.class, getParam(param));}                                        //get user param value as different type
    public int getParamInt(Params param) {return intConv.convert(Integer.class, getParam(param));}
    private String getParamXml(Params param) {return getParamStr(param).transform(s -> !s.isEmpty() ? String.format("%s=\"%s\"", param.toString(), s) : StringUtils.EMPTY); } //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    public String getLocationXml() {                                                                                                        //format location description in XML
        StringJoiner sj = new StringJoiner("", "", "</L>");
        String locationParamsXml = golocParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<L ", ">")); // add location data
        sj.add(locationParamsXml);
        buildings.forEach(b -> sj.add(b.getBuildingXml()));                                                                                 //add buildings data
        return sj.toString();
    }

    private Object getParam(Params paramName) {                                                                                             //try to find a field with the name equals to paramName
        try {
            Field field = this.getClass().getDeclaredField(paramName.toString());
            return field.get(this);                                                                                                         //and return it (or an empty string if null)
        } catch (Exception e) {logger.error("can't get location param %s: %s", paramName.toString(), e.getMessage()); }
        return StringUtils.EMPTY;
    }
}
