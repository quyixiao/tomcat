package com.luban;

import java.util.regex.Matcher;
import java.util.regex.Pattern;

public class TestMatch {

    public static void main(String[] args) {

        Pattern filter = Pattern.compile("[1][3-9][0-9]{9}");
        Matcher  matcher = filter.matcher("18378195149");

        if (matcher.matches()) {
            System.out.println("被 匹配上了");
        }
    }
}
