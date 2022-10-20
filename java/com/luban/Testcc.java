package com.luban;

import java.io.File;

public class Testcc {
    public static void main(String[] args) {
        File file = new File("oidsids");
        System.out.println(file.lastModified());
    }
}
