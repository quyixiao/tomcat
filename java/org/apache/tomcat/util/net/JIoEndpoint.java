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
import java.net.*;
import java.security.AccessController;
import java.security.PrivilegedAction;
import java.util.Iterator;
import java.util.concurrent.ConcurrentLinkedQueue;
import java.util.concurrent.RejectedExecutionException;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.net.AbstractEndpoint.Handler.SocketState;
import org.apache.tomcat.util.security.PrivilegedSetTccl;


/**
 * Handle incoming TCP connections.
 *
 * This class implement a simple server model: one listener thread accepts on a socket and
 * creates a new worker thread for each incoming connection.
 *
 * More advanced Endpoints will reuse the threads, use queues, etc.
 *
 * @author James Duncan Davidson
 * @author Jason Hunter
 * @author James Todd
 * @author Costin Manolache
 * @author Gal Shachor
 * @author Yoav Shapira
 * @author Remy Maucherat
 */
public class JIoEndpoint extends AbstractEndpoint<Socket> {


    // -------------------------------------------------------------- Constants

    private static final Log log = LogFactory.getLog(JIoEndpoint.class);

    // ----------------------------------------------------------------- Fields

    /**
     * Associated server socket.
     */
    protected ServerSocket serverSocket = null;


    // ------------------------------------------------------------ Constructor

    public JIoEndpoint() {
        // Set maxConnections to zero so we can tell if the user has specified
        // their own value on the connector when we reach bind()
        setMaxConnections(0);
        // Reduce the executor timeout for BIO as threads in keep-alive will not
        // terminate when the executor interrupts them.
        setExecutorTerminationTimeoutMillis(0);
    }

    // ------------------------------------------------------------- Properties

    /**
     * Handling of accepted sockets.
     */
    protected Handler handler = null;
    public void setHandler(Handler handler ) { this.handler = handler; }
    public Handler getHandler() { return handler; }

    /**
     * Server socket factory.
     */
    protected ServerSocketFactory serverSocketFactory = null;
    public void setServerSocketFactory(ServerSocketFactory factory) { this.serverSocketFactory = factory; }
    public ServerSocketFactory getServerSocketFactory() { return serverSocketFactory; }

    /**
     * Port in use.
     */
    @Override
    public int getLocalPort() {
        ServerSocket s = serverSocket;
        if (s == null) {
            return -1;
        } else {
            return s.getLocalPort();
        }
    }

    /*
     * Optional feature support.
     */
    @Override
    public boolean getUseSendfile() { return false; } // Not supported
    @Override
    public boolean getUseComet() { return false; } // Not supported
    @Override
    public boolean getUseCometTimeout() { return false; } // Not supported
    @Override
    public boolean getDeferAccept() { return false; } // Not supported
    @Override
    public boolean getUsePolling() { return false; } // Not supported


    // ------------------------------------------------ Handler Inner Interface

    /**
     * Bare bones interface used for socket processing. Per thread data is to be
     * stored in the ThreadWithAttributes extra folders, or alternately in
     * thread local fields.
     */
    public interface Handler extends AbstractEndpoint.Handler {
        public SocketState process(SocketWrapper<Socket> socket,
                SocketStatus status);
        public SSLImplementation getSslImplementation();
        public boolean isAvailable(SocketWrapper<Socket> socket);
    }


