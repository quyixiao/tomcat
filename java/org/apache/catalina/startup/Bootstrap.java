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
import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Method;
import java.net.MalformedURLException;
import java.net.URL;
import java.util.ArrayList;
import java.util.List;
import java.util.StringTokenizer;

import org.apache.catalina.Globals;
import org.apache.catalina.security.SecurityClassLoad;
import org.apache.catalina.startup.ClassLoaderFactory.Repository;
import org.apache.catalina.startup.ClassLoaderFactory.RepositoryType;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;

/**
 * Bootstrap loader for Catalina.  This application constructs a class loader
 * for use in loading the Catalina internal classes (by accumulating all of the
 * JAR files found in the "server" directory under "catalina.home"), and
 * starts the regular execution of the container.  The purpose of this
 * roundabout approach is to keep the Catalina internal classes (and any
 * other classes they depend on, such as an XML parser) out of the system
 * class path and therefore not visible to application level classes.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 */
public final class Bootstrap {

    private static final Log log = LogFactory.getLog(Bootstrap.class);


    // ------------------------------------------------------- Static Variables


    /**
     * Daemon object used by main.
     */
    private static Bootstrap daemon = null;


    // -------------------------------------------------------------- Variables


    /**
     * Daemon reference.
     */
    private Object catalinaDaemon = null;


    ClassLoader commonLoader = null;
    ClassLoader catalinaLoader = null;
    ClassLoader sharedLoader = null;


    // -------------------------------------------------------- Private Methods
    // Java 的设计初衷是主要面向嵌入式领域，对于自定义的一些类，考虑使用按需要加载的原则，即程序使用到时才加载类，节省内存的消耗，这时即可
    // 通过类加载器来动态的加载 。
    // 如果你平时只做Web 开发，那应该很少会跟类加载器打交道，但如果想深入学习Tomcat的架构，那它必不可少，所谓的类加载器，就是用于加载Java
    // 类到Java 虚拟机中的组件，它负责读取Java字节码，并转换成java.lang.Class类的一个实例，使用字节码.class  文件得以运行，一般的类加载器
    // 负责根据一个指定的类找到对应的字节码，然后根据这些字节码定义一个Java 类，另外还可加载资源，包括图像文件和配置文件 。
    // 类加载器在实际使用中给我们带来的好处，它可以使Java 类动态的加载到JVM中并运行，即可在程序运行时再加载类，提供了很灵活的动态加载方式 。
    // 例如 Applet ，从远程服务器下载字节码到客户端再动态加载到JVM 中便可以运行。
    // 在Java 体系中，可以将系统分为三种类加载器。

    // 1. 启动类加载器（Bootstrap ClassLoader）: 加载对象是Java核心库，把一些核心的Java 类加载进入JVM中，这个加载器使用原生的（C , C++）
    // 实现，并不是继承java.lang.ClassLoader ，它是所有其他类加载器的最终父加载器，负责加载 <JAVA_HOME>/jre/lib ，目录下JVM指定
    // 的类库，其实它属于JVM整体的一部分，JVM 一启动就将这些指定的类加载到内存中，避免以后过多的I/O操作，提高系统的运行效率，启动类加载器
    // 无法被JAva 程序直接使用。
    // 2. 扩展类加载器（Extension ClassLoader） : 加载对象的Java的扩展库，即加载<JAVA_HOME>/jre/lib/ext 目录里面在的类，这个类由启动类
    // 加载器加载，但因为启动类加载器并非用Java 实现  ，已经脱离了Java体系，所以如果尝试调用扩展类加载器的getParent()方法获取父类加载器
    // 会得到 null ,然而，它的父类加载器是启动类加载器。
    // 3.应用程序类加载器（Application ClassLoader） : 也称为系统类加载器（SystemClassLoader） ，它负责加载用户的类路径 （CLASSPATH）
    // 指定的类库，如果程序没有自己定义类加载器。
    //  就默认使用应用程序类加载器，它由于启动类加载器加载，但是它的父类加载器被设置为扩展类加载器， 如果要使用这个类加载器，可以通过ClassLoader.getSystemClassLoader()
    // 获取 。
    // 假如想自己写一个类加载器，那么只需要继承java.lang.ClassLoader 类即可，可以用图13.1 来清晰的表示各种类加载器的关系，启动类加载器是最根本的类加载器，其不存在父类加载
    // 器，扩展类加载器由启动类加载器加载，所以它的父类加载器是启动类加载器，应用程序类加载器由启动类加载器加载，它的父类是指向扩展类加载器。
    // 而其他的用户自定义的类加载器由应用程序类加载器加载 。

