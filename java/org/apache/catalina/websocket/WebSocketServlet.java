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
package org.apache.catalina.websocket;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.List;
import java.util.Queue;
import java.util.concurrent.ConcurrentLinkedQueue;

import javax.servlet.ServletException;
import javax.servlet.ServletRequest;
import javax.servlet.ServletRequestWrapper;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.RequestFacade;
import org.apache.tomcat.util.buf.B2CConverter;
import org.apache.tomcat.util.codec.binary.Base64;
import org.apache.tomcat.util.res.StringManager;

/**
 * Provides the base implementation of a Servlet for processing WebSocket
 * connections as per RFC6455. It is expected that applications will extend this
 * implementation and provide application specific functionality.
 *
 * @deprecated  Replaced by the JSR356 WebSocket 1.1 implementation and will be
 *              removed in Tomcat 8.0.x.
 * WebSocket协议属于Html5 标准，越来越多的浏览器已经原生的支持WebSocket ，它能让客户端和服务器端实现双向通信，在客户端和服务器端口
 * 建立一条WebSocket  连接后，服务器端消息可直接发送到客户端，从而打破传统的请求响应模式，避免了无意义的请求，比如，传统的方式可能会使用
 * AJAX 不断的请求服务器端，而WebSocket则可以直接发送数据到客户端且客户端不必请求，同时由于有了浏览器的原生支持，编写客户端的应用程序
 * 变更加的便捷，且不必依赖于第三方插件，另外，WebSocket 协议要摒弃HTTP 协议烦琐的请求头部，而是以数据帧的方式进行传输，效率会更高。
 *
 * WebSocket协议通信的过程，首先，客户端会发送一个握手包告诉服务器它想升级成WebSocket ，不知道服务器是否同意 ， 这时，如果服务器支持
 * WebSocket 协议，则会返回一个握手包告诉客户端没有问题，升级已经确认，然后就成功的建立起一条WebSocket 连接，连接支持双向通信，并且使用
 * WebSocket 协议的数据帧格式发送消息。
 *
 * 握手过程需要说明 ，为了让WebSocket 协议能和现在的Http 协议的Web 架构互相兼容，WebSocket 协议的握手要基于HTTP协议，比如客户端会发送
 * 如下 的HTTP 报文到服务器，请求升级为WebSocket 协议，其中包含了Upgrade:websocket 就是告诉客户端想升级协议
 *
 * GET ws://localhost:8080/hello HTTP/1.1
 * Origin : http://localhost:8080
 * Connection : Upgrade
 * Sec-WebSocket-Key :uRovscZjNo1/umbTt5ukMw==
 *      Upgrade :websocket
 * Sec-WebSocket-Version: 13
 *
 * 此时，如果服务器端支持WebSocket 协议，则它会发送一个同意客户端升级协议的报文，具体的报文类似如下 ， 其中 Upgrade:websocket 就告诉客户端服务器
 * 同意客户端的升级协议 。
 *
 * HTTP/1.1 101  WebSocket Protocol Handshake
 * Date : Fri , 10 Feb 2016 17:38:18 GMT
 * Connection : Upgrade
 *      Upgrade : WebSocket
 * Sec-WebSocket-Accept : rLHCkw/SKsO9GAH/ZSFhBATDKrU==
 *
 * 完成以上握手后，HTTP 协议连接就被打破，接下来，则开始使用WebSocket协议进行双方通信，这条连接还是原来的那条TCP/IP 连接，端口也还是
 * 原来的80和443 。
 *
 * 这个Servlet 必须要继承WebSocketServlet ，接着创建一个继承MessageInBound 的WebSocketMessageInBound 类，该类中填充了onClose
 * onOpen ，onBinaryMessage 和onTextMessage 等方法即可完成各个事件的逻辑，其中，onOpen 会在一个WebSocket 连接建立时调用，onClose
 * 会在一个WebSocket 关闭时调用，onBinaryMessage 则在Binary 方式下接收到客户端数据时调用，onTextMessage 则在Text 方式下接收到客户端数据时
 * 调用，上面一段代码实现了一个广播的效果 。
 *
 * 按上面的处理逻辑，Tomcat 对WebSocket 的集成就不会太难了，就是处理请求时，如果遇到WebSocket 协议请求时，则做特殊的处理，保持住链接并
 * 在适当的时机调用WebSocketServlet 的MessageInBound 的onClose ,onOpen , onBinaryMessage和onTextMessage 等方法，因为WebSocket
 * 一般建立在NIO模式下使用，所以看看以NIO 模式集成的WebSocket 协议 。
 *
 * 如图10.10所示 ，WebSocket的客户端连接被接收器接收后注册到NioChannel 队列中，Poller 组件不断的轮询是否有NioChannel 需要处理，如果有，则
 * 经过处理管道后进入继承了WebSocketServlet 的Servlet 上，WebSocketServlet 的doGet 方法会处理WebSocket握手，告诉客户端同意升级协议，
 * 往后，Poller 继续不断轮询相关的NioChannel ，一旦发现使用了WebSocket 协议通道，则会调用MessageInBound 的相关方法，完成不同事件的处理。
 * 从而实现对WebSocket协议的支持。
 *
 *
 *
 */
