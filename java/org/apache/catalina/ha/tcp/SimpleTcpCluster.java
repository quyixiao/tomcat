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

package org.apache.catalina.ha.tcp;

import java.beans.PropertyChangeSupport;
import java.io.Serializable;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

import javax.management.ObjectName;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Manager;
import org.apache.catalina.Valve;
import org.apache.catalina.ha.CatalinaCluster;
import org.apache.catalina.ha.ClusterDeployer;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterManager;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.catalina.ha.ClusterValve;
import org.apache.catalina.ha.session.ClusterSessionListener;
import org.apache.catalina.ha.session.DeltaManager;
import org.apache.catalina.ha.session.JvmRouteBinderValve;
import org.apache.catalina.ha.session.JvmRouteSessionIDBinderListener;
import org.apache.catalina.ha.session.SessionMessage;
import org.apache.catalina.ha.util.IDynamicProperty;
import org.apache.catalina.mbeans.MBeanUtils;
import org.apache.catalina.tribes.Channel;
import org.apache.catalina.tribes.ChannelListener;
import org.apache.catalina.tribes.Member;
import org.apache.catalina.tribes.MembershipListener;
import org.apache.catalina.tribes.group.GroupChannel;
import org.apache.catalina.tribes.group.interceptors.MessageDispatch15Interceptor;
import org.apache.catalina.tribes.group.interceptors.TcpFailureDetector;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.IntrospectionUtils;
import org.apache.tomcat.util.res.StringManager;

/**
 * A <b>Cluster </b> implementation using simple multicast. Responsible for
 * setting up a cluster and provides callers with a valid multicast
 * receiver/sender.
 *
 * FIXME wrote testcases
 *
 * @author Filip Hanik
 * @author Remy Maucherat
 * @author Peter Rossbach
 *
 *
 * Cluster 其实就是集群的意思，它是为了方便上层调用抽象出来的一个比较高层次的概念，总的来说，它最重要的两个接口就是发送和接收接口，对于
 * Cluster 来说，它可以让你不必关心集群之间是如何通信，与谁通信，你只要调用Cluster 接口将消息发送出去即可完成集群内的消息传递，Cluster 的
 * 的抽象将各种繁杂的细节屏蔽掉了，使我们很容易的实现集群通信 。
 *
 * 如图 20.7  ,Cluster 组件包含或者说关联的组件包括，会话管理器，集群通信通道，部署器（Deployer ），集群监听器（ClusterListener）
 * 集群阀门等，下面将对每个组件进一步的介绍 。
 *
 * Tomcat 的Cluster 模块主要的功能就是提供会话复制，上下文属性复制和在集群下的Web 应用部署，这些操作都存在跨节点问题， 默认的集群组件为实现
 * SimpleTcpCluster ,SimpleTcpCluster 使用组播为集群通信方式，它提供了组播发射器和接收器用于消息的传递。
 *
 *
 * 集群会话管理器。
 * 会话管理器主要用于管理集群中的会话，这些会话都涉及跨节点操作，包括DeltaManager 和BackupManager 两个会话管理器， 这两个会话管理器
 * 也是Tomcat集群中目前可选 的两种自带会话管理器。
 *
 * 集群通信通道
 * 通道是集群之间的通道组件，它是一个代表消息传递的通道的抽象，通道默认的实现为GroupChannel 即为群组通道，通道是一个较复杂的组件，它其实
 * 是Tribes 组件封装的一个对象，它可能包含若干个监听器和拦截器， 对数据的发送只须要调用通道的发送方法， 而数据接收则在监听器中实现，每个接收到
 * 数据都会调用ChannelListener 进行处理。
 *
 *
 */
