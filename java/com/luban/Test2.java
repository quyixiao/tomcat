package com.luban;

import java.lang.reflect.Method;

public class Test2 {

    public static void main(String[] args) {
        try {





            Method mainMethod = Class.forName("org.apache.catalina.startup.Bootstrap").getMethod("main", String[].class);
            mainMethod.invoke(null, new Object[]{new String[]{"start"}});




        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
