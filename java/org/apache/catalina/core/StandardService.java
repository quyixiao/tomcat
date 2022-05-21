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


import java.beans.PropertyChangeListener;
import java.beans.PropertyChangeSupport;
import java.util.ArrayList;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Engine;
import org.apache.catalina.Executor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.connector.Connector;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;


/**
 * Standard implementation of the <code>Service</code> interface.  The
 * associated Container is generally an instance of Engine, but this is
 * not required.
 *
 * @author Craig R. McClanahan
 * org.apache.catalina.core.StandardService 类是 Service 接口的标准实现。 StandardService 类的 initialize 方法初始化所有的添加
 * 到该服务的连接器。 该类还实现了 org.apache.catalina.Lifecycle 接口，所以调用它的 start 方法 能启动所有的连接器和容器。
 *
 *
 * 一个 StandardService 实例包括两种组件:一个容器和多个连接器。多个连接器 可以使得 Tomcat 能服务于多个协议。一个协议用处处理 HTTP 请求，
 * 另一个用于 处理 HTTPS 请求。
 *
 * StandardService 类用 container 变量来持有容器实例，用 connectors 数组来 持有所有的连接器
 *
 */

public class StandardService extends LifecycleMBeanBase implements Service {

    private static final Log log = LogFactory.getLog(StandardService.class);


    // ----------------------------------------------------- Instance Variables


    /**
     * Descriptive information about this component implementation.
     */
    private static final String info =
        "org.apache.catalina.core.StandardService/1.0";


    /**
     * The name of this service.
     */
    private String name = null;


    /**
     * The string manager for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * The <code>Server</code> that owns this Service, if any.
     */
    private Server server = null;

    /**
     * The property change support for this component.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);


    /**
     * The set of Connectors associated with this Service.
     */
    protected Connector connectors[] = new Connector[0];
    private final Object connectorsLock = new Object();

    /**
     *
     */
    protected ArrayList<Executor> executors = new ArrayList<Executor>();

    /**
     * The Container associated with this Service. (In the case of the
     * org.apache.catalina.startup.Embedded subclass, this holds the most
     * recently added Engine.)
     */
    protected Container container = null;

    private ClassLoader parentClassLoader = null;

    // ------------------------------------------------------------- Properties


    /**
     * Return the <code>Container</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     */
    @Override
    public Container getContainer() {

        return (this.container);

    }


    /**
     * Set the <code>Container</code> that handles requests for all
     * <code>Connectors</code> associated with this Service.
     *
     * @param container The new Container
     */
    @Override
    public void setContainer(Container container) {

        Container oldContainer = this.container;
        if ((oldContainer != null) && (oldContainer instanceof Engine))
            ((Engine) oldContainer).setService(null);
        this.container = container;
        if ((this.container != null) && (this.container instanceof Engine))
            ((Engine) this.container).setService(this);
        if (getState().isAvailable() && (this.container != null)) {
            try {
                this.container.start();
            } catch (LifecycleException e) {
                // Ignore
            }
        }
        if (getState().isAvailable() && (oldContainer != null)) {
            try {
                oldContainer.stop();
            } catch (LifecycleException e) {
                // Ignore
            }
        }

        // Report this property change to interested listeners
        support.firePropertyChange("container", oldContainer, this.container);

    }


    /**
     * Return descriptive information about this Service implementation and
     * the corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    @Override
    public String getInfo() {

        return (info);

    }


    /**
     * Return the name of this Service.
     */
    @Override
    public String getName() {

        return (this.name);

    }


    /**
     * Set the name of this Service.
     *
     * @param name The new service name
     */
    @Override
    public void setName(String name) {

        this.name = name;

    }


    /**
     * Return the <code>Server</code> with which we are associated (if any).
     */
    @Override
    public Server getServer() {

        return (this.server);

    }


