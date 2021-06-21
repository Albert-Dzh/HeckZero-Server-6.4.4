package ru.heckzero.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.sql.SQLException;

public class DbUtil {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static HikariDataSource dataSource;
    private static QueryRunner queryRunner;

    static {
        HikariConfig config = new HikariConfig("conf/hikari.properties");
        dataSource = new HikariDataSource(config);
        queryRunner = new QueryRunner(dataSource);
    }

    public static <T> T query(String sql , ResultSetHandler<T> resultSetHandler, Object... params) throws SQLException {
        return queryRunner.query(sql, resultSetHandler, params);
    }

    public static int insert(String sql, Object... params) throws SQLException {
        return queryRunner.update(sql, params);
    }

    public static <T> T insert(String sql, ResultSetHandler<T> resultSetHandler, Object... params) throws SQLException {
         return queryRunner.insert(sql, resultSetHandler, params);
    }

    public static void close() {
        dataSource.close();
    }
}
