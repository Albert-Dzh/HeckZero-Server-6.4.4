package ru.heckzero.server.world;

import org.apache.commons.lang3.StringUtils;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import org.hibernate.Hibernate;
import org.hibernate.Session;
import org.hibernate.query.Query;
import ru.heckzero.server.ServerMain;
import ru.heckzero.server.user.User;

import javax.persistence.Column;
import javax.persistence.Entity;
import javax.persistence.PrimaryKeyJoinColumn;
import javax.persistence.Table;
import java.util.EnumSet;
import java.util.StringJoiner;
import java.util.stream.Collectors;

@Entity(name = "CityHall")
@Table(name = "city_hall")
@PrimaryKeyJoinColumn(name = "ch_id")
public class CityHall extends Building {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static final EnumSet<Params> cityHallParams = EnumSet.of(Params.p1, Params.p2, Params.d1, Params.ds, Params.cash, Params.m1, Params.o, Params.vip /* Params.mod, Params.paint, Params.color*/);

    public static CityHall getCityHall(int id) {                                                                                            //try to get a CityHall instance by building id
        try (Session session = ServerMain.sessionFactory.openSession()) {
            Query<CityHall> query = session.createQuery("select ch from CityHall ch where ch.id = :id", CityHall.class).setParameter("id", id).setCacheable(true);
            CityHall cityHall = query.getSingleResult();
            Hibernate.initialize(cityHall.getLocation());                                                                                   //need by bank cells to get bank coordinates
            return cityHall;
        } catch (Exception e) {                                                                                                             //database problem occurred
            logger.error("can't load cityHall with id %d from database: %s", id, e.getMessage());
        }
        return new CityHall();
    }

    private int p1;
    private int p2;
    private int d1;
    private int ds;
    private int o;
    @Column(name = "mayor")
    private String m1;
    private int mod;
    private int vip;
//    int paint;
//    String color;

    protected CityHall() { }

    public String chXml() {                                                                                                                 //XML formatted city hall data
        StringJoiner sj = new StringJoiner("", "", "</MR>");
        sj.add(cityHallParams.stream().map(this::getParamXml).filter(StringUtils::isNotBlank).collect(Collectors.joining(" ", "<MR ", ">")));   //add XML bank params
        return sj.toString();
    }

    public void processCmd(User user, int p1, int p2, int d1, int ds, String m1, int o, int vip, int mod, int paint, String color) {
        user.sendMsg(chXml());
        return;
    }

}
