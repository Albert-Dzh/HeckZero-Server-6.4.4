package ru.heckzero.server.user;

import org.apache.commons.lang3.math.NumberUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import javax.persistence.Column;
import javax.persistence.Embeddable;
import javax.persistence.Transient;
import java.lang.reflect.Field;

@Embeddable
class UserParams {
    private static Logger logger = LogManager.getFormatterLogger();

    @Transient                                                                                                                              //transient params first
    private String nochat;                                                                                                                  //user chat status (whether he has his chat channel != null)

    private String login, password, email;                                                                                                  //ask the Captain Obvious about that params
    private Long reg_time;                                                                                                                  //user registration time (epoch)
    private Long lastlogin;                                                                                                                 //user last login time, needed for computing new loc_time
    private Long lastlogout;                                                                                                                //user last logout time epoch
    private Long lastclantime;                                                                                                              //last time the user has left his clan
    private Long loc_time;                                                                                                                  //allowed time to leave location (epoch)
    private Long cure_time;                                                                                                                 //time of completion of treatment (epoch)
    private String god;                                                                                                                     //is user BoDun ?
    private Integer hint;                                                                                                                   //user custom settings used in PDA
    private Integer exp;                                                                                                                    //experience
    private Integer pro;                                                                                                                    //profession
    private Double propwr;                                                                                                                  //profession power (rate) (0-1)
    private Integer rank_points;                                                                                                            //frags
    private String clan, clan_img;                                                                                                          //clan name and clan image
    private Integer clr;                                                                                                                    //chat font color
    private String img;                                                                                                                     //user avatar image in i/avatar client folder
    private String alliance;                                                                                                                //user's alliance
    private Integer man;                                                                                                                    //man - 1, woman - 0
    @Column(name = "\"HP\"")   private Integer HP;                                                                                          //Health Points (HP)
    private Integer psy;                                                                                                                    //psychic energy level
    private Integer stamina, str, dex, pow, acc, intel;                                                                                     //user "stats" stamina, strength, dexterity, accuracy, intellect
    @Column(name = "int")      private Integer intu;                                                                                        //intuition ("int" in db, "intu" here, cause the word "int" is reserved in java)
    private Integer sk0, sk1, sk2, sk3, sk4, sk5, sk6, sk7, sk8, sk9, sk10, sk11, sk12;                                                     //skills level
    @Column(name = "\"X\"")    private Integer X;                                                                                           //X coordinate
    @Column(name = "\"Y\"")    private Integer Y;                                                                                           //Y coordinate
    @Column(name = "\"Z\"")    private Integer Z;                                                                                           //Z - house number within a location
    private Integer hz;                                                                                                                     //hz - house type
    @Column(name = "\"ROOM\"") private Integer ROOM;                                                                                        //Z - room number within a house
    private Long id1, id2;                                                                                                                  //items id generator borders
    private Integer i1;                                                                                                                     //item id generator cursor
    private String ne, ne2;                                                                                                                 //tell what it is
    private Integer cup_0, cup_1, cup_2, p78money;                                                                                          //copper and perk coins
    private Double silv, gold;                                                                                                              //silver and gold coins
    private Integer acc_flags;                                                                                                              //some account options, still not well known
    private String siluet;                                                                                                                  //siluet description
    private Integer bot;                                                                                                                    //0 - player is a human, 1- bot
    private String name, city, about, note;                                                                                                 //user self-given params in "about" section of PDA
    private String list;                                                                                                                    //user contact list
    private String plist;                                                                                                                   //perk list
    @Column(name = "\"ODratio\"") private Integer ODratio;                                                                                  //OD ratio affected on user
    private Integer virus;                                                                                                                  //biological infection
    private String brokenslots;                                                                                                             //broken slots list. each slot represents by a single letter
    private Integer poisoning;                                                                                                              //poison level
    private Integer ill;                                                                                                                    //infection with the virus X (-1...1)
    private String illtime;                                                                                                                 //virus X infection duration
    private Double sp_head, sp_left, sp_right, sp_foot;                                                                                     //slot damage
    private Double eff1, eff2, eff3, eff4, eff5, eff6, eff7, eff8, eff9, eff10;                                                             //negative effects which affect the user
    private Integer rd;                                                                                                                     //reconstructions count made since last UP
    private Integer rd1;                                                                                                                    //stats available for free reconstruction
    private Integer t1, t2;                                                                                                                 //training1,2 setting are for the initial quest
    private String dismiss;                                                                                                                 //user block reason, we treat the user as blocked if it's not empty
    private Integer chatblock, forumblock;                                                                                                  //user chat or forum is blocked (banned) by moderator (cop)


    void setParam(User.Params paramName, Object paramValue) {                                                                               //set user param value
        try {
            Field field = this.getClass().getDeclaredField(paramName.toString());                                                           //find a field of name paramName
            Class fieldType = field.getType();                                                                                              //found field type
            Class valueType = paramValue.getClass();                                                                                        //value type
            if (fieldType.equals(valueType)) {                                                                                              //if they are equals, just set the value
                field.set(this, paramValue);
                return;
            }
            String strValue = paramValue instanceof String ? (String)paramValue : paramValue.toString();                                    //cast or convert paramValue to String
            switch (fieldType.getSimpleName()) {                                                                                            //a short field type name (Integer, String, etc.)
                case "String" -> field.set(this, strValue);                                                                                 //just set a String value to String field type
                case "Integer" -> field.set(this, NumberUtils.isParsable(strValue) ? Math.toIntExact(Math.round(Double.parseDouble(strValue))) : 0); //convert String value to field type
                case "Long" -> field.set(this, NumberUtils.isParsable(strValue) ? Math.round(Double.parseDouble(strValue)) : 0L);
                case "Double" -> field.set(this, NumberUtils.isParsable(strValue) ? Math.round(Double.parseDouble(strValue) * 1000D) / 1000D  : 0D);
                default -> logger.error("can't set param %s, param type '%s' is not supported", paramName, fieldType.getSimpleName());
            }
        } catch (NoSuchFieldException | SecurityException | IllegalAccessException e) {
            logger.warn("cannot set param %s to value %s: %s", paramName, paramValue, e.getMessage());
        }
        return;
    }

    Object getParam(User.Params paramName) throws NoSuchFieldException, IllegalAccessException {                                            //try to find a field with the name equals to paramName
        Field field = this.getClass().getDeclaredField(paramName.toString());
        return field.get(this);                                                                                                             //and return it
    }
}