    // 由此可以看出，越重要的类加载器就越早被JVM载入，这是考虑到安全性，因为先加载的类加载器会充当下一个类加载器的父类加载器，在双亲委派
    // 机截机制下，就能确保安全性，双亲委派模型会在类加载器加载类时首先委托维生父类加载器加载，除非父类加载器不能加载才自己加载 。
    // 这种模型要求，除了顶层的启动类加载器外，其他的类加载器都要有自己的父类加载器，假如有一个类要加载进来，一个类加载器并不会马上尝试自己将其加载，而是
    // 委派给父类加载器加载，父类加载器收到后又尝试委派给其父类加载器，以此类推，直到委派给启动类加载器， 这样一层层的往上委派，只有当父类加载器反馈自己
    // 没有成这个类加载时，子加载器才会尝试自己的加载，通过这个机制，保证了Java 应用所使用的都是同一个版本的Java 核心库的类，同时这个机制也保证了安全性，
    // 设想，如果应用程序类加载器想要加载一个有破坏性的Java.lang.System类。双亲委派模型会一层层的向上委派，最终委派给启动类加载器。
    // 而启动类加载器检查到缓存中已经有这个类了，并不会再加载这个有破坏性的System类。

    // 另外，类加载器还拥有全盘负责机制，即当一个类加载器加载一个类时，这个类所依赖的，引用的其他的所有类都由这个类加载器加载 ，除非在程序中
    // 显式的指定另外一个类加载器加载 。

    // 在Java 中，我们用完全匹配类名来标识一个类，即用包名 和类名，而在JVM 中，一个类由完全匹配类名和一个类加载器的实例ID作为唯一标识，也就是说
    // 同一个虚拟机可以有两个包名，类名都相同的类，只要它们由两个不同的类加载器加载，当我们在Java 中说两个类是否相等时，必须在针对同一个类
    // 加载器加载的前提下才有意义，否则，就算是同样的字节码，由不同的类加载器加载，这两个类也不是相等的，这种特性我们称提供了隔离机制 。
    // 在Tomcat 服务器中它十分有用。
    // 了解了JVM  的类加载器的各种机制后，看看一个类是怎样被类加载器载入进来的，如图13.2所示，要加载一个类，类加载器先判断此类是否已经加载过了
    // （加载过的类会缓存在内存中），如果缓存中存在此类，则直接返回这个类，否则，获取父类加载器，如果父类加载器为null，则由父类加载器载入
    // 载入成功就返回Class, 载入失败则根据类路径查找Class 文件，找到了就加载此Class 文件并返回Class, 找不到就抛出ClassNotFindException 异常。
    // 类加载器属于JVM级别的设计，我们很多的时候基本不会与它打交道，假如你想深入理解Tomcat 内核或设计开发自己的框架和中间件，那么你必须熟悉类
    // 加载相关的机制，在现实设计中，根据实际情况利用类加载器可以提供类库的隔离及共享，保证软件不同级别的逻辑，分割程序不会互相影响，提供更好的
    // 安全性。

