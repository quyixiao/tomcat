package com.luban.classloadertest;

public class Test {


    public Test() {
        System.out.println(this.getClass().getClassLoader().toString());
    }

    public static void main(String[] args) {

    }
}
