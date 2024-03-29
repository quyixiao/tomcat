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
package org.apache.coyote;

import java.util.concurrent.Executor;


/**
 * Abstract the protocol implementation, including threading, etc.
 *
 * This is the main interface to be implemented by a coyote protocol.
 * Adapter is the main interface to be implemented by a coyote servlet
 * container.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * @see Adapter
 *
 *ProtocolHandler : Coyote协议接口，通过封装Endpoint和Processor，实现针对具体的协议的处理功能，Tomcat按照协议和I/O提供了6个实现类
 * Http11NioProtocol,Http11AprProtocol,Http11Nio2Protocol,AJPNioProtocol,AjpAprProtocol,AjpNio2Protocol,我们在$CATALINA_BASE
 * /conf/server.xml 中设置链接时，至少要指定具体的ProtocolHandler(当然，也可以指定协议名称，如"HTTP/1.1" ，如果服务器安装了APR ,
 * 那么将使用Http11AprProtocol，否则使用Http11NioProtocol,Tomcat7 以及之前的版本则会是Http11Protocol)。
 *
 *
 */
public interface ProtocolHandler {

    /**
     * Return the adapter associated with the protocol handler.
     * @return the adapter
     */
    public Adapter getAdapter();


    /**
     * The adapter, used to call the connector.
     *
     * @param adapter The adapter to associate
     */
    public void setAdapter(Adapter adapter);


    /**
     * The executor, provide access to the underlying thread pool.
     *
     * @return The executor used to process requests
     */
    public Executor getExecutor();


    /**
     * Initialise the protocol.
     *
     * @throws Exception If the protocol handler fails to initialise
     */
    public void init() throws Exception;


    /**
     * Start the protocol.
     *
     * @throws Exception If the protocol handler fails to start
     */
    public void start() throws Exception;


    /**
     * Pause the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to pause
     */
    public void pause() throws Exception;


    /**
     * Resume the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to resume
     */
    public void resume() throws Exception;


    /**
     * Stop the protocol.
     *
     * @throws Exception If the protocol handler fails to stop
     */
    public void stop() throws Exception;


    /**
     * Destroy the protocol (optional).
     *
     * @throws Exception If the protocol handler fails to destroy
     */
    public void destroy() throws Exception;


    /**
     * Requires APR/native library
     */
    public boolean isAprRequired();
}
