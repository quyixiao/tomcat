/*
 * Licensed to the Apache Software Foundation (ASF) under one or more
 * contributor license agreements.  See the NOTICE file distributed with
 * this work for additional information regarding copyright ownership.
 * The ASF licenses this file to You under the Apache License, Version 2.0
 * (the "License"); you may not use this file except in compliance with
 * the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */


package org.apache.catalina.startup;


import java.io.File;
import java.io.IOException;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Set;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;


/**
 * <p>Utility class for building class loaders for Catalina.  The factory
 * method requires the following parameters in order to build a new class
 * loader (with suitable defaults in all cases):</p>
 * <ul>
 * <li>A set of directories containing unpacked classes (and resources)
 *     that should be included in the class loader's
 *     repositories.</li>
 * <li>A set of directories containing classes and resources in JAR files.
 *     Each readable JAR file discovered in these directories will be
 *     added to the class loader's repositories.</li>
 * <li><code>ClassLoader</code> instance that should become the parent of
 *     the new class loader.</li>
 * </ul>
 *
 * @author Craig R. McClanahan
 */

public final class ClassLoaderFactory {


    private static final Log log = LogFactory.getLog(ClassLoaderFactory.class);

    // --------------------------------------------------------- Public Methods


    /**
     * Create and return a new class loader, based on the configuration
     * defaults and the specified directory paths:
     *
     * @param unpacked Array of pathnames to unpacked directories that should
     *  be added to the repositories of the class loader, or <code>null</code>
     * for no unpacked directories to be considered
     * @param packed Array of pathnames to directories containing JAR files
     *  that should be added to the repositories of the class loader,
     * or <code>null</code> for no directories of JAR files to be considered
     * @param parent Parent class loader for the new class loader, or
     *  <code>null</code> for the system class loader.
     *
     * @exception Exception if an error occurs constructing the class loader
     *
     * 类加载器工厂 -ClassLoaderFactory
     *
     * Java 虚拟机利用类加载器将类载入内存的过程中，类加载器要做很多的事情，例如，读取字节数组，验证，解析，初始化等，Java 提供了URLClassLoader
     * 类能方便的将Jar,Class 或网络资源加载到内存中，Tomcat 则用一个工厂类ClassLoaderFactory把创建类加载器细节进行寺封装，通过它可以很方便的创建自定义
     * 类加载器。
     * 如图13.4 所示，利用createClassLoader 方法并传入     资源路径和父类加载器好可创建一个自定义类载器，此类加载器负责传入所有资源 。
     *
     * ClassLoaderFactory 有个内部类Repository ，它就是表示资源类，资源类型用一个RepositoryType枚举表示 。
     *
     * public static enum RepositoryType{DIR,GLOB,JAR,URL};
     * 1.DIR 表示整个目录下的资源 ，包括所有的Class,Jar 包及其他类型的资源 。
     * 2.GLOB : 表示整个目录下所有的Jar 包资源，仅仅是.jar 后缀的资源 。
     * 3. JAR ：表示单个Jar包资源 。
     * 4. URL：表示URL 上获取的Jar包资源 。
     *
     * 通过上面的介绍，读者已经对ClassLoaderFactory 类有所了解，下面用一个简单的例子展示Tomcat 中的常见的类加载器是如何利用ClassLoaderFactory
     * 工厂类来创建，代码如下 ：
     *
     * List<Repository> repositories = new ArrayList<Repository>();
     * repositories.add(new Repository("${catalina.home}/lib",RepositoryType.DIR));
     * repositories.add(new Repository("${catalina.home}/lib",RepositoryType.GLOB));
     * repositories.add(new Repository("${catalina.base}/lib",RepositoryType.DIR));
     * repositories.add(new Repository("${catalina.base}/lib",RepositoryType.DIR));
     * ClassLoaderparent = null ;
     * ClassLoader commonLoader = ClassLoaderFactory.createClassLoader(repositories,parent);
     *
     * 至此，Common类加载器创建完毕，其中，${catalina.home}与${catalina.base}表示变量，它的值分别为Tomcat安装目录与Tomcat 的工作目录   。
     * Parent 为父类加载器，如果它设置为null,ClassLoaderFactory 创建时会使用默认的父类加载器，即系统类加载器，总结起来，只需要以下几步
     * 就能完成一个类加载器的创建，首先，把要加载的资源都添加到一个列表中，其实，确定父类加载器，默认的设置为null,最后，把这些作为参数
     * 传入ClassLoaderFactory 工厂类。
     *
     * 假如我们不确定要加载资源在网络上还是本地的，那么可以使用下面方式进行处理。
     * try{
     *     URL url  = new URL("路径 ");
     *     repositories.add(new Repository("路径",RepositoryType.URL));
     * }catch(MalformedURLException e ){
     *
     * }
     *
     *  这种方式处理得比较巧妙，URL在实例化时就可以检查这个路径的有效性，假如为本地资源或网络上不存在此路径的资源，那么将抛出异常，不会把此
     *  路径添加到资源列表中。。。
     *  ClassLoaderFactory 工厂类最终将资源转换成URL[] 数组，因为ClassLoaderFactory 生成的类加载器是继承于URLClassLoader 的，而
     *  URLClassLoader的构造函数只支持URL[]数组，从Repository类转换成URL[] 数组可分为以下几种情况 。
     *
     *  1. 若为RepositoryType.URL 类型的资源，则直接创建一个URL实例并把它添加到URL[]数组即可 。
     *  2. 若为RepositoryType.DIR类型的资源，则要把File类型转化为URL类型，由于URL 类用于网络，带有明显的协议，于是把本地文件的协议设定为
     *  file,即处理new URL("file:/D:/test/"） 末尾的"/"切记要加上，它表示D盘test整个目录下的所有资源，最后，把这个URL实例添加到URL[]数组中。
     *  3.若为RepositoryType.JAR 类型的资源，则与处理RepositoryType.DIR 类型的资源类似，本地文件协议为file，处理为new URL("file:/D:test/test.jar");
     *  然后把URL实例添加到URL []数组中。
     *  4.若为RepositoryType.GLOB类型的资源 ，则找到目录下的所有文件，然后判断是不是以jar后缀结尾，如果是，则与处理的RepositoryType.JAR
     *  类型的资源一样进行转换，再将URL实例添加到URL[]数组中，如果不是以.jar 结尾，则直接忽略 。
     *  现在读取ClassLoaderFactory 有了更深的了解，知道怎样轻松的建立一个类加载器实例了解其细节实现。
     *
     *  前面提到Tomcat 会创建Common 类加载器，Catalina类加载器和共享加载器三个类加载器供自己使用，这三个其实是同一个类加载器对象，Tomcat
     *  创建类加载器后马上就将其设置成当前线程类加载器，即Thread.currentThread().setContextClassLoader(CatalinaLoader)；
     *  这里主要是为了避免后面加载类时加载不成功，下面将举一个典型的例子说明如何利用URLClassLoader 加载指定的Jar包，并且解析由此
     *  引起的加载失败问题。
     *
     *  1.在Java中，我们用完全匹配类名来标识一个类，即用包名和类名，而在JVM中，一个类由完全匹配类名和一个类加载器的实例ID作为唯一的标识，
     *  也就是说，同一个虚拟机可以有两个包名，类名都相同的类，只要它们由两个不同的类加载器加载，而各自的类加载器中的类实例也是不同的。并且不能互相转换。
     *  2.在类加载器加载某个类时，一般会在类中引用，继承，扩展其他类，于是类加载器查找这些引用类也是一层一层往父类加载器去查找，最后查看自己，如果
     *  找不到，将会报出找不到类的错误，也就是说， 只会向上查找引用类，而不会向下从子类加载器中查找 。
     *  3.每个运行中的线程都有一个成员ContextClassLoader ，用来运行时动态地载入其他类，在没有显式的声明哪个类加载器加载类例如在程序中直接新
     *  建一个类，时，将默认由当前线程类加载器加载，即线程运行到需要加载新类时，用自己的类加载器对其进行加载，系统默认的为 ContextClassLoader
     *  是系统类加载器，所以一般而言，Java 程序在执行时可以使用JVM 自带的类， $JAVA_HOME/jre/lib/ext/ 中的类和$CLASSPATH/中的类。
     *
     *
     *  了解以以上三点，再对前面的加载时抛出的找不类的异常进行分析 。
     *
     *  当测试类运行命令时，这所以能正常运行是因为，运行时当前线程类加载器是系统类加载器，TestInterface接口类自然由它加载，URLClassLoader
     *  的默认父类加载器也是系统类加载器，由双亲委派机制得知，最后TestClassLoader 由系统类加载器加载，那么接口与类由同一个类加载器加载 。
     *  自然也就能找到类与接口并且进行转化 。
     *
     *  当测试类移到到Web 项目中时，假如将代码移到到Servlet里面，将直接报错，指出无法运行，其中运行是当前线程类加载器是WebApp类加载，
     *  而WebApp类加载器在交维生系统类加载器试图加载无果后，自己尝试加载类，所以TestInterface 接口类由WebApp类加载器加载，同样，URLClassLoader
     *  的父类加载器为系统类加载器，它负责加载TestClassLoader 类，于是，问题来了，两个不同的类加载器分别加载两个类， 两个不同的类加载器分别
     *  加载两个类，而且WebApp 类加载器又是系统类加载器的子孙类加载器，因为TestClassLoader 类扩展了TestInterface接口，所以当URLClassLoader
     *  加载TestClassLoader类扩展了TestInterface 接口，所以当URLClassLoader 加载TestClassLoader 时找不到Webapp类加载器中的TestInterface
     *  接口类，即抛出java.lang.ClassNotFoundException:com.test.TestInterface异常。
     *
     *  针对上以的错误：有两个种解决办法 。
     *
     *  既然是因为两个类的加载器加载而导致找不到类，那么简单的解决办法就是使这两个类由一个统一的类加载器载，即在加载testclassloader.jar 时
     *  用当前线程类加载器加载，只需要稍微的修改代码 。
     *
     *
     *
     *  URLClassLoader myClassLoader = new URLClassLoader(new URL[] {url}, Thread.currentThread().getContextClassLoader() );
     *
     *  重点是加粗部分，即在创建URLClassLoader对象时将当前类加载器作为父类加载器传入，WebApp当前线程类加载器是WebAppClassLoader，那么
     *  当加载器testclassLoader.jar 时，将优先交给WebAppClassLoader加载，这 样就可以保证两个类在同一个类加载器中，不会再报找不到类异常
     *  URLClassLoader 如果不设置父类加载器，它的默认父类加载器为系统类加载器，于是 testclassloader.jar 将由系统类加载器加载，复制得到
     *  $JAVA_HOME/jre/lib/ext 目录下，保证由URLClassLoader加载的类的引用类能从扩展类加载器中找到，问题同样得到解决 。
     *
     *
     *  讨论了这么多，回归到到Tomcat 中Thread.currentThread().setContextClassLoader(catalinaLoader);
     *  上面讨论典型类加载器错误在Tomcat 中同样存在，因此Tomcat 正是通过设置线程上下文类加载来解决，在Tomcat 中类加载器存在三种情况 。
     * Tomcat7 默认由Common ClassLoader类加载器加载 。
     * CommonLoader 的父类加载器是系统类加载器。
     * 当前线程类加载器是系统类加载器。
     *
     * 如图13.5所示 ，先看默认情况，ContextClassLoader被赋值为系统类加载器，系统类加载器看不见   Common 类加载器加载类的情况，即如果
     * 在过程上引用就会报找不到类的错误，所以启动Tomcat 的过程中肯定会报错，同时，它也能看到系统类加载器及其父类加载器所有加载的类。 简单
     * 地说，解决方法就是把Common 类加载器设置为线程上下文类加载器。
     *
     * 为避免类加载错误，应该尽早的设置线程上下文类加载器，所以在Tomcat中启动一初始化就马上设置，即初始化时马上通过Thread.currentThread()
     * .setContextClassLoader(catalinaLoader);
     *
     * 如图13.5 所示，先看默认情况，ContextClassLoader的父类加载器是系统类加载器。
     * 当前线程类加载器是系统类加载器。
     *
     * 如1.35 先看默认情况，ContextClassLoader 被赋值为系统类加载器。系统类加载器看不见Common类加载器加载的类，即如果通过引用就会找不到。
     * 类的错误，所以启动Tomcat 的过程中肯定会报错，接着看改进后的情况，把ContextClassLoader 赋值为Common类中的类，就不会报错了。
     * 同时它也能看到系统类加载器及其父类加载器所加载的类，简单的，解决方法就是把Common类加载器设置为线程上下文类加载器。
     *
     *  为了避免类加载器的错误，应该尽早设置线程上下文类加载器，所以在Tomcat中启动一初始化就马上设置，即初始化时马上通过Thread.currentThread().setContextClassLoader(catalinaLoader)
     *  设置线程上下文类加载器，此后此线程运行时默认由Common 类加载器载入类。
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     *
     */
    public static ClassLoader createClassLoader(File unpacked[],
                                                File packed[],
                                                final ClassLoader parent)
        throws Exception {

        if (log.isDebugEnabled())
            log.debug("Creating new class loader");

        // Construct the "class path" for this class loader
        Set<URL> set = new LinkedHashSet<URL>();

        // Add unpacked directories
        if (unpacked != null) {
            for (int i = 0; i < unpacked.length; i++)  {
                File file = unpacked[i];
                if (!file.exists() || !file.canRead())
                    continue;
                file = new File(file.getCanonicalPath() + File.separator);
                URL url = file.toURI().toURL();
                if (log.isDebugEnabled())
                    log.debug("  Including directory " + url);
                set.add(url);
            }
        }

        // Add packed directory JAR files
        if (packed != null) {
            for (int i = 0; i < packed.length; i++) {
                File directory = packed[i];
                if (!directory.isDirectory() || !directory.exists() ||
                    !directory.canRead())
                    continue;
                String filenames[] = directory.list();
                if (filenames == null) {
                    continue;
                }
                for (int j = 0; j < filenames.length; j++) {
                    String filename = filenames[j].toLowerCase(Locale.ENGLISH);
                    if (!filename.endsWith(".jar"))
                        continue;
                    File file = new File(directory, filenames[j]);
                    if (log.isDebugEnabled())
                        log.debug("  Including jar file " + file.getAbsolutePath());
                    URL url = file.toURI().toURL();
                    set.add(url);
                }
            }
        }

        // Construct the class loader itself
        final URL[] array = set.toArray(new URL[set.size()]);
        // 这种特权操作在Tomcat 很多的地方都可以看到，例如，ClassLoaderFactory中，这是一个所以生产类的工厂，在createClassLoader 方法中
        // 利用如下方式返回，这样一来就不会检查所有的调用ClassLoaderFactory 中的createClassLoader方法的其他实例权限
        return AccessController.doPrivileged(
                new PrivilegedAction<URLClassLoader>() {
                    @Override
                    public URLClassLoader run() {
                        if (parent == null)
                            return new URLClassLoader(array);
                        else
                            return new URLClassLoader(array, parent);
                    }
                });
    }


