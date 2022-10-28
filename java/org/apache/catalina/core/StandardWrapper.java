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

import java.io.PrintStream;
import java.lang.reflect.Method;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Stack;
import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.locks.ReentrantReadWriteLock;

import javax.management.ListenerNotFoundException;
import javax.management.MBeanNotificationInfo;
import javax.management.Notification;
import javax.management.NotificationBroadcasterSupport;
import javax.management.NotificationEmitter;
import javax.management.NotificationFilter;
import javax.management.NotificationListener;
import javax.management.ObjectName;
import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletResponse;
import javax.servlet.SingleThreadModel;
import javax.servlet.UnavailableException;
import javax.servlet.annotation.MultipartConfig;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerServlet;
import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.InstanceEvent;
import org.apache.catalina.InstanceListener;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Wrapper;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.InstanceSupport;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.PeriodicEventListener;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.modeler.Util;

/**
 * Standard implementation of the <b>Wrapper</b> interface that represents
 * an individual servlet definition.  No child Containers are allowed, and
 * the parent Container must be a Context.
 *
 * Wrapper接口的标准实现，表示一个Servlet，不允许有子容器，并且父容器一定要是一个Context
 *
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 * 一共有四种容器： engine(引擎)，host(主机)，context (上下文)和wrapper(包装器)，在前面的章节里也介绍了如何建立自己的context 和wrapper
 * 一个上下文一般包括一个或多个包装器，每一个包装器表示一个servlet，本章将会看到 Catalina 中Wrapper接口的标准实现，首先介绍一个HTTP
 * 请求会唤醒的一系列方法，接下来介绍了javax.servlet.SingleThreadModel接口，最后介绍了StandardWrapper 和StandardWrapperValue类。
 * 本章的应用程序说明了如何用StandardWrapper 实例来表示servlet
 *
 * 方法调用序列 Sequence of Methods Invocation
 * 对于每一个连接，连接器都会调用关联容器的invoke方法，接下来容器调用它的所有子容器的invoke方法，例如 ，如果一个连接器跟一个StandardContext
 * 实例相dubu，那么连接器会调用StandardContext 实例的invoke方法，该方法会调用所有它的子容器的invoke方法，说明一个连接器收到了一个HTTP
 * 请求的时候会做一系列的事情 。
 *
 * 一个StandardWrapper 对象的主要职责是：加载它表示servlet 并分配它的一个实例，该StandardWrapper 不会调用servlet的service方法，这个任务
 * 留给StandardWrapperValue对象，在StandardWrapper 实例的基本阀门管道 ，StandardWrapperValue 对象通过调用StandardWrapper的allocate
 * 方法获得Servlet 实例，在获得Service实例之后 ，StandardWrapperValue 调用servlet 的service方法 。
 *
 * 在servlet第一次被请求的时候，StandardWrapper 加载servlet类，它是动态加载servlet ，所以需要知道servlet 类的完全限定名称，通过StandardWrapper
 * 类的setServletClass方法将servlet 的类名传递给StandardWrapper ，另外使用setName 方法也可以传递servlet名 。考虑到StandardWrapper
 * 负责在StandardWrapperValue请求的时候分配一个servlet实例， 它必须考虑到一个servlet 是否实现了SingleThreadModel 接口，如果一个servlet
 * 没有实现SingleThreadModel 接口，StandardWapper 加载该servlet 一次，对于以后的请求返回相同的实例即可 ， StandardWapper 假设 servlet
 * 的service 方法现场安全的，所以并没有创建servlet 的多个实例，如果需要的话，由程序员自己解决资源同步问题。
 *
 * 对于 STM servlet 情况有所不同了，StandardWrapper 必须保证不能同时有两个线程提交STM servlet 的service 方法，如果StandardWapper
 * 维持一个STM servlet 实现，下面是它如何调用servlet 的service 方法 。
 *
 *
 *
 * Allocating the Servlet
 *  在本节开始的时候介绍了StandardWrapperValue 的invoke方法调用了包装器的allocate 方法来获得一个请求的servlet 的实例，因此Standard
 *  Wrapper 类必须实现该接口，该方法的签名如下 ：
 *
 *
 *
 *
 *
 */
