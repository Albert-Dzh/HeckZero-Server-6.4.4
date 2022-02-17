package ru.heckzero.server.utils;

import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;
import ru.heckzero.server.user.User;

import javax.persistence.*;

@Entity(name = "History")
@Table(name = "history")
public class HistoryUser extends History {
    private static final Logger logger = LogManager.getFormatterLogger();

    protected HistoryUser() { }

    public HistoryUser(int ims, long dt, int code, User user) {
        super(ims, dt, code);
        this.user = user;
    }

    @ManyToOne(fetch = FetchType.LAZY)
    @JoinColumn(name = "user_id")
    private User user;                                                                                                                      //User this record relates to


}
