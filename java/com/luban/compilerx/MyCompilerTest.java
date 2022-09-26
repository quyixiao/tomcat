package com.luban.compilerx;

import com.luban.MyJDTCompiler;

import java.io.File;
import java.io.FileOutputStream;
import java.io.OutputStreamWriter;
import java.io.Writer;

public class MyCompilerTest {

    public static void main(String[] args) throws Exception {
        String content = "package com.luban.compilerx;\n" +
                "\n" +
                "\n" +
                "\n" +
                "\n" +
                "public class ReflictInvoke extends BaseInvoke {\n" +
                "\n" +
                "    public void invoke(){\n" +
                "        System.out.println(\"ReflictInvoke调用11111111\");\n" +
                "    }\n" +
                "}\n";

        // 如果文件不存在 ，则创建文件
        File file = new File("/Users/quyixiao/gitlab/tomcat/output/production/tomcat/com/luban/compilerx");
        if (!file.exists()) {
            file.mkdirs();
        }
        String sorceFile = "/Users/quyixiao/gitlab/tomcat/output/production/tomcat/com/luban/compilerx/ReflictInvoke.java";
        FileOutputStream fos = new FileOutputStream(sorceFile);
        Writer out = new OutputStreamWriter(fos, "UTF-8");
        out.write(content);
        out.close();
        fos.close();


        String outputDir = "/Users/quyixiao/gitlab/tomcat/output/production/tomcat";
        String packageName = "com.luban.compilerx";
        String targetClassName = "ReflictInvoke";

        MyJDTCompiler myJDTCompiler = new MyJDTCompiler();
        ClassLoader classLoader = MyCompilerTest.class.getClassLoader();
        myJDTCompiler.generateClass(sorceFile, outputDir, packageName, targetClassName, classLoader);

        Class<?> clazz = classLoader.loadClass("com.luban.compilerx.ReflictInvoke");

        BaseInvoke c = (BaseInvoke) clazz.newInstance();
        c.invoke();
    }

}
