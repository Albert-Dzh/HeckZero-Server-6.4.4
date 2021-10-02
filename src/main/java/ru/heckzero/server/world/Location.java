package ru.heckzero.server.world;

import org.apache.commons.lang3.ArrayUtils;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.NaturalId;
import ru.heckzero.server.ParamUtils;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.user.User;

import javax.persistence.*;
import java.util.ArrayList;
import java.util.EnumSet;
import java.util.List;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "Location")
@Table(name = "locations")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class Location {
    private static final Logger logger = LogManager.getFormatterLogger();
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
    private String monsters = "2,2,2;3,3,3";                                                                                                //location monsters type and count (attribute "m" in <GOLOC/> reply)  from the loc_x.swf (monstersX_Y sprite) (see the function AddMonsters() in client)

    @OneToMany(mappedBy = "location", cascade = CascadeType.ALL, orphanRemoval = true, fetch = FetchType.LAZY)
    @org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE)
    private final List<Building> buildings = new ArrayList<>();

    public static Location getLocation(int X, int Y, int btnNum) {return getLocation(shiftCoordinate(X, dxdy[btnNum - 1][0]), shiftCoordinate(Y, dxdy[btnNum - 1][1]));}
    public static Location getLocation(int X, int Y) {                                                                                      //try to get location from a database
        Session session = ServerMain.sessionFactory.openSession();
        try (session) {
            Location location = session.createQuery("select l from Location l left join fetch l.buildings where l.X = :X and l.Y = :Y", Location.class).setParameter("X", X).setParameter("Y", Y).setCacheable(true).uniqueResult();
            if (location != null) {
                if (!Hibernate.isInitialized(location.buildings))
                    Hibernate.initialize(location.buildings);                                                                               //initialize buildings collection from the L2 cache or db on subsequent queries
                return location;
            } else
                logger.debug("location %d/%d (%d/%d) does not exist in database, generating a default location", X, Y, normalLocToLocal(X), normalLocToLocal(Y));
        } catch (Exception e) {                                                                                                             //database problem occurred
            e.printStackTrace();
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

    public int getLocBtnNum(User user) {                                                                                                    //return the minimap button number this location is corresponding to
        int userX = user.getParamInt(User.Params.X);                                                                                        //current user coordinates
        int userY = user.getParamInt(User.Params.Y);

        int col = normalLocToLocal(this.X) - normalLocToLocal(userX) + 2;                                                                   //get locNum[] indexes
        int row = normalLocToLocal(this.Y) - normalLocToLocal(userY) + 2;
        return ArrayUtils.get(ArrayUtils.get(locNums, row), col, ArrayUtils.INDEX_NOT_FOUND);                                               //get locNum value or -1 if indexes are out of bounds
    }

    public String getParamStr(Params param) {return ParamUtils.getParamStr(this, param.toString());}                                        //get user param value as different type
    public int getParamInt(Params param) {return ParamUtils.getParamInt(this, param.toString());}
    private String getParamXml(Params param) {return ParamUtils.getParamXml(this, param.toString());}                                       //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false

    public String getXml() {                                                                                                                //location XML representation
        StringJoiner sj = new StringJoiner("", "", "</L>");
        String locationParamsXml = golocParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<L ", ">")); // add location data
        sj.add(locationParamsXml);
        buildings.forEach(bld -> sj.add(bld.getXml()));                                                                                     //add buildings data
        return sj.toString();
    }
}