    // 一般场景中使用Java 默认的类加载器即可，但有时为了达到某种目的，又不得不实现自己的类加载器，例如，为了使类库互相隔离，为了实现热部署。
    // 重新加载功能，这个时候就需要自己定义类加载器，每个类加载器加载各自的资源，以此达到资源隔离的效果，在对资源的加载上可以沿用双亲委派机制
    // 也可以打破双亲委派机制 。
    // 1. 沿用双亲委派机制自定义类加载器很简单，只须要继承ClassLoader 类并重写FindClass 方法即可，下面给出一个例子。
    //    先定义一个待加载的Test ，它很简单，只是在构建函数中输出由哪个类加载器加载 。
    // public class Test {
    //      public Test(){
    //          System.out.println(this.getClass().getClassLoader().toString());
    //      }
    // }
    // 2. 定义一个TomcatClassLoader 类，它继承（ClassLoader） ，重写了findClass方法，此方法要做的事情就是读取Test.class 字节流
    // 并传入父类的defineClass方法，然后，就可以通过自定义类加载器加载TomcatClassLoader对Test.class进行加载，完成加载后输出 "TomcatLoader"
    /** public class TomcatClassLoader extends ClassLoader{
    *       private String name ;
     *      public TomcatClassLoader(ClassLoader parent,String name ){
     *          super(parent);
     *          this.name = name ;
     *      }
     *      public String toString(){
     *          return this.name ;
     *      }
     *      public Class <?> findClass(String name ){
     *
     *          InputStream is = null;
     *          byte [] data = null;
     *          ByteArrayOutputStream baos = new ByteArrayOutputStream();
     *          try{
     *              is = new FileInputStream(new File("d:/Test.class"));
     *              int c = 0;
     *              while(-1 != (c = is.read)){
     *                  baos.write(c);
     *              }
     *              data = baos.toByteArray();
     *          }catch(Exception e ){
     *              e.printStackTrace();
     *          }finally{
     *              try{
     *                  is.close();
     *                  baos.close();
     *              }catch(IOException e ){
     *                  e.printStackTrace();
     *              }
     *          }
     *          return this.defineClass(name,data ,0 ,data.length);
     *      }
     *
     *      public static void main(String [] args){
     *          TomcatClassLoader loader  = new TomcatClassLoader(TomcatClassLoader.class.getClassLoader(),"TomcatLoader");
     *          Class clazz ;
     *          try{
     *              clazz = loader.loadClass("test.classloader.Test");
     *              Object object = clazz.newInstance();
     *          }catch (Exception e ){
     *              e.printStackTrace();
     *          }
     *      }
    * }
     * 3. 打破双亲委派机制不仅要继承ClassLoader 类，还需要重写loadClass和findClass方法，下面给出一个例子。
     * 定义Test 类
     * public class Test{
     *     public Test(){
     *         System.out.println(this.getClass().getClassLoader().toString());
     *     }
     * }
     *
     * Tomcat 中的类加载器。
     * Tomcat 拥有不同的自定义类加载器，以实现对各种资源的控制，一般来说，Tomcat主要用类加载器解决以下4个问题。
     * 1. 同一个Web服务器里，各个Web 项目之间各自使用的Java 类库互相隔离 。
     * 2. 同一个Web服务器里，各个Web项目之间可以提供共享的Java 类库。
     * 3. 为了使服务器不受Web 项目的影响，应该使用服务器的类库与应用程序类库互相独立 。
     * 4. 对于支持JSP的Web 服务器，应该支持热插拔（HotSwap)功能 。
     *
     * 对于以上的几个问题，如果单独使用一个类加载器明显达不到效果，必须根据具体情况使用若干个自定义类加载器。
     *  下面看Tomcat 的类加载器是怎样定义的，如图13.3 所示，启动类加载器，扩展类加载器，应用程序类加载器这三个类加载器属于JDK级别的类加载器。
     *  它们的唯一的，我们一般不会对其做任何更改，接下来，则是Tomcat 的类加载器，在Tomcat 中，最重要的一个类加载器是Common 类加载器。
     *  它的父类加载器是应用程序类加载器。负责加载$CATALINA_BASE/lib ,$CATALINA_HOME/lib 两个目录下的所有的.class 文件与jar 文件，
     *  而下面的虚线框的两个类加载器的主要用在Tomcat 5 版本中，Tomcat 5 版本中的两个类加载器实例默认与常见的类加载器的实现不同，
     *  Common 类加载器是它们的父类加载器，而在Tomcat 7 版本中，这两个实例变量也是存在的，只是catalina.propeties 配置文件没有
     *  对server.loader 和share.loader 两项进行配置，所以程序里这身份个类加载器的实例被赋值为Common 类加载器实例，即一个Tomcat 7
     *  版本的实例其实就是只有Common类加载器实例。
     *
     *
     *
    */