    /**
     * Create and return a new class loader, based on the configuration
     * defaults and the specified directory paths:
     *
     * @param repositories List of class directories, jar files, jar directories
     *                     or URLS that should be added to the repositories of
     *                     the class loader.
     * @param parent Parent class loader for the new class loader, or
     *  <code>null</code> for the system class loader.
     *
     * @exception Exception if an error occurs constructing the class loader
     */
    public static ClassLoader createClassLoader(List<Repository> repositories,
                                                final ClassLoader parent)
        throws Exception {

        if (log.isDebugEnabled())
            log.debug("Creating new class loader");

        // Construct the "class path" for this class loader
        Set<URL> set = new LinkedHashSet<URL>();

        if (repositories != null) {
            for (Repository repository : repositories)  {
                if (repository.getType() == RepositoryType.URL) {
                    URL url = buildClassLoaderUrl(repository.getLocation());
                    if (log.isDebugEnabled())
                        log.debug("  Including URL " + url);
                    set.add(url);
                } else if (repository.getType() == RepositoryType.DIR) {
                    File directory = new File(repository.getLocation());
                    directory = directory.getCanonicalFile();
                    if (!validateFile(directory, RepositoryType.DIR)) {
                        continue;
                    }
                    URL url = buildClassLoaderUrl(directory);
                    if (log.isDebugEnabled())
                        log.debug("  Including directory " + url);
                    set.add(url);
                } else if (repository.getType() == RepositoryType.JAR) {
                    File file=new File(repository.getLocation());
                    file = file.getCanonicalFile();
                    if (!validateFile(file, RepositoryType.JAR)) {
                        continue;
                    }
                    URL url = buildClassLoaderUrl(file);
                    if (log.isDebugEnabled())
                        log.debug("  Including jar file " + url);
                    set.add(url);
                } else if (repository.getType() == RepositoryType.GLOB) {
                    File directory=new File(repository.getLocation());
                    directory = directory.getCanonicalFile();
                    if (!validateFile(directory, RepositoryType.GLOB)) {
                        continue;
                    }
                    if (log.isDebugEnabled())
                        log.debug("  Including directory glob "
                            + directory.getAbsolutePath());
                    String filenames[] = directory.list();
                    if (filenames == null) {
                        continue;
                    }
                    for (int j = 0; j < filenames.length; j++) {
                        String filename = filenames[j].toLowerCase(Locale.ENGLISH);
                        if (!filename.endsWith(".jar"))
                            continue;
                        File file = new File(directory, filenames[j]);
                        file = file.getCanonicalFile();
                        if (!validateFile(file, RepositoryType.JAR)) {
                            continue;
                        }
                        if (log.isDebugEnabled())
                            log.debug("    Including glob jar file "
                                + file.getAbsolutePath());
                        URL url = buildClassLoaderUrl(file);
                        set.add(url);
                    }
                }
            }
        }

        // Construct the class loader itself
        final URL[] array = set.toArray(new URL[set.size()]);
        if (log.isDebugEnabled())
            for (int i = 0; i < array.length; i++) {
                log.debug("  location " + i + " is " + array[i]);
            }

        return AccessController.doPrivileged(
                new PrivilegedAction<URLClassLoader>() {
                    @Override
                    public URLClassLoader run() {
                        if (parent == null)
                            // URLClassLoader是一个可以从指定目录或网络地址加载class的一个类加载器
                            return new URLClassLoader(array);
                        else
                            return new URLClassLoader(array, parent);
                    }
                });
    }

