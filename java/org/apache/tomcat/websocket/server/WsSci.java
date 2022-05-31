/*
 *  Licensed to the Apache Software Foundation (ASF) under one or more
 *  contributor license agreements.  See the NOTICE file distributed with
 *  this work for additional information regarding copyright ownership.
 *  The ASF licenses this file to You under the Apache License, Version 2.0
 *  (the "License"); you may not use this file except in compliance with
 *  the License.  You may obtain a copy of the License at
 *
 *      http://www.apache.org/licenses/LICENSE-2.0
 *
 *  Unless required by applicable law or agreed to in writing, software
 *  distributed under the License is distributed on an "AS IS" BASIS,
 *  WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 *  See the License for the specific language governing permissions and
 *  limitations under the License.
 */
package org.apache.tomcat.websocket.server;

import java.lang.reflect.InvocationTargetException;
import java.lang.reflect.Modifier;
import java.util.HashSet;
import java.util.Set;

import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;
import javax.websocket.ContainerProvider;
import javax.websocket.DeploymentException;
import javax.websocket.Endpoint;
import javax.websocket.server.ServerApplicationConfig;
import javax.websocket.server.ServerEndpoint;
import javax.websocket.server.ServerEndpointConfig;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.res.StringManager;

/**
 * Registers an interest in any class that is annotated with
 * {@link ServerEndpoint} so that Endpoint can be published via the WebSocket
 * server.
 * 一般伴随着Service 或Filters等，Servlet 规范中通过ServletContainerInitializer实现此功能，每个框架要使用ServletContainerInitializer
 * 就必须在对应的Jar 包的META-INF/services 目录中创建一个名为javax.servlet.ServletContainerInitializer的文件，文件内容指定具体的ServletContainerInitializer
 * 实现类，于是当Web 容器启动时，就会运行这个初始化器做一些组件内的初始化工作 。
 *
 * 一般伴随着ServletContainerInitializer 一起使用的还有HandlesTypes 注解，通过HandleTypes 可以将感兴趣的类注入到ServletContainerInitializer
 *  的onStartup 方法中作为参数传入
 *
 *  Tomcat 容器的ServletContainerInitializer 机制，主要交由Context 容器和ContextConfig 监听器共同实现，ContextConfig 监听器首先
 *  负责在容器启动时读取每个Web 应用  的WEB-INF/lib 目录下包含的jar 包的META-INF/serivces/javax.servlet.ServletContainerInitializer
 *  以及Web 根目录下的META-INF/services/javax.servlet.ServerletContainerInitializer，通过反射完成这些ServletContainerInitializer
 *  的onStartup 方法，并将感兴趣的类作为参数传入
 *
 */
@HandlesTypes({ServerEndpoint.class, ServerApplicationConfig.class,
        Endpoint.class})
public class WsSci implements ServletContainerInitializer {

    private static boolean logMessageWritten = false;

    private final Log log = LogFactory.getLog(WsSci.class); // must not be static
    private static final StringManager sm = StringManager.getManager(WsSci.class);

    @Override
    public void onStartup(Set<Class<?>> clazzes, ServletContext ctx)
            throws ServletException {

        if (!isJava7OrLater()) {
            // The WebSocket implementation requires Java 7 so don't initialise
            // it if Java 7 is not available.
            if (!logMessageWritten) {
                logMessageWritten = true;
                log.info(sm.getString("sci.noWebSocketSupport"));
            }
            return;
        }

        WsServerContainer sc = init(ctx, true);

        if (clazzes == null || clazzes.size() == 0) {
            return;
        }

        // Group the discovered classes by type
        Set<ServerApplicationConfig> serverApplicationConfigs = new HashSet<ServerApplicationConfig>();
        Set<Class<? extends Endpoint>> scannedEndpointClazzes = new HashSet<Class<? extends Endpoint>>();
        Set<Class<?>> scannedPojoEndpoints = new HashSet<Class<?>>();

        try {
            // wsPackage is "javax.websocket."
            String wsPackage = ContainerProvider.class.getName();
            wsPackage = wsPackage.substring(0, wsPackage.lastIndexOf('.') + 1);
            for (Class<?> clazz : clazzes) {
                int modifiers = clazz.getModifiers();
                if (!Modifier.isPublic(modifiers) ||
                        Modifier.isAbstract(modifiers)) {
                    // Non-public or abstract - skip it.
                    continue;
                }
                // Protect against scanning the WebSocket API JARs
                if (clazz.getName().startsWith(wsPackage)) {
                    continue;
                }
                if (ServerApplicationConfig.class.isAssignableFrom(clazz)) {
                    serverApplicationConfigs.add(
                            (ServerApplicationConfig) clazz.getConstructor().newInstance());
                }
                if (Endpoint.class.isAssignableFrom(clazz)) {
                    @SuppressWarnings("unchecked")
                    Class<? extends Endpoint> endpoint =
                            (Class<? extends Endpoint>) clazz;
                    scannedEndpointClazzes.add(endpoint);
                }
                if (clazz.isAnnotationPresent(ServerEndpoint.class)) {
                    scannedPojoEndpoints.add(clazz);
                }
            }
        } catch (InstantiationException e) {
            throw new ServletException(e);
        } catch (IllegalArgumentException e) {
            throw new ServletException(e);
        } catch (SecurityException e) {
            throw new ServletException(e);
        } catch (IllegalAccessException e) {
            throw new ServletException(e);
        } catch (InvocationTargetException e) {
            throw new ServletException(e);
        } catch (NoSuchMethodException e) {
            throw new ServletException(e);
        }

        // Filter the results
        Set<ServerEndpointConfig> filteredEndpointConfigs = new HashSet<ServerEndpointConfig>();
        Set<Class<?>> filteredPojoEndpoints = new HashSet<Class<?>>();

        if (serverApplicationConfigs.isEmpty()) {
            filteredPojoEndpoints.addAll(scannedPojoEndpoints);
        } else {
            for (ServerApplicationConfig config : serverApplicationConfigs) {
                Set<ServerEndpointConfig> configFilteredEndpoints =
                        config.getEndpointConfigs(scannedEndpointClazzes);
                if (configFilteredEndpoints != null) {
                    filteredEndpointConfigs.addAll(configFilteredEndpoints);
                }
                Set<Class<?>> configFilteredPojos =
                        config.getAnnotatedEndpointClasses(
                                scannedPojoEndpoints);
                if (configFilteredPojos != null) {
                    filteredPojoEndpoints.addAll(configFilteredPojos);
                }
            }
        }

        try {
            // Deploy endpoints
            for (ServerEndpointConfig config : filteredEndpointConfigs) {
                sc.addEndpoint(config);
            }
            // Deploy POJOs
            for (Class<?> clazz : filteredPojoEndpoints) {
                sc.addEndpoint(clazz, true);
            }
        } catch (DeploymentException e) {
            throw new ServletException(e);
        }
    }


    static WsServerContainer init(ServletContext servletContext,
            boolean initBySciMechanism) {

        WsServerContainer sc = new WsServerContainer(servletContext);

        servletContext.setAttribute(
                Constants.SERVER_CONTAINER_SERVLET_CONTEXT_ATTRIBUTE, sc);

        servletContext.addListener(new WsSessionListener(sc));
        // Can't register the ContextListener again if the ContextListener is
        // calling this method
        if (initBySciMechanism) {
            servletContext.addListener(new WsContextListener());
        }

        return sc;
    }


    private static boolean isJava7OrLater() {
        try {
            Class.forName("java.nio.channels.AsynchronousSocketChannel");
        } catch (ClassNotFoundException e) {
            return false;
        }
        return true;
    }
}
