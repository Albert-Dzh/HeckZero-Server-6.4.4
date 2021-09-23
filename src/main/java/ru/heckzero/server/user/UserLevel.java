package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import java.util.Comparator;
import java.util.List;

@Entity(name = "UserLevel")
@Table(name = "users_level")
@Cacheable
@org.hibernate.annotations.Cache(usage = CacheConcurrencyStrategy.NONSTRICT_READ_WRITE, region = "default")
//TODO  remove @Column annotation, rename fields, load table data into usersLevels list in a static {}  block

public class UserLevel {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static List<UserLevel> userLevels;

    @Id
    private int exp;
    private int level;
    private int stat;
    private int maxrank;
    private int perkpoints;
    private int statup;
    private int max_hp_man;
    private int max_psy_man;
    private int exp_pve_man;
    private int exp_pvp_man;
    private int max_hp_woman;
    private int max_psy_woman;
    private int exp_pve_woman;
    private int exp_pvp_woman;
    private int quest_diff1;
    private int quest_diff2;
    private int quest_diff3;
    private int quest_diff4;
    private int quest_diff5;

    static {
        try (Session ses = ServerMain.sessionFactory.openSession()) {
            userLevels = ses.createQuery("select ul from UserLevel ul", UserLevel.class).list();
        }
        catch (NoResultException ex) { ex.printStackTrace(); }
        userLevels.forEach(logger::info);
    }

    public UserLevel() { }

    public static UserLevel getClosestState(User usr) {
        return userLevels.stream()
                .filter(ulvl -> usr.getParamInt(User.Params.exp) >= ulvl.exp)
                .max(Comparator.comparingInt(o -> o.exp))
                .orElseGet(UserLevel::new);
    }

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