    private void initClassLoaders() {
        try {
            // CommonClassLoader是一个公共的类加载器,默认加载${catalina.base}/lib,${catalina.base}/lib/*.jar,${catalina.home}/lib,${catalina.home}/lib/*.jar下的class
            commonLoader = createClassLoader("common", null); // 虽然这个地方parent是null，实际上是appclassloader
//            System.out.println("commonLoader的父类加载器===="+commonLoader.getParent());
            if( commonLoader == null ) {
                // no config file, default to this loader - we might be in a 'single' env.
                commonLoader=this.getClass().getClassLoader();
            }
            // 下面这个两个类加载器默认情况下就是commonLoader
            catalinaLoader = createClassLoader("server", commonLoader);
            sharedLoader = createClassLoader("shared", commonLoader);
        } catch (Throwable t) {
            handleThrowable(t);
            log.error("Class loader creation threw exception", t);
            System.exit(1);
        }
    }
    /**
     * 首先创建一个Common 类加载器，再把Common 类加载器作为参数传进createClassLoader方法里，这个方法里面根据catalina.properties
     * 中的server.loader 和share.loader 属性是否为空判断是否另外创建新的类加载器，如果属性为空，则把常见的类加载器直接赋值给Catalina
     * 类加载器和共享类加载器，如果默认配置满足不了你的需求，可以通过修改catalina.properties 配置文件满足需求 。
     *
     * 从图13.3 中的WebApp ClassLoader 来看，就大概知道它主要的加载Web 应用程序，它的父类加载器是Common 类加载器 ，Tomcat 中一般
     * 会有多个WebApp 类加载器实例，每个类加载器负责加载一个Web 程序 。
     *
     * 对照这样的一个类加载器结构，看看上面需要解决的问题是否解决 。由于每个Web 应用项目都有自己的WebApp 类加载器，很多的使用多个
     * Web 应用项目都有自己的WebApp 类加载器，很好的使用了Web 应用程序之间的互相隔离且能通过创建新的WebApp 类加载器达到热部署。
     * 这种类加载器的结构能使有效的Tomcat 不受Web 应用程序影响 ，而Common类加载器在存在使用多个Web应用程序能够互相共享类库。
     *
     *
     *
     */


    /**
     *
     * @param name 是配置项的名字，全名为name.loader，配置项配置了类加载器应该从哪些目录去加载类
     * @param parent 父级类加载器
     * @return
     * @throws Exception
     *
     *
     *
     */
    private ClassLoader createClassLoader(String name, ClassLoader parent)
        throws Exception {

        String value = CatalinaProperties.getProperty(name + ".loader");
        if ((value == null) || (value.equals("")))
            return parent;

        value = replace(value);

        List<Repository> repositories = new ArrayList<Repository>();

        StringTokenizer tokenizer = new StringTokenizer(value, ",");
        while (tokenizer.hasMoreElements()) {
            String repository = tokenizer.nextToken().trim();
            if (repository.length() == 0) {
                continue;
            }

            // Check for a JAR URL repository
            try {
                // 从URL上获取Jar包资源
                @SuppressWarnings("unused")
                URL url = new URL(repository);
                repositories.add(
                        new Repository(repository, RepositoryType.URL));
                continue;
            } catch (MalformedURLException e) {
                // Ignore
            }

            // Local repository
            if (repository.endsWith("*.jar")) {
                // 表示目录下所有的jar包资源
                repository = repository.substring
                    (0, repository.length() - "*.jar".length());
                repositories.add(
                        new Repository(repository, RepositoryType.GLOB));
            } else if (repository.endsWith(".jar")) {
                // 表示目录下当个的jar包资源
                repositories.add(
                        new Repository(repository, RepositoryType.JAR));
            } else {
                // 表示目录下所有资源，包括jar包、class文件、其他类型资源
                repositories.add(
                        new Repository(repository, RepositoryType.DIR));
            }
        }

        // 基于类仓库类创建一个ClassLoader
        return ClassLoaderFactory.createClassLoader(repositories, parent);
    }

