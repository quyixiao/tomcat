/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */

package org.apache.catalina.tribes.util;

import java.text.MessageFormat;
import java.util.Hashtable;
import java.util.Locale;
import java.util.MissingResourceException;
import java.util.ResourceBundle;

/**
 * An internationalization / localization helper class which reduces
 * the bother of handling ResourceBundles and takes care of the
 * common cases of message formating which otherwise require the
 * creation of Object arrays and such.
 *
 * <p>The StringManager operates on a package basis. One StringManager
 * per package can be created and accessed via the getManager method
 * call.
 *
 * <p>The StringManager will look for a ResourceBundle named by
 * the package name given plus the suffix of "LocalStrings". In
 * practice, this means that the localized information will be contained
 * in a LocalStrings.properties file located in the package
 * directory of the classpath.
 *
 * <p>Please see the documentation for java.util.ResourceBundle for
 * more information.
 *
 * @author James Duncan Davidson [duncan@eng.sun.com]
 * @author James Todd [gonzo@eng.sun.com]
 * @author Mel Martinez [mmartinez@g1440.com]
 * @see java.util.ResourceBundle
 *
 * Locale locale = new Locale("zh");
 * ResourceBundle resb = new ResourceBundle.getBundle("resource",locale);
 * System.out.println(resb.getString("name"));
 *
 * 最后输出的是的是resource_zh.properties 中键的name值，类似的，其他语言只要实例化一个对应的Locale 作为参数传进ResourceBundle ，就可以
 * 确定用哪个语言的属性文件 。
 *
 * 通过上面描述，我们知道日志的国际化通过MessageFormat ，Locale ， ResourceBundle 这三个类进行操作管理，日志的操作频繁而且类似，
 * 我们需要一个更高的层的类对这些类进行封装，从而更加方便的使用Tomcat 中用StringManager 类对其他进行封装，它提供了两个getString()方法 。
 * 我们一般只会用到这两个方法，通过这两个方法即可根据JVM默认语言获取对应的语言属性文件里面的键为key 的值，当一个类里面要合一静待国际化
 * 日志时，只需要如下代码 。
 *
 * StringManagerr sm = StringManager.getManager("包路径");;
 * sm.getString("key");
 *
 * StringManager 的使用非常简单，但它的设计比较独特，我们知道在每个Java 包里都有对应的不同的语言的Properties文件，每个包下的Java 类的日志信息
 * 只要到对应的包下的Properties 文件中查找中就可以了，当一个类要获取属性文件的一个错误信息时，如果要通过StringManager 的方法获取，那么它
 * 必须实例化一个StringManager 对象，于是问题来了，整个Tomcat 应用这么多类，如果每个类都实例化一个StringManager对象，那么必然造成
 * 资源浪费，从设计的模式考虑，我们马上想到用单例模式，这样就可以解决重复实例化，浪费资源问题，但如果整个Tomcat 的所有的类都共用一个
 * StringManager 对象，那么又会存在另外一个问题，一个对象处理那么多的信息，且对象里面的同步操作，多线程执行下导致性能较低，Tomcat
 * 设计者采取了折中的巧妙处理，既不只用一个对象，也不用太多的对象，而为每一个Java包提供了一个StringManager 对象， 相当于一个Java包
 * 一个包一个单例，这个单例在包内被所有的类共享，各自的StringManager 对象管理各自包下的Properties文件，实现这种以Java 包为单位的
 * 单例模式 的主要代码如下：
 *
 * public static final synchronized StringManager getManager(String packageName ,Locale locale ){
 *     Map<Locale,StringManager > map = managers.get(packageName);
 *     if( map == null){
 *          map = new Hashtable<Locale,StringManager>() ;
 *          managers.put(packageName,map);
 *     }
 *      StringManager mgr = map.get(locale);
 *      if( mgr == null){
 *          mgr = new StringManager(packageName,locale);
 *          map.put(locale,mgr);
 *      }
        return mgr ;
 * }
 *
 *  类中维护了一个静态变量managers ,每个StringManager 实例存储在一个包以包名为键的Map 中，当获取StringManager 实例时。 先根据包名
 *  查找内存中是否已经存在包名对应的StringManager 对象，如果不存在 ，则实例化一个StringManager ,并且放到内存中，供下次直接读取内存。
 *  至此，一个以包为单位的单例模式就得以实现。
 *
 *  本节主要讨论日志中的国际化的实现，其中使用了JDK里面的三个类，MessageFormat ， Locale ,ResourceBundle ，而Tomcat 中利用StringManager
 *  类把这三个类封装起来 ，方便操作，而StringManager 类的设计展示了Tomcat 设计人员优秀思想，每一个Java 包对应一个StringManager 对象，
 *  折中的考虑使性能与资源得到同时的兼顾 。
 *
 *
 */
