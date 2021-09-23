package ru.heckzero.server.user;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Session;
import org.hibernate.annotations.CacheConcurrencyStrategy;
import ru.heckzero.server.ServerMain;

import javax.persistence.*;
import javax.persistence.criteria.CriteriaBuilder;
import javax.persistence.criteria.CriteriaQuery;
import javax.persistence.criteria.Root;
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
    @Column(name = "max_hp_man") private int maxHPMan;
    @Column(name = "max_psy_man") private int maxPsyMan;
    @Column(name = "exp_pve_man") private int expPvEMan;
    @Column(name = "exp_pvp_man") private int expPvPMan;
    @Column(name = "max_hp_woman") private int maxHPWoman;
    @Column(name = "max_psy_woman") private int maxPsyWoman;
    @Column(name = "exp_pve_woman") private int expPvEWoman;
    @Column(name = "exp_pvp_woman") private int expPvPWoman;
    @Column(name = "quest_diff1") private int questDiff1;
    @Column(name = "quest_diff2") private int questDiff2;
    @Column(name = "quest_diff3") private int questDiff3;
    @Column(name = "quest_diff4") private int questDiff4;
    @Column(name = "quest_diff5") private int questDiff5;

    static {
        //read all table users_level select * from users_level
        userLevels.forEach(logger::info);
    }

    public UserLevel() { }




    public static UserLevel getFrom(User user) {
        int userExp = user.getParamInt(User.Params.exp);

        UserLevel lvl = null;

        try (Session ses = ServerMain.sessionFactory.openSession()) {
            CriteriaBuilder cb = ses.getCriteriaBuilder();
            CriteriaQuery<UserLevel> cr = cb.createQuery(UserLevel.class);
            Root<UserLevel> from = cr.from(UserLevel.class);
            cr.select(from)
                    .where(cb.lessThanOrEqualTo(from.get("exp"), userExp))
                    .orderBy(cb.desc(from.get("exp")));
            lvl = ses.createQuery(cr).getResultList().get(0);
        }
        catch (NoResultException ex) { ex.printStackTrace(); }

        return lvl;
    }

    //main stats
    public int getExp() { return exp; }
    public int getLevel() { return level; }
    public int getStat() { return stat; }
    public int getMaxrank() { return maxrank; }
    public int getPerkpoints() { return perkpoints; }
    public int getStatup() { return statup; }

    //man
    public int getMaxHPMan() { return maxHPMan; }
    public int getMaxPsyMan() { return maxPsyMan; }
    public int getExpPvEMan() { return expPvEMan; }
    public int getExpPvPMan() { return expPvPMan; }

    //woman
    public int getMaxHPWoman() { return maxHPWoman; }
    public int getMaxPsyWoman() { return maxPsyWoman; }
    public int getExpPvEWoman() { return expPvEWoman; }
    public int getExpPvPWoman() { return expPvPWoman; }

    //quest
    public int getQuestDiff1() { return questDiff1; }
    public int getQuestDiff2() { return questDiff2; }
    public int getQuestDiff3() { return questDiff3; }
    public int getQuestDiff4() { return questDiff4; }
    public int getQuestDiff5() { return questDiff5; }

    @Override
    public String toString() {
        return "UserLevel{" +
                "exp=" + exp +
                ", level=" + level +
                ", stat=" + stat +
                ", maxHPMan=" + maxHPMan +
                ", maxPsyMan=" + maxPsyMan +
                ", expPvEMan=" + expPvEMan +
                ", expPvPMan=" + expPvPMan +
                ", maxHPWoman=" + maxHPWoman +
                ", maxPsyWoman=" + maxPsyWoman +
                ", expPvEWoman=" + expPvEWoman +
                ", expPvPWoman=" + expPvPWoman +
                ", maxrank=" + maxrank +
                ", perkpoints=" + perkpoints +
                ", statup=" + statup +
                ", qestDiff1=" + questDiff1 +
                ", qestDiff2=" + questDiff2 +
                ", qestDiff3=" + questDiff3 +
                ", qestDiff4=" + questDiff4 +
                ", qestDiff5=" + questDiff5 +
                '}';
    }
}