    /**
     * Set the <code>Server</code> with which we are associated (if any).
     *
     * @param server The server that owns this Service
     */
    @Override
    public void setServer(Server server) {

        this.server = server;

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add a new Connector to the set of defined Connectors, and associate it
     * with this Service's Container.
     *
     * @param connector The Connector to be added
     * 要与服务相关联的容器传递给该每个连接器，这样来建立容器和每个连接器的关 系。
     * 要给一个服务添加连接器，可以使用 addConnector 方法。要删除一个连接器， 可以使用 removeConnector 方法。
     *                  Lifecycle 方法是从 Lifecycle 接口继承而来。Initialize 方法调用每个连接器 的 initialize 方法
     *
     * 这个应用程序展示了如何使用服务器和服务，特别是它说明了如何使用 StandardServer 类的启动和停止机制。本程序由三个类组成，
     *                  SimpleContextConfig 跟第 13 章中的相同，另外两个类一个是 Bootstrap 启动 程序，另一个是 Stopper 类用来停止它。
     */
    @Override
    public void addConnector(Connector connector) {

        synchronized (connectorsLock) {
            connector.setService(this);
            Connector results[] = new Connector[connectors.length + 1];
            System.arraycopy(connectors, 0, results, 0, connectors.length);
            results[connectors.length] = connector;
            connectors = results;

            if (getState().isAvailable()) {
                try {
                    connector.start();
                } catch (LifecycleException e) {
                    log.error(sm.getString(
                            "standardService.connector.startFailed",
                            connector), e);
                }
            }

            // Report this property change to interested listeners
            support.firePropertyChange("connector", null, connector);
        }

    }

    public ObjectName[] getConnectorNames() {
        ObjectName results[] = new ObjectName[connectors.length];
        for (int i=0; i<results.length; i++) {
            results[i] = connectors[i].getObjectName();
        }
        return results;
    }


    /**
     * Add a property change listener to this component.
     *
     * @param listener The listener to add
     */
    public void addPropertyChangeListener(PropertyChangeListener listener) {

        support.addPropertyChangeListener(listener);

    }


    /**
     * Find and return the set of Connectors associated with this Service.
     */
    @Override
    public Connector[] findConnectors() {

        return (connectors);

    }


    /**
     * Remove the specified Connector from the set associated from this
     * Service.  The removed Connector will also be disassociated from our
     * Container.
     *
     * @param connector The Connector to be removed
     */
    @Override
    public void removeConnector(Connector connector) {

        synchronized (connectorsLock) {
            int j = -1;
            for (int i = 0; i < connectors.length; i++) {
                if (connector == connectors[i]) {
                    j = i;
                    break;
                }
            }
            if (j < 0)
                return;
            if (connectors[j].getState().isAvailable()) {
                try {
                    connectors[j].stop();
                } catch (LifecycleException e) {
                    log.error(sm.getString(
                            "standardService.connector.stopFailed",
                            connectors[j]), e);
                }
            }
            connector.setService(null);
            int k = 0;
            Connector results[] = new Connector[connectors.length - 1];
            for (int i = 0; i < connectors.length; i++) {
                if (i != j)
                    results[k++] = connectors[i];
            }
            connectors = results;

            // Report this property change to interested listeners
            support.firePropertyChange("connector", connector, null);
        }

    }


    /**
     * Remove a property change listener from this component.
     *
     * @param listener The listener to remove
     */
    public void removePropertyChangeListener(PropertyChangeListener listener) {

        support.removePropertyChangeListener(listener);

    }


    /**
     * Return a String representation of this component.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("StandardService[");
        sb.append(getName());
        sb.append("]");
        return (sb.toString());

    }


    /**
     * Adds a named executor to the service
     * @param ex Executor
     */
    @Override
    public void addExecutor(Executor ex) {
        synchronized (executors) {
            if (!executors.contains(ex)) {
                executors.add(ex);
                if (getState().isAvailable())
                    try {
                        ex.start();
                    } catch (LifecycleException x) {
                        log.error("Executor.start", x);
                    }
            }
        }
    }

    /**
     * Retrieves all executors
     * @return Executor[]
     */
    @Override
    public Executor[] findExecutors() {
        synchronized (executors) {
            Executor[] arr = new Executor[executors.size()];
            executors.toArray(arr);
            return arr;
        }
    }

    /**
     * Retrieves executor by name, null if not found
     * @param executorName String
     * @return Executor
     */
    @Override
    public Executor getExecutor(String executorName) {
        synchronized (executors) {
            for (Executor executor: executors) {
                if (executorName.equals(executor.getName()))
                    return executor;
            }
        }
        return null;
    }

    /**
     * Removes an executor from the service
     * @param ex Executor
     */
    @Override
    public void removeExecutor(Executor ex) {
        synchronized (executors) {
            if ( executors.remove(ex) && getState().isAvailable() ) {
                try {
                    ex.stop();
                } catch (LifecycleException e) {
                    log.error("Executor.stop", e);
                }
            }
        }
    }



    /**
     * Start nested components ({@link Executor}s, {@link Connector}s and
     * {@link Container}s) and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     *
     *  启动StandardEngine、Executor、Connector
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if(log.isInfoEnabled())
            log.info(sm.getString("standardService.start.name", this.name));
        setState(LifecycleState.STARTING);

        // Start our defined Container first
        if (container != null) {
            synchronized (container) {
                container.start();
            }
        }

        synchronized (executors) {
            for (Executor executor: executors) {
                executor.start();
            }
        }

        // Start our defined Connectors second
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                try {
                    // If it has already failed, don't try and start it
                    if (connector.getState() != LifecycleState.FAILED) {
                        connector.start();
                    }
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.startFailed",
                            connector), e);
                }
            }
        }
    }


    /**
     * Stop nested components ({@link Executor}s, {@link Connector}s and
     * {@link Container}s) and implement the requirements of
     * {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        // Pause connectors first
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                try {
                    connector.pause();
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.pauseFailed",
                            connector), e);
                }
            }
        }

        if(log.isInfoEnabled())
            log.info(sm.getString("standardService.stop.name", this.name));
        setState(LifecycleState.STOPPING);

        // Stop our defined Container second
        if (container != null) {
            synchronized (container) {
                container.stop();
            }
        }

        // Now stop the connectors
        synchronized (connectorsLock) {
            for (Connector connector: connectors) {
                if (!LifecycleState.STARTED.equals(
                        connector.getState())) {
                    // Connectors only need stopping if they are currently
                    // started. They may have failed to start or may have been
                    // stopped (e.g. via a JMX call)
                    continue;
                }
                try {
                    connector.stop();
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.stopFailed",
                            connector), e);
                }
            }
        }

        synchronized (executors) {
            for (Executor executor: executors) {
                executor.stop();
            }
        }
    }


    /**
     * Invoke a pre-startup initialization. This is used to allow connectors
     * to bind to restricted ports under Unix operating environments.
     */
    @Override
    protected void initInternal() throws LifecycleException {

        super.initInternal(); // 将StandardService注册到jmx中

        // 将Service下的容器进行初始化，默认情况下是StandardEngine
        if (container != null) {
            container.init();   // 注意：这里是Engine，这个流程只会初始化StandardEngine，并没有去初始话Engine下的Host，那么Host是在哪初始化的呢？
                                // 实际上，对于Host容器，并不需要进行初始化
        }

        // Initialize any Executors
        // 初始化线程池
        // 可以在Service下配置定义executor，默认实现类为org.apache.catalina.core.StandardThreadExecutor
        // 这个初始化只是走了一下生命周期的初始化流程，没有其他作用
        for (Executor executor : findExecutors()) {
            if (executor instanceof LifecycleMBeanBase) {
                ((LifecycleMBeanBase) executor).setDomain(getDomain());
            }
            executor.init();
        }

        // Initialize our defined Connectors
        // 初始化连接器
        // 为什么这里要同步，而上面的container和executor不同步？
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                try {
                    connector.init();
                } catch (Exception e) {
                    String message = sm.getString(
                            "standardService.connector.initFailed", connector);
                    log.error(message, e);

                    if (Boolean.getBoolean("org.apache.catalina.startup.EXIT_ON_INIT_FAILURE"))
                        throw new LifecycleException(message);
                }
            }
        }
    }

    @Override
    protected void destroyInternal() throws LifecycleException {
        // Destroy our defined Connectors
        synchronized (connectorsLock) {
            for (Connector connector : connectors) {
                try {
                    connector.destroy();
                } catch (Exception e) {
                    log.error(sm.getString(
                            "standardService.connector.destroyFailed",
                            connector), e);
                }
            }
        }

        // Destroy any Executors
        for (Executor executor : findExecutors()) {
            executor.destroy();
        }

        if (container != null) {
            container.destroy();
        }

        super.destroyInternal();
    }

    /**
     * Return the parent class loader for this component.
     */
    @Override
    public ClassLoader getParentClassLoader() {
        if (parentClassLoader != null)
            return (parentClassLoader);
        if (server != null) {
            return (server.getParentClassLoader());
        }
        return (ClassLoader.getSystemClassLoader());
    }

    /**
     * Set the parent class loader for this server.
     *
     * @param parent The new parent class loader
     */
    @Override
    public void setParentClassLoader(ClassLoader parent) {
        ClassLoader oldParentClassLoader = this.parentClassLoader;
        this.parentClassLoader = parent;
        support.firePropertyChange("parentClassLoader", oldParentClassLoader,
                                   this.parentClassLoader);
    }
    @Override
    protected String getDomainInternal() {

        return MBeanUtils.getDomain(this);
    }

    @Override
    public final String getObjectNameKeyProperties() {
        return "type=Service";
    }
}
