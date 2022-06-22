package com.luban;

import org.apache.catalina.tribes.util.Arrays;

import java.io.File;

public class Testxx {

    public static void main(String[] args) throws Exception{
        File directory=new File("/Users/quyixiao/Desktop/subline");
        directory = directory.getCanonicalFile();
        String filenames[] = directory.list();
        System.out.println(Arrays.toString(filenames));
    }
}
