package com.luban.transformlet;


import javassist.ClassPool;
import javassist.CtClass;
import javassist.LoaderClassPath;

import java.io.ByteArrayInputStream;
import java.io.IOException;
import java.util.Set;

/**
 * @author Jerry Lee (oldratlee at gmail dot com)
 * @since 2.6.0
 */
class Utils {

    static CtClass getCtClass(final byte[] classFileBuffer, final ClassLoader classLoader) throws IOException {
        final ClassPool classPool = new ClassPool(true);
        if (classLoader == null) {
            classPool.appendClassPath(new LoaderClassPath(ClassLoader.getSystemClassLoader()));
        } else {
            classPool.appendClassPath(new LoaderClassPath(classLoader));
        }
        final CtClass clazz = classPool.makeClass(new ByteArrayInputStream(classFileBuffer), false);
        clazz.defrost();
        return clazz;
    }


    public static boolean isNotNull(String str) {
        if (str != null && str != "") {
            return true;
        }
        return false;
    }



    public static boolean contains(Set<String> includeClassNames, String className) {
        for (String include : includeClassNames) {
            if (className.contains(include)) {
                return true;
            }
        }
        return false;
    }

    public static boolean endContains(Set<String> includeClassNames, String className) {
        for (String include : includeClassNames) {
            if (className.endsWith(include)) {
                return true;
            }
        }
        return false;
    }


    
    
    


}
