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
package org.apache.catalina.loader;


import java.beans.PropertyChangeEvent;
import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.io.File;
import java.io.FileOutputStream;
import java.io.FilePermission;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;
import java.lang.reflect.Constructor;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLStreamHandlerFactory;
import java.util.ArrayList;
import java.util.jar.JarFile;

import javax.management.ObjectName;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.ServletContext;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.naming.resources.DirContextURLStreamHandler;
import org.apache.naming.resources.DirContextURLStreamHandlerFactory;
import org.apache.naming.resources.Resource;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UDecoder;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.res.StringManager;


/**
 * Classloader implementation which is specialized for handling web
 * applications in the most efficient way, while being Catalina aware (all
 * accesses to resources are made through the DirContext interface).
 * This class loader supports detection of modified
 * Java classes, which can be used to implement auto-reload support.
 * <p>
 * This class loader is configured by adding the pathnames of directories,
 * JAR files, and ZIP files with the <code>addRepository()</code> method,
 * prior to calling <code>start()</code>.  When a new class is required,
 * these repositories will be consulted first to locate the class.  If it
 * is not present, the system class loader will be used instead.
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 *
 *
 * StandardContext 中 Loader 接口的标准实现 WebappLoader 类，有一个单独线程 来检查 WEB-INF 目录下面所有类和 JAR 文件的时间戳。
 * 你需要做的是启动该线程， 将 WebappLoader关联到StandardContext，使用setContainer方法即可。下面是 Tomcat4 中 WebappLoader 的实现:
 *
 * 每个Web 应用都有各自的Class类和Jar包，一般来说，在Tomcat 启动时要准备好相应的类加载器，包括加载策略及Class 文件的查找，方便后面对
 * Web 应用实例的Servlet对象时通过类加载器加载相关的类，因为每个Web应用不仅仅要达到资源的互相隔离，还要能支持生加载 ，所以这里需要为
 * 每个Web 应用安排不同的类加载器对象加载，重加载时可直接将旧的类加载器对象丢弃而使用新的类加载器。
 *
 *
 *
 *  WebappLoader 的核心工作其实交给其他的内部WebAppClassLoader，它才是真正的完成类加载工作的加载器，它是一个自定义的类加载器，WebappClassLoader
 *  继承了URLClassLoader只需要把/WEB-INF/lib 和WEB-INF/classes 目录下的类和Jar包以URL 的形式添加到URLClassLoader即可，后面就可以用该类加载器
 *  对类进行加载 。
 *
 *
 *
 *
 */

