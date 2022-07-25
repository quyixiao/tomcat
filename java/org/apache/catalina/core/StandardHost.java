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
package org.apache.catalina.core;


import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.WeakHashMap;
import java.util.concurrent.ExecutorService;
import java.util.regex.Pattern;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Valve;
import org.apache.catalina.loader.WebappClassLoaderBase;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.ExceptionUtils;


/**
 * Standard implementation of the <b>Host</b> interface.  Each
 * child container must be a Context implementation to process the
 * requests directed to a particular web application.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * org.apache.catalina.core.StandardHost 类是对 Host 接口的标准实现。该继 承了 org.apache.catalina.core.ContainerBase
 * 类并实现了 Host 接口和 Deployer 接口。Deployer 接口将在第 17 章讨论。
 *
 * 跟 StandardContext 和 StandardWrapper 类相似，StandardHost 类的构造函数 在它的流水线中添加一个基本阀门。
 *
 *
 * 在整个Servlet 引擎中抽象出Host 容器用于表示虚拟主机，它根据URL 地址中的主机部分抽象，一个Servlet引擎可以包含若干个Host容器，而一个
 * Host 容器可以包含若干个Context容器，在Tomcat 的Host标准实现StandardHost ，它从虚拟主机级别对请求和响应进行处理，下面是对StandardHost 内部结构进行剖析 。
 *
 * Host 容器包含了若干Context容器，AccessLog 维持，Pipeline 组件，Cluster组件，Realm 组件，HostConfig组件和Log 组件 。
 *
 * 每个Host 容器包含了若干个Web 应用（Context） ,对于Web 项目来说，其结构相对比较复杂，而且包含了很多机制，Tomcat 需要对它结构进行解析 。
 * 同时还要具体的实现各种功能和机制，这些复杂的工作就交给了Context容器，Context 容器对应的实现了Web 应用包含的语义，实现了Servlet和JSP的规范。
 *
 * Host 容器里的AccessLog 组件负责客户端请求访问日志的记录，Host 容器的访问日志作用范围是该虚拟机主机的所有客户端的请求访问，不管访问哪个
 * 应用都会被该日志组件记录。
 *
 *
 * 不同级别的容器的管道完成的工作都不一样，每个管道要搭配阀门（Value） 才能工作，Host 容器的Pipeline 默认以StandardHostValue 作为基础
 * 阀门，这个阀门的主要处理逻辑是先将当前线程上下文类加载器设置成Context容器的类加载器，让后面的Context容器处理时使用该类加载器，然后调用
 * 子容器的Context的管道 。
 *
 * 如果有其他的逻辑需要在Host容器级别处理，可以往该管道添加包含逻辑的阀门，当Host 管道被调用时会执行该阀门的逻辑 。
 *
 * Host 集群，Cluster
 *
 * 这里的集群组件属于虚拟主机容器，它提供了Host 级别的集群会话及集群部署，关于集群的详细机制及Tomcat 中集群的实现 。
 *
 * Host 域 Realm
 *
 * Realm 对象其实就是一个存储了用户，密码及权限的数据对象，它的存储方式可能是内存，xml 文件或数据库等，它的作用主要配合Tomcat 实现资源的
 * 谁模块
 *
 * Tomcat 中有很多级别的域（Realm） ，这里的域属于Host容器级别，它的作用范围是某个Host 容器内包含的所有Web应用，在配置文件 <Host> 节点
 * 下配置域则在启动时对应的域会添加到Host  容器中。
 *
 */
public class StandardHost extends ContainerBase implements Host {

