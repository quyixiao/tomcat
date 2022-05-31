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


import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;

import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.Servlet;
import javax.servlet.ServletException;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.Context;
import org.apache.catalina.Globals;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.comet.CometProcessor;
import org.apache.catalina.connector.ClientAbortException;
import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.log.SystemLogHandler;
import org.apache.tomcat.util.res.StringManager;

/**
 * Valve that implements the default basic behavior for the
 * <code>StandardWrapper</code> container implementation.
 *
 * @author Craig R. McClanahan
 *
 * 下面更深入的讨论StandardWrapperValue 阀门调用Servlet的过程，Web 应用的Servlet类都根据Servlet接口，例如我们一般在写业务处理Servlet
 * 类时都会继承HttpServlet类，为了遵循Servlet的规范，它其实最终也实现了Servlet接口，只是HttpServlet 定义的Http协议的Servlet ，将协议
 * 共性的东西抽离出来复用，Servlet 处理客户端请求的核心方法是service 方法，所以对HttpServlet来说，它需要针对Http协议的Get ,Post ，put
 * ,delete ,head ,options ，trace 等请求方法做出不同的分发处理 。
 *
 *  service 方法将请求对象和响应对象转换成HttpServletRequest 和HttpServletResponse ，然后获取请求方法，根据请求方法调用不同的处理方法
 *  倒好，如果为GET 方法则调用doGet 方法，那么继承HttpServlet 类的Servlet只需要重写doGet 或doPost方法完成业务逻辑处理， 这就我们熟悉
 *  Servlet了。
 *
 *  这样一来，StandardWrapperValue 阀门调用了Servlet 的工作其实就是通过反射机制实现对Servlet 对象来控制 ，例如，在不配置load-on-startup
 *  的情况下，客户端首次访问该Servlet 时由于还不存在该Servlet对象，需要通过反射机制实例化出Servlet对象，并且调用初始化方法，这也是第一次
 *  访问某个Servlet时会比较耗时的原因，后面客户端再对该Servlet 访问时都会使用该Servlet对象，无须再做实例化和初始化操作，有了Servlet对象后。
 *  调用其service 方法即完成了对客户端请求的处理。
 *
 *  实际上 通过反射机制实例化Servlet 对象是一个比较复杂的过程 ，它除了完成实例化和初始化工作外还要解析该Servlet 类包含的各种注解并进行处理
 *  另外，对于实现了SingleThreadModel 接口的Servlet类，它还需要维护一个Servlet对象池。
 *
 *  综上所述，Servlet 工作机制的大致流程：Request -StandardEngineValue，StandardHostValue -> StandardContextValue->StandardWrapperValue
 *  实例化并初始化Servlet对象，由于过滤器链执行过滤器操作，调用Servlet对象的service 方法，response
 *
 *
 *  Servlet 在不实现SingleThreadModel 的情况下单个实例模式运行， 这种情况下，以单个实例模式运行，如图10.3 所示，这种情况下，Wrapper 容器只会
 *  通过反射实例化一个Servlett对象，对应此Servlet 的所有客户端请求都会共用此Servlet 对象，而对于多个客户端请求Tomcat 会使用多线程处理
 *  所以要注意保持线程安全问题，这里举一个刚刚   伏笔和Web 应用开发时可能会犯一个错误，在某个Servlet 中使用成员变量累加去统计访问次数，这就是
 *  存在线程安全问题。
 *
 *  为了支持一个Servlet 对象一个线程，Servlet规范提出了一个SingleThreadModel 接口，Tomcat 容器必须要完成的机制是，如果某个Servlet 实现
 *  了SingleThreadModel 接口，则要保证一个线程独占一个Servlet对象，假如线程1正在使用Servlet1对象，则线程2 不能再使用Servlet1对象
 *  则只能使用Sevlet2 对象了。
 *
 *  针对SingleThreadModel 模式，Tomcat 的Wrapper 容器使用了对象池的策略， Wrapper 容器会有一个Servlet堆，负责保存若干个Servlet对象
 *  当需要Servlet对象时从堆中pop出一个对象，而当用完则push 回堆中，Wrapper 容器中最多的有20个Servlet对象，例如XXXServlet 类对象池。
 *  已经有20个线程占用了20个对象，于是在第21个线程执行时就会因为阻塞而等待，直到对象池中有可用的对象才继续执行。
 *
 *
 *
 */
