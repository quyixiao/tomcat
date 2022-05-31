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


package org.apache.catalina;


import javax.servlet.MultipartConfigElement;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;


/**
 * A <b>Wrapper</b> is a Container that represents an individual servlet
 * definition from the deployment descriptor of the web application.  It
 * provides a convenient mechanism to use Interceptors that see every single
 * request to the servlet represented by this definition.
 * <p>
 * Implementations of Wrapper are responsible for managing the servlet life
 * cycle for their underlying servlet class, including calling init() and
 * destroy() at appropriate times, as well as respecting the existence of
 * the SingleThreadModel declaration on the servlet class itself.
 * <p>
 * The parent Container attached to a Wrapper will generally be an
 * implementation of Context, representing the servlet context (and
 * therefore the web application) within which this servlet executes.
 * <p>
 * Child Containers are not allowed on Wrapper implementations, so the
 * <code>addChild()</code> method should throw an
 * <code>IllegalArgumentException</code>.
 *
 * @author Craig R. McClanahan
 *
 * 一般 来说，Context 容器包含若干个子容器，这些容器就叫Wrapper 容器，它属于Tomcat 中最小级别的容器，它不能再包含其他子容器，而且
 * 它的父容器必须为Context 容器，每个Wrapper 其实就对应一个Servlet,Servlet 的各种定义在Tomcat中就Wrapper 的形式存在 ，Wrapper 属于
 * 核心类，它的构造比较复杂 。
 *
 * Wrapper 属于Tomcat 4 个级别容器中最小的级别的容器，与之相对的是Servlet ，Servlet 的概念对我们来说非常熟悉，我们会在它的doGet 和doPost
 * 方法上编写逻辑处理代码，面Wrapper 则负责调用这些方法的逻辑，一般来说，一个Wrapper 对应一个Servlet 对象，也就是说，所有处理线程都共同
 * 一个Servlet对象，但是按规定，实现了SingleThreadModel 接口的Servlet也允许多个对象存在，如图 10.1 所示，Wrapper 容器可能对应一个Servlet
 * 对象，也可能对应一个Servelt 对象池，本章将深入讨论Servlet相关的机制及实现。
 *
 *
 * 在研究Servlet 在Tomcat 中的工作机制前， 必须先看看Servlet 规范的一些重要规定，该规范提供了一个Servlet接口，接口中包含了重要的方法init ,
 * service ,destory 等方法，Servlet 在初始化时要调用init 方法，在销毁时要调用destory 方法，而客户端请求处理时则调用service 方法，
 * 对于这些机制，都必须由Tomcat 的内部提供支持，具体则由Wrapper 容器提供支持。
 *
 * 对于 Tomcat 中消息流的流转机制，我们都已经清楚了。 4个不同级别的容器是通过管道机制进行流转，对于每个请求都是一层层处理的，如图10.2 所示
 * 当客户端请求到达服务端后，请求被抽象成了Request 对象的4个容器进行传递，首先通过Engine 容器的管道通过若干个阀门，最后通过StandardEngineValue
 * 阀门流转到Host 容器的管道，处理后继续往下流转，通过StandardardengineValue 阀门流转到Host 容器的管道，处理后继续往下流转，通过StandardHostValue
 * 阀门流转到Context 容器的管道，继续往下流转，通过StandardContextValue阀门流转到Wrapper 容器的管道，而对Servlet 的核心处理也正是因为StandardWrapperValue
 * 阀门流转到Wrapper容器的管道，而对Servlet 的核心处理也正是StandardWrapperValue 阀门中，StandardWrapperValue 阀门先由ApplicationFilterChain
 * 组件执行过滤器，然后调用Servlet的service方法请求进行处理，然后对客户端响应。
 *
 *
 *
 *
 */
public interface Wrapper extends Container {

    /**
     * Container event for adding a wrapper.
     */
    public static final String ADD_MAPPING_EVENT = "addMapping";

    /**
     * Container event for removing a wrapper.
     */
    public static final String REMOVE_MAPPING_EVENT = "removeMapping";

    // ------------------------------------------------------------- Properties


    /**
     * Return the available date/time for this servlet, in milliseconds since
     * the epoch.  If this date/time is in the future, any request for this
     * servlet will return an SC_SERVICE_UNAVAILABLE error.  If it is zero,
     * the servlet is currently available.  A value equal to Long.MAX_VALUE
     * is considered to mean that unavailability is permanent.
     */
    public long getAvailable();


    /**
     * Set the available date/time for this servlet, in milliseconds since the
     * epoch.  If this date/time is in the future, any request for this servlet
     * will return an SC_SERVICE_UNAVAILABLE error.  A value equal to
     * Long.MAX_VALUE is considered to mean that unavailability is permanent.
     *
     * @param available The new available date/time
     */
    public void setAvailable(long available);


    /**
     * Return the load-on-startup order value (negative value means
     * load on first call).
     */
    public int getLoadOnStartup();


    /**
     * Set the load-on-startup order value (negative value means
     * load on first call).
     *
     * @param value New load-on-startup value
     */
    public void setLoadOnStartup(int value);


    /**
     * Return the run-as identity for this servlet.
     */
    public String getRunAs();


    /**
     * Set the run-as identity for this servlet.
     *
     * @param runAs New run-as identity value
     */
    public void setRunAs(String runAs);


    /**
     * Return the fully qualified servlet class name for this servlet.
     */
    public String getServletClass();


    /**
     * Set the fully qualified servlet class name for this servlet.
     *
     * @param servletClass Servlet class name
     */
    public void setServletClass(String servletClass);


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
    public String[] getServletMethods() throws ServletException;


