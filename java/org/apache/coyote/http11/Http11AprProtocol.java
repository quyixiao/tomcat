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
package org.apache.coyote.http11;

import java.io.IOException;

import org.apache.coyote.AbstractProtocol;
import org.apache.coyote.Processor;
import org.apache.coyote.http11.upgrade.AprProcessor;
import org.apache.coyote.http11.upgrade.servlet31.HttpUpgradeHandler;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.AprEndpoint;
import org.apache.tomcat.util.net.AprEndpoint.Handler;
import org.apache.tomcat.util.net.AprEndpoint.Poller;
import org.apache.tomcat.util.net.SocketStatus;
import org.apache.tomcat.util.net.SocketWrapper;


/**
 * Abstract the protocol implementation, including threading, etc.
 * Processor is single threaded and specific to stream-based protocols,
 * will not fit Jk protocols like JNI.
 *
 * @author Remy Maucherat
 * @author Costin Manolache
 * Http11AprProtocol 表示使用 APR 模式的HTTP协议通信，它包含了从套接字连接接收，处理请求，响应客户端的整个过程 ，APR 模式主要是指由
 * Native 库完成套接字的各种操作，APR 库提供了sendfile，epoll ，和OpenSSL 等I/O高级功能，Linux 和Windows 操作系统都有各自所实现库
 * ，Tomcat 中通过JNI 方式调用这些Native 库，Http11AprProtocol 组件主要包含AprEndPoint组件和Http11AprProcessor组件，启动时APrEndPoint
 * 组件将启动某些端口的监听，一个连接到来后可能会直接被线程处理，也可能被放到一个待轮询的队列里面由Poller 负责检测，如果该连接被检测到已经
 * 准备好，则将由线程池处理，处理过程中将通过协议解析器Http11AprProcessor 组件对HTTP 协议解析，通过适配器（Adapter）匹配到指定的容器
 * 进行处理并响应客户端，HTTP APR 模式协议的整体结构如图6.45 所示 。
 *
 *
 *
 * HTTP  Connector 所支持的协议版本HTTP/1.0和HTTP/1.0 无须显式配置HTTP 的版本，Connector 会自动适配版本，每个Connector实例对应一个
 * 商品，在同个Service 实例内可以配置若干Connector实例，商品必须不同，但协议可以相同，HTTP Connector 包含的协议处理组件有Http1Protocol
 * Java BIO 模式，Http11NioProtocol(Java NIO 模式) BIO 模式为org.apache.coyote.http11.Http11Protocol ，NIO 模式为
 * org.apache.coyote.http11NioProtocol ,APR/native模式为org.apache.coyote.http11.Http11AprProtocol 。
 *
 * AJP Connector组件用于支持AJP协议通信，当我们想将Web 应用中包含的静态内容交给Apache 处理时， Apache 与Tomcat 之间的通信则使用AJP
 * Protocol(Java BIO模式 )，AjpNioProtocol (Java NIO 模式)和AjpAprProtocol(APR/native模式) Tomcat 启动时根据server.xml 的
 * <Connector> 节点配置I/O模式， BIO 模式为org.apache.coyote.ajp.AjpProtocol，NIO 模式为org.apache.coyote.ajp.AjpNioProtocol，
 * APR/native模式为org.apache.coyote.ajp.AjpAprProtocol 。
 *
 * Connector 也在服务端提供了SSL 安全通道的支持，用于客户端以HTTPS 方式访问，可以通过配置server.xml的<Connector> 节点的SSLEnabled
 *
 * 在BIO 模式下，对于 每个客户端的请求连接都将消耗线程池里面的一条连接 ， 直到整个请求响应完毕，此时，如果有很多的请求几乎同时到达Connector
 * ，当线程池中的空闲线程用完后，则会创建新的线程，直到达到最大线程数， 但如果此时还有更多的请求到来， 虽然线程池已经处理不过来了，但操作
 * 系统还是会将客户端接收起来放到一个队列里，这个队列的大小通过SocketServer设置backlog 而来，如果还有再多的请求过来，队列已经超过了
 * SocketServer 的backlog 大小，那么直接被拒绝掉，客户端将收到 "connection refused" 报错。
 *
 * 在NIO 模式下，则是所有的客户端的请求连接先由一个接收线程接收，然后由若干（一般为CPU数）线程轮询读写事件，最后将具体的读写操作交由线程池
 * 处理， 可以看到，以这种方式，客户端连接不会在整个请求响应过程中占用连接池内的连接，它可以同时处理比BIO 模式多得多的客户端连接数，此种
 * 模式能承受更大的并发，机器资源使用效率高很多， 另外，APR/native 模式也是NIO模式，它直接用本地代码实现了NIO模式 。
 *
 *
 */
