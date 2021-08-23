package ru.heckzero.server;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.query.Query;

import javax.persistence.*;

@Entity(name = "Location")
@Table(name = "locations")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class Location {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final Integer DEF_LOC_TIME = 5;
    public static final int dxdy [ ][ ] = { {-1, -1}, {0, -1}, {1, -1}, {-1, 0}, {0, 0}, {1, 0}, {-1, 1}, {0, 1}, {1, 1} };

    private static int normalLocToLocal(int val) {return val > 180 ? (val - 360) : (val <= -180) ? (val + 360) : val;}
    private static int normalizeLoc(int val) {return val < 0 ? val + 360 : (val > 359 ? val - 360 : val);}
    public static int getShiftedCoordinate(int currCoordinate, int shift) {
        return normalizeLoc(currCoordinate + shift);
    }

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loc_generator_sequence")
    @SequenceGenerator(name = "loc_generator_sequence", sequenceName = "locations_id_seq", allocationSize = 1)
    private Integer id;

    @Column(name = "\"X\"")    private Integer X;                                                                                           //X,Y coordinate (server format) (0-359)
    @Column(name = "\"Y\"")    private Integer Y;
    private Integer tm;                                                                                                                     //location wait time (loc_time) sec
    private String t;                                                                                                                       //location surface type 1 symbol (A-Z)
    private String m;                                                                                                                       //location map itself
    private String n;                                                                                                                       //location music
    private String r;                                                                                                                       //Is there a road on the location (0 - no, 1 - yes)
    private String name;                                                                                                                    //name for the mini map and chat
    private String b;                                                                                                                       //Is there a cupola (0 - yes, 1 - no) (can user battle here)
    private String z;                                                                                                                       //number of artefacts (taken from world.swf)
    private String battlemap_f;                                                                                                             //type of battle map for the location
    private String danger;                                                                                                                  //danger level for the player (0-low, 1 - location is rangers protected, 2 - high)
    private String o;                                                                                                                       //radiation level if o < 999 or not accessible if o >=999
    private String p;                                                                                                                       //location road condition ?
    private String repair;                                                                                                                  //location road is being repairing  ?
    private String monsters;                                                                                                                //monster type (attribute m in <GOLOC/> reply)  from the loc_x.swf (monstersX_Y sprite) (see the function AddMonsters() in client)

    protected Location() { }

    private Location (Integer X, Integer Y) {
        this.X = X;
        this.Y = Y;
        this.tm = DEF_LOC_TIME;
        this.t = "A";
        this.m = "t1:19:8,t1:26:12";
        this.name = StringUtils.EMPTY;
        this.b = "1";
        this.o = X > 360 || Y > 360 || X <= -360 || Y <= -360 ? "999" : "";
        this.monsters = "2,2,2;3,3,3";
        return;
    }

    public static Location getLocation(Integer X, Integer Y) {
        Session session = ServerMain.sessionFactory.openSession();
        Query<Location> query = session.createQuery("select l from Location l where X=:X and Y = :Y", Location.class).setParameter("X", X).setParameter("Y", Y);
        try (session) {
            Location location = query.uniqueResult();
            return (location == null) ? new Location(X, Y) : location;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't execute query %s: %s", query.getQueryString(), e.getMessage());
        }
        return new Location(X, Y);                                                                                                          //in case of database error
    }

    public String getXML() {
        return String.format("<L X=\"%d\" Y=\"%d\" m=\"%s\" name=\"%s\" />", X, Y, m, name);
    }

}