    /**
     * Async timeout thread
     */
    protected class AsyncTimeout implements Runnable {
        /**
         * The background thread that checks async requests and fires the
         * timeout if there has been no activity.
         */
        @Override
        public void run() {

            // Loop until we receive a shutdown command
            while (running) {
                try {
                    Thread.sleep(1000);
                } catch (InterruptedException e) {
                    // Ignore
                }
                long now = System.currentTimeMillis();
                Iterator<SocketWrapper<Socket>> sockets =
                    waitingRequests.iterator();
                while (sockets.hasNext()) {
                    SocketWrapper<Socket> socket = sockets.next();
                    long access = socket.getLastAccess();
                    if (socket.getTimeout() > 0 && (now-access)>socket.getTimeout() ||
                            !handler.isAvailable(socket)) {
                        // Prevent multiple timeouts
                        socket.setTimeout(-1);
                        // socket超时了
                        processSocketAsync(socket,SocketStatus.TIMEOUT);
                    }
                }

                // Loop if endpoint is paused
                while (paused && running) {
                    try {
                        Thread.sleep(1000);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

            }
        }
    }


    // --------------------------------------------------- Acceptor Inner Class
    /**
     * The background thread that listens for incoming TCP/IP connections and
     * hands them off to an appropriate processor.
     *
     *
     * Acceptor 主要职责就是监听是否有客户端套接字连接并接收套接字，再将套接字次由任务执行器（Executor）执行， 字不断从系统底层读取套接字，
     * 接着做尽可能少的处理， 最后扔进线程池， 由于接收线程默认就只有这一条， 因此这里强调要做尽可能少的处理， 它对每次接收处理时间长短
     * 可能对整个性能产生影响 。
     *
     * 于是接收器所做的工作都是非常少且简单的， 仅仅维护了几个状态变量，负责流量控制闸门的累加操作和ServerSocket 的接收操作， 设置接收到
     * 套接字的一些属性，将接收到的套接字放入到线程池及一些异常处理，其他需要较长的时间处理逻辑就交给了线程池， 例如，对套接字底层的读取，
     * 对HTTP协议报文的解析及响应客户端的一些操作等， 这样处理有助于提升系统处理响应性能，此过程如图6.5所示 。
     *
     *
     *
     */
    protected class Acceptor extends AbstractEndpoint.Acceptor {

        @Override
        public void run() {

            int errorDelay = 0;

            // Loop until we receive a shutdown command
            while (running) {

                // Loop if endpoint is paused
                // 如果Endpoint仍然在运行，但是被暂停了，那么就无限循环，从而不能接受请求
                while (paused && running) {
                    state = AcceptorState.PAUSED;
                    try {
                        Thread.sleep(50);
                    } catch (InterruptedException e) {
                        // Ignore
                    }
                }

                if (!running) {
                    break;
                }
                state = AcceptorState.RUNNING;

                try {
                    //if we have reached max connections, wait
                    //达到了最大连接数限制则等待
                    countUpOrAwaitConnection();

                    Socket socket = null;  // bio，nio
                    try {
                        // Accept the next incoming connection from the server
                        // bio socket
                        // 此处是阻塞的，那么running属性就算已经被改成false，那么怎么进入到下一次循环呢？
                        socket = serverSocketFactory.acceptSocket(serverSocket);//
                        System.out.println("接收到了一个socket连接");

                    } catch (IOException ioe) {
                        countDownConnection();
                        // Introduce delay if necessary
                        errorDelay = handleExceptionWithDelay(errorDelay);
                        // re-throw
                        throw ioe;
                    }
                    // Successful accept, reset the error delay
                    errorDelay = 0;

                    // Configure the socket
                    // 如果Endpoint正在运行并且没有被暂停，那么就处理该socket
                    if (running && !paused && setSocketOptions(socket)) {
                        // Hand this socket off to an appropriate processor
                        // socket被正常的交给了线程池，processSocket就会返回true
                        // 如果没有被交给线程池或者中途Endpoint被停止了，则返回false
                        // 返回false则关闭该socket
                        if (!processSocket(socket)) {
                            countDownConnection();
                            // Close socket right away
                            closeSocket(socket);
                        }
                    } else {
                        countDownConnection();
                        // Close socket right away
                        closeSocket(socket);
                    }
                } catch (IOException x) {
                    if (running) {
                        log.error(sm.getString("endpoint.accept.fail"), x);
                    }
                } catch (NullPointerException npe) {
                    if (running) {
                        log.error(sm.getString("endpoint.accept.fail"), npe);
                    }
                } catch (Throwable t) {
                    ExceptionUtils.handleThrowable(t);
                    log.error(sm.getString("endpoint.accept.fail"), t);
                }
            }
            state = AcceptorState.ENDED;
        }
    }


    private void closeSocket(Socket socket) {
        try {
//            System.out.println("JioEndpoint关闭了socket");
            socket.close();
        } catch (IOException e) {
            // Ignore
        }
    }


    // ------------------------------------------- SocketProcessor Inner Class


    /**
     * This class is the equivalent of the Worker, but will simply use in an
     * external Executor thread pool.
     */
    protected class SocketProcessor implements Runnable {

        protected SocketWrapper<Socket> socket = null;
        protected SocketStatus status = null;

        public SocketProcessor(SocketWrapper<Socket> socket) {
            if (socket==null) throw new NullPointerException();
            this.socket = socket;
        }

        public SocketProcessor(SocketWrapper<Socket> socket, SocketStatus status) {
            this(socket);
            this.status = status;
        }

        @Override
        public void run() {
            boolean launch = false;
            synchronized (socket) {
                // 开始处理socket
                // Socket默认状态为OPEN
                try {
                    SocketState state = SocketState.OPEN;

                    try {
                        // SSL handshake
                        serverSocketFactory.handshake(socket.getSocket());
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("endpoint.err.handshake"), t);
                        }
                        // Tell to close the socket
                        state = SocketState.CLOSED;
                    }

                    // 当前socket没有关闭则处理socket
                    if ((state != SocketState.CLOSED)) {
                        // SocketState是Tomcat定义的一个状态,这个状态需要处理一下socket才能确定，因为跟客户端，跟具体的请求信息有关系
                        if (status == null) {
                            state = handler.process(socket, SocketStatus.OPEN_READ);
                        } else {
                            // status表示应该读数据还是应该写数据
                            // state表示处理完socket后socket的状态
                            state = handler.process(socket,status);
                        }
                    }
                    // 如果Socket的状态是被关闭，那么就减掉连接数并关闭socket
                    // 那么Socket的状态是在什么时候被关闭的？
                    if (state == SocketState.CLOSED) {
                        // Close socket
                        if (log.isTraceEnabled()) {
                            log.trace("Closing socket:"+socket);
                        }
                        countDownConnection();
                        try {
                            socket.getSocket().close();
                        } catch (IOException e) {
                            // Ignore
                        }
                    } else if (state == SocketState.OPEN ||
                            state == SocketState.UPGRADING ||
                            state == SocketState.UPGRADING_TOMCAT  ||
                            state == SocketState.UPGRADED){
                        socket.setKeptAlive(true);
                        socket.access();
                        launch = true;
                    } else if (state == SocketState.LONG) {
                        // socket不会关闭，但是当前线程会执行结束
                        socket.access();
                        waitingRequests.add(socket);
                    }
                } finally {
                    if (launch) {
                        try {
                            getExecutor().execute(new SocketProcessor(socket, SocketStatus.OPEN_READ));
                        } catch (RejectedExecutionException x) {
                            log.warn("Socket reprocessing request was rejected for:"+socket,x);
                            try {
                                //unable to handle connection at this time
                                handler.process(socket, SocketStatus.DISCONNECT);
                            } finally {
                                countDownConnection();
                            }


                        } catch (NullPointerException npe) {
                            if (running) {
                                log.error(sm.getString("endpoint.launch.fail"),
                                        npe);
                            }
                        }
                    }
                }
            }
            socket = null;
            // Finish up this request
        }

    }