public class Http11AprProtocol extends AbstractHttp11Protocol<Long> {

    private static final Log log = LogFactory.getLog(Http11AprProtocol.class);


    @Override
    protected Log getLog() { return log; }


    @Override
    protected AbstractEndpoint.Handler getHandler() {
        return cHandler;
    }


    @Override
    public boolean isAprRequired() {
        // Override since this protocol implementation requires the APR/native
        // library
        return true;
    }


    public Http11AprProtocol() {
        endpoint = new AprEndpoint();
        cHandler = new Http11ConnectionHandler(this);
        ((AprEndpoint) endpoint).setHandler(cHandler);
        setSoLinger(Constants.DEFAULT_CONNECTION_LINGER);
        setSoTimeout(Constants.DEFAULT_CONNECTION_TIMEOUT);
        setTcpNoDelay(Constants.DEFAULT_TCP_NO_DELAY);
    }

    private final Http11ConnectionHandler cHandler;

    public boolean getUseSendfile() { return ((AprEndpoint)endpoint).getUseSendfile(); }
    public void setUseSendfile(boolean useSendfile) { ((AprEndpoint)endpoint).setUseSendfile(useSendfile); }

    public int getPollTime() { return ((AprEndpoint)endpoint).getPollTime(); }
    public void setPollTime(int pollTime) { ((AprEndpoint)endpoint).setPollTime(pollTime); }

    public void setPollerSize(int pollerSize) { endpoint.setMaxConnections(pollerSize); }
    public int getPollerSize() { return endpoint.getMaxConnections(); }

    public int getSendfileSize() { return ((AprEndpoint)endpoint).getSendfileSize(); }
    public void setSendfileSize(int sendfileSize) { ((AprEndpoint)endpoint).setSendfileSize(sendfileSize); }

    public void setSendfileThreadCount(int sendfileThreadCount) { ((AprEndpoint)endpoint).setSendfileThreadCount(sendfileThreadCount); }
    public int getSendfileThreadCount() { return ((AprEndpoint)endpoint).getSendfileThreadCount(); }

    public boolean getDeferAccept() { return ((AprEndpoint)endpoint).getDeferAccept(); }
    public void setDeferAccept(boolean deferAccept) { ((AprEndpoint)endpoint).setDeferAccept(deferAccept); }

    // --------------------  SSL related properties --------------------

    /**
     * SSL protocol.
     */
    public String getSSLProtocol() { return ((AprEndpoint)endpoint).getSSLProtocol(); }
    public void setSSLProtocol(String SSLProtocol) { ((AprEndpoint)endpoint).setSSLProtocol(SSLProtocol); }


    /**
     * SSL password (if a cert is encrypted, and no password has been provided, a callback
     * will ask for a password).
     */
    public String getSSLPassword() { return ((AprEndpoint)endpoint).getSSLPassword(); }
    public void setSSLPassword(String SSLPassword) { ((AprEndpoint)endpoint).setSSLPassword(SSLPassword); }


    /**
     * SSL cipher suite.
     */
    public String getSSLCipherSuite() { return ((AprEndpoint)endpoint).getSSLCipherSuite(); }
    public void setSSLCipherSuite(String SSLCipherSuite) { ((AprEndpoint)endpoint).setSSLCipherSuite(SSLCipherSuite); }


    /**
     * SSL honor cipher order.
     *
     * Set to <code>true</code> to enforce the <i>server's</i> cipher order
     * instead of the default which is to allow the client to choose a
     * preferred cipher.
     */
    public boolean getSSLHonorCipherOrder() { return ((AprEndpoint)endpoint).getSSLHonorCipherOrder(); }
    public void setSSLHonorCipherOrder(boolean SSLHonorCipherOrder) { ((AprEndpoint)endpoint).setSSLHonorCipherOrder(SSLHonorCipherOrder); }


