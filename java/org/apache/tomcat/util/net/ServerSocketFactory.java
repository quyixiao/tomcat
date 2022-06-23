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
package org.apache.tomcat.util.net;

import java.io.IOException;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

/**
 * The common interface through which the {@link JIoEndpoint} interacts with
 * both non-SSL and SSL sockets.
 *
 *
 * 接收器Acceptor 在接收连接的过程中， 根据不同的使用场合可能需要不同的安全级别，例如安全性较高的场景需要对消息加密后传输，而在另外一些
 * 安全性要求较低的场合则无须对消息加密 ， 反映到应用层则使用HTTP 与HTTPS 的问题。
 *
 * 图6.6 为HTTPS 的组成层次图， 它在应用层添加了一个SSL/TLS协议，于是组成了HTTPS ，简单的来说， SSL/TLS 协议为了通信提供了以下服务 。
 * 1. 提供验证服务，验证会话内实体身份的合法性。
 * 2. 提供了加密服务，强加密机制能保证通信过程中的消息不会被破译 。
 * 3. 提供了防篡改服务，利用Hash算法对消息进行签名，通过验证签名保证通信内容不被篡改。
 *
 * Java 为开发者提供了方便手段实现SSL/TLS协议，即安全套接字，它是套接字的安全版本，Tomcat 作为Web 服务器必须满足不同的安全级别的通道，
 * HTTP 使用了套接字，而HTTPS 则使用了SSLSocket，由于接收终端根据不同的安全配置需要产生不同的类别的套接字，于是引入了工厂模式处理的
 * 套接字，即是ServerSocketFactory工厂类， 另外，不同的厂商可以自己定义SSL 的实现。
 *
 * ServerSocketFactory 是Tomcat接收端的重要组件，先看看它的运行逻辑，Tomcat 中有两个工厂类DefaultServerSocketFactory和JSSESocketFactory
 *  它们都实现了ServerSocketFactory接口，分别对应的HTTP套接字通道与HTTPS 套接字通道，假如机器的某商品使用了加密通道，则由JSSSocketFactory
 *  作为套接字工厂，反之，则使用DefaultServerSocketFactory 作为套接字工厂，于是Tomcat 中存在一个变量SSLEnabled 用于标识是否使用加密
 *  通道，则由JSSESocketFactory作为套接字工厂，反之则使用DefaultServerSocketFactory 作为套接字工厂，于是Tomcat 中存在一个变量
 *  SSLEnabled 用于标识是否使用加密通道， 通过此变量的定义就可以决定哪个工厂，Tomcat 提供了自问配置文件供用户自定义 。
 *
 *
 *
 */
public interface ServerSocketFactory {

    /**
     * Returns a server socket which uses all network interfaces on the host,
     * and is bound to a the specified port. The socket is configured with the
     * socket options (such as accept timeout) given to this factory.
     *
     * @param port
     *            the port to listen to
     * @exception IOException
     *                for networking errors
     * @exception InstantiationException
     *                for construction errors
     */
    ServerSocket createSocket(int port) throws IOException,
            InstantiationException;

    /**
     * Returns a server socket which uses all network interfaces on the host, is
     * bound to a the specified port, and uses the specified connection backlog.
     * The socket is configured with the socket options (such as accept timeout)
     * given to this factory.
     *
     * @param port
     *            the port to listen to
     * @param backlog
     *            how many connections are queued
     * @exception IOException
     *                for networking errors
     * @exception InstantiationException
     *                for construction errors
     */
    ServerSocket createSocket(int port, int backlog) throws IOException,
            InstantiationException;

    /**
     * Returns a server socket which uses only the specified network interface
     * on the local host, is bound to a the specified port, and uses the
     * specified connection backlog. The socket is configured with the socket
     * options (such as accept timeout) given to this factory.
     *
     * @param port
     *            the port to listen to
     * @param backlog
     *            how many connections are queued
     * @param ifAddress
     *            the network interface address to use
     * @exception IOException
     *                for networking errors
     * @exception InstantiationException
     *                for construction errors
     */
    ServerSocket createSocket(int port, int backlog, InetAddress ifAddress)
            throws IOException, InstantiationException;

    /**
     * Wrapper function for accept(). This allows us to trap and translate
     * exceptions if necessary.
     *
     * @exception IOException
     */
    Socket acceptSocket(ServerSocket socket) throws IOException;

    /**
     * Triggers the SSL handshake. This will be a no-op for non-SSL sockets.
     *
     * @exception IOException
     */
    void handshake(Socket sock) throws IOException;
}
