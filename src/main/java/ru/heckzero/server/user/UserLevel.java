package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import org.hibernate.annotations.Immutable;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.util.Comparator;
import java.util.List;

@Cacheable
@Immutable
@Entity(name = "UserLevel")
@Table(name = "users_level")
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
public class UserLevel {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static List<UserLevel> userLevels;

    @Id
    private int exp;
    private int level,          stat,           maxrank,        perkpoints,     statup;
    private int max_hp_man,     max_psy_man,    exp_pve_man,    exp_pvp_man;
    private int max_hp_woman,   max_psy_woman,  exp_pve_woman,  exp_pvp_woman;
    private int quest_diff1,    quest_diff2,    quest_diff3,    quest_diff4,    quest_diff5;


    public UserLevel() { }

    //make sure list of UserLevel objects initialized
    private static void ensureInitialized() {
        if (userLevels != null)
            return;
        try (Session ses = ServerMain.sessionFactory.openSession()) {
            userLevels = ses.createQuery("select u from UserLevel u", UserLevel.class).list();
        }
        catch (NoResultException ex) { logger.error("can't load user level table: %s:%s", ex.getClass().getSimpleName(), ex.getMessage()); }
        userLevels.forEach(logger::info);
    }

    //get UserLevel state by User
    private static UserLevel getUserStatus(User usr) {
        ensureInitialized();
        return userLevels.stream()
                .filter(ulvl -> usr.getParamInt(User.Params.exp) >= ulvl.exp)
                .max(Comparator.comparingInt(o -> o.exp))
                .orElseGet(UserLevel::new);
    }

    public static int getLevel(User u) {
        return 0;
    }

    public static int getMaxHP(User u) {
        return 0;
    }

    public static int getMaxPsy(User u) {
        return 0;
    }


    //main stats
    public int getExp()         { return exp;           }
    public int getLevel()       { return level;         }
    public int getStat()        { return stat;          }
    public int getMaxRank()     { return maxrank;       }
    public int getPerkPoints()  { return perkpoints;    }
    public int getStatUp()      { return statup;        }

    //man
    public int getMax_hp_man()  { return max_hp_man;    }
    public int getMax_psy_man() { return max_psy_man;   }
    public int getExp_pve_man() { return exp_pve_man;   }
    public int getExp_pvp_man() { return exp_pvp_man;   }

    //woman
    public int getMax_hp_woman()    { return max_hp_woman;  }
    public int getMax_psy_woman()   { return max_psy_woman; }
    public int getExp_pve_woman()   { return exp_pve_woman; }
    public int getExp_pvp_woman()   { return exp_pvp_woman; }

    //quest
    public int getQuest_diff1() { return quest_diff1; }
    public int getQuest_diff2() { return quest_diff2; }
    public int getQuest_diff3() { return quest_diff3; }
    public int getQuest_diff4() { return quest_diff4; }
    public int getQuest_diff5() { return quest_diff5; }

    @Override
    public String toString() {
        return "UserLevel{" +
                "exp=" + exp +
                ", level=" + level +
                ", stat=" + stat +
                ", maxHPMan=" + max_hp_man +
                ", maxPsyMan=" + max_psy_man +
                ", expPvEMan=" + exp_pve_man +
                ", expPvPMan=" + exp_pvp_man +
                ", maxHPWoman=" + max_hp_woman +
                ", maxPsyWoman=" + max_psy_woman +
                ", expPvEWoman=" + exp_pve_woman +
                ", expPvPWoman=" + exp_pvp_woman +
                ", maxrank=" + maxrank +
                ", perkpoints=" + perkpoints +
                ", statup=" + statup +
                ", qestDiff1=" + quest_diff1 +
                ", qestDiff2=" + quest_diff2 +
                ", qestDiff3=" + quest_diff3 +
                ", qestDiff4=" + quest_diff4 +
                ", qestDiff5=" + quest_diff5 +
                '}';
    }
}