    /**
     * SSL certificate file.
     */
    public String getSSLCertificateFile() { return ((AprEndpoint)endpoint).getSSLCertificateFile(); }
    public void setSSLCertificateFile(String SSLCertificateFile) { ((AprEndpoint)endpoint).setSSLCertificateFile(SSLCertificateFile); }


    /**
     * SSL certificate key file.
     */
    public String getSSLCertificateKeyFile() { return ((AprEndpoint)endpoint).getSSLCertificateKeyFile(); }
    public void setSSLCertificateKeyFile(String SSLCertificateKeyFile) { ((AprEndpoint)endpoint).setSSLCertificateKeyFile(SSLCertificateKeyFile); }


    /**
     * SSL certificate chain file.
     */
    public String getSSLCertificateChainFile() { return ((AprEndpoint)endpoint).getSSLCertificateChainFile(); }
    public void setSSLCertificateChainFile(String SSLCertificateChainFile) { ((AprEndpoint)endpoint).setSSLCertificateChainFile(SSLCertificateChainFile); }


    /**
     * SSL CA certificate path.
     */
    public String getSSLCACertificatePath() { return ((AprEndpoint)endpoint).getSSLCACertificatePath(); }
    public void setSSLCACertificatePath(String SSLCACertificatePath) { ((AprEndpoint)endpoint).setSSLCACertificatePath(SSLCACertificatePath); }


    /**
     * SSL CA certificate file.
     */
    public String getSSLCACertificateFile() { return ((AprEndpoint)endpoint).getSSLCACertificateFile(); }
    public void setSSLCACertificateFile(String SSLCACertificateFile) { ((AprEndpoint)endpoint).setSSLCACertificateFile(SSLCACertificateFile); }


    /**
     * SSL CA revocation path.
     */
    public String getSSLCARevocationPath() { return ((AprEndpoint)endpoint).getSSLCARevocationPath(); }
    public void setSSLCARevocationPath(String SSLCARevocationPath) { ((AprEndpoint)endpoint).setSSLCARevocationPath(SSLCARevocationPath); }


    /**
     * SSL CA revocation file.
     */
    public String getSSLCARevocationFile() { return ((AprEndpoint)endpoint).getSSLCARevocationFile(); }
    public void setSSLCARevocationFile(String SSLCARevocationFile) { ((AprEndpoint)endpoint).setSSLCARevocationFile(SSLCARevocationFile); }


    /**
     * SSL verify client.
     */
    public String getSSLVerifyClient() { return ((AprEndpoint)endpoint).getSSLVerifyClient(); }
    public void setSSLVerifyClient(String SSLVerifyClient) { ((AprEndpoint)endpoint).setSSLVerifyClient(SSLVerifyClient); }


    /**
     * SSL verify depth.
     */
    public int getSSLVerifyDepth() { return ((AprEndpoint)endpoint).getSSLVerifyDepth(); }
    public void setSSLVerifyDepth(int SSLVerifyDepth) { ((AprEndpoint)endpoint).setSSLVerifyDepth(SSLVerifyDepth); }

    /**
     * Disable SSL compression.
     */
    public boolean getSSLDisableCompression() { return ((AprEndpoint)endpoint).getSSLDisableCompression(); }
    public void setSSLDisableCompression(boolean disable) { ((AprEndpoint)endpoint).setSSLDisableCompression(disable); }

    // ----------------------------------------------------- JMX related methods

    @Override
    protected String getNamePrefix() {
        return ("http-apr");
    }


    // --------------------  Connection handler --------------------

