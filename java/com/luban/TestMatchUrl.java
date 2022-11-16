package com.luban;

public class TestMatchUrl {


    public static void main(String[] args) {


        System.out.println( matchFiltersURL("*.jsp", "/aaa."));

    }


    public static boolean matchFiltersURL(String testPath, String requestPath) {

        if (testPath == null)
            return (false);

        // Case 1 - Exact Match
        if (testPath.equals(requestPath))
            return (true);

        // Case 2 - Path Match ("/.../*")
        if (testPath.equals("/*"))
            return (true);
        if (testPath.endsWith("/*")) {
            if (testPath.regionMatches(0, requestPath, 0,
                    testPath.length() - 2)) {
                // testPath 为 /aaa/*
                // requestPath 为 /aaa 的情况
                if (requestPath.length() == (testPath.length() - 2)) {
                    return (true);
                    // testPath 为 /aaa/*
                    // requestPath 为 /aaa
                } else if ('/' == requestPath.charAt(testPath.length() - 2)) {
                    return (true);
                }
            }
            return (false);
        }

        // Case 3 - Extension Match
        // 如 testPath = *.jsp, requestPath = /aaa.jsp ,则匹配成功
        // 如 testPath = *.jsp, requestPath = /aaa.html 匹配失败
        // 如testPath = *.jsp, requestPath = /aaa. 匹配失败
        if (testPath.startsWith("*.")) {
            int slash = requestPath.lastIndexOf('/');
            int period = requestPath.lastIndexOf('.');
            if ((slash >= 0) && (period > slash)
                    && (period != requestPath.length() - 1)
                    && ((requestPath.length() - period)
                    == (testPath.length() - 1))) {
                return (testPath.regionMatches(2, requestPath, period + 1,
                        testPath.length() - 2));
            }
        }

        // Case 4 - "Default" Match
        return (false); // NOTE - Not relevant for selecting filters

    }
}