@SuppressWarnings("deprecation") // SingleThreadModel
public class StandardWrapper extends ContainerBase
    implements ServletConfig, Wrapper, NotificationEmitter {

    private final Log log = LogFactory.getLog(StandardWrapper.class); // must not be static

    protected static final String[] DEFAULT_SERVLET_METHODS = new String[] {
                                                    "GET", "HEAD", "POST" };

    // ----------------------------------------------------------- Constructors


    /**
     * Create a new StandardWrapper component with the default basic Valve.
     */
    public StandardWrapper() {

        super();
        // 【注意】StandardWrapper 类的构造函数将一个StandardWrapperValue 作为它的基本阀门
        // 本章关注的是一个servlet 被调用的时候发生的细节，因此我们需要自习看StandardWrapper 和StandardWrapperValue 类，在学习它们
        // 之前，我们需要首先关注下，javax.servlet.SignleThreaadModel ，理解该接口对于理解一个包装器是如何工作的非常重要 。
        swValve=new StandardWrapperValve();
        pipeline.setBasic(swValve);  //
        // 广播
        broadcaster = new NotificationBroadcasterSupport();

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The date and time at which this servlet will become available (in
     * milliseconds since the epoch), or zero if the servlet is available.
     * If this value equals Long.MAX_VALUE, the unavailability of this
     * servlet is considered permanent.
     */
    protected long available = 0L;

    /**
     * The broadcaster that sends j2ee notifications.
     */
    protected NotificationBroadcasterSupport broadcaster = null;

    /**
     * The count of allocations that are currently active (even if they
     * are for the same instance, as will be true on a non-STM servlet).
     */
    protected AtomicInteger countAllocated = new AtomicInteger(0);


    /**
     * The facade associated with this wrapper.
     * 下面几行是 StandardWrapperFacade 的构造函数，它获得一个 StandardWrapper 类型的参数。
     * 当一个 StandardWrapperFacade 对象创建的时候，构造函数将该 StandardWrapper 负值给 config 变量
     */
    protected StandardWrapperFacade facade =
        new StandardWrapperFacade(this);


    /**
     * The descriptive information string for this implementation.
     */
    protected static final String info =
        "org.apache.catalina.core.StandardWrapper/1.0";


    /**
     * The (single) possibly uninitialized instance of this servlet.
     */
    protected volatile Servlet instance = null;


    /**
     * Flag that indicates if this instance has been initialized
     */
    protected volatile boolean instanceInitialized = false;

    /**
     * The support object for our instance listeners.
     */
    protected InstanceSupport instanceSupport = new InstanceSupport(this);


    /**
     * The load-on-startup order value (negative value means load on
     * first call) for this servlet.
     */
    protected int loadOnStartup = -1;


    /**
     * Mappings associated with the wrapper.
     */
    protected ArrayList<String> mappings = new ArrayList<String>();


    /**
     * The initialization parameters for this servlet, keyed by
     * parameter name.
     * 在 StandardWrapper 中，初始化参数被存放在一个名为 parameters 的 HashMap 中
     */
    protected HashMap<String, String> parameters = new HashMap<String, String>();


    /**
     * The security role references for this servlet, keyed by role name
     * used in the servlet.  The corresponding value is the role name of
     * the web application itself.
     */
    protected HashMap<String, String> references = new HashMap<String, String>();


    /**
     * The run-as identity for this servlet.
     */
    protected String runAs = null;

    /**
     * The notification sequence number.
     */
    protected long sequenceNumber = 0;

    /**
     * The fully qualified servlet class name for this servlet.
     */
    protected String servletClass = null;


    /**
     * Does this servlet implement the SingleThreadModel interface?
     */
    protected volatile boolean singleThreadModel = false;


    /**
     * Are we unloading our servlet instance at the moment?
     */
    protected volatile boolean unloading = false;


    /**
     * Maximum number of STM instances.
     */
    protected int maxInstances = 20;


    /**
     * Number of instances currently loaded for a STM servlet.
     */
    protected int nInstances = 0;


    /**
     * Stack containing the STM instances.
     * Servlet池
     */
    protected Stack<Servlet> instancePool = null;


    /**
     * Wait time for servlet unload in ms.
     */
    protected long unloadDelay = 2000;


    /**
     * True if this StandardWrapper is for the JspServlet
     */
    protected boolean isJspServlet;


    /**
     * The ObjectName of the JSP monitoring mbean
     */
    protected ObjectName jspMonitorON;


    /**
     * Should we swallow System.out
     */
    protected boolean swallowOutput = false;

    // To support jmx attributes
    protected StandardWrapperValve swValve;
    protected long loadTime=0;
    protected int classLoadTime=0;

    /**
     * Multipart config
     */
    protected MultipartConfigElement multipartConfigElement = null;

    /**
     * Async support
     */
    protected boolean asyncSupported = false;

    /**
     * Enabled
     */
    protected boolean enabled = true;

    private boolean overridable = false;

    /**
     * Static class array used when the SecurityManager is turned on and
     * <code>Servlet.init</code> is invoked.
     */
    protected static Class<?>[] classType = new Class[]{ServletConfig.class};


    /**
     * Static class array used when the SecurityManager is turned on and
     * <code>Servlet.service</code>  is invoked.
     */
    @Deprecated
    protected static Class<?>[] classTypeUsedInService = new Class[]{
                                                         ServletRequest.class,
                                                         ServletResponse.class};


    private final ReentrantReadWriteLock parametersLock =
            new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock mappingsLock =
            new ReentrantReadWriteLock();

    private final ReentrantReadWriteLock referencesLock =
            new ReentrantReadWriteLock();


    // ------------------------------------------------------------- Properties

    @Override
    public boolean isOverridable() {
        return overridable;
    }

    @Override
    public void setOverridable(boolean overridable) {
        this.overridable = overridable;
    }

    /**
     * Return the available date/time for this servlet, in milliseconds since
     * the epoch.  If this date/time is Long.MAX_VALUE, it is considered to mean
     * that unavailability is permanent and any request for this servlet will return
     * an SC_NOT_FOUND error.  If this date/time is in the future, any request for
     * this servlet will return an SC_SERVICE_UNAVAILABLE error.  If it is zero,
     * the servlet is currently available.
     */
    @Override
    public long getAvailable() {

        return (this.available);

    }


    /**
     * Set the available date/time for this servlet, in milliseconds since the
     * epoch.  If this date/time is Long.MAX_VALUE, it is considered to mean
     * that unavailability is permanent and any request for this servlet will return
     * an SC_NOT_FOUND error. If this date/time is in the future, any request for
     * this servlet will return an SC_SERVICE_UNAVAILABLE error.
     *
     * @param available The new available date/time
     */
    @Override
    public void setAvailable(long available) {

        long oldAvailable = this.available;
        if (available > System.currentTimeMillis())
            this.available = available;
        else
            this.available = 0L;
        support.firePropertyChange("available", Long.valueOf(oldAvailable),
                                   Long.valueOf(this.available));

    }


    /**
     * Return the number of active allocations of this servlet, even if they
     * are all for the same instance (as will be true for servlets that do
     * not implement <code>SingleThreadModel</code>.
     */
    public int getCountAllocated() {

        return (this.countAllocated.get());

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
     * Return the InstanceSupport object for this Wrapper instance.
     */
    public InstanceSupport getInstanceSupport() {

        return (this.instanceSupport);

    }


    /**
     * Return the load-on-startup order value (negative value means
     * load on first call).
     */
    @Override
    public int getLoadOnStartup() {

        if (isJspServlet && loadOnStartup < 0) {
            /*
             * JspServlet must always be preloaded, because its instance is
             * used during registerJMX (when registering the JSP
             * monitoring mbean)
             */
             return Integer.MAX_VALUE;
        } else {
            return (this.loadOnStartup);
        }
    }


    /**
     * Set the load-on-startup order value (negative value means
     * load on first call).
     *
     * @param value New load-on-startup value
     */
    @Override
    public void setLoadOnStartup(int value) {

        int oldLoadOnStartup = this.loadOnStartup;
        this.loadOnStartup = value;
        support.firePropertyChange("loadOnStartup",
                                   Integer.valueOf(oldLoadOnStartup),
                                   Integer.valueOf(this.loadOnStartup));

    }



    /**
     * Set the load-on-startup order value from a (possibly null) string.
     * Per the specification, any missing or non-numeric value is converted
     * to a zero, so that this servlet will still be loaded at startup
     * time, but in an arbitrary order.
     *
     * @param value New load-on-startup value
     */
    public void setLoadOnStartupString(String value) {

        try {
            setLoadOnStartup(Integer.parseInt(value));
        } catch (NumberFormatException e) {
            setLoadOnStartup(0);
        }
    }

    public String getLoadOnStartupString() {
        return Integer.toString( getLoadOnStartup());
    }


    /**
     * Return maximum number of instances that will be allocated when a single
     * thread model servlet is used.
     */
    public int getMaxInstances() {

        return (this.maxInstances);

    }


    /**
     * Set the maximum number of instances that will be allocated when a single
     * thread model servlet is used.
     *
     * @param maxInstances New value of maxInstances
     */
    public void setMaxInstances(int maxInstances) {

        int oldMaxInstances = this.maxInstances;
        this.maxInstances = maxInstances;
        support.firePropertyChange("maxInstances", oldMaxInstances,
                                   this.maxInstances);

    }


    /**
     * Set the parent Container of this Wrapper, but only if it is a Context.
     *
     * @param container Proposed parent Container
     * 一个包装器的父容器只能是一个上下文容器。如果传递的参数不是一个上下文容 器，它的 setParent 方法会抛出 java.lang.IllegalArgumentException。
     */
    @Override
    public void setParent(Container container) {

        if ((container != null) &&
            !(container instanceof Context))
            throw new IllegalArgumentException
                (sm.getString("standardWrapper.notContext"));
        if (container instanceof StandardContext) {
            swallowOutput = ((StandardContext)container).getSwallowOutput();
            unloadDelay = ((StandardContext)container).getUnloadDelay();
        }
        super.setParent(container);

    }


    /**
     * Return the run-as identity for this servlet.
     */
    @Override
    public String getRunAs() {

        return (this.runAs);

    }


    /**
     * Set the run-as identity for this servlet.
     *
     * @param runAs New run-as identity value
     */
    @Override
    public void setRunAs(String runAs) {

        String oldRunAs = this.runAs;
        this.runAs = runAs;
        support.firePropertyChange("runAs", oldRunAs, this.runAs);

    }


    /**
     * Return the fully qualified servlet class name for this servlet.
     */
    @Override
    public String getServletClass() {

        return (this.servletClass);

    }


    /**
     * Set the fully qualified servlet class name for this servlet.
     *
     * @param servletClass Servlet class name
     */
    @Override
    public void setServletClass(String servletClass) {

        String oldServletClass = this.servletClass;
        this.servletClass = servletClass;
        support.firePropertyChange("servletClass", oldServletClass,
                                   this.servletClass);
        if (Constants.JSP_SERVLET_CLASS.equals(servletClass)) {
            isJspServlet = true;
        }
    }



    /**
     * Set the name of this servlet.  This is an alias for the normal
     * <code>Container.setName()</code> method, and complements the
     * <code>getServletName()</code> method required by the
     * <code>ServletConfig</code> interface.
     *
     * @param name The new name of this servlet
     */
    public void setServletName(String name) {

        setName(name);

    }


    /**
     * Does the servlet class represented by this component implement the
     * <code>SingleThreadModel</code> interface? This can only be determined
     * once the class is loaded. Calling this method will not trigger loading
     * the class since that may cause the application to behave unexpectedly.
     *
     * @return {@code null} if the class has not been loaded, otherwise {@code
     *         true} if the servlet does implement {@code SingleThreadModel} and
     *         {@code false} if it does not.
     */
    public Boolean isSingleThreadModel() {
        // If the servlet has been loaded either singleThreadModel will be true
        // or instance will be non-null
        if (singleThreadModel || instance != null) {
            return Boolean.valueOf(singleThreadModel);
        }
        return null;
    }


    /**
     * Is this servlet currently unavailable?
     */
    @Override
    public boolean isUnavailable() {
        // 方法false，表示Wrapper可用

        if (!isEnabled())
            return true;
        else if (available == 0L)
            return false;
        else if (available <= System.currentTimeMillis()) {
            available = 0L;
            return false;
        } else
            return true;

    }


    /**
     * Gets the names of the methods supported by the underlying servlet.
     *
     * This is the same set of methods included in the Allow response header
     * in response to an OPTIONS request method processed by the underlying
     * servlet.
     *
     * @return Array of names of the methods supported by the underlying
     * servlet
     */
    @Override
    public String[] getServletMethods() throws ServletException {

        instance = loadServlet();

        Class<? extends Servlet> servletClazz = instance.getClass();
        if (!javax.servlet.http.HttpServlet.class.isAssignableFrom(
                                                        servletClazz)) {
            return DEFAULT_SERVLET_METHODS;
        }

        HashSet<String> allow = new HashSet<String>();
        allow.add("TRACE");
        allow.add("OPTIONS");

        Method[] methods = getAllDeclaredMethods(servletClazz);
        for (int i=0; methods != null && i<methods.length; i++) {
            Method m = methods[i];

            if (m.getName().equals("doGet")) {
                allow.add("GET");
                allow.add("HEAD");
            } else if (m.getName().equals("doPost")) {
                allow.add("POST");
            } else if (m.getName().equals("doPut")) {
                allow.add("PUT");
            } else if (m.getName().equals("doDelete")) {
                allow.add("DELETE");
            }
        }

        String[] methodNames = new String[allow.size()];
        return allow.toArray(methodNames);

    }


    /**
     * Return the associated servlet instance.
     */
    @Override
    public Servlet getServlet() {
        return instance;
    }


    /**
     * Set the associated servlet instance.
     */
    @Override
    public void setServlet(Servlet servlet) {
        instance = servlet;
    }


    /**
     * {@inheritDoc}
     */
    @Override
    public void setServletSecurityAnnotationScanRequired(boolean b) {
        // NO-OP
    }

    // --------------------------------------------------------- Public Methods


    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     *
     * 首先，需要一个后台执行线程，Tomcat 中有专门的一条线程负责处理不同的容器的后台任务，要在不同的容器中执行某些后台任务，只须要重写
     * backgroundProcess 方法即可实现， 由于 JpsServlet 对应于Wrapper 级别，因此要在StandardWrapper 中重写backgroundProcess
     * 它会调用实现了PeriodicEventListener 接口的Servlet ，JspServlet 就实现了PeriodicEventListener 接口，此接口只有一个
     * periodicEvent方法，具体的检测逻辑在此方法中实现即可 。
     *
     * 其次，判断重新编译的根据是什么？重新编译就是再次把JSP变成Java再变成Class,而触发这个动作的条件就是，当我们修改了某个JSP文件后。
     * 或者某个JSP文件引入的资源被修改后，所以最好判断依据是某个JSP 或资源的最后修改时间-LastModified 属性，正常的顺序是JSP经过编译后生成
     * Class 文件，把此Class 文件的LastModified属性设置成JSP文件的LastModified，此时两个文件的LastModified属性值是相同的，当我们
     * 改了JSP文件保存后，JSP 的LastModified 属性就设置为当前时间，此时，通过判断两个文件的LastModified 属性值决定是否重新编译，
     * 重新编译后，JSP与Class 文件的LastModified 属性再次设置为相同的值，对于引入的资源，内存中维护了上次编译时引入的资源的LastModified
     * 属性，不断的获取引入资源的LastModified属性并与内存中的LastModified属性进行比较，同样可以很容易的判断是否需要重新编译 。
     *
     * 最后，对于本地和远程资源分别是如何检测的，对于本地资源来说，使用java.io.File 类可以很方便的实现对某个JSP 文件或其他文件的
     * LastModified 属性读取，对于远程资源，比如 Jar 包，为了方便处理Jar 包含的属性，使用java.net.URL 可以很方便的操作，它包含了很多的
     * 协议，例如常见的Jar,File ，Ftp 等协议，使用它是相当的方便 。
     *
     * URL includeUrl = new Url("jar://http://hostname/third.jar!/")
     * URLConnection iuc =  includeUrl.openConnection();
     * long includeLastModified = ((JarUrlConnection)iuc).getJarEntry().getTime();
     *
     * 如前所述，只需要三步即可以完成对远程的Jar 包的读取且取出最后修改时间，当然，URL还支持本地文件资源的读取，所以它是很好的资源读取抽象成对象
     * ，Tomcat 中对引入的资源管理都使用了URL 作为操作对象 。
     *
     * 本节探讨了Jasper 自动检测机制的实现，自动检测机制给我们开发带了很好的体验，我们不必自己修改JSP 后自己去执行编译操作，而是由Tomcat
     * 通过Jasper 帮我们定时检测编译操作，
     *
     */
    @Override
    public void backgroundProcess() {
        super.backgroundProcess();

        if (!getState().isAvailable())
            return;

        if (getServlet() != null && (getServlet() instanceof PeriodicEventListener)) {
            ((PeriodicEventListener) getServlet()).periodicEvent();
        }
    }


    /**
     * Extract the root cause from a servlet exception.
     *
     * @param e The servlet exception
     */
    public static Throwable getRootCause(ServletException e) {
        Throwable rootCause = e;
        Throwable rootCauseCheck = null;
        // Extra aggressive rootCause finding
        int loops = 0;
        do {
            loops++;
            rootCauseCheck = rootCause.getCause();
            if (rootCauseCheck != null)
                rootCause = rootCauseCheck;
        } while (rootCauseCheck != null && (loops < 20));
        return rootCause;
    }


    /**
     * Refuse to add a child Container, because Wrappers are the lowest level
     * of the Container hierarchy.
     *
     * @param child Child container to be added
     *              一个包装器表示一个独立 Servlet 的容器。这样，包装器就不能再有子容器，因 此不可以调用它的 addChild 方法，如果调用了
     *              会得到一个 java.langIllegalStateException。这里是 StandardWrapper 对 addChild 方法 的实现
     */
    @Override
    public void addChild(Container child) {

        throw new IllegalStateException
            (sm.getString("standardWrapper.notChild"));

    }


    /**
     * Add a new servlet initialization parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     *              可以调用 StandardWrapper 类的 addInitParameter 方法来填充 parameters。传 递参数的名字和值。
     */
    @Override
    public void addInitParameter(String name, String value) {

        try {
            parametersLock.writeLock().lock();
            parameters.put(name, value);
        } finally {
            parametersLock.writeLock().unlock();
        }
        fireContainerEvent("addInitParameter", name);

    }


    /**
     * Add a new listener interested in InstanceEvents.
     *
     * @param listener The new listener
     */
    @Override
    public void addInstanceListener(InstanceListener listener) {

        instanceSupport.addInstanceListener(listener);

    }


    /**
     * Add a mapping associated with the Wrapper.
     *
     * @param mapping The new wrapper mapping
     */
    @Override
    public void addMapping(String mapping) {

        try {
            mappingsLock.writeLock().lock();
            mappings.add(mapping);
        } finally {
            mappingsLock.writeLock().unlock();
        }
        if(parent.getState().equals(LifecycleState.STARTED))
            fireContainerEvent(ADD_MAPPING_EVENT, mapping);

    }


    /**
     * Add a new security role reference record to the set of records for
     * this servlet.
     *
     * @param name Role name used within this servlet
     * @param link Role name used within the web application
     */
    @Override
    public void addSecurityReference(String name, String link) {

        try {
            referencesLock.writeLock().lock();
            references.put(name, link);
        } finally {
            referencesLock.writeLock().unlock();
        }
        fireContainerEvent("addSecurityReference", name);

    }


    /**
     * Allocate an initialized instance of this Servlet that is ready to have
     * its <code>service()</code> method called.  If the servlet class does
     * not implement <code>SingleThreadModel</code>, the (only) initialized
     * instance may be returned immediately.  If the servlet class implements
     * <code>SingleThreadModel</code>, the Wrapper implementation must ensure
     * that this instance is not allocated again until it is deallocated by a
     * call to <code>deallocate()</code>.
     *
     * @exception ServletException if the servlet init() method threw
     *  an exception
     * @exception ServletException if a loading error occurs
     *
     * 分配一个已经初始化好的Servlet实例。
     * 如果Servlet没有实现SingleThreadModel接口，已经被初始化好的实例可以被立即返回
     * 如果Servlet实现了SingleThreadModel接口，Wrapper的实现要确保某一实例不会重复分配，除非调用了deallocate方法回收了该实例
     *
     *
     *
     *
     * 注意 allocate 方法返回的是一个请求的servlet的一个实例。
     * 由于要支持STM servlet ，这使得该方法更加复杂一点，实际上，该方法有两部分组成，一部分负责非STM servlet 的工作，另一部分负责STM servlet
     * 第一部分的结构如下 ：
     *
     *  布尔变量singleThreadModel 负责标志一个servlet 是否是STM servlet ，它的初始值是false , loadServlet 方法会检测加载的servlet
     *  是否是STM ，如果是则将它的值设置为true , loadServlet 方法在下面会介绍到。
     *
     *  现在来看一下第一部分和第二部分。方法allocate 检查该实例是否为null , 如果是调用loadServlet 方法来加载servlet ，然后增加contAllocated整形并返回该实例。
     *  如果StandardWrapper 表示一个STM servlet ，方法allocate 尝试返回池中的一个实例，变量 instancePool 是一个java.util.Stack类型的STM servlet 实例池。
     *  private Stack instancePool = null;
     *  该变量在loadServlet 方法的内部初始化，该部分在接口的小节中进行讨论，方法allocate 负责分配STM Servlet实例，前提是实例的数目不超过
     *  最大数目，该数目由maxInstances 整形定义 ，默认值是20
     *  上面的代码使用了一个while循环等待直到nInstances的数目少于或等待countAllocated (应该是多余或等于把)，在循环里，allocate方法检查nInstance
     *  的值，如果低于maxInstances的值，调用loadServlet 的值等于或大于 maxInstances 的值，它调用实例池堆栈的wait方法，知道一个实例被返回 。
     *
     *  StandardWrapper 实现了Wrapper 接口的load方法，load方法调用 loadServlet 方法来加载一个servlet类，并调用该servlet的init方法，传递
     *  一个javax.servlet.ServiceConfig 实例，这里就是loadServlet是如何工作的， 方法loadServlet 首先检查StandardWrapper 是否表示
     *  一个STM servlet ，如果不是并且该实例不是null ,即之前已经加载过， 直接返回该实例。
     *
     *
     *
     *
     */
    @Override
    public Servlet allocate() throws ServletException {

        // If we are currently unloading this servlet, throw an exception
        if (unloading) {
            throw new ServletException(sm.getString("standardWrapper.unloading", getName()));
        }

        boolean newInstance = false;

        // If not SingleThreadedModel, return the same instance every time
        if (!singleThreadModel) {
            // Load and initialize our instance if necessary
            if (instance == null || !instanceInitialized) {
                synchronized (this) {
                    if (instance == null) {
                        try {
                            if (log.isDebugEnabled()) {
                                log.debug("Allocating non-STM instance");
                            }

                            // Note: We don't know if the Servlet implements
                            // SingleThreadModel until we have loaded it.
                            // 一个Servlet只有被加载后才能知道是不是实现了SingleThreadModel接口
                            instance = loadServlet();
                            newInstance = true;
                            // 如果没有继承singleThreadModel接口
                            if (!singleThreadModel) {
                                // For non-STM, increment here to prevent a race
                                // condition with unload. Bug 43683, test case
                                // #3
                                // 分配实例的次数+1
                                countAllocated.incrementAndGet();
                            }
                        } catch (ServletException e) {
                            throw e;
                        } catch (Throwable e) {
                            ExceptionUtils.handleThrowable(e);
                            throw new ServletException(sm.getString("standardWrapper.allocate"), e);
                        }
                    }
                    if (!instanceInitialized) {
                        initServlet(instance);
                    }
                }
            }

            if (singleThreadModel) {
                // 新生成了一个实例后，把实例放入instancePool
                if (newInstance) {
                    // Have to do this outside of the sync above to prevent a
                    // possible deadlock
                    synchronized (instancePool) {
                        instancePool.push(instance);
                        nInstances++;
                    }
                }
            } else {
                if (log.isTraceEnabled()) {
                    log.trace("  Returning non-STM instance");
                }
                // For new instances, count will have been incremented at the
                // time of creation
                if (!newInstance) {
                    // 分配实例的次数加1，如果是新创的实例，在上面就会加1
                    countAllocated.incrementAndGet();
                }
                return instance;
            }
        }

        synchronized (instancePool) {
            // countAllocated表示当前需要的实例数
            // nInstances表示当前已经生成的实例数
            // 如果需要的大于或等于存在的实例数，那么则要新生成了，如果已经超过了最大限制，就只能等其他线程释放servlet了
            while (countAllocated.get() >= nInstances) {
                // Allocate a new instance if possible, or else wait
                // 如果现在生成的实例小于最大限制，则继续生成
                if (nInstances < maxInstances) {
                    try {
                        instancePool.push(loadServlet());
                        nInstances++;
                    } catch (ServletException e) {
                        throw e;
                    } catch (Throwable e) {
                        ExceptionUtils.handleThrowable(e);
                        throw new ServletException(sm.getString("standardWrapper.allocate"), e);
                    }
                } else {
                    // 否则等等
                    try {
                        instancePool.wait();
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }
            }
            if (log.isTraceEnabled()) {
                log.trace("  Returning allocated STM instance");
            }
            // 分配次数+1，直接从instancePool取
            countAllocated.incrementAndGet();
            return instancePool.pop();
        }
    }


    /**
     * Return this previously allocated servlet to the pool of available
     * instances.  If this servlet class does not implement SingleThreadModel,
     * no action is actually required.
     *
     * @param servlet The servlet to be returned
     *
     * @exception ServletException if a deallocation error occurs
     */
    @Override
    public void deallocate(Servlet servlet) throws ServletException {

        // If not SingleThreadModel, no action is required
        if (!singleThreadModel) {
            // 分配次数-1
            countAllocated.decrementAndGet();
            return;
        }

        // Unlock and free this instance
        synchronized (instancePool) {
            // 分配次数-1，
            countAllocated.decrementAndGet();
            instancePool.push(servlet);
            instancePool.notify();
        }

    }


    /**
     * Return the value for the specified initialization parameter name,
     * if any; otherwise return <code>null</code>.
     *
     * @param name Name of the requested initialization parameter
     */
    @Override
    public String findInitParameter(String name) {

        try {
            parametersLock.readLock().lock();
            return parameters.get(name);
        } finally {
            parametersLock.readLock().unlock();
        }

    }


    /**
     * Return the names of all defined initialization parameters for this
     * servlet.
     */
    @Override
    public String[] findInitParameters() {

        try {
            parametersLock.readLock().lock();
            String results[] = new String[parameters.size()];
            return parameters.keySet().toArray(results);
        } finally {
            parametersLock.readLock().unlock();
        }

    }


    /**
     * Return the mappings associated with this wrapper.
     */
    @Override
    public String[] findMappings() {

        try {
            mappingsLock.readLock().lock();
            return mappings.toArray(new String[mappings.size()]);
        } finally {
            mappingsLock.readLock().unlock();
        }

    }


    /**
     * Return the security role link for the specified security role
     * reference name, if any; otherwise return <code>null</code>.
     *
     * @param name Security role reference used within this servlet
     */
    @Override
    public String findSecurityReference(String name) {
        String reference = null;

        try {
            referencesLock.readLock().lock();
            reference = references.get(name);
        } finally {
            referencesLock.readLock().unlock();
        }

        // If not specified on the Wrapper, check the Context
        if (getParent() instanceof Context) {
            Context context = (Context) getParent();
            if (reference != null) {
                reference = context.findRoleMapping(reference);
            } else {
                reference = context.findRoleMapping(name);
            }
        }

        return reference;
    }


    /**
     * Return the set of security role reference names associated with
     * this servlet, if any; otherwise return a zero-length array.
     */
    @Override
    public String[] findSecurityReferences() {

        try {
            referencesLock.readLock().lock();
            String results[] = new String[references.size()];
            return references.keySet().toArray(results);
        } finally {
            referencesLock.readLock().unlock();
        }

    }


    /**
     * FIXME: Fooling introspection ...
     */
    @Deprecated
    public Wrapper findMappingObject() {
        return (Wrapper) getMappingObject();
    }


    /**
     * Load and initialize an instance of this servlet, if there is not already
     * at least one initialized instance.  This can be used, for example, to
     * load servlets that are marked in the deployment descriptor to be loaded
     * at server startup time.
     * <p>
     * <b>IMPLEMENTATION NOTE</b>:  Servlets whose classnames begin with
     * <code>org.apache.catalina.</code> (so-called "container" servlets)
     * are loaded by the same classloader that loaded this class, rather than
     * the classloader for the current web application.
     * This gives such classes access to Catalina internals, which are
     * prevented for classes loaded for web applications.
     *
     * @exception ServletException if the servlet init() method threw
     *  an exception
     * @exception ServletException if some other loading problem occurs
     */
    @Override
    public synchronized void load() throws ServletException {
        instance = loadServlet();

        if (!instanceInitialized) {
            initServlet(instance);
        }

        if (isJspServlet) {
            StringBuilder oname =
                new StringBuilder(MBeanUtils.getDomain(getParent()));

            oname.append(":type=JspMonitor,name=");
            oname.append(getName());

            oname.append(getWebModuleKeyProperties());

            try {
                jspMonitorON = new ObjectName(oname.toString());
                Registry.getRegistry(null, null).registerComponent(instance, jspMonitorON, null);
            } catch (Exception ex) {
                log.warn("Error registering JSP monitoring with jmx " + instance);
            }
        }
    }


    /**
     * Load and initialize an instance of this servlet, if there is not already
     * at least one initialized instance.  This can be used, for example, to
     * load servlets that are marked in the deployment descriptor to be loaded
     * at server startup time.
     *    方法loadServlet负责加载servlet类，类名应该被分配给servletClass 变量，该方法被该值分配给一个String 类型的变量 actualClass
     *
     *  但是，由于Catalina 也是一个JSP容器，在请求的JSP页面的时候，loadServlet 必须也能工作，如果是jsp页面，则得到相应的Servlet类。
     *  如果JSP 页面的servlet 名字找不到，就用servletClass 变量的值，但是，如果访劁珠值没有使用StandardWrapper 类中的setServletClass方法
     *  设置会产生异常，剩余部分不会被执行。
     *  现在servlet的名字已经获得了，接下来，是loadServlet 方法获得加载器，如果找不到加载器，则产生异常并停止执行。
     *
     *
     */
    public synchronized Servlet loadServlet() throws ServletException {

        if (unloading) {
            throw new ServletException(
                    sm.getString("standardWrapper.unloading", getName()));
        }

        // Nothing to do if we already have an instance or an instance pool
        if (!singleThreadModel && (instance != null))
            return instance;

        PrintStream out = System.out;
        if (swallowOutput) {
            SystemLogHandler.startCapture();
        }

        Servlet servlet;
        try {
            long t1=System.currentTimeMillis();
            // Complain if no servlet class has been specified
            if (servletClass == null) {
                unavailable(null);
                throw new ServletException
                    (sm.getString("standardWrapper.notClass", getName()));
            }

            InstanceManager instanceManager = ((StandardContext)getParent()).getInstanceManager();
            try {
                // 有了类加载器和要加载的 Servlet 名字，就可以使用 loadServlet 方法来加载类 了。
                // 1. 创建Servlet实例，如果添加了JNDI 注解，将进行依赖注入
                servlet = (Servlet) instanceManager.newInstance(servletClass);
            } catch (ClassCastException e) {
                unavailable(null);
                // Restore the context ClassLoader
                throw new ServletException
                    (sm.getString("standardWrapper.notServlet", servletClass), e);
            } catch (Throwable e) {
                e = ExceptionUtils.unwrapInvocationTargetException(e);
                ExceptionUtils.handleThrowable(e);
                unavailable(null);

                // Added extra log statement for Bugzilla 36630:
                // https://bz.apache.org/bugzilla/show_bug.cgi?id=36630
                if(log.isDebugEnabled()) {
                    log.debug(sm.getString("standardWrapper.instantiate", servletClass), e);
                }

                // Restore the context ClassLoader
                throw new ServletException
                    (sm.getString("standardWrapper.instantiate", servletClass), e);
            }

            if (multipartConfigElement == null) {
                // 2. 读取javax.servlet.annotation.MultipartConfig配置，以用于multipart/form-data请求处理，包括临时文件存储路径 。
                // 上传文件最大字节数，请求最大字节数，文件大小阈值。
                MultipartConfig annotation =
                        servlet.getClass().getAnnotation(MultipartConfig.class);
                if (annotation != null) {
                    multipartConfigElement =
                            new MultipartConfigElement(annotation);
                }
            }

            // Special handling for ContainerServlet instances
            // 如果通过了安全性检查，接下来检查该 Servlet 是否是一个 ContainerServlet。 ContainerServlet 是实现了 org.apache.catalina.ContainerServlet
            // 接口的 Servlet，它可以访问 Catalina 的内部函数。如果该 Servlet 是 ContainerServlet，loadServlet 方法调用 ContainerServlet 的 setWrapper
            // 方法，传递该 StandardWrapper 实例。
            if ((servlet instanceof ContainerServlet) &&
                    // isContainerProvidedServlet 方法返回 true 值。classLoader 会获得另一个 ClassLoader 的实例，这样就可以访问 Catalina 的内部了。
                    (isContainerProvidedServlet(servletClass) ||
                            ((Context) getParent()).getPrivileged() )) {
                ((ContainerServlet) servlet).setWrapper(this);
            }

            classLoadTime=(int) (System.currentTimeMillis() -t1);

            // 实现了SingleThreadModel接口
            if (servlet instanceof SingleThreadModel) {
                if (instancePool == null) {
                    instancePool = new Stack<Servlet>();
                }
                singleThreadModel = true;
            }

            // 4. 初始化servlet
            initServlet(servlet);

            fireContainerEvent("load", this);

            loadTime=System.currentTimeMillis() -t1;
        } finally {
            if (swallowOutput) {
                String log = SystemLogHandler.stopCapture();
                if (log != null && log.length() > 0) {
                    if (getServletContext() != null) {
                        getServletContext().log(log);
                    } else {
                        out.println(log);
                    }
                }
            }
        }
        return servlet;

    }

    /**
     * {@inheritDoc}
     */
    @Override
    public void servletSecurityAnnotationScan() throws ServletException {
        // NO-OP
    }


    private synchronized void initServlet(Servlet servlet)
            throws ServletException {

        if (instanceInitialized && !singleThreadModel) return;

        // Call the initialization method of this servlet
        try {
            // 接下来 loadServlet 方法触发 BEFORE_INIT_EVENT 事件，并调用发送者的 init 方法。
            instanceSupport.fireInstanceEvent(InstanceEvent.BEFORE_INIT_EVENT,
                                              servlet);

            if( Globals.IS_SECURITY_ENABLED) {
                boolean success = false;
                try {
                    Object[] args = new Object[] { facade };
                    SecurityUtil.doAsPrivilege("init",
                                               servlet,
                                               classType,
                                               args);
                    success = true;
                } finally {
                    if (!success) {
                        // destroy() will not be called, thus clear the reference now
                        SecurityUtil.remove(servlet);
                    }
                }
            } else {
                //因此，当 StandardWrapper 对象调用 Servlet 实例的 init 方法的时候，它传递 的是一个 StandardWrapperFacade 对象。
                // 在 Servlet 内部调用 ServletConfig 的 getServletName, getInitParameter, 和 getInitParameterNames 方法只需
                // 要调用它们在 StandardWrapper 的实现就行。
                servlet.init(facade);
            }

            instanceInitialized = true;

            instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                              servlet);
        } catch (UnavailableException f) {
            instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                              servlet, f);
            unavailable(f);
            throw f;
        } catch (ServletException f) {
            instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                              servlet, f);
            // If the servlet wanted to be unavailable it would have
            // said so, so do not call unavailable(null).
            throw f;
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
            getServletContext().log("StandardWrapper.Throwable", f );
            instanceSupport.fireInstanceEvent(InstanceEvent.AFTER_INIT_EVENT,
                                              servlet, f);
            // If the servlet wanted to be unavailable it would have
            // said so, so do not call unavailable(null).
            throw new ServletException
                (sm.getString("standardWrapper.initException", getName()), f);
        }
    }

    /**
     * Remove the specified initialization parameter from this servlet.
     *
     * @param name Name of the initialization parameter to remove
     */
    @Override
    public void removeInitParameter(String name) {

        try {
            parametersLock.writeLock().lock();
            parameters.remove(name);
        } finally {
            parametersLock.writeLock().unlock();
        }
        fireContainerEvent("removeInitParameter", name);

    }


    /**
     * Remove a listener no longer interested in InstanceEvents.
     *
     * @param listener The listener to remove
     */
    @Override
    public void removeInstanceListener(InstanceListener listener) {

        instanceSupport.removeInstanceListener(listener);

    }


    /**
     * Remove a mapping associated with the wrapper.
     *
     * @param mapping The pattern to remove
     */
    @Override
    public void removeMapping(String mapping) {

        try {
            mappingsLock.writeLock().lock();
            mappings.remove(mapping);
        } finally {
            mappingsLock.writeLock().unlock();
        }
        if(parent.getState().equals(LifecycleState.STARTED))
            fireContainerEvent(REMOVE_MAPPING_EVENT, mapping);

    }


    /**
     * Remove any security role reference for the specified role name.
     *
     * @param name Security role used within this servlet to be removed
     */
    @Override
    public void removeSecurityReference(String name) {

        try {
            referencesLock.writeLock().lock();
            references.remove(name);
        } finally {
            referencesLock.writeLock().unlock();
        }
        fireContainerEvent("removeSecurityReference", name);

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
        sb.append("StandardWrapper[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    /**
     * Process an UnavailableException, marking this servlet as unavailable
     * for the specified amount of time.
     *
     * @param unavailable The exception that occurred, or <code>null</code>
     *  to mark this servlet as permanently unavailable
     */
    @Override
    public void unavailable(UnavailableException unavailable) {
        getServletContext().log(sm.getString("standardWrapper.unavailable", getName()));
        if (unavailable == null)
            setAvailable(Long.MAX_VALUE);
        else if (unavailable.isPermanent())
            setAvailable(Long.MAX_VALUE);
        else {
            int unavailableSeconds = unavailable.getUnavailableSeconds();
            if (unavailableSeconds <= 0)
                unavailableSeconds = 60;        // Arbitrary default
            setAvailable(System.currentTimeMillis() +
                         (unavailableSeconds * 1000L));
        }

    }


    /**
     * Unload all initialized instances of this servlet, after calling the
     * <code>destroy()</code> method for each instance.  This can be used,
     * for example, prior to shutting down the entire servlet engine, or
     * prior to reloading all of the classes from the Loader associated with
     * our Loader's repository.
     *
     * @exception ServletException if an exception is thrown by the
     *  destroy() method
     */
    @Override
    public synchronized void unload() throws ServletException {

        // Nothing to do if we have never loaded the instance
        if (!singleThreadModel && (instance == null))
            return;
        unloading = true;

        // Loaf a while if the current instance is allocated
        // (possibly more than once if non-STM)
        if (countAllocated.get() > 0) {
            int nRetries = 0;
            long delay = unloadDelay / 20;
            while ((nRetries < 21) && (countAllocated.get() > 0)) {
                if ((nRetries % 10) == 0) {
                    log.info(sm.getString("standardWrapper.waiting",
                                          countAllocated.toString(),
                                          getName()));
                }
                try {
                    Thread.sleep(delay);
                } catch (InterruptedException e) {
                    // Ignore
                }
                nRetries++;
            }
        }

        if (instanceInitialized) {
            PrintStream out = System.out;
            if (swallowOutput) {
                SystemLogHandler.startCapture();
            }

            // Call the servlet destroy() method
            try {
                instanceSupport.fireInstanceEvent
                  (InstanceEvent.BEFORE_DESTROY_EVENT, instance);

                if( Globals.IS_SECURITY_ENABLED) {
                    try {
                        SecurityUtil.doAsPrivilege("destroy",
                                                   instance);
                    } finally {
                        SecurityUtil.remove(instance);
                    }
                } else {
                    instance.destroy();
                }

                instanceSupport.fireInstanceEvent
                  (InstanceEvent.AFTER_DESTROY_EVENT, instance);

            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
                instanceSupport.fireInstanceEvent
                  (InstanceEvent.AFTER_DESTROY_EVENT, instance, t);
                instance = null;
                instancePool = null;
                nInstances = 0;
                fireContainerEvent("unload", this);
                unloading = false;
                throw new ServletException
                    (sm.getString("standardWrapper.destroyException", getName()),
                     t);
            } finally {
                // Annotation processing
                if (!((Context) getParent()).getIgnoreAnnotations()) {
                    try {
                        ((Context)getParent()).getInstanceManager().destroyInstance(instance);
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        log.error(sm.getString("standardWrapper.destroyInstance", getName()), t);
                    }
                }
                // Write captured output
                if (swallowOutput) {
                    String log = SystemLogHandler.stopCapture();
                    if (log != null && log.length() > 0) {
                        if (getServletContext() != null) {
                            getServletContext().log(log);
                        } else {
                            out.println(log);
                        }
                    }
                }
            }
        }

        // Deregister the destroyed instance
        instance = null;
        instanceInitialized = false;

        if (isJspServlet && jspMonitorON != null ) {
            Registry.getRegistry(null, null).unregisterComponent(jspMonitorON);
        }

        if (singleThreadModel && (instancePool != null)) {
            try {
                while (!instancePool.isEmpty()) {
                    Servlet s = instancePool.pop();
                    if (Globals.IS_SECURITY_ENABLED) {
                        try {
                            SecurityUtil.doAsPrivilege("destroy", s);
                        } finally {
                            SecurityUtil.remove(s);
                        }
                    } else {
                        s.destroy();
                    }
                    // Annotation processing
                    if (!((Context) getParent()).getIgnoreAnnotations()) {
                       ((StandardContext)getParent()).getInstanceManager().destroyInstance(s);
                    }
                }
            } catch (Throwable t) {
                t = ExceptionUtils.unwrapInvocationTargetException(t);
                ExceptionUtils.handleThrowable(t);
                instancePool = null;
                nInstances = 0;
                unloading = false;
                fireContainerEvent("unload", this);
                throw new ServletException
                    (sm.getString("standardWrapper.destroyException",
                                  getName()), t);
            }
            instancePool = null;
            nInstances = 0;
        }

        singleThreadModel = false;

        unloading = false;
        fireContainerEvent("unload", this);

    }


    // -------------------------------------------------- ServletConfig Methods


    /**
     * Return the initialization parameter value for the specified name,
     * if any; otherwise return <code>null</code>.
     *
     * @param name Name of the initialization parameter to retrieve
     *             该方法返回指定参数的值，该方法的签名如下:
     */
    @Override
    public String getInitParameter(String name) {
        // 方法 findInitParameter 的参数为参数名，并调用 parameters HashMap 的 get 方法
        return (findInitParameter(name));

    }


    /**
     * Return the set of initialization parameter names defined for this
     * servlet.  If none are defined, an empty Enumeration is returned.
     * Enumerator 实现了 java.util.Enumeration 接口，是 org.apache.catalina.util 包的一部分。
     */
    @Override
    public Enumeration<String> getInitParameterNames() {

        try {
            parametersLock.readLock().lock();
            // 该方法返回所有初始化参数名字的枚举(Enumeration)
            return Collections.enumeration(parameters.keySet());
        } finally {
            parametersLock.readLock().unlock();
        }

    }

    /**
     * Return the servlet context with which this servlet is associated.
     * 一个 StandardWrapper 实例必须是一个 StandardContext 容器的子容器。也就是 说，StandardWrapper 的父容器时 StandardContext。
     * 可以使用 StandardContext 对象的 getServletContext 来获得 ServletContext 对象。这里是 StandardWrapper 中方法
     * getServletContext 的实现
     *
     * 注意 ：现在你知道不能单独部署一个包装器来表示Servlet ，包装器必须从属于一个上下文容器，这样才能使用ServletConfig对象使用
     * getServletContext方法获得一个ServletContext实例。
     *
     */
    @Override
    public ServletContext getServletContext() {

        if (parent == null)
            return (null);
        else if (!(parent instanceof Context))
            return (null);
        else
            return (((Context) parent).getServletContext());

    }


    /**
     * Return the name of this servlet.
     * 它简单的调用 StandardWrapper 的父类 ContainerBase 类的 getName 方法
     * 可以使用 setName 方法来设置 name 的值。回忆是如何调用 StandardWrapper 实 例的 setName 方法来传递 Servlet 的 name 的。
     */
    @Override
    public String getServletName() {

        return (getName());

    }

    public long getProcessingTime() {
        return swValve.getProcessingTime();
    }

    @Deprecated
    public void setProcessingTime(long processingTime) {
        swValve.setProcessingTime(processingTime);
    }

    public long getMaxTime() {
        return swValve.getMaxTime();
    }

    @Deprecated
    public void setMaxTime(long maxTime) {
        swValve.setMaxTime(maxTime);
    }

    public long getMinTime() {
        return swValve.getMinTime();
    }

    @Deprecated
    public void setMinTime(long minTime) {
        swValve.setMinTime(minTime);
    }

    public int getRequestCount() {
        return swValve.getRequestCount();
    }

    @Deprecated
    public void setRequestCount(int requestCount) {
        swValve.setRequestCount(requestCount);
    }

    public int getErrorCount() {
        return swValve.getErrorCount();
    }

    @Deprecated
    public void setErrorCount(int errorCount) {
           swValve.setErrorCount(errorCount);
    }

    /**
     * Increment the error count used for monitoring.
     */
    @Override
    public void incrementErrorCount(){
        swValve.incrementErrorCount();
    }

    public long getLoadTime() {
        return loadTime;
    }

    @Deprecated
    public void setLoadTime(long loadTime) {
        this.loadTime = loadTime;
    }

    public int getClassLoadTime() {
        return classLoadTime;
    }

    @Override
    public MultipartConfigElement getMultipartConfigElement() {
        return multipartConfigElement;
    }

    @Override
    public void setMultipartConfigElement(
            MultipartConfigElement multipartConfigElement) {
        this.multipartConfigElement = multipartConfigElement;
    }

    @Override
    public boolean isAsyncSupported() {
        return asyncSupported;
    }

    @Override
    public void setAsyncSupported(boolean asyncSupported) {
        this.asyncSupported = asyncSupported;
    }

    @Override
    public boolean isEnabled() {
        return enabled;
    }

    @Override
    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    // -------------------------------------------------------- Package Methods


    // -------------------------------------------------------- protected Methods


    /**
     * Return <code>true</code> if the specified class name represents a
     * container provided servlet class that should be loaded by the
     * server class loader.
     *
     * @param classname Name of the class to be checked
     */
    protected boolean isContainerProvidedServlet(String classname) {

        if (classname.startsWith("org.apache.catalina.")) {
            return (true);
        }
        try {
            Class<?> clazz =
                this.getClass().getClassLoader().loadClass(classname);
            return (ContainerServlet.class.isAssignableFrom(clazz));
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            return (false);
        }

    }


    protected Method[] getAllDeclaredMethods(Class<?> c) {

        if (c.equals(javax.servlet.http.HttpServlet.class)) {
            return null;
        }

        Method[] parentMethods = getAllDeclaredMethods(c.getSuperclass());

        Method[] thisMethods = c.getDeclaredMethods();
        if (thisMethods.length == 0) {
            return parentMethods;
        }

        if ((parentMethods != null) && (parentMethods.length > 0)) {
            Method[] allMethods =
                new Method[parentMethods.length + thisMethods.length];
            System.arraycopy(parentMethods, 0, allMethods, 0,
                             parentMethods.length);
            System.arraycopy(thisMethods, 0, allMethods, parentMethods.length,
                             thisMethods.length);

            thisMethods = allMethods;
        }

        return thisMethods;
    }


    // ------------------------------------------------------ Lifecycle Methods


    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        // Send j2ee.state.starting notification
        if (this.getObjectName() != null) {
            Notification notification = new Notification("j2ee.state.starting",
                                                        this.getObjectName(),
                                                        sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Start up this component
        super.startInternal();

        setAvailable(0L);

        // Send j2ee.state.running notification
        if (this.getObjectName() != null) {
            Notification notification =
                new Notification("j2ee.state.running", this.getObjectName(),
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }
    }


    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        setAvailable(Long.MAX_VALUE);

        // Send j2ee.state.stopping notification
        if (this.getObjectName() != null) {
            Notification notification =
                new Notification("j2ee.state.stopping", this.getObjectName(),
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Shut down our servlet instance (if it has been initialized)
        try {
            unload();
        } catch (ServletException e) {
            getServletContext().log(sm.getString
                      ("standardWrapper.unloadException", getName()), e);
        }

        // Shut down this component
        super.stopInternal();

        // Send j2ee.state.stopped notification
        if (this.getObjectName() != null) {
            Notification notification =
                new Notification("j2ee.state.stopped", this.getObjectName(),
                                sequenceNumber++);
            broadcaster.sendNotification(notification);
        }

        // Send j2ee.object.deleted notification
        Notification notification =
            new Notification("j2ee.object.deleted", this.getObjectName(),
                            sequenceNumber++);
        broadcaster.sendNotification(notification);

    }


    @Override
    protected String getObjectNameKeyProperties() {

        StringBuilder keyProperties =
            new StringBuilder("j2eeType=Servlet,name=");

        String name = getName();
        if (Util.objectNameValueNeedsQuote(name)) {
            name = ObjectName.quote(name);
        }
        keyProperties.append(name);

        keyProperties.append(getWebModuleKeyProperties());

        return keyProperties.toString();
    }


    private String getWebModuleKeyProperties() {

        StringBuilder keyProperties = new StringBuilder(",WebModule=//");
        String hostName = getParent().getParent().getName();
        if (hostName == null) {
            keyProperties.append("DEFAULT");
        } else {
            keyProperties.append(hostName);
        }

        String contextName = ((Context) getParent()).getName();
        if (!contextName.startsWith("/")) {
            keyProperties.append('/');
        }
        keyProperties.append(contextName);

        StandardContext ctx = null;
        if (parent instanceof StandardContext) {
            ctx = (StandardContext) getParent();
        }

        keyProperties.append(",J2EEApplication=");
        if (ctx == null) {
            keyProperties.append("none");
        } else {
            keyProperties.append(ctx.getJ2EEApplication());
        }
        keyProperties.append(",J2EEServer=");
        if (ctx == null) {
            keyProperties.append("none");
        } else {
            keyProperties.append(ctx.getJ2EEServer());
        }

        return keyProperties.toString();
    }


    /**
     * JSR 77. Always return false.
     */
    public boolean isStateManageable() {
        return false;
    }


    /* Remove a JMX notificationListener
     * @see javax.management.NotificationEmitter#removeNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    @Override
    public void removeNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object object) throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener,filter,object);
    }

    protected MBeanNotificationInfo[] notificationInfo;

    /* Get JMX Broadcaster Info
     * @TODO use StringManager for international support!
     * @TODO This two events we not send j2ee.state.failed and j2ee.attribute.changed!
     * @see javax.management.NotificationBroadcaster#getNotificationInfo()
     */
    @Override
    public MBeanNotificationInfo[] getNotificationInfo() {

        if(notificationInfo == null) {
            notificationInfo = new MBeanNotificationInfo[]{
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.object.created"},
                    Notification.class.getName(),
                    "servlet is created"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.state.starting"},
                    Notification.class.getName(),
                    "servlet is starting"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.state.running"},
                    Notification.class.getName(),
                    "servlet is running"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.state.stopped"},
                    Notification.class.getName(),
                    "servlet start to stopped"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.object.stopped"},
                    Notification.class.getName(),
                    "servlet is stopped"
                    ),
                    new MBeanNotificationInfo(new String[] {
                    "j2ee.object.deleted"},
                    Notification.class.getName(),
                    "servlet is deleted"
                    )
            };
        }

        return notificationInfo;
    }


    /* Add a JMX-NotificationListener
     * @see javax.management.NotificationBroadcaster#addNotificationListener(javax.management.NotificationListener, javax.management.NotificationFilter, java.lang.Object)
     */
    @Override
    public void addNotificationListener(NotificationListener listener,
            NotificationFilter filter, Object object) throws IllegalArgumentException {
        broadcaster.addNotificationListener(listener,filter,object);
    }


    /**
     * Remove a JMX-NotificationListener
     * @see javax.management.NotificationBroadcaster#removeNotificationListener(javax.management.NotificationListener)
     */
    @Override
    public void removeNotificationListener(NotificationListener listener)
        throws ListenerNotFoundException {
        broadcaster.removeNotificationListener(listener);
    }


     // ------------------------------------------------------------- Attributes


    @Deprecated
    public boolean isEventProvider() {
        return false;
    }

    @Deprecated
    public boolean isStatisticsProvider() {
        return false;
    }
}
