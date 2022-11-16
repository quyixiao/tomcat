package com.luban;

import javax.annotation.Resource;
import javax.naming.Context;
import javax.servlet.ServletContextEvent;
import javax.servlet.ServletContextListener;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.sql.Statement;


public class DataSourceFiledListener implements ServletContextListener {

/*
    @Resource(lookup = "java:comp/env/jdbc/mysql")
    private DataSource dataSource;
*/


    @Override
    public void contextInitialized(ServletContextEvent sce) {
        System.out.println("……监听开始111……");
        Context ctx = null;
        Connection con = null;
        Statement stmt = null;
        ResultSet rs = null;


        System.out.println("DataSourceFiledListener类加载器=" + this.getClass().getClassLoader().toString());

      /*  try {


            System.out.println(dataSource);
            System.out.println("bbb============================" + dataSource);
            con = dataSource.getConnection();
            System.out.println("==con=====" + con);
            stmt = con.createStatement();
            System.out.println("==stmt=====" + stmt);
            rs = stmt.executeQuery("select * from lt_company ");
            while (rs.next()) {
                System.out.println(rs.getInt("id") + " " + rs.getInt("is_delete") + " " + rs.getString("company_name") + " ");
            }
        } catch (SQLException e) {
            e.printStackTrace();
        }*/

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}