    /**
     * System property replacement in the given string.
     *
     * @param str The original string
     * @return the modified string
     */
    protected String replace(String str) {
        // Implementation is copied from ClassLoaderLogManager.replace(),
        // but added special processing for catalina.home and catalina.base.
        String result = str;
        int pos_start = str.indexOf("${");
        if (pos_start >= 0) {
            StringBuilder builder = new StringBuilder();
            int pos_end = -1;
            while (pos_start >= 0) {
                builder.append(str, pos_end + 1, pos_start);
                pos_end = str.indexOf('}', pos_start + 2);
                if (pos_end < 0) {
                    pos_end = pos_start - 1;
                    break;
                }
                String propName = str.substring(pos_start + 2, pos_end);
                String replacement;
                if (propName.length() == 0) {
                    replacement = null;
                } else if (Globals.CATALINA_HOME_PROP.equals(propName)) {
                    replacement = getCatalinaHome();
                } else if (Globals.CATALINA_BASE_PROP.equals(propName)) {
                    replacement = getCatalinaBase();
                } else {
                    replacement = System.getProperty(propName);
                }
                if (replacement != null) {
                    builder.append(replacement);
                } else {
                    builder.append(str, pos_start, pos_end + 1);
                }
                pos_start = str.indexOf("${", pos_end + 1);
            }
            builder.append(str, pos_end + 1, str.length());
            result = builder.toString();
        }
        return result;
    }


    /**
     * Initialize daemon.
     * 主要初始化类加载器，在Tomcat的设计中，使用了很多自定义的类加载器，包括Tomcat自己本身的类会由CommonClassLoader来加载，每个wabapp由特定的类加载器来加载
     */
    public void init()
        throws Exception
    {

        // Set Catalina path
        // catalina.home表示安装目录
        // catalina.base表示工作目录
        setCatalinaHome();
        setCatalinaBase();

        // 初始化commonLoader、catalinaLoader、sharedLoader
        // 其中catalinaLoader、sharedLoader默认其实就是commonLoader
        initClassLoaders();

        // 设置线程的所使用的类加载器，默认情况下就是commonLoader
        Thread.currentThread().setContextClassLoader(catalinaLoader);

        // 如果开启了SecurityManager，那么则要提前加载一些类
        SecurityClassLoad.securityClassLoad(catalinaLoader);

        // Load our startup class and call its process() method
        // 加载Catalina类，并生成instance
        if (log.isDebugEnabled())
            log.debug("Loading startup class");
        Class<?> startupClass =
            catalinaLoader.loadClass
            ("org.apache.catalina.startup.Catalina");
        Object startupInstance = startupClass.newInstance();

        // Set the shared extensions class loader
        // 设置Catalina实例的父级类加载器为sharedLoader(默认情况下就是commonLoader)
        if (log.isDebugEnabled())
            log.debug("Setting startup class properties");
        String methodName = "setParentClassLoader";
        Class<?> paramTypes[] = new Class[1];
        paramTypes[0] = Class.forName("java.lang.ClassLoader");
        Object paramValues[] = new Object[1];
        paramValues[0] = sharedLoader;
        Method method =
            startupInstance.getClass().getMethod(methodName, paramTypes);
        method.invoke(startupInstance, paramValues);

        catalinaDaemon = startupInstance;

    }