public class StringManager {

    /**
     * The ResourceBundle for this StringManager.
     */
    private ResourceBundle bundle;
    private Locale locale;

    /**
     * Creates a new StringManager for a given package. This is a
     * private method and all access to it is arbitrated by the
     * static getManager method call so that only one StringManager
     * per package will be created.
     *
     * @param packageName Name of package to create StringManager for.
     */
    private StringManager(String packageName) {
        String bundleName = packageName + ".LocalStrings";
        try {
            bundle = ResourceBundle.getBundle(bundleName, Locale.getDefault());
        } catch( MissingResourceException ex ) {
            // Try from the current loader (that's the case for trusted apps)
            // Should only be required if using a TC5 style classloader structure
            // where common != shared != server
            ClassLoader cl = Thread.currentThread().getContextClassLoader();
            if( cl != null ) {
                try {
                    bundle = ResourceBundle.getBundle(
                            bundleName, Locale.getDefault(), cl);
                } catch(MissingResourceException ex2) {
                    // Ignore
                }
            }
        }
        // Get the actual locale, which may be different from the requested one
        if (bundle != null) {
            locale = bundle.getLocale();
        }
    }

    /**
        Get a string from the underlying resource bundle or return
        null if the String is not found.

        @param key to desired resource String
        @return resource String matching <i>key</i> from underlying
                bundle or null if not found.
        @throws IllegalArgumentException if <i>key</i> is null.
     */
    public String getString(String key) {
        if(key == null){
            String msg = "key may not have a null value";

            throw new IllegalArgumentException(msg);
        }

        String str = null;

        try {
            str = bundle.getString(key);
        } catch(MissingResourceException mre) {
            //bad: shouldn't mask an exception the following way:
            //   str = "[cannot find message associated with key '" + key + "' due to " + mre + "]";
            //     because it hides the fact that the String was missing
            //     from the calling code.
            //good: could just throw the exception (or wrap it in another)
            //      but that would probably cause much havoc on existing
            //      code.
            //better: consistent with container pattern to
            //      simply return null.  Calling code can then do
            //      a null check.
            str = null;
        }

        return str;
    }

    /**
     * Get a string from the underlying resource bundle and format
     * it with the given set of arguments.
     *
     * @param key
     * @param args
     */
    public String getString(final String key, final Object... args) {
        String value = getString(key);
        if (value == null) {
            value = key;
        }

        MessageFormat mf = new MessageFormat(value);
        mf.setLocale(locale);
        return mf.format(args, new StringBuffer(), null).toString();
    }

    // --------------------------------------------------------------
    // STATIC SUPPORT METHODS
    // --------------------------------------------------------------

    private static Hashtable<String, StringManager> managers =
        new Hashtable<String, StringManager>();

    /**
     * Get the StringManager for a particular package. If a manager for
     * a package already exists, it will be reused, else a new
     * StringManager will be created and returned.
     *
     * @param packageName The package name
     */
    public static final synchronized StringManager getManager(String packageName) {
        StringManager mgr = managers.get(packageName);
        if (mgr == null) {
            mgr = new StringManager(packageName);
            managers.put(packageName, mgr);
        }
        return mgr;
    }

}
