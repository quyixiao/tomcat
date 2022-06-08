package com.luban;


import org.eclipse.jdt.core.compiler.IProblem;
import org.eclipse.jdt.internal.compiler.Compiler;
import org.eclipse.jdt.internal.compiler.*;
import org.eclipse.jdt.internal.compiler.classfmt.ClassFileReader;
import org.eclipse.jdt.internal.compiler.env.ICompilationUnit;
import org.eclipse.jdt.internal.compiler.env.INameEnvironment;
import org.eclipse.jdt.internal.compiler.env.NameEnvironmentAnswer;
import org.eclipse.jdt.internal.compiler.impl.CompilerOptions;
import org.eclipse.jdt.internal.compiler.problem.DefaultProblemFactory;

import java.io.File;
import java.io.InputStream;
import java.util.HashMap;
import java.util.Locale;
import java.util.Map;

public class JDTCompile extends Exception {

    public static final File WORKDIR = new File("/test");

    public static void main(String[] args) throws Exception {
        // INameEnvironment 接口，它需要实现的主要方法是findType和isPackage ，FindType 有助于JDT 找到相应的Java 源文件或者Class 字节码 。
        // 根据传进来的包名和类名寻找，例如：传入了java.lang.String或org.apache.jsp.HelloWord_jsp ，则分别要找到 JDK 自带的String 字
        // 节码及Tomcat 中编译的HelloWord_jsp.java 文件，接着按要求封装这些对象，返回JDT 规定的NameEnvironmentAnswer 对象，而
        // isPackage 则提供子是否是包的判断 。
        INameEnvironment nameEnvironment = new INameEnvironment() {
            @Override
            public NameEnvironmentAnswer findType(char[][] compoundTypeName) {
                return findType(join(compoundTypeName));
            }

            @Override
            public NameEnvironmentAnswer findType(char[] typeName, char[][] packageName) {
                return findType(join(packageName) + "." + new String(typeName));
            }

            private NameEnvironmentAnswer findType(final String name) {
                File file = new File(WORKDIR, name.replace(".", "/") + ".java");
                if (file.isFile()) {
                    return new NameEnvironmentAnswer(new CompilationUnit(file), null);
                }
                try {
                    InputStream input = this.getClass().getClassLoader().getResourceAsStream(name.replace(".", "/") + ".class");
                    if (input != null) {
                        byte[] bytes = null; // IOUtils.toByteArray(input);
                        if (bytes != null) {
                            ClassFileReader classFileReader = new ClassFileReader(bytes, name.toCharArray(), true);
                            return new NameEnvironmentAnswer(classFileReader, null);
                        }

                    }
                } catch (Exception e) {
                    throw new RuntimeException(e);
                }
                return null;
            }

            @Override
            public boolean isPackage(char[][] parentPackageName, char[] packageName) {
                String name = new String(packageName);
                if (parentPackageName != null) {
                    name = join(parentPackageName) + "." + name;
                }
                File target = new File(WORKDIR, name.replace(".", "/"));
                return !target.isFile();
            }

            @Override
            public void cleanup() {

            }
        };

        ICompilerRequestor compilerRequestor = new ICompilerRequestor() {
            @Override
            public void acceptResult(CompilationResult result) {
                if (result.hasErrors()) {
                    for (IProblem problem : result.getErrors()) {
                        String className = new String(problem.getOriginatingFileName()).replace("/", ".");
                        className = className.substring(0, className.length() - 5);
                        String message = problem.getMessage();
                        if (problem.getID() == IProblem.CannotImportPackage) {
                            message = problem.getArguments()[0] + " cannot be resolved ";
                        }
                        throw new RuntimeException(className + ":" + message);
                    }
                }

                ClassFile[] classFiles = result.getClassFiles();
                for (int i = 0; i < classFiles.length; i++) {
                    String className = join(classFiles[i].getCompoundName());
                    File target = new File(WORKDIR, className.replace(".", "/") + ".class");
                    try {
                        //FileUtils.writeByteArrayToFile(target, classFile[i].getBytes());
                    } catch (Exception e) {
                        throw new RuntimeException(e);
                    }
                }
            }
        };

        // IProblemFactory 接口，主要用于控制编译错误信息的格式
        IProblemFactory problemFactory = new DefaultProblemFactory(Locale.ENGLISH);
        // IErrorHandlingPolicy 接口，用于描述错误策略，可直接使用DefaultErrorHandlingPolicies.exitOnFirstError() ，如果表示第一个错误
        // 就退出编译
        IErrorHandlingPolicy policy = DefaultErrorHandlingPolicies.exitOnFirstError();
        // CompilerOptions 对象，指定编译时的一些参数，例如，这里的指定的编译的Java 版本为1.7
        Compiler jtdCompiler = new Compiler(nameEnvironment, policy, getCompilerOptions(), compilerRequestor, problemFactory);
        // IComilerRequestor 接口，它只是一个acceptResult 方法，这个方法用于处理编译后的结果，如果包含了错误信息，则抛出异常，
        // 否则，把编译成功的字节码写到指定的路径HelloWord_jsp.class 文件中，即生成字节码 。
        jtdCompiler.compile(new ICompilationUnit[]{new CompilationUnit(new File(WORKDIR, "org/apache/jsp/HelloWord_jsp.java"))});
    }


    public static CompilerOptions getCompilerOptions() {
        Map settings = new HashMap();
        String javaVersion = CompilerOptions.VERSION_1_7;
        settings.put(CompilerOptions.OPTION_Source, javaVersion);
        settings.put(CompilerOptions.OPTION_Compliance, javaVersion);
        return new CompilerOptions(settings);
    }


    private static class CompilationUnit implements ICompilationUnit {
        private File file;

        public CompilationUnit(File file) {
            this.file = file;
        }

        public char[] getContents() {
            try {
                //return FileUtils.readFiletoString(file).toCharArray();
                return null;
            } catch (Exception e) {
                throw new RuntimeException(e);
            }
        }

        @Override
        public char[] getMainTypeName() {
            return file.getName().replace(".java", "").toCharArray();
        }

        @Override
        public char[][] getPackageName() {
            String fullPkgName = this.file.getParentFile().getAbsolutePath().replace(WORKDIR.getAbsolutePath(), "");
            fullPkgName = fullPkgName.replace("/", ".").replace("\\", ".");
            if (fullPkgName.startsWith(".")) {
                fullPkgName = fullPkgName.substring(1);
            }
            String[] items = fullPkgName.split("[.]");
            char[][] packageName = new char[items.length][];
            for (int i = 0; i < items.length; i++) {
                packageName[i] = items[i].toCharArray();
            }
            return packageName;
        }

        @Override
        public boolean ignoreOptionalProblems() {
            return false;
        }

        @Override
        public char[] getFileName() {
            return this.file.getName().toCharArray();
        }
    }

    public static String join(char[][] chars) {
        StringBuilder sb = new StringBuilder();
        for (char[] item : chars) {
            if (sb.length() > 0) {
                sb.append(".");
            }
            sb.append(item);
        }
        return sb.toString();
    }
}
