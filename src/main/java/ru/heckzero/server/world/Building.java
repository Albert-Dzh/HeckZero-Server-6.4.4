package ru.heckzero.server.world;

import org.apache.commons.beanutils.converters.IntegerConverter;
import org.apache.commons.beanutils.converters.StringConverter;
import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ParamUtils;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.items.ItemBox;

import javax.persistence.*;
import java.util.EnumSet;
import java.util.stream.Collectors;

@Entity(name = "Building")
@Table(name = "locations_b")
@Inheritance(strategy = InheritanceType.JOINED)
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.READ_WRITE, region = "Building_Region")
public class Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final StringConverter strConv = new StringConverter(StringUtils.EMPTY);                                                  //type converters used in getParam***() methods
    private static final IntegerConverter intConv = new IntegerConverter(0);

    public enum Params {X, Y, Z, cash, txt, maxHP, HP, name, upg, maxl, repair, clan,      ds, city, p1, p2, clon, bigmap_city, bigmap_shown,   cost, cost2, cost3, free, tkey, key}
    private static final EnumSet<Params> bldParams = EnumSet.of(Params.X, Params.Y, Params.Z, Params.txt, Params.maxHP, Params.HP, Params.name, Params.upg, Params.maxl, Params.repair, Params.clan);

    @Transient protected ItemBox itemBox = null;                                                                                              //building Item box

    @Id
    @GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "loc_b_generator_sequence")
    @SequenceGenerator(name = "loc_b_generator_sequence", sequenceName = "locations_b_seq", allocationSize = 1)
    protected Integer id;

    @Column(name = "\"X\"") private int X = 20;                                                                                             //X,Y coordinate within a location
    @Column(name = "\"Y\"") private int Y = 8;
    @Column(name = "\"Z\"") private int Z = 0;                                                                                              //unique building number withing a location

    protected String txt = "!!!STUB!!!";                                                                                                      //the building visible name
    @Column(name = "\"maxHP\"") private String maxHP;
    @Column(name = "\"HP\"") private String HP;
    private int name = 188;                                                                                                                 //building type - "ruins" by default
    private String upg;
    private String maxl;
    private String repair;
    private String clan;                                                                                                                    //clan which owns the building
    private int cash;                                                                                                                       //amount of money stored in building cash

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "l_id")
    private Location location;                                                                                                              //location association

    protected Building() { }

    public boolean isEmpty() {return id == null;}

    public Integer getId() { return id; }
    public int getX()      {return location.getX();}
    public int getY()      {return location.getY();}
    public int getZ()      {return Z; }
    public int getName()   {return name;}
    public String getTxt() {return txt;}

    public Location getLocation() {return location;}                                                                                        //get the location this Building belongs to
    public String getParamStr(Params param) {return ParamUtils.getParamStr(this, param.toString());};
    public int getParamInt(Params param) {return intConv.convert(Integer.class, ParamUtils.getParamInt(this, param.toString()));}
    protected String getParamXml(Params param) {return ParamUtils.getParamXml(this, param.toString()); }                                    //get param as XML attribute, will return an empty string if value is empty and appendEmpty == false
    protected String getXml() {return bldParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<B ", "/>"));}

    public ItemBox getItemBox() {return itemBox == null ? (itemBox = ItemBox.init(ItemBox.BoxType.BUILDING, id, true)) : itemBox;}          //get the building itembox, initialize if needed

    synchronized public int decMoney(int amount) {
        amount = Math.min(amount, cash);
        logger.info("decreasing building id %d '%s' cash by %d", getId(), getTxt(), amount);
        cash -= amount;
        sync();
        return amount;
    }
    synchronized public boolean addMoney(int amount) {
        logger.info("increasing building id %d '%s' cash by %d", getId(), getTxt(), amount);
        cash += amount;
        return sync();
    }

    public boolean sync() {                                                                                                                 //force=true means sync the item anyway, whether needSync is true
        logger.info("syncing building id %d '%s'", getId(), getTxt());
        if (!ServerMain.sync(this)) {
            logger.error("can't sync building id %d", getId());
            return false;
        }
        return true;
    }
}