public class WebappLoader extends LifecycleMBeanBase
    implements Loader, PropertyChangeListener {

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new WebappLoader with no defined parent class loader
     * (so that the actual parent will be the system class loader).
     */
    public WebappLoader() {

        this(null);

    }


    /**
     * Construct a new WebappLoader with the specified class loader
     * to be defined as the parent of the ClassLoader we ultimately create.
     *
     * @param parent The parent class loader
     */
    public WebappLoader(ClassLoader parent) {
        super();
        this.parentClassLoader = parent;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * First load of the class.
     */
    private static boolean first = true;


    /**
     * The class loader being managed by this Loader component.
     */
    private WebappClassLoaderBase classLoader = null;


    /**
     * The Container with which this Loader has been associated.
     */
    private Container container = null;


    /**
     * The "follow standard delegation model" flag that will be used to
     * configure our ClassLoader.
     */
    private boolean delegate = false;

    /**
     * The interval in milliseconds to keep all jar files open if no jar is accessed
     */
    private int jarOpenInterval = 90000;

    /**
     * The descriptive information about this Loader implementation.
     */
    private static final String info =
        "org.apache.catalina.loader.WebappLoader/1.0";


    /**
     * The Java class name of the ClassLoader implementation to be used.
     * This class should extend WebappClassLoaderBase, otherwise, a different
     * loader implementation must be used.
     */
    private String loaderClass =
        "org.apache.catalina.loader.WebappClassLoader";


    /**
     * The parent class loader of the class loader we will create.
     */
    private ClassLoader parentClassLoader = null;


    /**
     * The reloadable flag for this Loader.
     */
    private boolean reloadable = false;


    /**
     * The set of repositories associated with this class loader.
     */
    private String repositories[] = new String[0];


    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    /**
     * The property change support for this component.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * Classpath set in the loader.
     */
    private String classpath = null;


    /**
     * Repositories that are set in the loader, for JMX.
     * 存了/WEB-INF/classes路径
     * 存了/WEB-INF/lib目录下的所有jar包的路径
     */
    private ArrayList<String> loaderRepositories = null;


    /**
     * Whether we should search the external repositories first
     */
    private boolean searchExternalFirst = false;


    // ------------------------------------------------------------- Properties


    /**
     * Return the Java class loader to be used by this Container.
     */
    @Override
    public ClassLoader getClassLoader() {

        return classLoader;

    }


    /**
     * Return the Container with which this Logger has been associated.
     */
    @Override
    public Container getContainer() {

        return (container);

    }


    /**
     * Set the Container with which this Logger has been associated.
     *
     * @param container The associated Container
     */
    @Override
    public void setContainer(Container container) {

        // Deregister from the old Container (if any)
        if ((this.container != null) && (this.container instanceof Context))
            ((Context) this.container).removePropertyChangeListener(this);

        // Process this property change
        Container oldContainer = this.container;
        this.container = container;
        support.firePropertyChange("container", oldContainer, this.container);

        // Register with the new Container (if any)
        // 注意最后一个 if 语句块中，如果容器是一个上下文容器，调用 setReloadable 方法，也就是说 WebappLoader 的 reloadable 属性跟 StandardContext 的 reloadable 属性相同。
        if ((this.container != null) && (this.container instanceof Context)) {
            setReloadable( ((Context) this.container).getReloadable() );
            ((Context) this.container).addPropertyChangeListener(this);
        }

    }


    /**
     * Return the "follow standard delegation model" flag used to configure
     * our ClassLoader.
     */
    @Override
    public boolean getDelegate() {

        return (this.delegate);

    }


    /**
     * Set the "follow standard delegation model" flag used to configure
     * our ClassLoader.
     *
     * @param delegate The new flag
     */
    @Override
    public void setDelegate(boolean delegate) {

        boolean oldDelegate = this.delegate;
        this.delegate = delegate;
        support.firePropertyChange("delegate", Boolean.valueOf(oldDelegate),
                                   Boolean.valueOf(this.delegate));

    }

    /**
     * The interval to keep all jar files open if no jar is accessed
     *
     * @param jarOpenInterval The new interval
     */
    public void setJarOpenInterval(int jarOpenInterval) {
        this.jarOpenInterval = jarOpenInterval;
    }

    /**
     * Return the interval to keep all jar files open if no jar is accessed
     */
    public int getJarOpenInterval() {
        return jarOpenInterval;
    }

    /**
     * Return descriptive information about this Loader implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    @Override
    public String getInfo() {

        return (info);

    }


    /**
     * Return the ClassLoader class name.
     */
    public String getLoaderClass() {

        return (this.loaderClass);

    }


    /**
     * Set the ClassLoader class name.
     *
     * @param loaderClass The new ClassLoader class name
     */
    public void setLoaderClass(String loaderClass) {

        this.loaderClass = loaderClass;

    }


    /**
     * Return the reloadable flag for this Loader.
     */
    @Override
    public boolean getReloadable() {

        return (this.reloadable);

    }


    /**
     * Set the reloadable flag for this Loader.
     *
     * @param reloadable The new reloadable flag
     */
    @Override
    public void setReloadable(boolean reloadable) {

        // Process this property change
        boolean oldReloadable = this.reloadable;
        this.reloadable = reloadable;
        support.firePropertyChange("reloadable",
                                   Boolean.valueOf(oldReloadable),
                                   Boolean.valueOf(this.reloadable));

    }

    /**
     * @return Returns searchExternalFirst.
     */
    public boolean getSearchExternalFirst() {
        return searchExternalFirst;
    }

    /**
     * @param searchExternalFirst Whether external repositories should be searched first
     */
    public void setSearchExternalFirst(boolean searchExternalFirst) {
        this.searchExternalFirst = searchExternalFirst;
        if (classLoader != null) {
            classLoader.setSearchExternalFirst(searchExternalFirst);
        }
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    @Override
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }


    /**
     * Add a new repository to the set of repositories for this class loader.
     *
     * @param repository Repository to be added
     */
    @Override
    public void addRepository(String repository) {

        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.addRepository", repository));

        for (int i = 0; i < repositories.length; i++) {
            if (repository.equals(repositories[i]))
                return;
        }
        String results[] = new String[repositories.length + 1];
        for (int i = 0; i < repositories.length; i++)
            results[i] = repositories[i];
        results[repositories.length] = repository;
        repositories = results;

        if (getState().isAvailable() && (classLoader != null)) {
            classLoader.addRepository(repository);
            if( loaderRepositories != null ) loaderRepositories.add(repository);
            setClassPath();
        }

    }


    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     */
    @Override
    public void backgroundProcess() {
        if (reloadable && modified()) {
            System.out.println(container.getInfo()+"触发了热加载");
            try {
                Thread.currentThread().setContextClassLoader
                    (WebappLoader.class.getClassLoader());
                if (container instanceof StandardContext) {
                    ((StandardContext) container).reload();
                }
            } finally {
                if (container.getLoader() != null) {
                    Thread.currentThread().setContextClassLoader
                        (container.getLoader().getClassLoader());
                }
            }
        } else {
            closeJARs(false);
        }
    }


    /**
     * Return the set of repositories defined for this class loader.
     * If none are defined, a zero-length array is returned.
     * For security reason, returns a clone of the Array (since
     * String are immutable).
     */
    @Override
    public String[] findRepositories() {

        return repositories.clone();

    }

    public String[] getRepositories() {
        return repositories.clone();
    }

    /** Extra repositories for this loader
     */
    public String getRepositoriesString() {
        StringBuilder sb=new StringBuilder();
        for( int i=0; i<repositories.length ; i++ ) {
            sb.append( repositories[i]).append(":");
        }
        return sb.toString();
    }

    public String[] getLoaderRepositories() {
        if( loaderRepositories==null ) return  null;
        String res[]=new String[ loaderRepositories.size()];
        loaderRepositories.toArray(res);
        return res;
    }

    public String getLoaderRepositoriesString() {
        String repositories[]=getLoaderRepositories();
        StringBuilder sb=new StringBuilder();
        for( int i=0; i<repositories.length ; i++ ) {
            sb.append( repositories[i]).append(":");
        }
        return sb.toString();
    }


    /**
     * Classpath, as set in org.apache.catalina.jsp_classpath context
     * property
     *
     * @return The classpath
     */
    public String getClasspath() {
        return classpath;
    }


    /**
     * Has the internal repository associated with this Loader been modified,
     * such that the loaded classes should be reloaded?
     */
    @Override
    public boolean modified() {
        return classLoader != null ? classLoader.modified() : false ;
    }


    /**
     * Used to periodically signal to the classloader to release JAR resources.
     */
    public void closeJARs(boolean force) {
        if (classLoader !=null) {
            classLoader.closeJARs(force);
        }
    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("WebappLoader[");
        if (container != null)
            sb.append(container.getName());
        sb.append("]");
        return (sb.toString());

    }


    /**
     * Start associated {@link ClassLoader} and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.starting"));

        // 获取容器的文件Context--DirContext,如果为空，证明Context没有有用的内容
        if (container.getResources() == null) {
            log.info("No resources for " + container);
            setState(LifecycleState.STARTING);
            return;
        }

        // Register a stream handler factory for the JNDI protocol
        URLStreamHandlerFactory streamHandlerFactory =
                DirContextURLStreamHandlerFactory.getInstance();
        if (first) {
            first = false;
            try {
                URL.setURLStreamHandlerFactory(streamHandlerFactory);
            } catch (Exception e) {
                // Log and continue anyway, this is not critical
                log.error("Error registering jndi stream handler", e);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                // This is likely a dual registration
                log.info("Dual registration of jndi stream handler: "
                         + t.getMessage());
            }
        }

        // Construct a class loader based on our current repositories list
        try {

            classLoader = createClassLoader(); // 创建一个WebappClassLoader
            classLoader.setJarOpenInterval(this.jarOpenInterval);
            classLoader.setResources(container.getResources());
            classLoader.setDelegate(this.delegate);
            classLoader.setSearchExternalFirst(searchExternalFirst);
            if (container instanceof StandardContext) {
                classLoader.setAntiJARLocking(
                        ((StandardContext) container).getAntiJARLocking());
                classLoader.setClearReferencesRmiTargets(
                        ((StandardContext) container).getClearReferencesRmiTargets());
                classLoader.setClearReferencesStatic(
                        ((StandardContext) container).getClearReferencesStatic());
                classLoader.setClearReferencesStopThreads(
                        ((StandardContext) container).getClearReferencesStopThreads());
                classLoader.setClearReferencesStopTimerThreads(
                        ((StandardContext) container).getClearReferencesStopTimerThreads());
                classLoader.setClearReferencesHttpClientKeepAliveThread(
                        ((StandardContext) container).getClearReferencesHttpClientKeepAliveThread());
                classLoader.setClearReferencesObjectStreamClassCaches(
                        ((StandardContext) container).getClearReferencesObjectStreamClassCaches());
                classLoader.setClearReferencesThreadLocals(
                        ((StandardContext) container).getClearReferencesThreadLocals());
            }

            for (int i = 0; i < repositories.length; i++) {
                classLoader.addRepository(repositories[i]);
            }

            // Configure our repositories
            setRepositories();  // 将/WEB-INF/classes和/WEB-INF/lib目录添加到WebappClassLoader的Repository中，以后将从Repository中寻找并加载类
            setClassPath(); // 设置当前加载器的classpath，应该是只有在jsp中用到

            setPermissions();

            ((Lifecycle) classLoader).start();  // 调用WebappClassLoaderBase.start()，赋值webInfClassesCodeBase属性，这个属性不知道哪里会用到，表示web-inf/classes目录

            // Binding the Webapp class loader to the directory context
            // 类加载器与DirContext的一个映射关系
            DirContextURLStreamHandler.bind(classLoader,
                    this.container.getResources());

            // 注册jmx
            StandardContext ctx=(StandardContext)container;
            String contextName = ctx.getName();
            if (!contextName.startsWith("/")) {
                contextName = "/" + contextName;
            }
            ObjectName cloname = new ObjectName
                (MBeanUtils.getDomain(ctx) + ":type=WebappClassLoader,context="
                 + contextName + ",host=" + ctx.getParent().getName());
            Registry.getRegistry(null, null)
                .registerComponent(classLoader, cloname, null);

        } catch (Throwable t) {
            t = ExceptionUtils.unwrapInvocationTargetException(t);
            ExceptionUtils.handleThrowable(t);
            log.error( "LifecycleException ", t );
            throw new LifecycleException("start: ", t);
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop associated {@link ClassLoader} and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled())
            log.debug(sm.getString("webappLoader.stopping"));

        setState(LifecycleState.STOPPING);

        // Remove context attributes as appropriate
        if (container instanceof Context) {
            ServletContext servletContext =
                ((Context) container).getServletContext();
            servletContext.removeAttribute(Globals.CLASS_PATH_ATTR);
        }

        // Throw away our current class loader
        if (classLoader != null) {
            ((Lifecycle) classLoader).stop();
            DirContextURLStreamHandler.unbind(classLoader);
        }

        try {
            StandardContext ctx=(StandardContext)container;
            String contextName = ctx.getName();
            if (!contextName.startsWith("/")) {
                contextName = "/" + contextName;
            }
            ObjectName cloname = new ObjectName
                (MBeanUtils.getDomain(ctx) + ":type=WebappClassLoader,context="
                 + contextName + ",host=" + ctx.getParent().getName());
            Registry.getRegistry(null, null).unregisterComponent(cloname);
        } catch (Exception e) {
            log.warn("LifecycleException ", e);
        }

        classLoader = null;
    }


    // ----------------------------------------- PropertyChangeListener Methods


    /**
     * Process property change events from our associated Context.
     *
     * @param event The property change event that has occurred
     */
    @Override
    public void propertyChange(PropertyChangeEvent event) {

        // Validate the source of this event
        if (!(event.getSource() instanceof Context))
            return;

        // Process a relevant property change
        if (event.getPropertyName().equals("reloadable")) {
            try {
                setReloadable
                    ( ((Boolean) event.getNewValue()).booleanValue() );
            } catch (NumberFormatException e) {
                log.error(sm.getString("webappLoader.reloadable",
                                 event.getNewValue().toString()));
            }
        }

    }


    // ------------------------------------------------------- Private Methods


    /**
     * Create associated classLoader.
     */
    private WebappClassLoaderBase createClassLoader()
        throws Exception {
        // 创建一个类加载器WebappClassLoader

        Class<?> clazz = Class.forName(loaderClass);
        WebappClassLoaderBase classLoader = null;

        if (parentClassLoader == null) {
            // 父加载器为容器的父加载器
            parentClassLoader = container.getParentClassLoader();
        }

        // 下面的代码相当于 classLoader = new WebappClassLoader(parentClassLoader);
        Class<?>[] argTypes = { ClassLoader.class };
        Object[] args = { parentClassLoader };
        Constructor<?> constr = clazz.getConstructor(argTypes);
        classLoader = (WebappClassLoaderBase) constr.newInstance(args);

        return classLoader;

    }


    /**
     * Configure associated class loader permissions.
     */
    private void setPermissions() {

        if (!Globals.IS_SECURITY_ENABLED)
            return;
        if (!(container instanceof Context))
            return;

        // Tell the class loader the root of the context
        ServletContext servletContext =
            ((Context) container).getServletContext();

        // Assigning permissions for the work directory
        File workDir =
            (File) servletContext.getAttribute(ServletContext.TEMPDIR);
        if (workDir != null) {
            try {
                String workDirPath = workDir.getCanonicalPath();
                classLoader.addPermission
                    (new FilePermission(workDirPath, "read,write"));
                classLoader.addPermission
                    (new FilePermission(workDirPath + File.separator + "-",
                                        "read,write,delete"));
            } catch (IOException e) {
                // Ignore
            }
        }

        try {

            URL rootURL = servletContext.getResource("/");
            classLoader.addPermission(rootURL);

            String contextRoot = servletContext.getRealPath("/");
            if (contextRoot != null) {
                try {
                    contextRoot = (new File(contextRoot)).getCanonicalPath();
                    classLoader.addPermission(contextRoot);
                } catch (IOException e) {
                    // Ignore
                }
            }

            URL classesURL = servletContext.getResource("/WEB-INF/classes/");
            classLoader.addPermission(classesURL);
            URL libURL = servletContext.getResource("/WEB-INF/lib/");
            classLoader.addPermission(libURL);

            if (contextRoot != null) {

                if (libURL != null) {
                    File rootDir = new File(contextRoot);
                    File libDir = new File(rootDir, "WEB-INF/lib/");
                    try {
                        String path = libDir.getCanonicalPath();
                        classLoader.addPermission(path);
                    } catch (IOException e) {
                        // Ignore
                    }
                }

            } else {

                if (workDir != null) {
                    if (libURL != null) {
                        File libDir = new File(workDir, "WEB-INF/lib/");
                        try {
                            String path = libDir.getCanonicalPath();
                            classLoader.addPermission(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                    if (classesURL != null) {
                        File classesDir = new File(workDir, "WEB-INF/classes/");
                        try {
                            String path = classesDir.getCanonicalPath();
                            classLoader.addPermission(path);
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                }

            }

        } catch (MalformedURLException e) {
            // Ignore
        }

    }


    /**
     * Configure the repositories for our class loader, based on the
     * associated Context.
     * 基于Context配置类加载器的仓库
     *
     * @throws IOException
     */
    private void setRepositories() throws IOException {

        if (!(container instanceof Context))
            return;
        ServletContext servletContext =
            ((Context) container).getServletContext();
        if (servletContext == null)
            return;

        loaderRepositories=new ArrayList<String>();
        // Loading the work directory
        File workDir =
            (File) servletContext.getAttribute(ServletContext.TEMPDIR);
        if (workDir == null) {
            log.info("No work dir for " + servletContext);
        }

        if( log.isDebugEnabled() && workDir != null)
            log.debug(sm.getString("webappLoader.deploy", workDir.getAbsolutePath()));

        // 设置类加载器的工作目录
        classLoader.setWorkDir(workDir);

        // 获取Context对应的文件目录DirContext
        DirContext resources = container.getResources();

        // Setting up the class repository (/WEB-INF/classes), if it exists

        String classesPath = "/WEB-INF/classes";
        DirContext classes = null;

        try {
            // 从文件目录DirContext查找"/WEB-INF/classes"文件目录
            Object object = resources.lookup(classesPath);
            if (object instanceof DirContext) {
                classes = (DirContext) object;
            }
        } catch(NamingException e) {
            // Silent catch: it's valid that no /WEB-INF/classes collection
            // exists
        }

        if (classes != null) {

            File classRepository = null;

            String absoluteClassesPath =
                servletContext.getRealPath(classesPath);

            // 如果是一个绝对路径
            if (absoluteClassesPath != null) {

                classRepository = new File(absoluteClassesPath);

            } else {

                classRepository = new File(workDir, classesPath);
                if (!classRepository.mkdirs() &&
                        !classRepository.isDirectory()) {
                    throw new IOException(
                            sm.getString("webappLoader.mkdirFailure"));
                }
                if (!copyDir(classes, classRepository)) {
                    throw new IOException(
                            sm.getString("webappLoader.copyFailure"));
                }

            }

            if(log.isDebugEnabled())
                log.debug(sm.getString("webappLoader.classDeploy", classesPath,
                             classRepository.getAbsolutePath()));


            // Adding the repository to the class loader
            // 将"/WEB-INF/classes"目录添加到类加载器仓库中
            classLoader.addRepository(classesPath + "/", classRepository);
            loaderRepositories.add(classesPath + "/" );

        }

        // Setting up the JAR repository (/WEB-INF/lib), if it exists

        String libPath = "/WEB-INF/lib";

        // 设置类加载器的jar包路径
        classLoader.setJarPath(libPath);

        DirContext libDir = null;
        // Looking up directory /WEB-INF/lib in the context
        try {
            Object object = resources.lookup(libPath);
            if (object instanceof DirContext)
                libDir = (DirContext) object;
        } catch(NamingException e) {
            // Silent catch: it's valid that no /WEB-INF/lib collection
            // exists
        }

        if (libDir != null) {

            boolean copyJars = false;
            String absoluteLibPath = servletContext.getRealPath(libPath);

            File destDir = null;

            if (absoluteLibPath != null) {
                destDir = new File(absoluteLibPath);
            } else {
                copyJars = true;
                destDir = new File(workDir, libPath);
                if (!destDir.mkdirs() && !destDir.isDirectory()) {
                    throw new IOException(
                            sm.getString("webappLoader.mkdirFailure"));
                }
            }

            // Looking up directory /WEB-INF/lib in the context
            NamingEnumeration<NameClassPair> enumeration = null;
            try {
                enumeration = libDir.list("");
            } catch (NamingException e) {
                IOException ioe = new IOException(sm.getString(
                        "webappLoader.namingFailure", libPath));
                ioe.initCause(e);
                throw ioe;
            }

            // 便利"/WEB-INF/lib"目录下的jar包
            while (enumeration.hasMoreElements()) {
                NameClassPair ncPair = enumeration.nextElement();
                String filename = libPath + "/" + ncPair.getName();
                if (!filename.endsWith(".jar"))
                    continue;

                // Copy JAR in the work directory, always (the JAR file
                // would get locked otherwise, which would make it
                // impossible to update it or remove it at runtime)
                File destFile = new File(destDir, ncPair.getName());

                if( log.isDebugEnabled())
                log.debug(sm.getString("webappLoader.jarDeploy", filename,
                                 destFile.getAbsolutePath()));

                // Bug 45403 - Explicitly call lookup() on the name to check
                // that the resource is readable. We cannot use resources
                // returned by listBindings(), because that lists all of them,
                // but does not perform the necessary checks on each.
                Object obj = null;
                try {
                    obj = libDir.lookup(ncPair.getName());
                } catch (NamingException e) {
                    IOException ioe = new IOException(sm.getString(
                            "webappLoader.namingFailure", filename));
                    ioe.initCause(e);
                    throw ioe;
                }

                if (!(obj instanceof Resource))
                    continue;

                Resource jarResource = (Resource) obj;

                if (copyJars) {
                    if (!copy(jarResource.streamContent(),
                              new FileOutputStream(destFile))) {
                        throw new IOException(
                                sm.getString("webappLoader.copyFailure"));
                    }
                }

                try {
                    JarFile jarFile = JreCompat.getInstance().jarFileNewInstance(destFile);
                    // 把jar添加到类加载器中
                    classLoader.addJar(filename, jarFile, destFile);
                } catch (Exception ex) {
                    // Catch the exception if there is an empty jar file
                    // Should ignore and continue loading other jar files
                    // in the dir
                }

                loaderRepositories.add( filename );
            }
        }


        System.out.println(classLoader.jarPath);
        System.out.println(classLoader.files);
        System.out.println(classLoader.paths);
        System.out.println(classLoader.jarNames);
        System.out.println(classLoader.jarRealFiles);
        System.out.println(repositories);
        System.out.println(loaderRepositories);

    }


    /**
     * Set the appropriate context attribute for our class path.  This
     * is required only because Jasper depends on it.
     */
    private void setClassPath() {

        // Validate our current state information
        if (!(container instanceof Context))
            return;
        ServletContext servletContext =
            ((Context) container).getServletContext();
        if (servletContext == null)
            return;

        if (container instanceof StandardContext) {
            String baseClasspath =
                ((StandardContext) container).getCompilerClasspath();
            if (baseClasspath != null) {
                servletContext.setAttribute(Globals.CLASS_PATH_ATTR,
                                            baseClasspath);
                return;
            }
        }

        StringBuilder classpath = new StringBuilder();

        // Assemble the class path information from our class loader chain
        ClassLoader loader = getClassLoader();

        // 如果委托给父类，则直接从父类加载器开始组装classpath链，然后把本应用的类加载器中的资源放在最后
        if (delegate && loader != null) {
            // Skip the webapp loader for now as delegation is enabled
            loader = loader.getParent();
        }

        while (loader != null) {
            if (!buildClassPath(servletContext, classpath, loader)) {
                break;
            }
            loader = loader.getParent();
        }

        if (delegate) {
            // Delegation was enabled, go back and add the webapp paths
            loader = getClassLoader();
            if (loader != null) {
                buildClassPath(servletContext, classpath, loader);
            }
        }
        // /Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/classes/:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/commons-codec-1.11.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/commons-logging-1.2.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/fastjson-1.2.60.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/hamcrest-core-1.3.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/httpclient-4.5.13.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/httpcore-4.4.13.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/hutool-all-5.7.20.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/junit-4.12.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/logback-classic-1.2.3.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/logback-core-1.2.3.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/lombok-1.18.16.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/mysql-connector-java-5.1.32.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/slf4j-api-1.7.25.jar:/Users/quyixiao/gitlab/tomcat/webapps/servelet-test-1.0/WEB-INF/lib/test-resource-1.0-20220808.112917-2.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/charsets.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/deploy.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/cldrdata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/dnsns.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/jaccess.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/jfxrt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/localedata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/nashorn.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/sunec.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/sunjce_provider.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/sunpkcs11.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/zipfs.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/javaws.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/jce.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/jfr.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/jfxswt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/jsse.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/management-agent.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/plugin.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/resources.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/rt.jar:/Users/quyixiao/gitlab/tomcat/output/production/tomcat/:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/ant-1.9.8.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/cglib-2.2.3/cglib-nodep-2.2.3.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/commons-daemon-1.2.1/commons-daemon-1.2.1-tests.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/commons-daemon-1.2.1/commons-daemon-1.2.1.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/easymock-3.2/easymock-3.2.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/ecj-4.4.2/ecj-4.4.2.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/hamcrest-1.3/hamcrest-core-1.3.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/jaxrpc-1.1-rc4/geronimo-spec-jaxrpc-1.1-rc4.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/junit-4.12/junit-4.12.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/objenesis-1.2/objenesis-1.2.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/saaj-api-1.3.5/saaj-api-1.3.5.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/wsdl4j-1.6.3/wsdl4j-1.6.3.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/jstl-api-1.2.jar:/Users/quyixiao/gitlab/tomcat/tomcat-build-libs/standard-1.1.2.jar:/Applications/IntelliJ IDEA.app/Contents/lib/idea_rt.jar:/Users/quyixiao/Library/Caches/JetBrains/IntelliJIdea2021.3/captureAgent/debugger-agent.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/sunec.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/nashorn.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/cldrdata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/jfxrt.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/dnsns.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/localedata.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/sunjce_provider.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/sunpkcs11.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/jaccess.jar:/Library/Java/JavaVirtualMachines/jdk1.8.0_162.jdk/Contents/Home/jre/lib/ext/zipfs.jar:/System/Library/Java/Extensions/MRJToolkit.jar
        this.classpath=classpath.toString();

        // Store the assembled class path as a servlet context attribute
        servletContext.setAttribute(Globals.CLASS_PATH_ATTR,
                                    classpath.toString());

    }


    private boolean buildClassPath(ServletContext servletContext,
            StringBuilder classpath, ClassLoader loader) {
        if (loader instanceof URLClassLoader) {
            URL repositories[] = ((URLClassLoader) loader).getURLs();
                for (int i = 0; i < repositories.length; i++) {
                    String repository = repositories[i].toString();
                    if (repository.startsWith("file://"))
                        repository = UDecoder.URLDecode(repository.substring(7));
                    else if (repository.startsWith("file:"))
                        repository = UDecoder.URLDecode(repository.substring(5));
                    else if (repository.startsWith("jndi:"))
                        repository =
                            servletContext.getRealPath(repository.substring(5));
                    else
                        continue;
                    if (repository == null)
                        continue;
                    if (classpath.length() > 0)
                        classpath.append(File.pathSeparator);
                    classpath.append(repository);
                }
        } else if (loader == ClassLoader.getSystemClassLoader()){
            // Java 9 onwards. The internal class loaders no longer extend
            // URLCLassLoader
            String cp = System.getProperty("java.class.path");
            if (cp != null && cp.length() > 0) {
                if (classpath.length() > 0) {
                    classpath.append(File.pathSeparator);
                }
                classpath.append(cp);
            }
            return false;
        } else {
            log.info( "Unknown loader " + loader + " " + loader.getClass());
            return false;
        }
        return true;
    }


    /**
     * Copy directory.
     */
    private boolean copyDir(DirContext srcDir, File destDir) {

        try {

            NamingEnumeration<NameClassPair> enumeration = srcDir.list("");
            while (enumeration.hasMoreElements()) {
                NameClassPair ncPair = enumeration.nextElement();
                String name = ncPair.getName();
                Object object = srcDir.lookup(name);
                File currentFile = new File(destDir, name);
                if (object instanceof Resource) {
                    InputStream is = ((Resource) object).streamContent();
                    OutputStream os = new FileOutputStream(currentFile);
                    if (!copy(is, os))
                        return false;
                } else if (object instanceof InputStream) {
                    OutputStream os = new FileOutputStream(currentFile);
                    if (!copy((InputStream) object, os))
                        return false;
                } else if (object instanceof DirContext) {
                    if (!currentFile.isDirectory() && !currentFile.mkdir())
                        return false;
                    if (!copyDir((DirContext) object, currentFile))
                        return false;
                }
            }

        } catch (NamingException e) {
            return false;
        } catch (IOException e) {
            return false;
        }

        return true;

    }


    /**
     * Copy a file to the specified temp directory. This is required only
     * because Jasper depends on it.
     */
    private boolean copy(InputStream is, OutputStream os) {

        try {
            byte[] buf = new byte[4096];
            while (true) {
                int len = is.read(buf);
                if (len < 0)
                    break;
                os.write(buf, 0, len);
            }
            is.close();
            os.close();
        } catch (IOException e) {
            return false;
        }

        return true;

    }


    private static final org.apache.juli.logging.Log log=
        org.apache.juli.logging.LogFactory.getLog( WebappLoader.class );


    @Override
    protected String getDomainInternal() {
        return MBeanUtils.getDomain(container);
    }


    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder name = new StringBuilder("type=Loader");

        if (container instanceof Context) {
            name.append(",context=");
            Context context = (Context) container;

            String contextName = context.getName();
            if (!contextName.startsWith("/")) {
                name.append("/");
            }
            name.append(contextName);

            name.append(",host=");
            name.append(context.getParent().getName());
        } else {
            // Unlikely / impossible? Handle it to be safe
            name.append(",container=");
            name.append(container.getName());
        }

        return name.toString();
    }

}
