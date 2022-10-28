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


public class MyTaglibListener implements ServletContextListener {



    @Override
    public void contextInitialized(ServletContextEvent sce) {

        System.out.println("====MyTaglibListener======");

    }

    @Override
    public void contextDestroyed(ServletContextEvent sce) {

    }
}
