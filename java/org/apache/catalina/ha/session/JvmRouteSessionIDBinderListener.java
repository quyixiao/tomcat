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

package org.apache.catalina.ha.session;

import java.io.IOException;

import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Session;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.ha.ClusterListener;
import org.apache.catalina.ha.ClusterMessage;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Receive SessionID cluster change from other backup node after primary session
 * node is failed.
 *
 * @author Peter Rossbach
 * @deprecated Will be removed in Tomcat 8.0.x
 *
 *  集群监听器用于监听集群的消息， 一旦接收到集群其他实例发过来的消息，所有的集群监听器监听messageReceived 方法会被调用，默认情况下
 *  会有两个监听器在启动时加入到Cluster 中，它们分别是JvmRouteSessionIDBinderListener 和 ClusterSessionListener
 *
 *  JvmRouteSessionIDBinderListener 主要负责的工作是监听会话ID 的变更，在使用BackupManager 的情况下， 当某节点失效后，为了保证会话能被
 *  正确的找到而且更改会话的ID,更改后的会话ID会同步集群中的其他节点，这个修改会话的ID的工作就交给此监听器，它的逻辑相当简单，当获取了
 *  SessionIDMessage 类型的消息时， 通过原来的ID 从会话管理器中找到会话对象，然后再通过会话的setId 设置变更后的ID , 这个监听器
 *  的作用就是协助实现集群的故障转移机制 。
 *
 *
 *
 *
 */
@Deprecated
public class JvmRouteSessionIDBinderListener extends ClusterListener {

    private static final Log log =
        LogFactory.getLog(JvmRouteSessionIDBinderListener.class);

    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    /**
     * The descriptive information about this implementation.
     */
    protected static final String info =
        "org.apache.catalina.ha.session.JvmRouteSessionIDBinderListener/1.1";

    //--Instance Variables--------------------------------------


    protected boolean started = false;

    /**
     * number of session that goes to this cluster node
     */
    private long numberOfSessions = 0;

    //--Constructor---------------------------------------------

    public JvmRouteSessionIDBinderListener() {
        // NO-OP
    }

    //--Logic---------------------------------------------------

    /**
     * Return descriptive information about this implementation.
     */
    public String getInfo() {

        return (info);

    }

    /**
     * @return Returns the numberOfSessions.
     */
    public long getNumberOfSessions() {
        return numberOfSessions;
    }

    /**
     * Add this Mover as Cluster Listener ( receiver)
     *
     * @throws LifecycleException
     */
    public void start() throws LifecycleException {
        if (started)
            return;
        getCluster().addClusterListener(this);
        started = true;
        if (log.isInfoEnabled())
            log.info(sm.getString("jvmRoute.clusterListener.started"));
    }

    /**
     * Remove this from Cluster Listener
     *
     * @throws LifecycleException
     */
    public void stop() throws LifecycleException {
        started = false;
        getCluster().removeClusterListener(this);
        if (log.isInfoEnabled())
            log.info(sm.getString("jvmRoute.clusterListener.stopped"));
    }

    /**
     * Callback from the cluster, when a message is received, The cluster will
     * broadcast it invoking the messageReceived on the receiver.
     *
     * @param msg
     *            ClusterMessage - the message received from the cluster
     */
    @Override
    public void messageReceived(ClusterMessage msg) {
        if (msg instanceof SessionIDMessage) {
            SessionIDMessage sessionmsg = (SessionIDMessage) msg;
            if (log.isDebugEnabled())
                log.debug(sm.getString(
                        "jvmRoute.receiveMessage.sessionIDChanged", sessionmsg
                                .getOrignalSessionID(), sessionmsg
                                .getBackupSessionID(), sessionmsg
                                .getContextName()));
            Container container = getCluster().getContainer();
            Container host = null ;
            if(container instanceof Engine) {
                host = container.findChild(sessionmsg.getHost());
            } else {
                host = container ;
            }
            if (host != null) {
                Context context = (Context) host.findChild(sessionmsg
                        .getContextName());
                if (context != null) {
                    try {
                        Session session = context.getManager().findSession(
                                sessionmsg.getOrignalSessionID());
                        if (session != null) {
                            session.setId(sessionmsg.getBackupSessionID());
                        } else if (log.isInfoEnabled())
                            log.info(sm.getString("jvmRoute.lostSession",
                                    sessionmsg.getOrignalSessionID(),
                                    sessionmsg.getContextName()));
                    } catch (IOException e) {
                        log.error(e);
                    }

                } else if (log.isErrorEnabled())
                    log.error(sm.getString("jvmRoute.contextNotFound",
                            sessionmsg.getContextName(), ((StandardEngine) host
                                    .getParent()).getJvmRoute()));
            } else if (log.isErrorEnabled())
                log.error(sm.getString("jvmRoute.hostNotFound", sessionmsg.getContextName()));
        }
        return;
    }

    /**
     * Accept only SessionIDMessages
     *
     * @param msg
     *            ClusterMessage
     * @return boolean - returns true to indicate that messageReceived should be
     *         invoked. If false is returned, the messageReceived method will
     *         not be invoked.
     */
    @Override
    public boolean accept(ClusterMessage msg) {
        return (msg instanceof SessionIDMessage);
    }
}

