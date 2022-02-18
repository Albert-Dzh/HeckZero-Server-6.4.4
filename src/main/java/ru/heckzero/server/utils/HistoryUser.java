package ru.heckzero.server.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.heckzero.server.user.User;

import javax.persistence.*;

@Entity(name = "HistoryUser")
public class HistoryUser extends History {
    private static final Logger logger = LogManager.getFormatterLogger();

    public static void add(int code, User user, String...params) {                                                                           //add a history record for use
        new HistoryUser(code, user, params).sync();
    }

    public static void addIms(int code, User user, String...params) {                                                                       //add a history record for user that will be sent as IMS
        new HistoryUser(1, code, user, params).sync();
    }

    protected HistoryUser() { }

    private HistoryUser(int ims, int code, User user, String...params) {
        super(code, params);
        this.ims = ims;
        this.user = user;
        return;
    }
    private HistoryUser(int code, User user, String...params) {
        super(code, params);
        this.user = user;
        return;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;                                                                                                                      //User this record relates to

    private int ims = 0;                                                                                                                    //send this message as IMS upon every user login

}
