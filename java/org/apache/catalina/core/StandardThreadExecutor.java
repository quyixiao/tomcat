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

import java.util.concurrent.RejectedExecutionException;
import java.util.concurrent.TimeUnit;

import org.apache.catalina.Executor;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.util.LifecycleMBeanBase;
import org.apache.tomcat.util.threads.ResizableExecutor;
import org.apache.tomcat.util.threads.TaskQueue;
import org.apache.tomcat.util.threads.TaskThreadFactory;
import org.apache.tomcat.util.threads.ThreadPoolExecutor;

public class StandardThreadExecutor extends LifecycleMBeanBase
        implements Executor, ResizableExecutor {

    // ---------------------------------------------- Properties
    /**
     * Default thread priority
     * 整数值，表示线程池中所有线程的线程优先级 。
     */
    protected int threadPriority = Thread.NORM_PRIORITY;

    /**
     * Run threads in daemon or non-daemon state
     * 决定这一Executor的线程是否应该为后台线程， 如果JVM 中的所有其他的非后台线程都结束了，则后台线程结束，要获得有关的后台线程的
     * 详细解释，参见 java.lang.Thread的Java 1.5 及更高版本，Javadoc 网页。
     */
    protected boolean daemon = true;

    /**
     * Default name prefix for the thread name
     * 在JVM上，每个运行线程都可以有一个name, 字符串，这一属性为线程池中每个线程的name 字符串设置了一个前缀，Tomcat 将把线程号
     * 追加到这一前缀的后面
     */
    protected String namePrefix = "tomcat-exec-";

    /**
     * max number of threads
     * maxThreads ：Tomcat是一款多线程Servlet容器，每个请求都会分配一个线程进行处理，因此Tomcat采用线程池来提高处理性能，maxThreads
     * 用于指定Connector 创建的请求处理线程的最大数目，该属性决定了可以并行处理的请求最大数目，即并发上限，当并发请求数超过maxThreads
     * 时，多余的请求只能排队等待，增大该属性可以提高Tomcat的并发处理能力，但是这也意味着会占用更多的系统资源，因此如果非常大反而会降低
     * 性能，甚至导致Tomcat崩溃。
     */
    protected int maxThreads = 200;

    /**
     * min number of threads
     * Tomcat 允许的空闲线程最小数目，也是启动Connector创建线程数目，如果空闲线程数小于该值，Tomcat将创建新的线程数，该属性设置为圈套值对
     * 性能并无益处，因为会占用额外的系统资源，默认值为4，可以满足大多数的Web应用的需求 ，但是对于存在突发的情况的Web应用，可能适当调大
     * 该值
     *
     */
    protected int minSpareThreads = 25;

    /**
     * idle time in milliseconds
     * 在Tomcat关闭一个空闲线程之前，允许空闲线程60000（一分钟）持续的时间（以毫秒为单位），只有当前活跃的线程数大于minSpareThread的值
     * ,才会关闭空闲线程
     */
    protected int maxIdleTime = 60000;

    /**
     * The executor we use for this component
     */
    protected ThreadPoolExecutor executor = null;

    /**
     * the name of this thread pool
     * 共离线程池的名字，这是Connector为了共享线程池要引用的名字，该名字必须唯一
     */
    protected String name;

    /**
     * prestart threads?
     */
    protected boolean prestartminSpareThreads = false;

    /**
     * The maximum number of elements that can queue up before we reject them
     */
    protected int maxQueueSize = Integer.MAX_VALUE;

    /**
     * After a context is stopped, threads in the pool are renewed. To avoid
     * renewing all threads at the same time, this delay is observed between 2
     * threads being renewed.
     */
    protected long threadRenewalDelay =
        org.apache.tomcat.util.threads.Constants.DEFAULT_THREAD_RENEWAL_DELAY;

    private TaskQueue taskqueue = null;
    // ---------------------------------------------- Constructors
    public StandardThreadExecutor() {
        //empty constructor for the digester
    }


    // ---------------------------------------------- Public Methods

    @Override
    protected void initInternal() throws LifecycleException {
        super.initInternal();
    }


    /**
     * Start the component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected void startInternal() throws LifecycleException {

        taskqueue = new TaskQueue(maxQueueSize);
        TaskThreadFactory tf = new TaskThreadFactory(namePrefix,daemon,getThreadPriority());
        executor = new ThreadPoolExecutor(getMinSpareThreads(), getMaxThreads(), maxIdleTime, TimeUnit.MILLISECONDS,taskqueue, tf);
        executor.setThreadRenewalDelay(threadRenewalDelay);
        if (prestartminSpareThreads) {
            executor.prestartAllCoreThreads();
        }
        taskqueue.setParent(executor);

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop the component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that needs to be reported
     */
    @Override
    protected void stopInternal() throws LifecycleException {

        setState(LifecycleState.STOPPING);
        if ( executor != null ) executor.shutdownNow();
        executor = null;
        taskqueue = null;
    }


    @Override
    protected void destroyInternal() throws LifecycleException {
        super.destroyInternal();
    }


    @Override
    public void execute(Runnable command, long timeout, TimeUnit unit) {
        if ( executor != null ) {
            executor.execute(command,timeout,unit);
        } else {
            throw new IllegalStateException("StandardThreadExecutor not started.");
        }
    }


    @Override
    public void execute(Runnable command) {
        if ( executor != null ) {
            try {
                executor.execute(command);
            } catch (RejectedExecutionException rx) {
                //there could have been contention around the queue
                if ( !( (TaskQueue) executor.getQueue()).force(command) ) throw new RejectedExecutionException("Work queue full.");
            }
        } else throw new IllegalStateException("StandardThreadPool not started.");
    }

    public void contextStopping() {
        if (executor != null) {
            executor.contextStopping();
        }
    }

    public int getThreadPriority() {
        return threadPriority;
    }

    public boolean isDaemon() {

        return daemon;
    }

    public String getNamePrefix() {
        return namePrefix;
    }

    public int getMaxIdleTime() {
        return maxIdleTime;
    }

    @Override
    public int getMaxThreads() {
        return maxThreads;
    }

    public int getMinSpareThreads() {
        return minSpareThreads;
    }

    @Override
    public String getName() {
        return name;
    }

    public boolean isPrestartminSpareThreads() {

        return prestartminSpareThreads;
    }
    public void setThreadPriority(int threadPriority) {
        this.threadPriority = threadPriority;
    }

    public void setDaemon(boolean daemon) {
        this.daemon = daemon;
    }

    public void setNamePrefix(String namePrefix) {
        this.namePrefix = namePrefix;
    }

    public void setMaxIdleTime(int maxIdleTime) {
        this.maxIdleTime = maxIdleTime;
        if (executor != null) {
            executor.setKeepAliveTime(maxIdleTime, TimeUnit.MILLISECONDS);
        }
    }

    public void setMaxThreads(int maxThreads) {
        this.maxThreads = maxThreads;
        if (executor != null) {
            executor.setMaximumPoolSize(maxThreads);
        }
    }

    public void setMinSpareThreads(int minSpareThreads) {
        this.minSpareThreads = minSpareThreads;
        if (executor != null) {
            executor.setCorePoolSize(minSpareThreads);
        }
    }

    public void setPrestartminSpareThreads(boolean prestartminSpareThreads) {
        this.prestartminSpareThreads = prestartminSpareThreads;
    }

    public void setName(String name) {
        this.name = name;
    }

    public void setMaxQueueSize(int size) {
        this.maxQueueSize = size;
    }

    public int getMaxQueueSize() {
        return maxQueueSize;
    }

    public long getThreadRenewalDelay() {
        return threadRenewalDelay;
    }

    public void setThreadRenewalDelay(long threadRenewalDelay) {
        this.threadRenewalDelay = threadRenewalDelay;
        if (executor != null) {
            executor.setThreadRenewalDelay(threadRenewalDelay);
        }
    }

    // Statistics from the thread pool
    @Override
    public int getActiveCount() {
        return (executor != null) ? executor.getActiveCount() : 0;
    }

    public long getCompletedTaskCount() {
        return (executor != null) ? executor.getCompletedTaskCount() : 0;
    }

    public int getCorePoolSize() {
        return (executor != null) ? executor.getCorePoolSize() : 0;
    }

    public int getLargestPoolSize() {
        return (executor != null) ? executor.getLargestPoolSize() : 0;
    }

    @Override
    public int getPoolSize() {
        return (executor != null) ? executor.getPoolSize() : 0;
    }

    public int getQueueSize() {
        return (executor != null) ? executor.getQueue().size() : -1;
    }


    @Override
    public boolean resizePool(int corePoolSize, int maximumPoolSize) {
        if (executor == null)
            return false;

        executor.setCorePoolSize(corePoolSize);
        executor.setMaximumPoolSize(maximumPoolSize);
        return true;
    }


    @Override
    public boolean resizeQueue(int capacity) {
        return false;
    }


    @Override
    protected String getDomainInternal() {
        // No way to navigate to Engine. Needs to have domain set.
        return null;
    }

    @Override
    protected String getObjectNameKeyProperties() {
        StringBuilder name = new StringBuilder("type=Executor,name=");
        name.append(getName());
        return name.toString();
    }
}
