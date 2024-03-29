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


import java.io.Serializable;
import java.lang.reflect.InvocationTargetException;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.List;
import java.util.Map;

import javax.management.ObjectName;
import javax.naming.NamingException;
import javax.servlet.Filter;
import javax.servlet.FilterConfig;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.security.SecurityUtil;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.InstanceManager;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.modeler.Registry;
import org.apache.tomcat.util.modeler.Util;
import org.apache.tomcat.util.res.StringManager;


/**
 * Implementation of a <code>javax.servlet.FilterConfig</code> useful in
 * managing the filter instances instantiated when a web application
 * is first started.
 *
 * @author Craig R. McClanahan
 * 实现了 javax.servlet.FilterConfig 接口。ApplicationFilterConfig 负责管理 web 应用程序启动的时候创建的过滤器实例。
 *
 * Servelt 规范提供了一个FilterConfig 接口访问Filter 的名称，初始化参数以及Servlet 上下文，ApplicationFilterConfig 是FilterConfig
 * 接口的具体实现，它的实例具体依赖于Context容器，而当初始化每个Filter时，ApplicationFilterConfig 对象都会作为Filter的init方法的参数。
 * 所以我们在自定义Filter时可以初始化方法中直接使用FilterConfig
 *
 * Context 容器的十字路口模块的主要对象就是包含了以上三个对象，FilterDef ,ContextFilterMaps ，和ApplicationFilterConfig ，而调用这些过滤器进行
 * 过滤的工作则由Wrapper 容器中的管道负责 。
 *
 * FilterConfig 实现类为ApplicationFilterConfig ，同时该类也负责Filter的实例化，FilterMap 用于存储 filter-mapping 配置，
 *
 *
 */
public final class ApplicationFilterConfig implements FilterConfig, Serializable {

    private static final long serialVersionUID = 1L;

    static final StringManager sm =
        StringManager.getManager(Constants.Package);

    private final Log log = LogFactory.getLog(ApplicationFilterConfig.class); // must not be static

