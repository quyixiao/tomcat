package com.luban.classloadertest;

import java.io.*;

public class TomcatClassLoader  extends ClassLoader{

    private String name ;

    public TomcatClassLoader(ClassLoader parent, String name) {
        super(parent);
        this.name = name;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        InputStream is = null;
        byte [] data = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            is = new FileInputStream(new File(""));
            int c = 0 ;
            while(-1 !=(c = is.read())){
                baos.write(c);
            }
            data = baos.toByteArray();
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
                baos.close();
            }catch (IOException e ){
                e.printStackTrace();
            }
        }
        return this.defineClass(name,data,0,data.length);
    }

    @Override
    public String toString() {
        return this.name;
    }


    public static void main(String[] args) {

    }
}