    // -------------------- Public methods --------------------

    @Override
    public void bind() throws Exception {

        // Initialize thread count defaults for acceptor
        if (acceptorThreadCount == 0) {
            acceptorThreadCount = 1;
        }
        // Initialize maxConnections
        if (getMaxConnections() == 0) {
            // User hasn't set a value - use the default
            // 本来maxConnections默认值是10000的，但是因为是bio，所以需要取线程池最大线程数，默认为200
            setMaxConnections(getMaxThreadsWithExecutor());
        }

        if (serverSocketFactory == null) {
            if (isSSLEnabled()) {
                serverSocketFactory =
                    handler.getSslImplementation().getServerSocketFactory(this);
            } else {
                serverSocketFactory = new DefaultServerSocketFactory(this);
            }
        }

        if (serverSocket == null) {
            try {
                if (getAddress() == null) {
                    serverSocket = serverSocketFactory.createSocket(getPort(),
                            getBacklog());
                } else {
                    // serverSocket会不停的接收客户端连接，getBacklog()
                    serverSocket = serverSocketFactory.createSocket(getPort(),
                            getBacklog(), getAddress());
                }
            } catch (BindException orig) {
                String msg;
                if (getAddress() == null)
                    msg = orig.getMessage() + " <null>:" + getPort();
                else
                    msg = orig.getMessage() + " " +
                            getAddress().toString() + ":" + getPort();
                BindException be = new BindException(msg);
                be.initCause(orig);
                throw be;
            }
        }

    }

