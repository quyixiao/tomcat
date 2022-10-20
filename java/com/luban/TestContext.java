package com.luban;

import java.io.File;
import java.io.IOException;

public class TestContext {

    public static void main(String[] args) {
        // Check for WARs with /../ /./ or similar sequences in the name
        File file = new File("/Users/quyixiao/gitlab/tomcat/webapps/../");
        String contextPath ="a/b/c/";
        //String contextPath ="a/b/c/./";
        //String contextPath ="a/b/c/../";
        System.out.println(validateContextPath(file,contextPath));
    }

    private static boolean validateContextPath(File appBase, String contextPath) {
        // More complicated than the ideal as the canonical path may or may
        // not end with File.separator for a directory

        StringBuilder docBase;
        String canonicalDocBase = null;

        try {
            String canonicalAppBase = appBase.getCanonicalPath();
            System.out.println(canonicalAppBase);
            docBase = new StringBuilder(canonicalAppBase);
            if (canonicalAppBase.endsWith(File.separator)) {
                docBase.append(contextPath.substring(1).replace(
                        '/', File.separatorChar));
            } else {
                docBase.append(contextPath.replace('/', File.separatorChar));
            }
            // At this point docBase should be canonical but will not end
            // with File.separator

            canonicalDocBase =
                    (new File(docBase.toString())).getCanonicalPath();

            // If the canonicalDocBase ends with File.separator, add one to
            // docBase before they are compared
            if (canonicalDocBase.endsWith(File.separator)) {
                docBase.append(File.separator);
            }
        } catch (IOException ioe) {
            return false;
        }

        // Compare the two. If they are not the same, the contextPath must
        // have /../ like sequences in it
        return canonicalDocBase.equals(docBase.toString());
    }

}