    protected static class Http11ConnectionHandler
            extends AbstractConnectionHandler<Long,Http11AprProcessor> implements Handler {

        protected Http11AprProtocol proto;

        Http11ConnectionHandler(Http11AprProtocol proto) {
            this.proto = proto;
        }

        @Override
        protected AbstractProtocol<Long> getProtocol() {
            return proto;
        }

        @Override
        protected Log getLog() {
            return log;
        }

        @Override
        public void recycle() {
            recycledProcessors.clear();
        }

        /**
         * Expected to be used by the handler once the processor is no longer
         * required.
         *
         * @param socket
         * @param processor
         * @param isSocketClosing   Not used in HTTP
         * @param addToPoller
         */
        @Override
        public void release(SocketWrapper<Long> socket,
                Processor<Long> processor, boolean isSocketClosing,
                boolean addToPoller) {
            processor.recycle(isSocketClosing);
            recycledProcessors.offer(processor);
            if (addToPoller && proto.endpoint.isRunning()) {
                ((AprEndpoint)proto.endpoint).getPoller().add(
                        socket.getSocket().longValue(),
                        proto.endpoint.getKeepAliveTimeout(), true, false);
            }
        }

        @Override
        protected void initSsl(SocketWrapper<Long> socket,
                Processor<Long> processor) {
            // NOOP for APR
        }

        @SuppressWarnings("deprecation") // Inbound/Outbound based upgrade
        @Override
        protected void longPoll(SocketWrapper<Long> socket,
                Processor<Long> processor) {

            if (processor.isAsync()) {
                // Async
                socket.setAsync(true);
            } else if (processor.isComet()) {
                // Comet
                if (proto.endpoint.isRunning()) {
                    socket.setComet(true);
                    ((AprEndpoint) proto.endpoint).getPoller().add(
                            socket.getSocket().longValue(),
                            proto.endpoint.getSoTimeout(), true, false);
                } else {
                    // Process a STOP directly
                    ((AprEndpoint) proto.endpoint).processSocket(
                            socket.getSocket().longValue(),
                            SocketStatus.STOP);
                }
            } else if (processor.isUpgrade()) {
                // Upgraded
                Poller p = ((AprEndpoint) proto.endpoint).getPoller();
                if (p == null) {
                    // Connector has been stopped
                    release(socket, processor, true, false);
                } else {
                    p.add(socket.getSocket().longValue(), -1, true, false);
                }
            } else {
                // Tomcat 7 proprietary upgrade
                ((AprEndpoint) proto.endpoint).getPoller().add(
                        socket.getSocket().longValue(),
                        processor.getUpgradeInbound().getReadTimeout(),
                        true, false);
            }
        }

        @Override
        protected Http11AprProcessor createProcessor() {
            Http11AprProcessor processor = new Http11AprProcessor(
                    proto.getMaxHttpHeaderSize(), proto.getRejectIllegalHeaderName(),
                    (AprEndpoint)proto.endpoint, proto.getMaxTrailerSize(),
                    proto.getAllowedTrailerHeadersAsSet(), proto.getMaxExtensionSize(),
                    proto.getMaxSwallowSize(), proto.getRelaxedPathChars(),
                    proto.getRelaxedQueryChars());
            processor.setAdapter(proto.adapter);
            processor.setMaxKeepAliveRequests(proto.getMaxKeepAliveRequests());
            processor.setKeepAliveTimeout(proto.getKeepAliveTimeout());
            processor.setConnectionUploadTimeout(
                    proto.getConnectionUploadTimeout());
            processor.setDisableUploadTimeout(proto.getDisableUploadTimeout());
            processor.setCompressionMinSize(proto.getCompressionMinSize());
            processor.setCompression(proto.getCompression());
            processor.setNoCompressionUserAgents(proto.getNoCompressionUserAgents());
            processor.setCompressableMimeTypes(proto.getCompressableMimeTypes());
            processor.setRestrictedUserAgents(proto.getRestrictedUserAgents());
            processor.setSocketBuffer(proto.getSocketBuffer());
            processor.setMaxSavePostSize(proto.getMaxSavePostSize());
            processor.setServer(proto.getServer());
            processor.setClientCertProvider(proto.getClientCertProvider());
            processor.setMaxCookieCount(proto.getMaxCookieCount());
            register(processor);
            return processor;
        }

        /**
         * @deprecated  Will be removed in Tomcat 8.0.x.
         */
        @Deprecated
        @Override
        protected Processor<Long> createUpgradeProcessor(
                SocketWrapper<Long> socket,
                org.apache.coyote.http11.upgrade.UpgradeInbound inbound)
                throws IOException {
            return new org.apache.coyote.http11.upgrade.UpgradeAprProcessor(
                    socket, inbound);
        }

        @Override
        protected Processor<Long> createUpgradeProcessor(
                SocketWrapper<Long> socket,
                HttpUpgradeHandler httpUpgradeProcessor)
                throws IOException {
            return new AprProcessor(socket, httpUpgradeProcessor,
                    (AprEndpoint) proto.endpoint,
                    proto.getUpgradeAsyncWriteBufferSize());
        }
    }
}