    /**
     * Empty String collection to serve as the basis for empty enumerations.
     */
    private static final List<String> emptyString = Collections.emptyList();

    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new ApplicationFilterConfig for the specified filter
     * definition.
     *
     * @param context The context with which we are associated
     * @param filterDef Filter definition for which a FilterConfig is to be
     *  constructed
     *
     * @exception ClassCastException if the specified class does not implement
     *  the <code>javax.servlet.Filter</code> interface
     * @exception ClassNotFoundException if the filter class cannot be found
     * @exception IllegalAccessException if the filter class cannot be
     *  publicly instantiated
     * @exception InstantiationException if an exception occurs while
     *  instantiating the filter object
     * @exception ServletException if thrown by the filter's init() method
     * @throws NamingException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     * 传递一个 org.apache.catalina.Context 对象和 ApplicationFilterConfig 对象 给 ApplicationFilterConfig 的构造来创建一个
     * ApplicationFilterConfig 实例:
     */
    ApplicationFilterConfig(Context context, FilterDef filterDef)
            throws ClassCastException, ClassNotFoundException, IllegalAccessException,
            InstantiationException, ServletException, InvocationTargetException, NamingException,
            IllegalArgumentException, NoSuchMethodException, SecurityException {

        super();

        this.context = context;
        this.filterDef = filterDef;
        // Allocate a new filter instance if necessary
        if (filterDef.getFilter() == null) {
            getFilter();
        } else {
            this.filter = filterDef.getFilter();
            getInstanceManager().newInstance(filter);
            initFilter();
        }
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The Context with which we are associated.
     */
    private transient Context context = null;


    /**
     * The application Filter we are configured for.
     */
    private transient Filter filter = null;


    /**
     * The <code>FilterDef</code> that defines our associated Filter.
     */
    private final FilterDef filterDef;

    /**
     * the InstanceManager used to create and destroy filter instances.
     */
    private transient InstanceManager instanceManager;

    /**
     * JMX registration name
     */
    private ObjectName oname;

    // --------------------------------------------------- FilterConfig Methods


    /**
     * Return the name of the filter we are configuring.
     */
    @Override
    public String getFilterName() {
        return (filterDef.getFilterName());
    }

    /**
     * Return the class of the filter we are configuring.
     */
    public String getFilterClass() {
        return filterDef.getFilterClass();
    }

    /**
     * Return a <code>String</code> containing the value of the named
     * initialization parameter, or <code>null</code> if the parameter
     * does not exist.
     *
     * @param name Name of the requested initialization parameter
     */
    @Override
    public String getInitParameter(String name) {

        Map<String,String> map = filterDef.getParameterMap();
        if (map == null) {
            return (null);
        }

        return map.get(name);

    }


    /**
     * Return an <code>Enumeration</code> of the names of the initialization
     * parameters for this Filter.
     */
    @Override
    public Enumeration<String> getInitParameterNames() {
        Map<String,String> map = filterDef.getParameterMap();

        if (map == null) {
            return Collections.enumeration(emptyString);
        }

        return Collections.enumeration(map.keySet());
    }


    /**
     * Return the ServletContext of our associated web application.
     */
    @Override
    public ServletContext getServletContext() {

        return this.context.getServletContext();

    }


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ApplicationFilterConfig[");
        sb.append("name=");
        sb.append(filterDef.getFilterName());
        sb.append(", filterClass=");
        sb.append(filterDef.getFilterClass());
        sb.append("]");
        return (sb.toString());

    }

    // --------------------------------------------------------- Public Methods

    public Map<String, String> getFilterInitParameterMap() {
        return Collections.unmodifiableMap(filterDef.getParameterMap());
    }

    // -------------------------------------------------------- Package Methods


    /**
     * Return the application Filter we are configured for.
     *
     * @exception ClassCastException if the specified class does not implement
     *  the <code>javax.servlet.Filter</code> interface
     * @exception ClassNotFoundException if the filter class cannot be found
     * @exception IllegalAccessException if the filter class cannot be
     *  publicly instantiated
     * @exception InstantiationException if an exception occurs while
     *  instantiating the filter object
     * @exception ServletException if thrown by the filter's init() method
     * @throws NamingException
     * @throws InvocationTargetException
     * @throws SecurityException
     * @throws NoSuchMethodException
     * @throws IllegalArgumentException
     * Context 对象表示一个一个 web 应用而 FilterDef 表示一个过滤器定义。 ApplicationFilterConfig 的 getFilter 方法可以返回一个
     * javax.servlet.Filter 方法，该方法加载过滤器类并初始化它。
     */
    Filter getFilter() throws ClassCastException, ClassNotFoundException, IllegalAccessException,
            InstantiationException, ServletException, InvocationTargetException, NamingException,
            IllegalArgumentException, NoSuchMethodException, SecurityException {

        // Return the existing filter instance, if any
        if (this.filter != null)
            return (this.filter);

        // Identify the class loader we will be using
        String filterClass = filterDef.getFilterClass();
        this.filter = (Filter) getInstanceManager().newInstance(filterClass);

        initFilter();

        return (this.filter);

    }

    private void initFilter() throws ServletException {
        if (context instanceof StandardContext &&
                context.getSwallowOutput()) {
            try {
                SystemLogHandler.startCapture();
                // ApplicationFilterConfig Servlet规范提供了一个FilterConfig 接口访问的Filter的名称 ，初始化参数，及Servlet上下文
                // ApplicationFilterConfig 是FilterConfig接口的具体实现， 它的实现具体依赖Context接口， 而当初始化每个Filter时，
                // ApplicationFilter对象都会作为Filter的init方法的参数，所以我们在自定义Filter时可以在初始化方法中直接使用FilterConfig
                filter.init(this);
            } finally {
                String capturedlog = SystemLogHandler.stopCapture();
                if (capturedlog != null && capturedlog.length() > 0) {
                    getServletContext().log(capturedlog);
                }
            }
        } else {
            filter.init(this);
        }

        // Expose filter via JMX
        registerJMX();
    }

    /**
     * Return the filter definition we are configured for.
     */
    FilterDef getFilterDef() {

        return (this.filterDef);

    }

    /**
     * Release the Filter instance associated with this FilterConfig,
     * if there is one.
     */
    void release() {

        unregisterJMX();

        if (this.filter != null) {
            try {
                if (Globals.IS_SECURITY_ENABLED) {
                    try {
                        SecurityUtil.doAsPrivilege("destroy", filter);
                    } finally {
                        SecurityUtil.remove(filter);
                    }
                } else {
                    filter.destroy();
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                context.getLogger().error(sm.getString(
                        "applicationFilterConfig.release",
                        filterDef.getFilterName(),
                        filterDef.getFilterClass()), t);
            }
            if (!context.getIgnoreAnnotations()) {
                try {
                    ((StandardContext) context).getInstanceManager().destroyInstance(this.filter);
                } catch (Exception e) {
                    Throwable t = ExceptionUtils
                            .unwrapInvocationTargetException(e);
                    ExceptionUtils.handleThrowable(t);
                    context.getLogger().error(
                            sm.getString("applicationFilterConfig.preDestroy",
                                    filterDef.getFilterName(), filterDef.getFilterClass()), t);
                }
            }
        }
        this.filter = null;

     }


    // -------------------------------------------------------- Private Methods

    private InstanceManager getInstanceManager() {
        if (instanceManager == null) {
            if (context instanceof StandardContext) {
                instanceManager = ((StandardContext)context).getInstanceManager();
            } else {
                instanceManager = new DefaultInstanceManager(null,
                        new HashMap<String, Map<String, String>>(),
                        context,
                        getClass().getClassLoader());
            }
        }
        return instanceManager;
    }

    private void registerJMX() {
        String parentName = context.getName();
        if (!parentName.startsWith("/")) {
            parentName = "/" + parentName;
        }

        String hostName = context.getParent().getName();
        hostName = (hostName == null) ? "DEFAULT" : hostName;

        // domain == engine name
        String domain = context.getParent().getParent().getName();

        String webMod = "//" + hostName + parentName;
        String onameStr = null;
        String filterName = filterDef.getFilterName();
        if (Util.objectNameValueNeedsQuote(filterName)) {
            filterName = ObjectName.quote(filterName);
        }
        if (context instanceof StandardContext) {
            StandardContext standardContext = (StandardContext) context;
            onameStr = domain + ":j2eeType=Filter,name=" + filterName +
                 ",WebModule=" + webMod + ",J2EEApplication=" +
                 standardContext.getJ2EEApplication() + ",J2EEServer=" +
                 standardContext.getJ2EEServer();
        } else {
            onameStr = domain + ":j2eeType=Filter,name=" + filterName +
                 ",WebModule=" + webMod;
        }
        try {
            oname = new ObjectName(onameStr);
            Registry.getRegistry(null, null).registerComponent(this, oname, null);
        } catch (Exception ex) {
            log.warn(sm.getString("applicationFilterConfig.jmxRegisterFail",
                    getFilterClass(), getFilterName()), ex);
        }
    }


    private void unregisterJMX() {
        // unregister this component
        if (oname != null) {
            try {
                Registry.getRegistry(null, null).unregisterComponent(oname);
                if (log.isDebugEnabled())
                    log.debug(sm.getString("applicationFilterConfig.jmxUnregister",
                            getFilterClass(), getFilterName()));
            } catch(Exception ex) {
                log.warn(sm.getString("applicationFilterConfig.jmxUnregisterFail",
                        getFilterClass(), getFilterName()), ex);
            }
        }
    }
}