    /**
     * Load daemon.
     * 调用Catalina实例的load方法
     */
    private void load(String[] arguments)
        throws Exception {

        // Call the load() method
        String methodName = "load";
        Object param[];
        Class<?> paramTypes[];
        if (arguments==null || arguments.length==0) {
            paramTypes = null;
            param = null;
        } else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method =
            catalinaDaemon.getClass().getMethod(methodName, paramTypes);
        if (log.isDebugEnabled())
            log.debug("Calling startup class " + method);
        method.invoke(catalinaDaemon, param);

    }


    /**
     * getServer() for configtest
     */
    private Object getServer() throws Exception {

        String methodName = "getServer";
        Method method =
            catalinaDaemon.getClass().getMethod(methodName);
        return method.invoke(catalinaDaemon);

    }


    // ----------------------------------------------------------- Main Program


    /**
     * Load the Catalina daemon.
     */
    public void init(String[] arguments)
        throws Exception {

        init();
        load(arguments);

    }


    /**
     * Start the Catalina daemon.
     */
    public void start()
        throws Exception {
        if( catalinaDaemon==null ) init();

        Method method = catalinaDaemon.getClass().getMethod("start", (Class [] )null);
        method.invoke(catalinaDaemon, (Object [])null);

    }


    /**
     * Stop the Catalina Daemon.
     */
    public void stop()
        throws Exception {

        Method method = catalinaDaemon.getClass().getMethod("stop", (Class [] ) null);
        method.invoke(catalinaDaemon, (Object [] ) null);

    }


    /**
     * Stop the standalone server.
     */
    public void stopServer()
        throws Exception {

        Method method =
            catalinaDaemon.getClass().getMethod("stopServer", (Class []) null);
        method.invoke(catalinaDaemon, (Object []) null);

    }


   /**
     * Stop the standalone server.
     */
    public void stopServer(String[] arguments)
        throws Exception {

        Object param[];
        Class<?> paramTypes[];
        if (arguments==null || arguments.length==0) {
            paramTypes = null;
            param = null;
        } else {
            paramTypes = new Class[1];
            paramTypes[0] = arguments.getClass();
            param = new Object[1];
            param[0] = arguments;
        }
        Method method =
            catalinaDaemon.getClass().getMethod("stopServer", paramTypes);
        method.invoke(catalinaDaemon, param);

    }


    /**
     * Set flag.
     * 设置Catalina实例的await标志，该标志表示Catalina启动后是否阻塞住，默认为false
     */
    public void setAwait(boolean await)
        throws Exception {

        Class<?> paramTypes[] = new Class[1];
        paramTypes[0] = Boolean.TYPE;
        Object paramValues[] = new Object[1];
        paramValues[0] = Boolean.valueOf(await);
        Method method =
            catalinaDaemon.getClass().getMethod("setAwait", paramTypes);
        method.invoke(catalinaDaemon, paramValues);

    }

    public boolean getAwait()
        throws Exception
    {
        Class<?> paramTypes[] = new Class[0];
        Object paramValues[] = new Object[0];
        Method method =
            catalinaDaemon.getClass().getMethod("getAwait", paramTypes);
        Boolean b=(Boolean)method.invoke(catalinaDaemon, paramValues);
        return b.booleanValue();
    }


    /**
     * Destroy the Catalina Daemon.
     */
    public void destroy() {

        // FIXME

    }