    @Override
    public void startInternal() throws Exception {

        if (!running) {
            running = true;
            paused = false;

            // Create worker collection
            // 如果配置文件里没有配置线程池，那么将创建一个默认的
            if (getExecutor() == null) {
                createExecutor();
            }

            initializeConnectionLatch();

            startAcceptorThreads();

            // Start async timeout thread
            Thread timeoutThread = new Thread(new AsyncTimeout(),
                    getName() + "-AsyncTimeout");
            timeoutThread.setPriority(threadPriority);
            timeoutThread.setDaemon(true);
            timeoutThread.start();
        }
    }

    @Override
    public void stopInternal() {
        releaseConnectionLatch();
        if (!paused) {
            pause();
        }
        if (running) {
            running = false;
            unlockAccept();
        }
        shutdownExecutor();
    }

    /**
     * Deallocate APR memory pools, and close server socket.
     */
    @Override
    public void unbind() throws Exception {
        if (running) {
            stop();
        }
        if (serverSocket != null) {
            try {
                if (serverSocket != null)
                    serverSocket.close();
            } catch (Exception e) {
                log.error(sm.getString("endpoint.err.close"), e);
            }
            serverSocket = null;
        }
        handler.recycle();
    }


    @Override
    protected AbstractEndpoint.Acceptor createAcceptor() {
        return new Acceptor();
    }


