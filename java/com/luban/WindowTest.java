package com.luban;

public class WindowTest {

    public static void main(String[] args) {
        String name = "'\"";
        name = name.replace("'", "\\'").replace("\"", "");
        System.out.println(name);
    }
}
