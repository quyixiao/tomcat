package com.luban;

import org.apache.catalina.Globals;

import java.io.File;

public class TestUserDir {

    public static void main(String[] args) throws Exception {
        String userIdr = System.getProperty("user.dir"); //可以获取我们计算机的系统信息。
        System.out.println(userIdr);
        File bootstrapJar =
                new File(System.getProperty("user.dir"), "bootstrap.jar");
        System.out.println(bootstrapJar.exists());


        File statusFile =
                new File(System.getProperty("user.dir"), "STATUS.txt");

        System.out.println(statusFile.exists());

                        String catalinaHome = (new File(System.getProperty("user.dir"), ".."))
                                .getCanonicalPath();
        System.out.println(catalinaHome);

    }
}
