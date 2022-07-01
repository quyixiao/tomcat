package com.luban.classloadtest;

public class Test {
    public Test() {
        System.out.println(this.getClass().getClassLoader().toString());
    }

    public static void main(String[] args) {
        System.out.println("===============");
    }
}