    private static final org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( StandardHost.class );

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardHost component with the default basic Valve.
     * // 构造StandardHost时设置一下管道的最后一个valve-StandardHostValve
     */
    public StandardHost() {

        super();
        pipeline.setBasic(new StandardHostValve());

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The set of aliases for this Host.
     */
    private String[] aliases = new String[0];

    private final Object aliasesLock = new Object();


    /**
     * The application root for this Host.
     */
    private String appBase = "webapps";

    /**
     * The XML root for this Host.
     */
    private String xmlBase = null;

    /**
     * The auto deploy flag for this Host.
     */
    private boolean autoDeploy = true;


    /**
     * The Java class name of the default context configuration class
     * for deployed web applications.
     */
    private String configClass =
        "org.apache.catalina.startup.ContextConfig";


    /**
     * The Java class name of the default Context implementation class for
     * deployed web applications.
     */
    private String contextClass =
        "org.apache.catalina.core.StandardContext";


    /**
     * The deploy on startup flag for this Host.
     * 启动时就部署
     */
    private boolean deployOnStartup = true;


    /**
     * deploy Context XML config files property.
     * 如果java安全机制开启了，那么则deployXML为false
     * 如果设置为false ，那么Tomcat 不会解析Web 应用中的用于设置Context 元素的META-INF/context.xml文件，出于安全原因，如果不希望Web
     * 应用中包含Tomcat 的配置元素 。 就可以把这个属性设置 为false . 在这种情况下， 应该<CATALINA_HOME>/conf/[enginename]/[hostname] 一设置Context 元素，该属性默认值为
     * true
     *
     */
    private boolean deployXML = !Globals.IS_SECURITY_ENABLED;


    /**
     * Should XML files be copied to
     * $CATALINA_BASE/conf/&lt;engine&gt;/&lt;host&gt; by default when
     * a web application is deployed?
     */
    private boolean copyXML = false;


    /**
     * The Java class name of the default error reporter implementation class
     * for deployed web applications.
     */
    private String errorReportValveClass =
        "org.apache.catalina.valves.ErrorReportValve";

    /**
     * The descriptive information string for this implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardHost/1.0";


    /**
     * Unpack WARs property.
     * 如果此项设置为true , 表示将appBase 属性txpgr目录下的Web 应用的WAR 文件先展开为开放目录结构后再运行，如果为false ,则直接运行WAR 文件
     *
     */
    private boolean unpackWARs = true;


    /**
     * Work Directory base for applications.
     * 指定虚拟主机的工作目录，Tomcat 运行时会把与这个虚拟主机的所有Web 应用相关的临时文件放在此目录下，它的默认值为<CATALINA_HOME>/work
     * 如果<HOST> 元素下的一个<Context>元素设置了workDir属性，那么<Context>元素的workDir属性会覆盖<Host>元素的workDir 属性。
     *
     */
    private String workDir = null;


    /**
     * Should we create directories upon startup for appBase and xmlBase
     */
     private boolean createDirs = true;


     /**
      * Track the class loaders for the child web applications so memory leaks
      * can be detected.
      */
     private Map<ClassLoader, String> childClassLoaders =
         new WeakHashMap<ClassLoader, String>();


     /**
      * Any file or directory in {@link #appBase} that this pattern matches will
      * be ignored by the automatic deployment process (both
      * {@link #deployOnStartup} and {@link #autoDeploy}).
      */
     private Pattern deployIgnore = null;


    private boolean undeployOldVersions = false;

    private boolean failCtxIfServletStartFails = false;


    // ------------------------------------------------------------- Properties

    @Override
    public boolean getUndeployOldVersions() {
        return undeployOldVersions;
    }


    @Override
    public void setUndeployOldVersions(boolean undeployOldVersions) {
        this.undeployOldVersions = undeployOldVersions;
    }


    @Override
    public ExecutorService getStartStopExecutor() {
        return startStopExecutor;
    }


    /**
     * Return the application root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     */
    @Override
    public String getAppBase() {
        return (this.appBase);
    }


    /**
     * Set the application root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     *
     * @param appBase The new application root
     */
    @Override
    public void setAppBase(String appBase) {

        if (appBase.trim().equals("")) {
            log.warn(sm.getString("standardHost.problematicAppBase", getName()));
        }
        String oldAppBase = this.appBase;
        this.appBase = appBase;
        support.firePropertyChange("appBase", oldAppBase, this.appBase);

    }


    /**
     * Return the XML root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     * If null, defaults to
     * ${catalina.base}/conf/&lt;engine name&gt;/&lt;host name&gt; directory
     */
    @Override
    public String getXmlBase() {
        return (this.xmlBase);

    }


    /**
     * Set the Xml root for this Host.  This can be an absolute
     * pathname, a relative pathname, or a URL.
     * If null, defaults to
     * ${catalina.base}/conf/&lt;engine name&gt;/&lt;host name&gt; directory
     *
     * @param xmlBase The new XML root
     */
    @Override
    public void setXmlBase(String xmlBase) {

        String oldXmlBase = this.xmlBase;
        this.xmlBase = xmlBase;
        support.firePropertyChange("xmlBase", oldXmlBase, this.xmlBase);

    }


    /**
     * Returns true if the Host will attempt to create directories for appBase and xmlBase
     * unless they already exist.
     */
    @Override
    public boolean getCreateDirs() {
        return createDirs;
    }

    /**
     * Set to true if the Host should attempt to create directories for xmlBase and appBase upon startup
     * @param createDirs
     */
    @Override
    public void setCreateDirs(boolean createDirs) {
        this.createDirs = createDirs;
    }

    /**
     * Return the value of the auto deploy flag.  If true, it indicates that
     * this host's child webapps will be dynamically deployed.
     */
    @Override
    public boolean getAutoDeploy() {

        return (this.autoDeploy);

    }


    /**
     * Set the auto deploy flag value for this host.
     *
     * @param autoDeploy The new auto deploy flag
     */
    @Override
    public void setAutoDeploy(boolean autoDeploy) {

        boolean oldAutoDeploy = this.autoDeploy;
        this.autoDeploy = autoDeploy;
        support.firePropertyChange("autoDeploy", oldAutoDeploy,
                                   this.autoDeploy);

    }


    /**
     * Return the Java class name of the context configuration class
     * for new web applications.
     */
    @Override
    public String getConfigClass() {

        return (this.configClass);

    }


    /**
     * Set the Java class name of the context configuration class
     * for new web applications.
     *
     * @param configClass The new context configuration class
     */
    @Override
    public void setConfigClass(String configClass) {

        String oldConfigClass = this.configClass;
        this.configClass = configClass;
        support.firePropertyChange("configClass",
                                   oldConfigClass, this.configClass);

    }


    /**
     * Return the Java class name of the Context implementation class
     * for new web applications.
     */
    public String getContextClass() {

        return (this.contextClass);

    }


    /**
     * Set the Java class name of the Context implementation class
     * for new web applications.
     *
     * @param contextClass The new context implementation class
     */
    public void setContextClass(String contextClass) {

        String oldContextClass = this.contextClass;
        this.contextClass = contextClass;
        support.firePropertyChange("contextClass",
                                   oldContextClass, this.contextClass);

    }


    /**
     * Return the value of the deploy on startup flag.  If true, it indicates
     * that this host's child webapps should be discovered and automatically
     * deployed at startup time.
     */
    @Override
    public boolean getDeployOnStartup() {

        return (this.deployOnStartup);

    }


    /**
     * Set the deploy on startup flag value for this host.
     *
     * @param deployOnStartup The new deploy on startup flag
     */
    @Override
    public void setDeployOnStartup(boolean deployOnStartup) {

        boolean oldDeployOnStartup = this.deployOnStartup;
        this.deployOnStartup = deployOnStartup;
        support.firePropertyChange("deployOnStartup", oldDeployOnStartup,
                                   this.deployOnStartup);

    }


    /**
     * Deploy XML Context config files flag accessor.
     */
    public boolean isDeployXML() {

        return (deployXML);

    }


    /**
     * Deploy XML Context config files flag mutator.
     */
    public void setDeployXML(boolean deployXML) {

        this.deployXML = deployXML;

    }


    /**
     * Return the copy XML config file flag for this component.
     */
    public boolean isCopyXML() {

        return (this.copyXML);

    }


    /**
     * Set the copy XML config file flag for this component.
     *
     * @param copyXML The new copy XML flag
     */
    public void setCopyXML(boolean copyXML) {

        this.copyXML= copyXML;

    }


    /**
     * Return the Java class name of the error report valve class
     * for new web applications.
     */
    public String getErrorReportValveClass() {

        return (this.errorReportValveClass);

    }


    /**
     * Set the Java class name of the error report valve class
     * for new web applications.
     *
     * @param errorReportValveClass The new error report valve class
     */
    public void setErrorReportValveClass(String errorReportValveClass) {

        String oldErrorReportValveClassClass = this.errorReportValveClass;
        this.errorReportValveClass = errorReportValveClass;
        support.firePropertyChange("errorReportValveClass",
                                   oldErrorReportValveClassClass,
                                   this.errorReportValveClass);

    }


    /**
     * Return the canonical, fully qualified, name of the virtual host
     * this Container represents.
     */
    @Override
    public String getName() {

        return (name);

    }


    /**
     * Set the canonical, fully qualified, name of the virtual host
     * this Container represents.
     *
     * @param name Virtual host name
     *
     * @exception IllegalArgumentException if name is null
     */
    @Override
    public void setName(String name) {

        if (name == null)
            throw new IllegalArgumentException
                (sm.getString("standardHost.nullName"));

        name = name.toLowerCase(Locale.ENGLISH);      // Internally all names are lower case

        String oldName = this.name;
        this.name = name;
        support.firePropertyChange("name", oldName, this.name);

    }


    /**
     * Unpack WARs flag accessor.
     */
    public boolean isUnpackWARs() {

        return (unpackWARs);

    }


    /**
     * Unpack WARs flag mutator.
     */
    public void setUnpackWARs(boolean unpackWARs) {

        this.unpackWARs = unpackWARs;

    }


    /**
     * Host work directory base.
     */
    public String getWorkDir() {

        return (workDir);
    }


    /**
     * Host work directory base.
     */
    public void setWorkDir(String workDir) {

        this.workDir = workDir;
    }


    /**
     * Return the regular expression that defines the files and directories in
     * the host's {@link #appBase} that will be ignored by the automatic
     * deployment process.
     */
    @Override
    public String getDeployIgnore() {
        if (deployIgnore == null) {
            return null;
        }
        return this.deployIgnore.toString();
    }


    /**
     * Return the compiled regular expression that defines the files and
     * directories in the host's {@link #appBase} that will be ignored by the
     * automatic deployment process.
     */
    @Override
    public Pattern getDeployIgnorePattern() {
        return this.deployIgnore;
    }


    /**
     * Set the regular expression that defines the files and directories in
     * the host's {@link #appBase} that will be ignored by the automatic
     * deployment process.
     */
    @Override
    public void setDeployIgnore(String deployIgnore) {
        String oldDeployIgnore;
        if (this.deployIgnore == null) {
            oldDeployIgnore = null;
        } else {
            oldDeployIgnore = this.deployIgnore.toString();
        }
        if (deployIgnore == null) {
            this.deployIgnore = null;
        } else {
            this.deployIgnore = Pattern.compile(deployIgnore);
        }
        support.firePropertyChange("deployIgnore",
                                   oldDeployIgnore,
                                   deployIgnore);
    }


    public boolean isFailCtxIfServletStartFails() {
        return failCtxIfServletStartFails;
    }


    public void setFailCtxIfServletStartFails(
            boolean failCtxIfServletStartFails) {
        boolean oldFailCtxIfServletStartFails = this.failCtxIfServletStartFails;
        this.failCtxIfServletStartFails = failCtxIfServletStartFails;
        support.firePropertyChange("failCtxIfServletStartFails",
                oldFailCtxIfServletStartFails,
                failCtxIfServletStartFails);
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add an alias name that should be mapped to this same Host.
     *
     * @param alias The alias to be added
     */
    @Override
    public void addAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {
            // Skip duplicate aliases
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias))
                    return;
            }
            // Add this alias to the list
            String newAliases[] = new String[aliases.length + 1];
            for (int i = 0; i < aliases.length; i++)
                newAliases[i] = aliases[i];
            newAliases[aliases.length] = alias;
            aliases = newAliases;
        }
        // Inform interested listeners
        fireContainerEvent(ADD_ALIAS_EVENT, alias);

    }


    /**
     * Add a child Container, only if the proposed child is an implementation
     * of Context.
     *
     * @param child Child container to be added
     */
    @Override
    public void addChild(Container child) {

        if (!(child instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardHost.notContext"));
        // 4. MemoryLeakTrackingListener 监听器则是在HostConfig监听器调用addChild方法把Context 容器添加到Host容器时添加，每个监听器
        // 负责详细的工作分别又有哪些？
        child.addLifecycleListener(new MemoryLeakTrackingListener());

        // Avoid NPE for case where Context is defined in server.xml with only a
        // docBase
        Context context = (Context) child;
        if (context.getPath() == null) {
            ContextName cn = new ContextName(context.getDocBase(), true);
            context.setPath(cn.getPath());
        }

        super.addChild(child);

    }


    /**
     * Used to ensure the regardless of {@link Context} implementation, a record
     * is kept of the class loader used every time a context starts.
     * MemoryLeakTrackingListener 监听器主要辅助完成关于内存泄漏跟踪的工作，一般情况下，如果我们通过重启Tomcat 重启Web 应用，
     * 则不存在内存泄漏问题，但是如果不重启Tomcat而对Web 应用进行重新加载 ，则可能会导致内存泄漏，因为重载后可能会导致原来的某些
     *
     * 看看是什么原因导致 Tomcat 内存泄漏，这个要从热部署开始说起，因为Tomcat 提供了不必要的重启容器而只需要重启Web 应用以达到热
     * 部署的功能，其实是通过定义一个WebClassLoader 类加载器，当热部署时，就将原来的类加载器废弃并重新实例化一个WebappClassLoader
     * 类加载器，但这种方式可能存在内存泄漏问题。因为类加载器是一个结构复杂的对象，导致它不能被GC回收的可能性比较多，除了对类加载器对象
     * 引用可能导致其无法回收之外，对其加载的元数据（方法，类，字段等） ,有引用也可能会导致无法被GC 回收
     *
     * Tomcat 的类加载器之间有父子关系，这里看启动类加载器BootstrapClassLoader和Web 应用类加载器WebappClassLoader ，在JVM 中
     * BootstrapClassLoader 负责加载rt.jar 包的java.sql.DriverManager ，而WebappClassLoader ，在JVM 中，BootstrapClassLoader
     * 负责加载 rt.jar 包下的java.sql.DriverManager，而WebappClassLoader 负责加载Web 应用中的Mysql 驱动包，其中 有一个很重要的
     * 步骤就是Mysql 的驱动类需要注册到DriverManager 中，即DriverManager.registerDriver （new Driver ） 它由Mysql 驱动包自动完成 。
     * 这样一来，Web应用进行热部署来操作时，如果没有将Mysql 的DriverManager 中反注册掉，则会导致 WebappclassLoader 无法回收，造成内存泄漏 。
     *
     * 接着讨论Tomcat 如何对内存泄漏进行监控，要判断WebappClassLoader 会不会导致内存泄漏，只须要判断WebappClassLoader 有没有被GC 回收
     * 即可，在Java 中有一种引用叫弱引用，它能很好的判断WebappClassLoader 有没有被GC 回收掉，被弱引用关联的对象只能生存到下一次垃圾回收。
     * 发生之前，即如果某WebappClassLoader 对象只能被某个弱引用关联外还被其他的对象引用，那么WebappClassLoader 对象是不会被回收的。
     * 根据这些条件就可以判断是否有WebappClassLoader 发生内存泄漏 。
     *
     * Tomcat 是实现通过WeakHashMap来实现弱引用的，只须将WebappClassLoader对象放到WeakHashMap 中，例如 weakMap.put("Loader1",WebappClassLoader)
     * 当WebappClassLoader 及其包含的元素没有被其他任何类加载器中的元素引用时，JVM 发生垃圾回收时则会把WebappClassLoader 对象回收。
     *
     * Tomcat 中的每个Host 容器都会对应若干个应用，为了跟踪这些应用是否有内存泄漏，需要将对应的Context 容器注册到Host 容器中的WeakHashMap
     * 中，而这里讨论的监听器MemoryLeakTrackingListenner 就负责Context 对应的WebappClassLoader 的注册工作 。
     *
     *
     */
    private class MemoryLeakTrackingListener implements LifecycleListener {
        @Override
        public void lifecycleEvent(LifecycleEvent event) {
            if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
                if (event.getSource() instanceof Context) {
                    Context context = ((Context) event.getSource());
                    childClassLoaders.put(context.getLoader().getClassLoader(),
                            context.getServletContext().getContextPath());
                }
            }
        }
    }


    /**
     * Attempt to identify the contexts that have a class loader memory leak.
     * This is usually triggered on context reload. Note: This method attempts
     * to force a full garbage collection. This should be used with extreme
     * caution on a production system.
     */
    public String[] findReloadedContextMemoryLeaks() {

        System.gc();

        List<String> result = new ArrayList<String>();

        for (Map.Entry<ClassLoader, String> entry :
                childClassLoaders.entrySet()) {
            ClassLoader cl = entry.getKey();
            if (cl instanceof WebappClassLoaderBase) {
                if (!((WebappClassLoaderBase) cl).isStarted()) {
                    result.add(entry.getValue());
                }
            }
        }

        return result.toArray(new String[result.size()]);
    }

    /**
     * Return the set of alias names for this Host.  If none are defined,
     * a zero length array is returned.
     */
    @Override
    public String[] findAliases() {

        synchronized (aliasesLock) {
            return (this.aliases);
        }

    }


    /**
     * Return descriptive information about this Container implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    @Override
    public String getInfo() {

        return (info);

    }


    /**
     * Remove the specified alias name from the aliases for this Host.
     *
     * @param alias Alias name to be removed
     */
    @Override
    public void removeAlias(String alias) {

        alias = alias.toLowerCase(Locale.ENGLISH);

        synchronized (aliasesLock) {

            // Make sure this alias is currently present
            int n = -1;
            for (int i = 0; i < aliases.length; i++) {
                if (aliases[i].equals(alias)) {
                    n = i;
                    break;
                }
            }
            if (n < 0)
                return;

            // Remove the specified alias
            int j = 0;
            String results[] = new String[aliases.length - 1];
            for (int i = 0; i < aliases.length; i++) {
                if (i != n)
                    results[j++] = aliases[i];
            }
            aliases = results;

        }

        // Inform interested listeners
        fireContainerEvent(REMOVE_ALIAS_EVENT, alias);

    }


    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder();
        if (getParent() != null) {
            sb.append(getParent().toString());
            sb.append(".");
        }
        sb.append("StandardHost[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }

    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     *
     * 1. 如果配置了集群组件Cluster ,则启动
     * 2. 如果配置了安全组件Realm ,则启动
     * 3. 启动子节点（即通过server.xml 中的<Context>创建StandardContext实例），StandardContext启动见ghlf
     * 4.启动Host持有的Pipeline组件
     * 5.设置Host状态为STARTING, 此时会触发START_EVENT生命周期事件，HostConfig监听该事件，扫描Web 部署目录，对于部署文件
     * ，WAR 包，目录会自动创建StandardContext闪现，添加到Host 并启动。
     * 6. 启动Host层级的后台任务处理， Cluster后台任务处理（包括部署变更检测，心跳，），Realm后台任务处理， Pipeline中的Value 的后台
     * 任务处理，某些Value 通过后台任务实现定期处理功能，如StuckThreadDetectionValue 用于检测耗时请求。
     *
     *
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Set error report valve
        String errorValve = getErrorReportValveClass();
        if ((errorValve != null) && (!errorValve.equals(""))) {
            try {
                boolean found = false;
                Valve[] valves = getPipeline().getValves();
                for (Valve valve : valves) {
                    if (errorValve.equals(valve.getClass().getName())) {
                        found = true;
                        break;
                    }
                }
                if(!found) {
                    Valve valve =
                        (Valve) Class.forName(errorValve).getDeclaredConstructor().newInstance();
                    getPipeline().addValve(valve);
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString(
                        "standardHost.invalidErrorReportValveClass",
                        errorValve), t);
            }
        }
        super.startInternal();
    }


    // -------------------- JMX  --------------------
    /**
      * Return the MBean Names of the Valves associated with this Host
      *
      * @exception Exception if an MBean cannot be created or registered
      */
     public String [] getValveNames()
         throws Exception
    {
         Valve [] valves = this.getPipeline().getValves();
         String [] mbeanNames = new String[valves.length];
         for (int i = 0; i < valves.length; i++) {
             if( valves[i] == null ) continue;
             if( ((ValveBase)valves[i]).getObjectName() == null ) continue;
             mbeanNames[i] = ((ValveBase)valves[i]).getObjectName().toString();
         }

         return mbeanNames;

     }

    public String[] getAliases() {
        synchronized (aliasesLock) {
            return aliases;
        }
    }

    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder keyProperties = new StringBuilder("type=Host");
        keyProperties.append(MBeanUtils.getContainerKeyProperties(this));

        return keyProperties.toString();
    }

}
