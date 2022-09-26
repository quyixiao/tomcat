package com.luban;

import org.apache.tomcat.util.net.URL;

public class TestLastIndex {


    public static void main(String[] args) {
       String uri = "/home/admin.jsp";
       String uriPath  = "";
        int index = uri.lastIndexOf('/');
        if (index >=0 ) {
            uriPath = uri.substring(0, index+1);
        }
        String uriExtension = null;
        index = uri.lastIndexOf('.');
        if (index >=0) {
            uriExtension = uri.substring(index+1);
        }
        System.out.println(uriPath);
        System.out.println(uriExtension);

    }
}