@Deprecated
public abstract class WebSocketServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;
    private static final byte[] WS_ACCEPT =
            "258EAFA5-E914-47DA-95CA-C5AB0DC85B11".getBytes(
                    B2CConverter.ISO_8859_1);
    private static final StringManager sm =
            StringManager.getManager(Constants.Package);

    private final Queue<MessageDigest> sha1Helpers =
            new ConcurrentLinkedQueue<MessageDigest>();


    @Override
    protected void doGet(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        // Information required to send the server handshake message
        String key;
        String subProtocol = null;
        List<String> extensions = Collections.emptyList();

        if (!headerContainsToken(req, "upgrade", "websocket")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (!headerContainsToken(req, "connection", "upgrade")) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        if (!headerContainsToken(req, "sec-websocket-version", "13")) {
            resp.setStatus(426);
            resp.setHeader("Sec-WebSocket-Version", "13");
            return;
        }

        key = req.getHeader("Sec-WebSocket-Key");
        if (key == null) {
            resp.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return;
        }

        String origin = req.getHeader("Origin");
        if (!verifyOrigin(origin)) {
            resp.sendError(HttpServletResponse.SC_FORBIDDEN);
            return;
        }

        List<String> subProtocols = getTokensFromHeader(req,
                "Sec-WebSocket-Protocol");
        if (!subProtocols.isEmpty()) {
            subProtocol = selectSubProtocol(subProtocols);

        }

        // TODO Read client handshake - Sec-WebSocket-Extensions

        // TODO Extensions require the ability to specify something (API TBD)
        //      that can be passed to the Tomcat internals and process extension
        //      data present when the frame is fragmented.

        // If we got this far, all is good. Accept the connection.
        resp.setHeader("Upgrade", "websocket");
        resp.setHeader("Connection", "upgrade");
        resp.setHeader("Sec-WebSocket-Accept", getWebSocketAccept(key));
        if (subProtocol != null) {
            resp.setHeader("Sec-WebSocket-Protocol", subProtocol);
        }
        if (!extensions.isEmpty()) {
            // TODO
        }

        WsHttpServletRequestWrapper wrapper = new WsHttpServletRequestWrapper(req);
        StreamInbound inbound = createWebSocketInbound(subProtocol, wrapper);
        wrapper.invalidate();

        // Small hack until the Servlet API provides a way to do this.
        ServletRequest inner = req;
        // Unwrap the request
        while (inner instanceof ServletRequestWrapper) {
            inner = ((ServletRequestWrapper) inner).getRequest();
        }
        if (inner instanceof RequestFacade) {
            ((RequestFacade) inner).doUpgrade(inbound);
        } else {
            resp.sendError(HttpServletResponse.SC_INTERNAL_SERVER_ERROR,
                    sm.getString("servlet.reqUpgradeFail"));
        }
    }


    /*
     * This only works for tokens. Quoted strings need more sophisticated
     * parsing.
     */
    private boolean headerContainsToken(HttpServletRequest req,
            String headerName, String target) {
        Enumeration<String> headers = req.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            String[] tokens = header.split(",");
            for (String token : tokens) {
                if (target.equalsIgnoreCase(token.trim())) {
                    return true;
                }
            }
        }
        return false;
    }


    /*
     * This only works for tokens. Quoted strings need more sophisticated
     * parsing.
     */
    private List<String> getTokensFromHeader(HttpServletRequest req,
            String headerName) {
        List<String> result = new ArrayList<String>();

        Enumeration<String> headers = req.getHeaders(headerName);
        while (headers.hasMoreElements()) {
            String header = headers.nextElement();
            String[] tokens = header.split(",");
            for (String token : tokens) {
                result.add(token.trim());
            }
        }
        return result;
    }


    private String getWebSocketAccept(String key) throws ServletException {

        MessageDigest sha1Helper = sha1Helpers.poll();
        if (sha1Helper == null) {
            try {
                sha1Helper = MessageDigest.getInstance("SHA1");
            } catch (NoSuchAlgorithmException e) {
                throw new ServletException(e);
            }
        }

        sha1Helper.reset();
        sha1Helper.update(key.getBytes(B2CConverter.ISO_8859_1));
        String result = Base64.encodeBase64String(sha1Helper.digest(WS_ACCEPT));

        sha1Helpers.add(sha1Helper);

        return result;
    }


    /**
     * Intended to be overridden by sub-classes that wish to verify the origin
     * of a WebSocket request before processing it.
     *
     * @param origin    The value of the origin header from the request which
     *                  may be <code>null</code>
     *
     * @return  <code>true</code> to accept the request. <code>false</code> to
     *          reject it. This default implementation always returns
     *          <code>true</code>.
     */
    protected boolean verifyOrigin(String origin) {
        return true;
    }


    /**
     * Intended to be overridden by sub-classes that wish to select a
     * sub-protocol if the client provides a list of supported protocols.
     *
     * @param subProtocols  The list of sub-protocols supported by the client
     *                      in client preference order. The server is under no
     *                      obligation to respect the declared preference
     * @return  <code>null</code> if no sub-protocol is selected or the name of
     *          the protocol which <b>must</b> be one of the protocols listed by
     *          the client. This default implementation always returns
     *          <code>null</code>.
     */
    protected String selectSubProtocol(List<String> subProtocols) {
        return null;
    }


    /**
     * Create the instance that will process this inbound connection.
     * Applications must provide a new instance for each connection.
     *
     * @param subProtocol   The sub-protocol agreed between the client and
     *                      server or <code>null</code> if none was agreed
     * @param request       The HTTP request that initiated this WebSocket
     *                      connection. Note that this object is <b>only</b>
     *                      valid inside this method. You must not retain a
     *                      reference to it outside the execution of this
     *                      method. If Tomcat detects such access, it will throw
     *                      an IllegalStateException
     */
    protected abstract StreamInbound createWebSocketInbound(String subProtocol,
            HttpServletRequest request);
}
