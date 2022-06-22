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

import java.util.concurrent.Executor;

import org.apache.catalina.Container;
import org.apache.catalina.ContainerEvent;
import org.apache.catalina.ContainerListener;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.coyote.ProtocolHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

/**
 * <p>
 * A {@link LifecycleListener} that triggers the renewal of threads in Executor
 * pools when a {@link Context} is being stopped to avoid thread-local related
 * memory leaks.
 * </p>
 * <p>
 * Note : active threads will be renewed one by one when they come back to the
 * pool after executing their task, see
 * {@link org.apache.tomcat.util.threads.ThreadPoolExecutor}.afterExecute().
 * </p>
 *
 * This listener must be declared in server.xml to be active.
 *
 *监听器主要解决ThreadLocal 的使用可能带来的内存泄漏问题， 该监听器会在Tomcat 启动后将监听器Web应用重加载的监听器注册到每个Web 应用上。
 * 当Web应用重新加载时，该监听器会将所有的工作线程销毁并再创建，以避免ThreadLocal引起的内存漏泄
 *
 * ThreadLocal 导致内存泄漏的经典场景是Web 应用重加载，如图5.5 所示 ，当Tomcat 启动后，对客户端的请求处理由专门的工作线程池负责，线程池中
 * 线程的生命周期一般比较长，假如Web 应用中使用ThreadLocal 保存了AA 对象，而且AA 类由WebappclassLoader 加载，那么它就可以看成是线程引用
 * AA对象，Web 应用加载是通过重新实例化一个Webappclassloader类加载器来实现的， 由线程一直未销毁，旧的WebappClassLoader 也无法被回收。
 * 导致内存泄漏 。
 *
 * 解决ThreadLocal内存泄漏最彻底的方法就是当Web 应用重新加载时，把线程池内的所有线程销毁并重新创建，这样就不会发生线程引用某些对象问题了。
 * 如图5.6所示 ， Tomcat 中处理ThreadLocal 内存泄漏的工作其实主要就是销毁线程池原来的线程，然后创建新的线程， 这分两部来做。 第一步
 * 先将任务队列堵住，不让新的任务进来，第二步将线程池中所有的线程停止 。
 *
 * ThreadLocalLeakPreventionListenr 监听器的工作就是实现当Web应用重新加载时销毁池中的线程并重新创建新的线程，以此避免ThreadLocal内存泄漏 。
 *
 *
 */
public class ThreadLocalLeakPreventionListener implements LifecycleListener,
        ContainerListener {

    private static final Log log =
        LogFactory.getLog(ThreadLocalLeakPreventionListener.class);

    private volatile boolean serverStopping = false;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * Listens for {@link LifecycleEvent} for the start of the {@link Server} to
     * initialize itself and then for after_stop events of each {@link Context}.
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {
        try {
            Lifecycle lifecycle = event.getLifecycle();
            if (Lifecycle.AFTER_START_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Server) {
                // when the server starts, we register ourself as listener for
                // all context
                // as well as container event listener so that we know when new
                // Context are deployed
                Server server = (Server) lifecycle;
                registerListenersForServer(server);
            }

            if (Lifecycle.BEFORE_STOP_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Server) {
                // Server is shutting down, so thread pools will be shut down so
                // there is no need to clean the threads
                serverStopping = true;
            }

            if (Lifecycle.AFTER_STOP_EVENT.equals(event.getType()) &&
                    lifecycle instanceof Context) {
                stopIdleThreads((Context) lifecycle);
            }
        } catch (Exception e) {
            String msg =
                sm.getString(
                    "threadLocalLeakPreventionListener.lifecycleEvent.error",
                    event);
            log.error(msg, e);
        }
    }

    @Override
    public void containerEvent(ContainerEvent event) {
        try {
            String type = event.getType();
            if (Container.ADD_CHILD_EVENT.equals(type)) {
                processContainerAddChild(event.getContainer(),
                    (Container) event.getData());
            } else if (Container.REMOVE_CHILD_EVENT.equals(type)) {
                processContainerRemoveChild(event.getContainer(),
                    (Container) event.getData());
            }
        } catch (Exception e) {
            String msg =
                sm.getString(
                    "threadLocalLeakPreventionListener.containerEvent.error",
                    event);
            log.error(msg, e);
        }

    }

    private void registerListenersForServer(Server server) {
        for (Service service : server.findServices()) {
            Engine engine = (Engine) service.getContainer();
            if (engine != null) {
                engine.addContainerListener(this);
                registerListenersForEngine(engine);
            }
        }

    }

    private void registerListenersForEngine(Engine engine) {
        for (Container hostContainer : engine.findChildren()) {
            Host host = (Host) hostContainer;
            host.addContainerListener(this);
            registerListenersForHost(host);
        }
    }

    private void registerListenersForHost(Host host) {
        for (Container contextContainer : host.findChildren()) {
            Context context = (Context) contextContainer;
            registerContextListener(context);
        }
    }

    private void registerContextListener(Context context) {
        context.addLifecycleListener(this);
    }

    protected void processContainerAddChild(Container parent, Container child) {
        if (log.isDebugEnabled())
            log.debug("Process addChild[parent=" + parent + ",child=" + child +
                "]");

        if (child instanceof Context) {
            registerContextListener((Context) child);
        } else if (child instanceof Engine) {
            registerListenersForEngine((Engine) child);
        } else if (child instanceof Host) {
            registerListenersForHost((Host) child);
        }

    }

    protected void processContainerRemoveChild(Container parent,
        Container child) {

        if (log.isDebugEnabled())
            log.debug("Process removeChild[parent=" + parent + ",child=" +
                child + "]");

        if (child instanceof Context) {
            Context context = (Context) child;
            context.removeLifecycleListener(this);
        } else if (child instanceof Host || child instanceof Engine) {
            child.removeContainerListener(this);
        }
    }

    /**
     * Updates each ThreadPoolExecutor with the current time, which is the time
     * when a context is being stopped.
     *
     * @param context
     *            the context being stopped, used to discover all the Connectors
     *            of its parent Service.
     */
    private void stopIdleThreads(Context context) {
        if (serverStopping) return;

        if (!(context instanceof StandardContext) ||
            !((StandardContext) context).getRenewThreadsWhenStoppingContext()) {
            log.debug("Not renewing threads when the context is stopping. "
                + "It is not configured to do it.");
            return;
        }

        Engine engine = (Engine) context.getParent().getParent();
        Service service = engine.getService();
        Connector[] connectors = service.findConnectors();
        if (connectors != null) {
            for (Connector connector : connectors) {
                ProtocolHandler handler = connector.getProtocolHandler();
                Executor executor = null;
                if (handler != null) {
                    executor = handler.getExecutor();
                }

                if (executor instanceof ThreadPoolExecutor) {
                    ThreadPoolExecutor threadPoolExecutor =
                        (ThreadPoolExecutor) executor;
                    threadPoolExecutor.contextStopping();
                } else if (executor instanceof StandardThreadExecutor) {
                    StandardThreadExecutor stdThreadExecutor =
                        (StandardThreadExecutor) executor;
                    stdThreadExecutor.contextStopping();
                }

            }
        }
    }
}
