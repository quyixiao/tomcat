package com.luban.classloadtest;

import java.io.*;

public class TomcatClassLoader extends ClassLoader {

    private String name;

    public TomcatClassLoader(ClassLoader parent, String name) {
        super(parent);
        this.name = name;
    }

    @Override
    public String toString() {
        return this.name;
    }

    @Override
    protected Class<?> findClass(String name) throws ClassNotFoundException {
        InputStream is = null;
        byte [] data = null;
        ByteArrayOutputStream baos = new ByteArrayOutputStream();
        try {
            is = new FileInputStream(new File("/Users/quyixiao/github/pitpat-server/pitpat-admin/target/classes/com/test/xxx/Test.class"));
            int c = 0 ;
            while ( -1 != (c = is.read())){
                baos.write(c);
            }
            data = baos.toByteArray();

        }catch (Exception e ){
            e.printStackTrace();
        }finally {
            try {
                is.close();
                baos.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }
        return this.defineClass(name,data,0 ,data.length);
    }

    public static void main(String[] args) {
        TomcatClassLoader loader = new TomcatClassLoader(TomcatClassLoader.class.getClassLoader() , "TomcatClassLoader");
        Class clazz ;
        try {
            clazz = loader.loadClass("com.test.xxx.Test");
            Object object =clazz.newInstance();

        }catch (Exception e){
            e.printStackTrace();
        }
    }


}
