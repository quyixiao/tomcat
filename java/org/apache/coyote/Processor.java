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

import java.io.IOException;
import java.util.concurrent.Executor;

import org.apache.coyote.http11.upgrade.servlet31.HttpUpgradeHandler;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.net.SSLSupport;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Common interface for processors of all protocols.
 * Processor :Coyote协议处理接口，负责构建Request和Response对象，并通过Adapter将其提交到Catalina容器处理，对应用层抽象，Processor是
 * 单线程的，Tomcat在同一次链接中复用Processor，Tomcat按照协议的不同提供了3个实现类：Http11Processor(HTTP/1.1) ，AjpProcessor(AJP)
 * StreamProcessor(HTTP/2.0)，除此之外，它还提供了两个用于升级协议处理的实现，UpgradeProcessorInternal和UpgradeProcessorExternal
 * 前者用于处理内部支持的升级协议（如HTTP/2.0和WebSocket,至于UpgradeProcessorInternal是如何实现StreamProcessor配置完成HTTP/2.0 处理）
 * 后者用于处理外部扩展升级协议支持
 *
 */
public interface Processor<S> {
    Executor getExecutor();

    SocketState process(SocketWrapper<S> socketWrapper) throws IOException;

    SocketState event(SocketStatus status) throws IOException;

    SocketState asyncDispatch(SocketStatus status);
    SocketState asyncPostProcess();

    /**
     * @deprecated  Will be removed in Tomcat 8.0.x.
     */
    @Deprecated
    org.apache.coyote.http11.upgrade.UpgradeInbound getUpgradeInbound();
    /**
     * @deprecated  Will be removed in Tomcat 8.0.x.
     */
    @Deprecated
    SocketState upgradeDispatch() throws IOException;

    HttpUpgradeHandler getHttpUpgradeHandler();
    SocketState upgradeDispatch(SocketStatus status) throws IOException;

    void errorDispatch();

    boolean isComet();
    boolean isAsync();
    boolean isUpgrade();

    Request getRequest();

    void recycle(boolean socketClosing);

    void setSslSupport(SSLSupport sslSupport);

    AsyncStateMachine<S> getAsyncStateMachine();
}