    /**
     * Main method and entry point when starting Tomcat via the provided
     * scripts.
     *
     * @param args Command line arguments to be processed
     */
    public static void main(String args[]) {

        if (daemon == null) {
            // Don't set daemon until init() has completed
            Bootstrap bootstrap = new Bootstrap();
            try {
                bootstrap.init(); // catalinaaemon
            } catch (Throwable t) {
                handleThrowable(t);
                t.printStackTrace();
                return;
            }
            daemon = bootstrap;
        } else {
            // When running as a service the call to stop will be on a new
            // thread so make sure the correct class loader is used to prevent
            // a range of class not found exceptions.
            Thread.currentThread().setContextClassLoader(daemon.catalinaLoader);
        }

        try {
            String command = "start";
            if (args.length > 0) {
                command = args[args.length - 1];
            }

            if (command.equals("startd")) {
                args[args.length - 1] = "start";
                daemon.load(args);
                daemon.start();
            } else if (command.equals("stopd")) {
                args[args.length - 1] = "stop";
                daemon.stop();
            } else if (command.equals("start")) {
                daemon.setAwait(true);  // 设置阻塞标志
                daemon.load(args);      // 解析server.xml,初始化Catalina
                daemon.start();
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
            } else if (command.equals("stop")) {
                daemon.stopServer(args);
            } else if (command.equals("configtest")) {
                daemon.load(args);
                if (null == daemon.getServer()) {
                    System.exit(1);
                }
                System.exit(0);
            } else {
                log.warn("Bootstrap: command \"" + command + "\" does not exist.");
            }
        } catch (Throwable t) {
            // Unwrap the Exception for clearer error reporting
            if (t instanceof InvocationTargetException &&
                    t.getCause() != null) {
                t = t.getCause();
            }
            handleThrowable(t);
            t.printStackTrace();
            System.exit(1);
        }

    }

    public void setCatalinaHome(String s) {
        System.setProperty(Globals.CATALINA_HOME_PROP, s);
    }

    public void setCatalinaBase(String s) {
        System.setProperty(Globals.CATALINA_BASE_PROP, s);
    }


    /**
     * Set the <code>catalina.base</code> System property to the current
     * working directory if it has not been set.
     */
    private void setCatalinaBase() {

        if (System.getProperty(Globals.CATALINA_BASE_PROP) != null)
            return;
        if (System.getProperty(Globals.CATALINA_HOME_PROP) != null)
            System.setProperty(Globals.CATALINA_BASE_PROP,
                               System.getProperty(Globals.CATALINA_HOME_PROP));
        else
            System.setProperty(Globals.CATALINA_BASE_PROP,
                               System.getProperty("user.dir"));

    }


    /**
     * Set the <code>catalina.home</code> System property to the current
     * working directory if it has not been set.
     */
    private void setCatalinaHome() {

        if (System.getProperty(Globals.CATALINA_HOME_PROP) != null)
            return;
        File bootstrapJar =
            new File(System.getProperty("user.dir"), "bootstrap.jar");
        if (bootstrapJar.exists()) {
            try {
                System.setProperty
                    (Globals.CATALINA_HOME_PROP,
                     (new File(System.getProperty("user.dir"), ".."))
                     .getCanonicalPath());
            } catch (Exception e) {
                // Ignore
                System.setProperty(Globals.CATALINA_HOME_PROP,
                                   System.getProperty("user.dir"));
            }
        } else {
            System.setProperty(Globals.CATALINA_HOME_PROP,
                               System.getProperty("user.dir"));
        }

    }


    /**
     * Get the value of the catalina.home environment variable.
     */
    public static String getCatalinaHome() {
        return System.getProperty(Globals.CATALINA_HOME_PROP,
                                  System.getProperty("user.dir"));
    }


    /**
     * Get the value of the catalina.base environment variable.
     */
    public static String getCatalinaBase() {
        return System.getProperty(Globals.CATALINA_BASE_PROP, getCatalinaHome());
    }


    // Copied from ExceptionUtils since that class is not visible during start
    private static void handleThrowable(Throwable t) {
        if (t instanceof ThreadDeath) {
            throw (ThreadDeath) t;
        }
        if (t instanceof VirtualMachineError) {
            throw (VirtualMachineError) t;
        }
        // All other instances of Throwable will be silently swallowed
    }
}