    /**
     * Is this servlet currently unavailable?
     */
    public boolean isUnavailable();


    /**
     * Return the associated servlet instance.
     */
    public Servlet getServlet();


    /**
     * Set the associated servlet instance
     */
    public void setServlet(Servlet servlet);

    // --------------------------------------------------------- Public Methods


    /**
     * Add a new servlet initialization parameter for this servlet.
     *
     * @param name Name of this initialization parameter to add
     * @param value Value of this initialization parameter to add
     */
    public void addInitParameter(String name, String value);


    /**
     * Add a new listener interested in InstanceEvents.
     *
     * @param listener The new listener
     */
    public void addInstanceListener(InstanceListener listener);


    /**
     * Add a mapping associated with the Wrapper.
     *
     * @param mapping The new wrapper mapping
     */
    public void addMapping(String mapping);


    /**
     * Add a new security role reference record to the set of records for
     * this servlet.
     *
     * @param name Role name used within this servlet
     * @param link Role name used within the web application
     */
    public void addSecurityReference(String name, String link);


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
     */
    public Servlet allocate() throws ServletException;


    /**
     * Return this previously allocated servlet to the pool of available
     * instances.  If this servlet class does not implement SingleThreadModel,
     * no action is actually required.
     *
     * @param servlet The servlet to be returned
     *
     * @exception ServletException if a deallocation error occurs
     */
    public void deallocate(Servlet servlet) throws ServletException;


    /**
     * Return the value for the specified initialization parameter name,
     * if any; otherwise return <code>null</code>.
     *
     * @param name Name of the requested initialization parameter
     */
    public String findInitParameter(String name);


    /**
     * Return the names of all defined initialization parameters for this
     * servlet.
     */
    public String[] findInitParameters();


    /**
     * Return the mappings associated with this wrapper.
     */
    public String[] findMappings();


    /**
     * Return the security role link for the specified security role
     * reference name, if any; otherwise return <code>null</code>.
     *
     * @param name Security role reference used within this servlet
     */
    public String findSecurityReference(String name);


    /**
     * Return the set of security role reference names associated with
     * this servlet, if any; otherwise return a zero-length array.
     */
    public String[] findSecurityReferences();


    /**
     * Increment the error count value used when monitoring.
     */
    public void incrementErrorCount();


    /**
     * Load and initialize an instance of this servlet, if there is not already
     * at least one initialized instance.  This can be used, for example, to
     * load servlets that are marked in the deployment descriptor to be loaded
     * at server startup time.
     *
     * @exception ServletException if the servlet init() method threw
     *  an exception
     * @exception ServletException if some other loading problem occurs
     */
    public void load() throws ServletException;


    /**
     * Remove the specified initialization parameter from this servlet.
     *
     * @param name Name of the initialization parameter to remove
     */
    public void removeInitParameter(String name);


    /**
     * Remove a listener no longer interested in InstanceEvents.
     *
     * @param listener The listener to remove
     */
    public void removeInstanceListener(InstanceListener listener);


    /**
     * Remove a mapping associated with the wrapper.
     *
     * @param mapping The pattern to remove
     */
    public void removeMapping(String mapping);


    /**
     * Remove any security role reference for the specified role name.
     *
     * @param name Security role used within this servlet to be removed
     */
    public void removeSecurityReference(String name);


    /**
     * Process an UnavailableException, marking this servlet as unavailable
     * for the specified amount of time.
     *
     * @param unavailable The exception that occurred, or <code>null</code>
     *  to mark this servlet as permanently unavailable
     */
    public void unavailable(UnavailableException unavailable);


    /**
     * Unload all initialized instances of this servlet, after calling the
     * <code>destroy()</code> method for each instance.  This can be used,
     * for example, prior to shutting down the entire servlet engine, or
     * prior to reloading all of the classes from the Loader associated with
     * our Loader's repository.
     *
     * @exception ServletException if an unload error occurs
     */
    public void unload() throws ServletException;


    /**
     * Get the multi-part configuration for the associated servlet. If no
     * multi-part configuration has been defined, then <code>null</code> will be
     * returned.
     */
    public MultipartConfigElement getMultipartConfigElement();


    /**
     * Set the multi-part configuration for the associated servlet. To clear the
     * multi-part configuration specify <code>null</code> as the new value.
     */
    public void setMultipartConfigElement(
            MultipartConfigElement multipartConfig);

    /**
     * Does the associated Servlet support async processing? Defaults to
     * <code>false</code>.
     */
    public boolean isAsyncSupported();

    /**
     * Set the async support for the associated servlet.
     */
    public void setAsyncSupported(boolean asyncSupport);

    /**
     * Is the associated Servlet enabled? Defaults to <code>true</code>.
     */
    public boolean isEnabled();

    /**
     * Sets the enabled attribute for the associated servlet.
     */
    public void setEnabled(boolean enabled);

    /**
     * This method is no longer used. All implementations should be NO-OPs.
     *
     * @param b Unused.
     *
     * @deprecated This will be removed in Tomcat 9.
     */
    @Deprecated
    public void setServletSecurityAnnotationScanRequired(boolean b);

    /**
     * This method is no longer used. All implementations should be NO-OPs.
     *
     * @throws ServletException Never thrown
     *
     * @deprecated This will be removed in Tomcat 9.
     */
    @Deprecated
    public void servletSecurityAnnotationScan() throws ServletException;

    /**
     * Is the Servlet overridable by a ServletContainerInitializer?
     */
    public boolean isOverridable();

    /**
     * Sets the overridable attribute for this Servlet.
     */
    public void setOverridable(boolean overridable);
}
