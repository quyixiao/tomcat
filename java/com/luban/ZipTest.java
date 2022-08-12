package com.luban;

import org.apache.naming.resources.WARDirContext;

import java.util.Arrays;
import java.util.Enumeration;
import java.util.zip.ZipEntry;
import java.util.zip.ZipFile;

public class ZipTest {

    public static void main(String[] args) throws Exception {

        ZipFile     base = new ZipFile("/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0.war");
        Enumeration<? extends ZipEntry> entryList = base.entries();
        while (entryList.hasMoreElements()) {
            ZipEntry entry = entryList.nextElement();
            System.out.println(entry.getName());
        }
    }



    /**
     * Entries structure.
     */
    protected static class Entry implements Comparable<Object> {


        // -------------------------------------------------------- Constructor


        public Entry(String name, ZipEntry entry) {
            this.name = name;
            this.entry = entry;
        }


        // --------------------------------------------------- Member Variables


        protected String name = null;


        protected ZipEntry entry = null;


        protected volatile boolean childrenSorted = false;


        // ----------------------------------------------------- Public Methods


        @Override
        public int hashCode() {
            return name.hashCode();
        }

        public ZipEntry getEntry() {
            return entry;
        }


        public String getName() {
            return name;
        }

        @Override
        public int compareTo(Object o) {
            return 0;
        }
    }

}
