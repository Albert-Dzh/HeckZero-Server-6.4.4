package ru.heckzero.server;

import com.zaxxer.hikari.HikariConfig;
import com.zaxxer.hikari.HikariDataSource;
import org.apache.commons.dbutils.QueryRunner;
import org.apache.commons.dbutils.ResultSetHandler;
import org.apache.commons.dbutils.handlers.BeanHandler;
import org.apache.commons.dbutils.handlers.BeanListHandler;
import org.apache.commons.dbutils.handlers.MapHandler;
import org.apache.commons.dbutils.handlers.MapListHandler;
import org.apache.logging.log4j.LogManager;
import org.apache.logging.log4j.Logger;

import java.util.List;
import java.util.Map;

public class DbUtil {
    private static final Logger logger = LogManager.getFormatterLogger();
    private static HikariDataSource dataSource;
    private static QueryRunner queryRunner;

    static {
        HikariConfig config = new HikariConfig("conf/hikari.properties");
        dataSource = new HikariDataSource(config);
        queryRunner = new QueryRunner(dataSource);
    }

    public static <T> T query(String sql , ResultSetHandler<T> resultSetHandler, Object... params) {
        T result = null;
        try {
            result = queryRunner.query(sql, resultSetHandler, params);
        }catch (Exception e) {
            logger.error(e);
        }
        return result;
    }

    public static int update(String sql, Object... params){
        int result = 0;
        try {
            result = queryRunner.update(sql, params);
        } catch (Exception e) {
            logger.error(e);
        }
        return result;
    }
    public static int insert(String sql,Object... params ){
        int result = 0;
        try {
            result = queryRunner.execute(sql,params);
        }catch (Exception e) {
            logger.error("", e);
        }
        return result;
    }
    public static Map<String,Object> findById(String table, int id) {
        String sql = "select * from " + table  + " where id = ?";
        return query(sql, new MapHandler(), id);
    }
    public static <T> T findById(String table , int id , BeanHandler<T> beanHandler){
        String sql = "select * from " + table  +" where id = ?";
        return query(sql, beanHandler,id);
    }
    public static List<Map<String,Object>> findByCondition(String table, String condition){
        String sql = "select * from "+ table + " where " + condition;
        return query(sql, new MapListHandler());
    }

    public static <T> List<T> findByCondition(String table, String condition , BeanListHandler<T> beanListHandler ) {
        String sql = "select * from "+table +" where "+ condition;
        return query(sql, beanListHandler);
    }

    public static List<Map<String,Object>> findByCondition(String table, String condition, String sort) {
        String sql = "select * from "+ table + " where "+ condition + "order by " + sort;
        return query(sql, new MapListHandler());
    }
    public static List<Map<String,Object>> findByCondition(String table, String condition,String sort,String limit){
        String sql = "select * from "+table +" where "+ condition + "order by "+ sort + limit;
        return query(sql, new MapListHandler());
    }
    public static void close() {
        dataSource.close();
    }
}