final class StandardWrapperValve
    extends ValveBase {

    //------------------------------------------------------ Constructor
    public StandardWrapperValve() {
        super(true);
    }

    // ----------------------------------------------------- Instance Variables


    // Some JMX statistics. This valve is associated with a StandardWrapper.
    // We expose the StandardWrapper as JMX ( j2eeType=Servlet ). The fields
    // are here for performance.
    private volatile long processingTime;
    private volatile long maxTime;
    private volatile long minTime = Long.MAX_VALUE;
    private final AtomicInteger requestCount = new AtomicInteger(0);
    private final AtomicInteger errorCount = new AtomicInteger(0);


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);


    // --------------------------------------------------------- Public Methods


    /**
     * Invoke the servlet we are managing, respecting the rules regarding
     * servlet lifecycle and SingleThreadModel support.
     *
     * @param request Request to be processed
     * @param response Response to be produced
     *
     * @exception IOException if an input/output error occurred
     * @exception ServletException if a servlet error occurred
     * StandardWrapperValve 是 StandardWrapper 实例上的基本阀门，该阀门做两件 事情:
     *  1.提交 Servlet 的所有相关过滤器
     *  2.调用发送者的 service 方法要实现这些内容，下面是 StandardWrapperValve 在他的 invoke 方法要实现的:
     *  3.调用 StandardWrapper 的 allocate 的方法来获得一个 servlet 实例
     *  4.调用它的 private createFilterChain 方法获得过滤链
     *  5.调用过滤器链的 doFilter 方法。包括调用 servlet 的 service 方法
     *  6.释放过滤器链
     *  7.调用包装器的 deallocate 方法
     *  8.如果 Servlet 无法使用了，调用包装器的 unload 方法
     */
    @Override
    public final void invoke(Request request, Response response)
        throws IOException, ServletException {

        // Initialize local variables we may need
        boolean unavailable = false;
        Throwable throwable = null;
        // This should be a Request attribute...
        long t1=System.currentTimeMillis();
        requestCount.incrementAndGet();
        StandardWrapper wrapper = (StandardWrapper) getContainer(); // // 属于哪个Wrapper
        Servlet servlet = null;
        Context context = (Context) wrapper.getParent();  // 属于哪个Context

        // Check for the application being marked unavailable
        if (!context.getState().isAvailable()) {
            response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardContext.isUnavailable"));
            unavailable = true;
        }

        // Check for the servlet being marked unavailable
        // 如果Context可用，但是Wrapper不可用, 在定义servlet时，可以设置enabled
        if (!unavailable && wrapper.isUnavailable()) {
            container.getLogger().info(sm.getString("standardWrapper.isUnavailable",
                    wrapper.getName()));
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                        sm.getString("standardWrapper.isUnavailable",
                                wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                        sm.getString("standardWrapper.notFound",
                                wrapper.getName()));
            }
            unavailable = true;
        }

        // Allocate a servlet instance to process this request
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();
            }
        } catch (UnavailableException e) {
            container.getLogger().error(
                    sm.getString("standardWrapper.allocateException",
                            wrapper.getName()), e);
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                           sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
        } catch (ServletException e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), StandardWrapper.getRootCause(e));
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }

        // Identify if the request is Comet related now that the servlet has been allocated
        boolean comet = false;
        if (servlet instanceof CometProcessor && Boolean.TRUE.equals(request.getAttribute(
                Globals.COMET_SUPPORTED_ATTR))) {
            comet = true;
            request.setComet(true);
        }

        MessageBytes requestPathMB = request.getRequestPathMB();
        DispatcherType dispatcherType = DispatcherType.REQUEST;
        if (request.getDispatcherType()==DispatcherType.ASYNC) dispatcherType = DispatcherType.ASYNC;
        request.setAttribute(Globals.DISPATCHER_TYPE_ATTR,dispatcherType);
        request.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                requestPathMB);
        // Create the filter chain for this request
        ApplicationFilterFactory factory =
            ApplicationFilterFactory.getInstance();

        // 最重要的方法是 createFilterChain 方法并调用过滤器链的 doFilter 方法。方 法 createFilterChain 创建了一个
        // ApplicationFilterChain 实例，并将所有的 过滤器添加到上面。ApplicationFilterChain 类将在下面的小节中介绍。
        // 要完 全的理解这个类，还需要理解 FilterDef 和 ApplicationFilterConfig 类。这些 内容将在下面介绍
        ApplicationFilterChain filterChain =
            factory.createFilterChain(request, wrapper, servlet);

        // Reset comet flag value after creating the filter chain
        request.setComet(false);

        // Call the filter chain for this request
        // NOTE: This also calls the servlet's service() method
        try {
            if ((servlet != null) && (filterChain != null)) {
                // Swallow output if needed
                if (context.getSwallowOutput()) {
                    try {
                        SystemLogHandler.startCapture();
                        if (request.isAsyncDispatching()) {
                            request.getAsyncContextInternal().doInternalDispatch();
                        } else if (comet) {
                            filterChain.doFilterEvent(request.getEvent());
                            request.setComet(true);
                        } else {
                            filterChain.doFilter(request.getRequest(),
                                    response.getResponse());
                        }
                    } finally {
                        String log = SystemLogHandler.stopCapture();
                        if (log != null && log.length() > 0) {
                            context.getLogger().info(log);
                        }
                    }
                } else {
                    if (request.isAsyncDispatching()) {
                        request.getAsyncContextInternal().doInternalDispatch();
                    } else if (comet) {
                        request.setComet(true);
                        filterChain.doFilterEvent(request.getEvent());
                    } else {
                        filterChain.doFilter
                            (request.getRequest(), response.getResponse());
                    }
                }

            }
        } catch (ClientAbortException e) {
            throwable = e;
            exception(request, response, e);
        } catch (IOException e) {
            container.getLogger().error(sm.getString(
                    "standardWrapper.serviceException", wrapper.getName(),
                    context.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (UnavailableException e) {
            container.getLogger().error(sm.getString(
                    "standardWrapper.serviceException", wrapper.getName(),
                    context.getName()), e);
            //            throwable = e;
            //            exception(request, response, e);
            wrapper.unavailable(e);
            long available = wrapper.getAvailable();
            if ((available > 0L) && (available < Long.MAX_VALUE)) {
                response.setDateHeader("Retry-After", available);
                response.sendError(HttpServletResponse.SC_SERVICE_UNAVAILABLE,
                           sm.getString("standardWrapper.isUnavailable",
                                        wrapper.getName()));
            } else if (available == Long.MAX_VALUE) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                            sm.getString("standardWrapper.notFound",
                                        wrapper.getName()));
            }
            // Do not save exception in 'throwable', because we
            // do not want to do exception(request, response, e) processing
        } catch (ServletException e) {
            Throwable rootCause = StandardWrapper.getRootCause(e);
            if (!(rootCause instanceof ClientAbortException)) {
                container.getLogger().error(sm.getString(
                        "standardWrapper.serviceExceptionRoot",
                        wrapper.getName(), context.getName(), e.getMessage()),
                        rootCause);
            }
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString(
                    "standardWrapper.serviceException", wrapper.getName(),
                    context.getName()), e);
            throwable = e;
            exception(request, response, e);
        }

        // Release the filter chain (if any) for this request
        if (filterChain != null) {
            if (request.isComet()) {
                // If this is a Comet request, then the same chain will be used for the
                // processing of all subsequent events.
                filterChain.reuse();
            } else {
                filterChain.release();
            }
        }

        // Deallocate the allocated servlet instance
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString("standardWrapper.deallocateException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        // If this servlet has been marked permanently unavailable,
        // unload it and release this instance
        try {
            if ((servlet != null) &&
                (wrapper.getAvailable() == Long.MAX_VALUE)) {
                wrapper.unload();
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString("standardWrapper.unloadException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }
        long t2=System.currentTimeMillis();

        long time=t2-t1;
        processingTime += time;
        if( time > maxTime) maxTime=time;
        if( time < minTime) minTime=time;

    }


    /**
     * Process a Comet event. The main differences here are to not use sendError
     * (the response is committed), to avoid creating a new filter chain
     * (which would work but be pointless), and a few very minor tweaks.
     *
     * @param request The servlet request to be processed
     * @param response The servlet response to be created
     *
     * @exception IOException if an input/output error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     * @exception ServletException if a servlet error occurs, or is thrown
     *  by a subsequently invoked Valve, Filter, or Servlet
     */
    @Override
    public void event(Request request, Response response, CometEvent event)
        throws IOException, ServletException {

        // Initialize local variables we may need
        Throwable throwable = null;
        // This should be a Request attribute...
        long t1=System.currentTimeMillis();
        // FIXME: Add a flag to count the total amount of events processed ? requestCount++;

        StandardWrapper wrapper = (StandardWrapper) getContainer();
        if (wrapper == null) {
            // Context has been shutdown. Nothing to do here.
            return;
        }

        Servlet servlet = null;
        Context context = (Context) wrapper.getParent();

        // Check for the application being marked unavailable
        boolean unavailable = !context.getState().isAvailable() ||
                wrapper.isUnavailable();

        // Allocate a servlet instance to process this request
        try {
            if (!unavailable) {
                servlet = wrapper.allocate();
            }
        } catch (UnavailableException e) {
            // The response is already committed, so it's not possible to do anything
        } catch (ServletException e) {
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), StandardWrapper.getRootCause(e));
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString("standardWrapper.allocateException",
                             wrapper.getName()), e);
            throwable = e;
            exception(request, response, e);
            servlet = null;
        }

        MessageBytes requestPathMB = request.getRequestPathMB();
        request.setAttribute(Globals.DISPATCHER_TYPE_ATTR,
                DispatcherType.REQUEST);
        request.setAttribute(Globals.DISPATCHER_REQUEST_PATH_ATTR,
                requestPathMB);
        // Get the current (unchanged) filter chain for this request
        ApplicationFilterChain filterChain =
            (ApplicationFilterChain) request.getFilterChain();

        // Call the filter chain for this request
        // NOTE: This also calls the servlet's event() method
        try {
            if ((servlet != null) && (filterChain != null)) {

                // Swallow output if needed
                if (context.getSwallowOutput()) {
                    try {
                        SystemLogHandler.startCapture();
                        filterChain.doFilterEvent(request.getEvent());
                    } finally {
                        String log = SystemLogHandler.stopCapture();
                        if (log != null && log.length() > 0) {
                            context.getLogger().info(log);
                        }
                    }
                } else {
                    filterChain.doFilterEvent(request.getEvent());
                }

            }
        } catch (ClientAbortException e) {
            throwable = e;
            exception(request, response, e);
        } catch (IOException e) {
            container.getLogger().error(sm.getString(
                    "standardWrapper.serviceException", wrapper.getName(),
                    context.getName()), e);
            throwable = e;
            exception(request, response, e);
        } catch (UnavailableException e) {
            container.getLogger().error(sm.getString(
                    "standardWrapper.serviceException", wrapper.getName(),
                    context.getName()), e);
            // Do not save exception in 'throwable', because we
            // do not want to do exception(request, response, e) processing
        } catch (ServletException e) {
            Throwable rootCause = StandardWrapper.getRootCause(e);
            if (!(rootCause instanceof ClientAbortException)) {
                container.getLogger().error(sm.getString(
                        "standardWrapper.serviceExceptionRoot",
                        wrapper.getName(), context.getName(), e.getMessage()),
                        rootCause);
            }
            throwable = e;
            exception(request, response, e);
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString(
                    "standardWrapper.serviceException", wrapper.getName(),
                    context.getName()), e);
            throwable = e;
            exception(request, response, e);
        }

        // Release the filter chain (if any) for this request
        if (filterChain != null) {
            filterChain.reuse();
        }

        // Deallocate the allocated servlet instance
        try {
            if (servlet != null) {
                wrapper.deallocate(servlet);
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString("standardWrapper.deallocateException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        // If this servlet has been marked permanently unavailable,
        // unload it and release this instance
        try {
            if ((servlet != null) &&
                (wrapper.getAvailable() == Long.MAX_VALUE)) {
                wrapper.unload();
            }
        } catch (Throwable e) {
            ExceptionUtils.handleThrowable(e);
            container.getLogger().error(sm.getString("standardWrapper.unloadException",
                             wrapper.getName()), e);
            if (throwable == null) {
                throwable = e;
                exception(request, response, e);
            }
        }

        long t2=System.currentTimeMillis();

        long time=t2-t1;
        processingTime += time;
        if( time > maxTime) maxTime=time;
        if( time < minTime) minTime=time;

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Handle the specified ServletException encountered while processing
     * the specified Request to produce the specified Response.  Any
     * exceptions that occur during generation of the exception report are
     * logged and swallowed.
     *
     * @param request The request being processed
     * @param response The response being generated
     * @param exception The exception that occurred (which possibly wraps
     *  a root cause exception
     */
    private void exception(Request request, Response response,
                           Throwable exception) {
        request.setAttribute(RequestDispatcher.ERROR_EXCEPTION, exception);
        response.setStatus(HttpServletResponse.SC_INTERNAL_SERVER_ERROR);
        response.setError();
    }

    public long getProcessingTime() {
        return processingTime;
    }

    /**
     * Deprecated   unused
     */
    @Deprecated
    public void setProcessingTime(long processingTime) {
        this.processingTime = processingTime;
    }

    public long getMaxTime() {
        return maxTime;
    }

    /**
     * Deprecated   unused
     */
    @Deprecated
    public void setMaxTime(long maxTime) {
        this.maxTime = maxTime;
    }

    public long getMinTime() {
        return minTime;
    }

    /**
     * Deprecated   unused
     */
    @Deprecated
    public void setMinTime(long minTime) {
        this.minTime = minTime;
    }

    public int getRequestCount() {
        return requestCount.get();
    }

    /**
     * Deprecated   unused
     */
    @Deprecated
    public void setRequestCount(int requestCount) {
        this.requestCount.set(requestCount);
    }

    public int getErrorCount() {
        return errorCount.get();
    }

    public void incrementErrorCount() {
        errorCount.incrementAndGet();
    }

    /**
     * Deprecated   unused
     */
    @Deprecated
    public void setErrorCount(int errorCount) {
        this.errorCount.set(errorCount);
    }

    @Override
    protected void initInternal() throws LifecycleException {
        // NOOP - Don't register this Valve in JMX
    }
}