public class SimpleTcpCluster extends LifecycleMBeanBase
        implements CatalinaCluster, LifecycleListener, IDynamicProperty,
               MembershipListener, ChannelListener{

    public static final Log log = LogFactory.getLog(SimpleTcpCluster.class);

    // ----------------------------------------------------- Instance Variables

    /**
     * Descriptive information about this component implementation.
     */
    protected static final String info = "SimpleTcpCluster/2.2";

    public static final String BEFORE_MEMBERREGISTER_EVENT = "before_member_register";

    public static final String AFTER_MEMBERREGISTER_EVENT = "after_member_register";

    public static final String BEFORE_MANAGERREGISTER_EVENT = "before_manager_register";

    public static final String AFTER_MANAGERREGISTER_EVENT = "after_manager_register";

    public static final String BEFORE_MANAGERUNREGISTER_EVENT = "before_manager_unregister";

    public static final String AFTER_MANAGERUNREGISTER_EVENT = "after_manager_unregister";

    public static final String BEFORE_MEMBERUNREGISTER_EVENT = "before_member_unregister";

    public static final String AFTER_MEMBERUNREGISTER_EVENT = "after_member_unregister";

    public static final String SEND_MESSAGE_FAILURE_EVENT = "send_message_failure";

    public static final String RECEIVE_MESSAGE_FAILURE_EVENT = "receive_message_failure";

    /**
     * Group channel.
     */
    protected Channel channel = new GroupChannel();


    /**
     * Name for logging purpose
     */
    protected String clusterImpName = "SimpleTcpCluster";

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    /**
     * The cluster name to join
     */
    protected String clusterName ;

    /**
     * call Channel.heartbeat() at container background thread
     * @see org.apache.catalina.tribes.group.GroupChannel#heartbeat()
     */
    protected boolean heartbeatBackgroundEnabled =false ;

    /**
     * The Container associated with this Cluster.
     */
    protected Container container = null;

    /**
     * The property change support for this component.
     */
    protected PropertyChangeSupport support = new PropertyChangeSupport(this);

    /**
     * The context name &lt;-&gt; manager association for distributed contexts.
     */
    protected Map<String, ClusterManager> managers =
        new HashMap<String, ClusterManager>();

    protected ClusterManager managerTemplate = new DeltaManager();

    private List<Valve> valves = new ArrayList<Valve>();

    private ClusterDeployer clusterDeployer;
    private ObjectName onameClusterDeployer;

    /**
     * Listeners of messages
     */
    protected List<ClusterListener> clusterListeners = new ArrayList<ClusterListener>();

    /**
     * Comment for <code>notifyLifecycleListenerOnFailure</code>
     */
    private boolean notifyLifecycleListenerOnFailure = false;

    /**
     * dynamic sender <code>properties</code>
     *
     * @deprecated  Unused - will be removed in Tomcat 8.0.x
     */
    @Deprecated
    private Map<String, Object> properties = new HashMap<String, Object>();

    private int channelSendOptions = Channel.SEND_OPTIONS_ASYNCHRONOUS;

    private int channelStartOptions = Channel.DEFAULT;

    private final Map<Member,ObjectName> memberOnameMap =
            new ConcurrentHashMap<Member,ObjectName>();

    // ------------------------------------------------------------- Properties

    public SimpleTcpCluster() {
        // NO-OP
    }

    /**
     * Return descriptive information about this Cluster implementation and the
     * corresponding version number, in the format
     * <code>&lt;description&gt;/&lt;version&gt;</code>.
     */
    @Override
    public String getInfo() {
        return (info);
    }

    /**
     * Return heartbeat enable flag (default false)
     * @return the heartbeatBackgroundEnabled
     */
    public boolean isHeartbeatBackgroundEnabled() {
        return heartbeatBackgroundEnabled;
    }

    /**
     * enabled that container backgroundThread call heartbeat at channel
     * @param heartbeatBackgroundEnabled the heartbeatBackgroundEnabled to set
     */
    public void setHeartbeatBackgroundEnabled(boolean heartbeatBackgroundEnabled) {
        this.heartbeatBackgroundEnabled = heartbeatBackgroundEnabled;
    }

    /**
     * Set the name of the cluster to join, if no cluster with this name is
     * present create one.
     *
     * @param clusterName
     *            The clustername to join
     */
    @Override
    public void setClusterName(String clusterName) {
        this.clusterName = clusterName;
    }

    /**
     * Return the name of the cluster that this Server is currently configured
     * to operate within.
     *
     * @return The name of the cluster associated with this server
     */
    @Override
    public String getClusterName() {
        if(clusterName == null && container != null)
            return container.getName() ;
        return clusterName;
    }

    /**
     * Set the Container associated with our Cluster
     *
     * @param container
     *            The Container to use
     */
    @Override
    public void setContainer(Container container) {
        Container oldContainer = this.container;
        this.container = container;
        support.firePropertyChange("container", oldContainer, this.container);
    }

    /**
     * Get the Container associated with our Cluster
     *
     * @return The Container associated with our Cluster
     */
    @Override
    public Container getContainer() {
        return this.container;
    }

    /**
     * @return Returns the notifyLifecycleListenerOnFailure.
     */
    public boolean isNotifyLifecycleListenerOnFailure() {
        return notifyLifecycleListenerOnFailure;
    }

    /**
     * @param notifyListenerOnFailure
     *            The notifyLifecycleListenerOnFailure to set.
     */
    public void setNotifyLifecycleListenerOnFailure(
            boolean notifyListenerOnFailure) {
        boolean oldNotifyListenerOnFailure = this.notifyLifecycleListenerOnFailure;
        this.notifyLifecycleListenerOnFailure = notifyListenerOnFailure;
        support.firePropertyChange("notifyLifecycleListenerOnFailure",
                oldNotifyListenerOnFailure,
                this.notifyLifecycleListenerOnFailure);
    }

    /**
     * Add cluster valve
     * Cluster Valves are only add to container when cluster is started!
     * @param valve The new cluster Valve.
     */
    @Override
    public void addValve(Valve valve) {
        if (valve instanceof ClusterValve && (!valves.contains(valve)))
            valves.add(valve);
    }

    /**
     * get all cluster valves
     * @return current cluster valves
     */
    @Override
    public Valve[] getValves() {
        return valves.toArray(new Valve[valves.size()]);
    }

    /**
     * Get the cluster listeners associated with this cluster. If this Array has
     * no listeners registered, a zero-length array is returned.
     * @return the listener array
     */
    public ClusterListener[] findClusterListeners() {
        if (clusterListeners.size() > 0) {
            ClusterListener[] listener = new ClusterListener[clusterListeners.size()];
            clusterListeners.toArray(listener);
            return listener;
        } else
            return new ClusterListener[0];

    }

    /**
     * Add cluster message listener and register cluster to this listener.
     *
     * @param listener The new listener
     * @see org.apache.catalina.ha.CatalinaCluster#addClusterListener(org.apache.catalina.ha.ClusterListener)
     */
    @Override
    public void addClusterListener(ClusterListener listener) {
        if (listener != null && !clusterListeners.contains(listener)) {
            clusterListeners.add(listener);
            listener.setCluster(this);
        }
    }

    /**
     * Remove message listener and deregister Cluster from listener.
     *
     * @param listener The listener to remove
     * @see org.apache.catalina.ha.CatalinaCluster#removeClusterListener(org.apache.catalina.ha.ClusterListener)
     */
    @Override
    public void removeClusterListener(ClusterListener listener) {
        if (listener != null) {
            clusterListeners.remove(listener);
            listener.setCluster(null);
        }
    }

    /**
     * @return the current Deployer
     */
    @Override
    public ClusterDeployer getClusterDeployer() {
        return clusterDeployer;
    }

    /**
     * set a new Deployer, must be set before cluster started!
     * @param clusterDeployer The associated deployer
     */
    @Override
    public void setClusterDeployer(ClusterDeployer clusterDeployer) {
        this.clusterDeployer = clusterDeployer;
    }

    @Override
    public void setChannel(Channel channel) {
        this.channel = channel;
    }

    public void setManagerTemplate(ClusterManager managerTemplate) {
        this.managerTemplate = managerTemplate;
    }

    public void setChannelSendOptions(int channelSendOptions) {
        this.channelSendOptions = channelSendOptions;
    }

    /**
     * has members
     */
    protected boolean hasMembers = false;
    @Override
    public boolean hasMembers() {
        return hasMembers;
    }

    /**
     * Get all current cluster members
     * @return all members or empty array
     */
    @Override
    public Member[] getMembers() {
        return channel.getMembers();
    }

    /**
     * Return the member that represents this node.
     *
     * @return Member
     */
    @Override
    public Member getLocalMember() {
        return channel.getLocalMember(true);
    }

    // ------------------------------------------------------------- dynamic
    // manager property handling

    /**
     * JMX hack to direct use at jconsole
     *
     * @param name
     * @param value
     * @deprecated  Unused - will be removed in Tomcat 8.0.x
     */
    @Deprecated
    public boolean setProperty(String name, String value) {
        return setProperty(name, (Object) value);
    }

    /**
     * set config attributes with reflect and propagate to all managers
     *
     * @param name
     * @param value
     * @deprecated  Unused - will be removed in Tomcat 8.0.x
     */
    @Override
    @Deprecated
    public boolean setProperty(String name, Object value) {
        if (log.isTraceEnabled())
            log.trace(sm.getString("SimpleTcpCluster.setProperty", name, value,properties.get(name)));
        properties.put(name, value);
        //using a dynamic way of setting properties is nice, but a security risk
        //if exposed through JMX. This way you can sit and try to guess property names,
        //we will only allow explicit property names
        log.warn("Dynamic setProperty("+name+",value) has been disabled, please use explicit properties for the element you are trying to identify");
        return false;
    }

    /**
     * get current config
     *
     * @param key
     * @return The property
     *
     * @deprecated  Unused - will be removed in Tomcat 8.0.x
     */
    @Override
    @Deprecated
    public Object getProperty(String key) {
        if (log.isTraceEnabled())
            log.trace(sm.getString("SimpleTcpCluster.getProperty", key));
        return properties.get(key);
    }

    /**
     * Get all properties keys
     *
     * @return An iterator over the property names.
     * @deprecated  Unused - will be removed in Tomcat 8.0.x
     */
    @Override
    @Deprecated
    public Iterator<String> getPropertyNames() {
        return properties.keySet().iterator();
    }

    /**
     * remove a configured property.
     *
     * @param key
     * @deprecated  Unused - will be removed in Tomcat 8.0.x
     */
    @Override
    @Deprecated
    public void removeProperty(String key) {
        properties.remove(key);
    }

    /**
     * transfer properties from cluster configuration to subelement bean.
     * @param prefix
     * @param bean
     * @deprecated  Unused - will be removed in Tomcat 8.0.x
     */
    @Deprecated
    protected void transferProperty(String prefix, Object bean) {
        if (prefix != null) {
            for (Iterator<String> iter = getPropertyNames(); iter.hasNext();) {
                String pkey = iter.next();
                if (pkey.startsWith(prefix)) {
                    String key = pkey.substring(prefix.length() + 1);
                    Object value = getProperty(pkey);
                    IntrospectionUtils.setProperty(bean, key, value.toString());
                }
            }
        }
    }

    // --------------------------------------------------------- Public Methods

    /**
     * @return Returns the managers.
     */
    @Override
    public Map<String, ClusterManager> getManagers() {
        return managers;
    }

    @Override
    public Channel getChannel() {
        return channel;
    }

    public ClusterManager getManagerTemplate() {
        return managerTemplate;
    }

    public int getChannelSendOptions() {
        return channelSendOptions;
    }

    /**
     * Create new Manager without add to cluster (comes with start the manager)
     *
     * @param name
     *            Context Name of this manager
     * @see org.apache.catalina.Cluster#createManager(java.lang.String)
     * @see DeltaManager#start()
     */
    @Override
    public synchronized Manager createManager(String name) {
        if (log.isDebugEnabled()) {
            log.debug("Creating ClusterManager for context " + name +
                    " using class " + getManagerTemplate().getClass().getName());
        }
        ClusterManager manager = null;
        try {
            manager = managerTemplate.cloneFromTemplate();
            manager.setName(name);
        } catch (Exception x) {
            log.error("Unable to clone cluster manager, defaulting to org.apache.catalina.ha.session.DeltaManager", x);
            manager = new org.apache.catalina.ha.session.DeltaManager();
        } finally {
            if ( manager != null) manager.setCluster(this);
        }
        return manager;
    }

    @Override
    public void registerManager(Manager manager) {

        if (! (manager instanceof ClusterManager)) {
            log.warn("Manager [ " + manager + "] does not implement ClusterManager, addition to cluster has been aborted.");
            return;
        }
        ClusterManager cmanager = (ClusterManager) manager;
        // Notify our interested LifecycleListeners
        fireLifecycleEvent(BEFORE_MANAGERREGISTER_EVENT, manager);
        String clusterName = getManagerName(cmanager.getName(), manager);
        cmanager.setName(clusterName);
        cmanager.setCluster(this);

        managers.put(clusterName, cmanager);
        // Notify our interested LifecycleListeners
        fireLifecycleEvent(AFTER_MANAGERREGISTER_EVENT, manager);
    }

    /**
     * Remove an application from cluster replication bus.
     *
     * @param manager The manager
     * @see org.apache.catalina.Cluster#removeManager(Manager)
     */
    @Override
    public void removeManager(Manager manager) {
        if (manager != null && manager instanceof ClusterManager ) {
            ClusterManager cmgr = (ClusterManager) manager;
            // Notify our interested LifecycleListeners
            fireLifecycleEvent(BEFORE_MANAGERUNREGISTER_EVENT,manager);
            managers.remove(getManagerName(cmgr.getName(),manager));
            cmgr.setCluster(null);
            // Notify our interested LifecycleListeners
            fireLifecycleEvent(AFTER_MANAGERUNREGISTER_EVENT, manager);
        }
    }

    @Override
    public String getManagerName(String name, Manager manager) {
        String clusterName = name ;
        if (clusterName == null) clusterName = manager.getContainer().getName();
        if (getContainer() instanceof Engine) {
            Context context = (Context) manager.getContainer() ;
            Container host = context.getParent();
            if (host instanceof Host && clusterName != null &&
                    !(clusterName.startsWith(host.getName() +"#"))) {
                clusterName = host.getName() +"#" + clusterName ;
            }
        }
        return clusterName;
    }

    @Override
    public Manager getManager(String name) {
        return managers.get(name);
    }

    // ------------------------------------------------------ Lifecycle Methods

    /**
     * Execute a periodic task, such as reloading, etc. This method will be
     * invoked inside the classloading context of this container. Unexpected
     * throwables will be caught and logged.
     * @see org.apache.catalina.ha.deploy.FarmWarDeployer#backgroundProcess()
     * @see org.apache.catalina.tribes.group.GroupChannel#heartbeat()
     * @see org.apache.catalina.tribes.group.GroupChannel.HeartbeatThread#run()
     *
     */
    @Override
    public void backgroundProcess() {
        if (clusterDeployer != null) clusterDeployer.backgroundProcess();

        //send a heartbeat through the channel
        if ( isHeartbeatBackgroundEnabled() && channel !=null ) channel.heartbeat();

        // periodic event
        fireLifecycleEvent(Lifecycle.PERIODIC_EVENT, null);
    }


    /**
     * Use as base to handle start/stop/periodic Events from host. Currently
     * only log the messages as trace level.
     *
     * @see org.apache.catalina.LifecycleListener#lifecycleEvent(org.apache.catalina.LifecycleEvent)
     * @deprecated  Unused - will be removed in Tomcat 8.0.x
     */
    @Override
    @Deprecated
    public void lifecycleEvent(LifecycleEvent lifecycleEvent) {
        if (log.isTraceEnabled())
            log.trace(sm.getString("SimpleTcpCluster.event.log", lifecycleEvent.getType(), lifecycleEvent.getData()));
    }


    // ------------------------------------------------------ public

    @SuppressWarnings("deprecation")
    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
        if (clusterDeployer != null) {
            StringBuilder name = new StringBuilder("type=Cluster");
            Container container = getContainer();
            name.append(MBeanUtils.getContainerKeyProperties(container));
            name.append(",component=Deployer");
            onameClusterDeployer = register(clusterDeployer, name.toString());
        }
    }


    /**
     * Start Cluster and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        if (log.isInfoEnabled()) log.info("Cluster is about to start");

        try {
            checkDefaults();
            registerClusterValve();
            channel.addMembershipListener(this);
            channel.addChannelListener(this);
            if (channel instanceof GroupChannel)
                ((GroupChannel)channel).setName(getClusterName() + "-Channel");
            channel.start(channelStartOptions);
            if (clusterDeployer != null) clusterDeployer.start();
            registerMember(channel.getLocalMember(false));
        } catch (Exception x) {
            log.error("Unable to start cluster.", x);
            throw new LifecycleException(x);
        }

        setState(LifecycleState.STARTING);
    }

    protected void checkDefaults() {
        if ( clusterListeners.size() == 0 ) {
            addClusterListener(new JvmRouteSessionIDBinderListener());
            if (managerTemplate instanceof DeltaManager) {
                addClusterListener(new ClusterSessionListener());
            }
        }
        if ( valves.size() == 0 ) {
            addValve(new JvmRouteBinderValve());
            addValve(new ReplicationValve());
        }
        if ( clusterDeployer != null ) clusterDeployer.setCluster(this);
        if ( channel == null ) channel = new GroupChannel();
        if ( channel instanceof GroupChannel && !((GroupChannel)channel).getInterceptors().hasNext()) {
            channel.addInterceptor(new MessageDispatch15Interceptor());
            channel.addInterceptor(new TcpFailureDetector());
        }
        if (heartbeatBackgroundEnabled) channel.setHeartbeat(false);
    }

    /**
     * register all cluster valve to host or engine
     */
    protected void registerClusterValve() {
        if(container != null ) {
            for (Iterator<Valve> iter = valves.iterator(); iter.hasNext();) {
                ClusterValve valve = (ClusterValve) iter.next();
                if (log.isDebugEnabled())
                    log.debug("Invoking addValve on " + getContainer()
                            + " with class=" + valve.getClass().getName());
                if (valve != null) {
                    container.getPipeline().addValve(valve);
                    valve.setCluster(this);
                }
            }
        }
    }

    /**
     * unregister all cluster valve to host or engine
     */
    protected void unregisterClusterValve() {
        for (Iterator<Valve> iter = valves.iterator(); iter.hasNext();) {
            ClusterValve valve = (ClusterValve) iter.next();
            if (log.isDebugEnabled())
                log.debug("Invoking removeValve on " + getContainer()
                        + " with class=" + valve.getClass().getName());
            if (valve != null) {
                container.getPipeline().removeValve(valve);
                valve.setCluster(null);
            }
        }
    }


    /**
     * Stop Cluster and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);

        unregisterMember(channel.getLocalMember(false));
        if (clusterDeployer != null) clusterDeployer.stop();
        this.managers.clear();
        try {
            if ( clusterDeployer != null ) clusterDeployer.setCluster(null);
            channel.stop(channelStartOptions);
            channel.removeChannelListener(this);
            channel.removeMembershipListener(this);
            this.unregisterClusterValve();
        } catch (Exception x) {
            log.error("Unable to stop cluster.", x);
        }
    }


    @Override
    protected void destroyInternal() throws LifecycleException {
        if (onameClusterDeployer != null) {
            unregister(onameClusterDeployer);
            onameClusterDeployer = null;
        }
        super.destroyInternal();
    }


    /**
     * Return a String rendering of this object.
     */
    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder(this.getClass().getName());
        sb.append('[');
        if (container == null) {
            sb.append("Container is null");
        } else {
            sb.append(container.getName());
        }
        sb.append(']');
        return sb.toString();
    }


    /**
     * send message to all cluster members
     * @param msg message to transfer
     *
     * @see org.apache.catalina.ha.CatalinaCluster#send(org.apache.catalina.ha.ClusterMessage)
     */
    @Override
    public void send(ClusterMessage msg) {
        send(msg, null);
    }

    /**
     * send a cluster message to one member
     *
     * @param msg message to transfer
     * @param dest Receiver member
     * @see org.apache.catalina.ha.CatalinaCluster#send(org.apache.catalina.ha.ClusterMessage,
     *      org.apache.catalina.tribes.Member)
     */
    @Override
    public void send(ClusterMessage msg, Member dest) {
        try {
            msg.setAddress(getLocalMember());
            int sendOptions = channelSendOptions;
            if (msg instanceof SessionMessage
                    && ((SessionMessage)msg).getEventType() == SessionMessage.EVT_ALL_SESSION_DATA) {
                sendOptions = Channel.SEND_OPTIONS_SYNCHRONIZED_ACK|Channel.SEND_OPTIONS_USE_ACK;
            }
            if (dest != null) {
                if (!getLocalMember().equals(dest)) {
                    channel.send(new Member[] {dest}, msg, sendOptions);
                } else
                    log.error("Unable to send message to local member " + msg);
            } else {
                Member[] destmembers = channel.getMembers();
                if (destmembers.length>0)
                    channel.send(destmembers,msg, sendOptions);
                else if (log.isDebugEnabled())
                    log.debug("No members in cluster, ignoring message:"+msg);
            }
        } catch (Exception x) {
            log.error("Unable to send message through cluster sender.", x);
        }
    }

    /**
     * New cluster member is registered
     *
     * @see org.apache.catalina.tribes.MembershipListener#memberAdded(org.apache.catalina.tribes.Member)
     */
    @Override
    public void memberAdded(Member member) {
        try {
            hasMembers = channel.hasMembers();
            if (log.isInfoEnabled()) log.info("Replication member added:" + member);
            // Notify our interested LifecycleListeners
            fireLifecycleEvent(BEFORE_MEMBERREGISTER_EVENT, member);

            registerMember(member);

            // Notify our interested LifecycleListeners
            fireLifecycleEvent(AFTER_MEMBERREGISTER_EVENT, member);
        } catch (Exception x) {
            log.error("Unable to connect to replication system.", x);
        }

    }

    /**
     * Cluster member is gone
     *
     * @see org.apache.catalina.tribes.MembershipListener#memberDisappeared(org.apache.catalina.tribes.Member)
     */
    @Override
    public void memberDisappeared(Member member) {
        try {
            hasMembers = channel.hasMembers();
            if (log.isInfoEnabled()) log.info("Received member disappeared:" + member);
            // Notify our interested LifecycleListeners
            fireLifecycleEvent(BEFORE_MEMBERUNREGISTER_EVENT, member);

            unregisterMember(member);

            // Notify our interested LifecycleListeners
            fireLifecycleEvent(AFTER_MEMBERUNREGISTER_EVENT, member);
        } catch (Exception x) {
            log.error("Unable remove cluster node from replication system.", x);
        }
    }

    // --------------------------------------------------------- receiver
    // messages

    /**
     * notify all listeners from receiving a new message is not ClusterMessage
     * emit Failure Event to LifecycleListener
     *
     * @param msg
     *            received Message
     */
    @Override
    public boolean accept(Serializable msg, Member sender) {
        return (msg instanceof ClusterMessage);
    }


    @Override
    public void messageReceived(Serializable message, Member sender) {
        ClusterMessage fwd = (ClusterMessage)message;
        fwd.setAddress(sender);
        messageReceived(fwd);
    }

    public void messageReceived(ClusterMessage message) {

        if (log.isDebugEnabled() && message != null)
            log.debug("Assuming clocks are synched: Replication for "
                    + message.getUniqueId() + " took="
                    + (System.currentTimeMillis() - (message).getTimestamp())
                    + " ms.");

        //invoke all the listeners
        boolean accepted = false;
        if (message != null) {
            for (Iterator<ClusterListener> iter = clusterListeners.iterator();
                    iter.hasNext();) {
                ClusterListener listener = iter.next();
                if (listener.accept(message)) {
                    accepted = true;
                    listener.messageReceived(message);
                }
            }
            if (!accepted && notifyLifecycleListenerOnFailure) {
                Member dest = message.getAddress();
                // Notify our interested LifecycleListeners
                fireLifecycleEvent(RECEIVE_MESSAGE_FAILURE_EVENT,
                        new SendMessageData(message, dest, null));
                if (log.isDebugEnabled()) {
                    log.debug("Message " + message.toString() + " from type "
                            + message.getClass().getName()
                            + " transferred but no listener registered");
                }
            }
        }
    }

    // --------------------------------------------------------- Logger

    @Override
    public Log getLogger() {
        return log;
    }


    // ------------------------------------------------------------- deprecated

    /**
     *
     * @see org.apache.catalina.Cluster#setProtocol(java.lang.String)
     */
    @Override
    public void setProtocol(String protocol) {
        // NO-OP
    }

    /**
     * @see org.apache.catalina.Cluster#getProtocol()
     */
    @Override
    public String getProtocol() {
        return null;
    }

    public int getChannelStartOptions() {
        return channelStartOptions;
    }

    public void setChannelStartOptions(int channelStartOptions) {
        this.channelStartOptions = channelStartOptions;
    }


    // --------------------------------------------------------------------- JMX

    @SuppressWarnings("deprecation")
    @Override
    protected String getDomainInternal() {
        Container container = getContainer();
        if (container == null) {
            return null;
        }
        return MBeanUtils.getDomain(container);
    }

    @SuppressWarnings("deprecation")
    @Override
    protected String getObjectNameKeyProperties() {
        StringBuilder name = new StringBuilder("type=Cluster");

        Container container = getContainer();
        if (container != null) {
            name.append(MBeanUtils.getContainerKeyProperties(container));
        }

        return name.toString();
    }

    @SuppressWarnings("deprecation")
    private void registerMember(Member member) {
        // JMX registration
        StringBuilder name = new StringBuilder("type=Cluster");
        Container container = getContainer();
        if (container != null) {
            name.append(MBeanUtils.getContainerKeyProperties(container));
        }
        name.append(",component=Member,name=");
        name.append(ObjectName.quote(member.getName()));

        ObjectName oname = register(member, name.toString());
        memberOnameMap.put(member, oname);
    }

    private void unregisterMember(Member member) {
        if (member == null) return;
        ObjectName oname = memberOnameMap.remove(member);
        if (oname != null) {
            unregister(oname);
        }
    }
}
