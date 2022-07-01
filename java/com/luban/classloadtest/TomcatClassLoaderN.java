package com.luban.classloadtest;

import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.InputStream;

public class TomcatClassLoaderN extends ClassLoader {
    private String name;

    public TomcatClassLoaderN(ClassLoader parent, String name) {
        super(parent);
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    public Class<?> loadClass(String name) throws ClassNotFoundException {
        Class<?> clazz = null;
        ClassLoader system = getSystemClassLoader();
        try {
            clazz = system.loadClass(name);
        } catch (Exception e) {
            e.printStackTrace();
        }
        if (clazz != null) {
            return clazz;
        }
        clazz = findClass(name);
        return clazz;
    }


    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        InputStream is = null;
        byte[] data = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            is = new FileInputStream(new File("/Users/quyixiao/github/pitpat-server/pitpat-admin/target/classes/com/test/xxx/Test.class"));
            int c = 0;
            while (-1 != (c = is.read())) {
                baos.write(c);
            }
            data = baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
                baos.close();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return this.defineClass(name, data, 0, data.length);
    }

    public static void main(String[] args) {
        TomcatClassLoaderN loader = new TomcatClassLoaderN(TomcatClassLoaderN.class.getClassLoader(), "TomcatLoaderN");
        Class clazz;
        try {
            clazz = loader.loadClass("com.luban.classloadtest.Test");
            Object o = clazz.newInstance();
        } catch (Exception e) {
            e.printStackTrace();
        }
    }
}