    /**
     * Configure the socket.
     */
    protected boolean setSocketOptions(Socket socket) {
        try {
            // 1: Set socket options: timeout, linger, etc
            socketProperties.setProperties(socket);
        } catch (SocketException s) {
            //error here is common if the client has reset the connection
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("endpoint.err.unexpected"), s);
            }
            // Close the socket
            return false;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("endpoint.err.unexpected"), t);
            // Close the socket
            return false;
        }
        return true;
    }


    /**
     * Process a new connection from a new client. Wraps the socket so
     * keep-alive and other attributes can be tracked and then passes the socket
     * to the executor for processing.
     *
     * @param socket    The socket associated with the client.
     *
     * @return          <code>true</code> if the socket is passed to the
     *                  executor, <code>false</code> if something went wrong or
     *                  if the endpoint is shutting down. Returning
     *                  <code>false</code> is an indication to close the socket
     *                  immediately.
     *
     *
     *
     * 为了确保整个Web 服务器的性能，应该在接收到请求后以最快的速度把它转交到其他线程上去处理，在接收到客户端的请求后，这些请求被次给
     * 任务执行器Executor ，它是一个拥有最大最小线程数限制的线程池， 这所以称为任务执行器， 是因为可能认为线程池启动了若干线程不断的检测某个
     * 任务队列，一旦发现有需要等待的任务，如图6.8 所示 ， 每个线程都不断的循环检测任务队列，线程数量不会少于最小的线程数，也不能大于最大的
     * 线程数。
     *
     * 任务执行器的实现使用了JUC工具包的ThreadPoolExecutor类，它提供了线程池的多种机制， 例如最大最小线程数量限制，多余线程回收时间，
     * 超出最大线程数时线程做出的拒绝动作等，继承此类并重写一些方法基本就能满足Tomcat 的个性化需求 。
     *
     * Connector 组件的Executor 分为两种类型，共享Executor 和私有Executor .
     *
     * 所谓共享的Executor 则指直接使用Service组件的线程池，多个Connector可以共用这些线程池， 可以在server.xml中通过如下配置，先在
     * <Service> 节点下配置一个<Executor> ，它表示该任务执行器的最大线程数为150 ，最小线程数为4 ，线程名的前缀为catalina-exec-
     * 并且命名为tomcatThreadPool, <Connector> 节点中指定以tomcatThreadPool 作为任务执行器， 对于多个Connector ，如图6.9 所示 ，
     * 可以同时指向同一个Executor ，以达到共享的目的 。
     *
     * <Service>
     *     <Executor name = "tomcatThreadPool" namePrefix="catalina-exec-" maxThreads="150" minSpareThreads="4"></Executor>
     *     <Connector executor="tomcatThreadPool" port="8080" protocol="HTTP/1.1" connectionTimeout="20000" redirectPort="8443" ></Connector>
     * </Service>
     *
     * 所谓私有Executor 是指<Connector> 未使用共享线程池，而是自己创建的线程池， 如下面的配置所示，第一个Connector 配置未引用共享线程池。
     * 所以它会为该Connector创建一个默认的Executor ，它的最小线程数为10，最大线程数为200，线程名字前缀为TP-exec-,线程池里面的线程
     * 全部为守护线程，线程数超过10时等待60秒， 如果还没有任务执行交销毁此线程，第二个Connector 配置未引用的共享线程池， 但它声明了
     * maxThreads 和 minSpareThreads属性，表示私有线程池最小的线程数为minSpareThreads ， 而最大线程数为maxThreads
     * 第一个Connector 和第二个Connector 各自使用自己的线程池，这便是私有Executor
     * <Service>
     *     <Connector port="8080" protocol="HTTP/1.1" connectionTimeout="2000" redirect port="8443"/>
     *     <Connector port="8009" protocol="AJP/1.3" redirectPort="8443" maxThreads=200 minSpareThreads=10/>
     * </Service>
     *
     */
    protected boolean processSocket(Socket socket) {
        // Process the request from this socket
        try {
            SocketWrapper<Socket> wrapper = new SocketWrapper<Socket>(socket);
            wrapper.setKeepAliveLeft(getMaxKeepAliveRequests());
            wrapper.setSecure(isSSLEnabled());
            // During shutdown, executor may be null - avoid NPE
            if (!running) {
                return false;
            }
            // bio， 一个socket连接对应一个线程
            // 一个http请求对应一个线程？
            getExecutor().execute(new SocketProcessor(wrapper));
        } catch (RejectedExecutionException x) {
            log.warn("Socket processing request was rejected for:"+socket,x);
            return false;
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
            return false;
        }
        return true;
    }


    /**
     * Process an existing async connection. If processing is required, passes
     * the wrapped socket to an executor for processing.
     *
     * @param socket    The socket associated with the client.
     * @param status    Only OPEN and TIMEOUT are used. The others are used for
     *                  Comet requests that are not supported by the BIO (JIO)
     *                  Connector.
     */
    @Override
    public void processSocketAsync(SocketWrapper<Socket> socket,
            SocketStatus status) {
        try {
            synchronized (socket) {
                if (waitingRequests.remove(socket)) {
                    SocketProcessor proc = new SocketProcessor(socket,status);
                    ClassLoader loader = Thread.currentThread().getContextClassLoader();
                    try {
                        //threads should not be created by the webapp classloader
                        if (Constants.IS_SECURITY_ENABLED) {
                            PrivilegedAction<Void> pa = new PrivilegedSetTccl(
                                    getClass().getClassLoader());
                            AccessController.doPrivileged(pa);
                        } else {
                            Thread.currentThread().setContextClassLoader(
                                    getClass().getClassLoader());
                        }
                        // During shutdown, executor may be null - avoid NPE
                        if (!running) {
                            return;
                        }
                        getExecutor().execute(proc);
                        //TODO gotta catch RejectedExecutionException and properly handle it
                    } finally {
                        if (Constants.IS_SECURITY_ENABLED) {
                            PrivilegedAction<Void> pa = new PrivilegedSetTccl(loader);
                            AccessController.doPrivileged(pa);
                        } else {
                            Thread.currentThread().setContextClassLoader(loader);
                        }
                    }
                }
            }
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            // This means we got an OOM or similar creating a thread, or that
            // the pool and its queue are full
            log.error(sm.getString("endpoint.process.fail"), t);
        }
    }

    protected ConcurrentLinkedQueue<SocketWrapper<Socket>> waitingRequests =
        new ConcurrentLinkedQueue<SocketWrapper<Socket>>();
    @Override
    public void removeWaitingRequest(SocketWrapper<Socket> socketWrapper) {
        waitingRequests.remove(socketWrapper);
    }



    @Override
    protected Log getLog() {
        return log;
    }
}
