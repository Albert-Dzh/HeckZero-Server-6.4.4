package ru.heckzero.server.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.heckzero.server.user.User;

import javax.persistence.Entity;
import javax.persistence.FetchType;
import javax.persistence.JoinColumn;
import javax.persistence.ManyToOne;

@Entity(name = "HistoryUser")
public class HistoryUser extends History {
    private static final Logger logger = LogManager.getFormatterLogger();

    public static void add(int code, User user, String...params) {                                                                          //add a history record for use
        new HistoryUser(0, code, user, params).sync();
        return;
    }
    public static void addIms(int code, User user, String...params) {                                                                       //add a history record for user that will be sent as IMS
        new HistoryUser(1, code, user, params).sync();
        return;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;                                                                                                                      //User this record relates to

    private int ims;                                                                                                                        //if this event gonna be sent as an IMS on every user login
    protected HistoryUser() { }

    private HistoryUser(int ims, int code, User user, String...params) {                                                                    //history records are intended to be instantiated using add() methods
        super(code, params);
        this.ims = ims;
        this.user = user;
        return;
    }
}