    private static boolean validateFile(File file,
            RepositoryType type) throws IOException {
        if (RepositoryType.DIR == type || RepositoryType.GLOB == type) {
            if (!file.exists() || !file.isDirectory() || !file.canRead()) {
                String msg = "Problem with directory [" + file +
                        "], exists: [" + file.exists() +
                        "], isDirectory: [" + file.isDirectory() +
                        "], canRead: [" + file.canRead() + "]";

                File home = new File (Bootstrap.getCatalinaHome());
                home = home.getCanonicalFile();
                File base = new File (Bootstrap.getCatalinaBase());
                base = base.getCanonicalFile();
                File defaultValue = new File(base, "lib");

                // Existence of ${catalina.base}/lib directory is optional.
                // Hide the warning if Tomcat runs with separate catalina.home
                // and catalina.base and that directory is absent.
                if (!home.getPath().equals(base.getPath())
                        && file.getPath().equals(defaultValue.getPath())
                        && !file.exists()) {
                    log.debug(msg);
                } else {
                    log.warn(msg);
                }
                return false;
            }
        } else if (RepositoryType.JAR == type) {
            if (!file.exists() || !file.canRead()) {
                log.warn("Problem with JAR file [" + file +
                        "], exists: [" + file.exists() +
                        "], canRead: [" + file.canRead() + "]");
                return false;
            }
        }
        return true;
    }


    /*
     * These two methods would ideally be in the utility class
     * org.apache.tomcat.util.buf.UriUtil but that class is not visible until
     * after the class loaders have been constructed.
     */
    private static URL buildClassLoaderUrl(String urlString) throws MalformedURLException {
        // URLs passed to class loaders may point to directories that contain
        // JARs. If these URLs are used to construct URLs for resources in a JAR
        // the URL will be used as is. It is therefore necessary to ensure that
        // the sequence "!/" is not present in a class loader URL.
        String result = urlString.replaceAll("!/", "%21/");
        return new URL(result);
    }


    private static URL buildClassLoaderUrl(File file) throws MalformedURLException {
        // Could be a directory or a file
        String fileUrlString = file.toURI().toString();
        fileUrlString = fileUrlString.replaceAll("!/", "%21/");
        return new URL(fileUrlString);
    }


    public enum RepositoryType {
        DIR,
        GLOB,
        JAR,
        URL
    }

    public static class Repository {
        private String location;
        private RepositoryType type;

        public Repository(String location, RepositoryType type) {
            this.location = location;
            this.type = type;
        }

        public String getLocation() {
            return location;
        }

        public RepositoryType getType() {
            return type;
        }
    }
}
