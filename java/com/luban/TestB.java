package com.luban;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.sql.DataSource;
import java.sql.Connection;

public class TestB {
    public static void main(String[] args) throws Exception {
        //获得对数据源的引用:
        Context ctx = new InitialContext();
        DataSource ds = (DataSource) ctx.lookup("java:comp/env/jdbc/myDB");
//获得数据库连接对象:
        Connection conn = ds.getConnection();
//返回数据库连接到连接池:
        conn.close();
    }
}
