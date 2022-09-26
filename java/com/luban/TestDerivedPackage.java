package com.luban;

import org.apache.jasper.compiler.JspUtil;

public class TestDerivedPackage {

    public static void main(String[] args) {
        String jspUri = "/index.jsp";
        int iSep = jspUri.lastIndexOf('/');
String         derivedPackageName = (iSep > 0) ?
                JspUtil.makeJavaPackage(jspUri.substring(1,iSep)) : "";
        System.out.println(derivedPackageName);



        iSep = jspUri.lastIndexOf('/') + 1;
String         className = JspUtil.makeJavaIdentifier(jspUri.substring(iSep));
        System.out.println(className);
    }
}
