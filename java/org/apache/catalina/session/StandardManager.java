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
package org.apache.catalina.session;

import java.io.BufferedInputStream;
import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.security.AccessController;
import java.security.PrivilegedActionException;
import java.security.PrivilegedExceptionAction;
import java.util.ArrayList;
import java.util.Iterator;

import javax.servlet.ServletContext;

import com.luban.LoggerUtils;
import org.apache.catalina.Context;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.LifecycleState;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.catalina.security.SecurityUtil;
import org.apache.catalina.util.CustomObjectInputStream;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * Standard implementation of the <b>Manager</b> interface that provides
 * simple session persistence across restarts of this component (such as
 * when the entire server is shut down and restarted, or when a particular
 * web application is reloaded.
 * <p>
 * <b>IMPLEMENTATION NOTE</b>:  Correct behavior of session storing and
 * reloading depends upon external calls to the <code>start()</code> and
 * <code>stop()</code> methods of this class at the correct times.
 *
 * @author Craig R. McClanahan
 * @author Jean-Francois Arcand
 *  Context 容器的会话管理器用于管理对应的Web 容器的会话，维护会话的生成，更新和销毁，每个Context 都有自己的会话管理器，如果显示的
 *  配置文件中配置会话管理器，则Context 容器会使用该会话管理器，否则，Tomcat 会分配默认的标识会话管理器（StandardManager）,同时
 *  如果在集群环境中会使用集群会话管理器，可能是DeltaManager 和BackupManager
 *
 *
 *  用于保存状态的会话对象已经有了，现在就需要一个管理器来管理所有的会话，例如，会话ID 的生成，根据会话ID 找出对应的会话，对于过期的会话进行销毁
 *  操作，用一句话描述标准的会话管理器，提供了一个专门管理某个Web 应用所有会话的容器，并且会在Web 应用启动，停止 时进行会话重新加载和持久化。
 *
 *  会话管理主要提供的功能包括会话id 的生成器，后台管理，处理过期会话，持久化模块及会话集的维护，如图 所示 ，标准会话管理器包含了SessionIdGenerator
 *  组件，backGroundProcess模块，持久化模块及会话集合。
 *
 *  首先看SessionIdGenerator ，它负责为每个会话生成，分配一个唯一的标识，例如，最终会话生成类似于 326257DA6DB767F8D2E38F2C4540D1DEA
 *  字符串的会话标识，具体默认生成算法主要依靠JDK 提供的SHA1PRNG算法，在集群环境中，为了方便识别会话归属 ， 它最终 生成会话标识类似于
 *  326257DA6DB767F8D2E38F2C4540D1DEA.tomcat1 , 后面会加上Tomcat 集群的标识jvmRoute 变量的值，这里假设其中一个集群标识配置为 "tomcat1"
 *  如果你想转换随机数生成算法，可以通过配置server.xml 的Manager 节点secureRandomAlgorithm 及secureRandomClass 属性达到修改算法的效果 。
 *
 *  然后看如何过期会话进行处理，负责判断会话是否过期的逻辑主要在backGroundProcess模块中， 在Tomcat 容器中会有一条线程专门用于执行后台处理
 *  ，当然，也包括标准的会话管理器的backgroundProcess ，它不断循环判断所有的会话中是否有过期的，一旦过期，则从会话集中删除此会话。
 *
 *  最后是善待持久化模块和会话集的维护，由于标准的会话旨在提供一个简单便捷的管理器，因此持久化和重加载操作并不会太灵活且扩展性弱， Tomcat
 *  会在每个StandardContext(Web 应用)停止时调用管理器属性将上经Web 应用的所有会话持久化磁盘中，文件名为SESSIONS.ser ，而目录则由server.xml
 *  的Manager 节点的pathname 指定或Javax.servlet.context.tempdir 变量指定，默认存放的路径为% CATALINA_HOME%/work/Catalina/localhost/WebName/SESSIONS.ser
 *  当Web应用启动时，又会加载这些持久化会话，加载完成后，SESSIONS.ser 文件将会被删除，所以每次启动成功后就看不到此文件的存在，另外，会话集维护是指
 *  创建新会话对象，删除指会话对象及更新会话对象的功能。
 *
 *  标准会话管理器是我们常用的会话管理器，也是Tomcat 默认的一个会话管理器，对它的进入深入了解有助于对Tomcat会话功能的掌握，同时对后面的
 *  会话管理器理解也更加容易 。
 *
 *
 *
 */
public class StandardManager extends ManagerBase {

    private final Log log = LogFactory.getLog(StandardManager.class); // must not be static

    // ---------------------------------------------------- Security Classes

    private class PrivilegedDoLoad
        implements PrivilegedExceptionAction<Void> {

        PrivilegedDoLoad() {
            // NOOP
        }

        @Override
        public Void run() throws Exception{
           doLoad();
           return null;
        }
    }

    private class PrivilegedDoUnload
        implements PrivilegedExceptionAction<Void> {

        PrivilegedDoUnload() {
            // NOOP
        }

        @Override
        public Void run() throws Exception{
            doUnload();
            return null;
        }

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The descriptive information about this implementation.
     */
    protected static final String info = "StandardManager/1.0";


    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    protected static final String name = "StandardManager";


    /**
     * Path name of the disk file in which active sessions are saved
     * when we stop, and from which these sessions are loaded when we start.
     * A <code>null</code> value indicates that no persistence is desired.
     * If this pathname is relative, it will be resolved against the
     * temporary working directory provided by our context, available via
     * the <code>javax.servlet.context.tempdir</code> context attribute.
     */
    protected String pathname = "SESSIONS.ser";


    // ------------------------------------------------------------- Properties

    @Override
    public String getInfo() {
        return info;
    }


    @Override
    public String getName() {
        return name;
    }


    /**
     * @return The session persistence pathname, if any.
     */
    public String getPathname() {
        return pathname;
    }


    /**
     * Set the session persistence pathname to the specified value.  If no
     * persistence support is desired, set the pathname to <code>null</code>.
     *
     * @param pathname New session persistence pathname
     */
    public void setPathname(String pathname) {
        String oldPathname = this.pathname;
        this.pathname = pathname;
        support.firePropertyChange("pathname", oldPathname, this.pathname);
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public void load() throws ClassNotFoundException, IOException {
        if (SecurityUtil.isPackageProtectionEnabled()){
            try{
                AccessController.doPrivileged( new PrivilegedDoLoad() );
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof ClassNotFoundException) {
                    throw (ClassNotFoundException)exception;
                } else if (exception instanceof IOException) {
                    throw (IOException)exception;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Unreported exception in load() ", exception);
                }
            }
        } else {
            doLoad();
        }
    }


    /**
     * Load any currently active sessions that were previously unloaded
     * to the appropriate persistence mechanism, if any.  If persistence is not
     * supported, this method returns without doing anything.
     *
     * @exception ClassNotFoundException if a serialized class cannot be
     *  found during the reload
     * @exception IOException if an input/output error occurs
     */
    protected void doLoad() throws ClassNotFoundException, IOException {
        if (log.isDebugEnabled()) {
            log.debug("Start: Loading persisted sessions");
        }

        // Initialize our internal data structures
        sessions.clear();

        // Open an input stream to the specified pathname, if any
        // 获取SESSIONS.ser文件
        File file = file();
        if (file == null) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("standardManager.loading", pathname));
        }
        FileInputStream fis = null;
        BufferedInputStream bis = null;
        ObjectInputStream ois = null;
        Loader loader = null;
        ClassLoader classLoader = null;
        Log logger = null;
        try {
            fis = new FileInputStream(file.getAbsolutePath());
            bis = new BufferedInputStream(fis);
            loader = container.getLoader();
            logger = container.getLogger();
            if (loader != null) {
                classLoader = loader.getClassLoader();
            }
            if (classLoader == null) {
                classLoader = getClass().getClassLoader();
            }
            ois = new CustomObjectInputStream(bis, classLoader, logger,
                    getSessionAttributeValueClassNamePattern(),
                    getWarnOnSessionAttributeFilterFailure());
        } catch (FileNotFoundException e) {
            if (log.isDebugEnabled()) {
                log.debug("No persisted data file found");
            }
            return;
        } catch (IOException e) {
            log.error(sm.getString("standardManager.loading.ioe", e), e);
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException f) {
                    // Ignore
                }
            }
            if (bis != null) {
                try {
                    bis.close();
                } catch (IOException f) {
                    // Ignore
                }
            }
            throw e;
        }

        // Load the previously unloaded active sessions
        synchronized (sessions) {
            try {
                Integer count = (Integer) ois.readObject();
                int n = count.intValue();
                if (log.isDebugEnabled())
                    log.debug("Loading " + n + " persisted sessions");
                for (int i = 0; i < n; i++) {
                    StandardSession session = getNewSession();
                    session.readObjectData(ois);
                    session.setManager(this);
                    sessions.put(session.getIdInternal(), session);
                    session.activate();
                    if (!session.isValidInternal()) {
                        // If session is already invalid,
                        // expire session to prevent memory leak.
                        session.setValid(true);
                        session.expire();
                    }
                    sessionCounter++;
                }
            } catch (ClassNotFoundException e) {
                log.error(sm.getString("standardManager.loading.cnfe", e), e);
                try {
                    ois.close();
                } catch (IOException f) {
                    // Ignore
                }
                throw e;
            } catch (IOException e) {
                log.error(sm.getString("standardManager.loading.ioe", e), e);
                try {
                    ois.close();
                } catch (IOException f) {
                    // Ignore
                }
                throw e;
            } finally {
                // Close the input stream
                try {
                    ois.close();
                } catch (IOException f) {
                    // ignored
                }

                // Delete the persistent storage file
                if (file.exists()) {
                    file.delete();
                }
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Finish: Loading persisted sessions");
        }
    }


    @Override
    public void unload() throws IOException {
        if (SecurityUtil.isPackageProtectionEnabled()) {
            try {
                AccessController.doPrivileged(new PrivilegedDoUnload());
            } catch (PrivilegedActionException ex){
                Exception exception = ex.getException();
                if (exception instanceof IOException) {
                    throw (IOException)exception;
                }
                if (log.isDebugEnabled()) {
                    log.debug("Unreported exception in unLoad()", exception);
                }
            }
        } else {
            doUnload();
        }
    }


    /**
     * Save any currently active sessions in the appropriate persistence
     * mechanism, if any.  If persistence is not supported, this method
     * returns without doing anything.
     *
     * @exception IOException if an input/output error occurs
     */
    protected void doUnload() throws IOException {

        if (log.isDebugEnabled())
            log.debug(sm.getString("standardManager.unloading.debug"));

        if (sessions.isEmpty()) {
            log.debug(sm.getString("standardManager.unloading.nosessions"));
            return; // nothing to do
        }

        // Open an output stream to the specified pathname, if any
        File file = file();
        if (file == null) {
            return;
        }
        if (log.isDebugEnabled()) {
            log.debug(sm.getString("standardManager.unloading", pathname));
        }
        FileOutputStream fos = null;
        BufferedOutputStream bos = null;
        ObjectOutputStream oos = null;
        boolean error = false;
        try {

            LoggerUtils.info("=doUnload====" + file.getAbsolutePath(),10);

            fos = new FileOutputStream(file.getAbsolutePath());
            bos = new BufferedOutputStream(fos);
            oos = new ObjectOutputStream(bos);
        } catch (IOException e) {
            error = true;
            log.error(sm.getString("standardManager.unloading.ioe", e), e);
            throw e;
        } finally {
            if (error) {
                if (oos != null) {
                    try {
                        oos.close();
                    } catch (IOException ioe) {
                        // Ignore
                    }
                }
                if (bos != null) {
                    try {
                        bos.close();
                    } catch (IOException ioe) {
                        // Ignore
                    }
                }
                if (fos != null) {
                    try {
                        fos.close();
                    } catch (IOException ioe) {
                        // Ignore
                    }
                }
            }
        }

        // Write the number of active sessions, followed by the details
        ArrayList<StandardSession> list = new ArrayList<StandardSession>();
        synchronized (sessions) {
            if (log.isDebugEnabled()) {
                log.debug("Unloading " + sessions.size() + " sessions");
            }
            try {
                oos.writeObject(Integer.valueOf(sessions.size()));
                Iterator<Session> elements = sessions.values().iterator();
                while (elements.hasNext()) {
                    StandardSession session =
                        (StandardSession) elements.next();
                    list.add(session);
                    session.passivate();
                    session.writeObjectData(oos);
                }
            } catch (IOException e) {
                log.error(sm.getString("standardManager.unloading.ioe", e), e);
                try {
                    oos.close();
                } catch (IOException f) {
                    // Ignore
                }
                throw e;
            }
        }

        // Flush and close the output stream
        try {
            oos.flush();
        } finally {
            try {
                oos.close();
            } catch (IOException f) {
                // Ignore
            }
        }

        // Expire all the sessions we just wrote
        if (log.isDebugEnabled()) {
            log.debug("Expiring " + list.size() + " persisted sessions");
        }
        Iterator<StandardSession> expires = list.iterator();
        while (expires.hasNext()) {
            StandardSession session = expires.next();
            try {
                session.expire(false);
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            } finally {
                session.recycle();
            }
        }

        if (log.isDebugEnabled()) {
            log.debug("Unloading complete");
        }
    }


    /**
     * Start this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#startInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void startInternal() throws LifecycleException {

        super.startInternal();

        // Load unloaded sessions, if any
        // 将SESSIONS.ser加载到ConcurrentHashMap中
        try {
            load();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardManager.managerLoad"), t);
        }

        setState(LifecycleState.STARTING);
    }


    /**
     * Stop this component and implement the requirements
     * of {@link org.apache.catalina.util.LifecycleBase#stopInternal()}.
     *
     * @exception LifecycleException if this component detects a fatal error
     *  that prevents this component from being used
     */
    @Override
    protected synchronized void stopInternal() throws LifecycleException {

        if (log.isDebugEnabled()) {
            log.debug("Stopping");
        }

        setState(LifecycleState.STOPPING);

        // Write out sessions
        // 将ConcurrentHashMap存入SESSIONS.ser文件中
        try {
            unload();
        } catch (Throwable t) {
            ExceptionUtils.handleThrowable(t);
            log.error(sm.getString("standardManager.managerUnload"), t);
        }

        // Expire all active sessions
        Session sessions[] = findSessions();
        for (int i = 0; i < sessions.length; i++) {
            Session session = sessions[i];
            try {
                if (session.isValid()) {
                    session.expire();
                }
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
            } finally {
                // Measure against memory leaking if references to the session
                // object are kept in a shared field somewhere
                session.recycle();
            }
        }

        // Require a new random number generator if we are restarted
        super.stopInternal();
    }


    // ------------------------------------------------------ Protected Methods

    /**
     * Return a File object representing the pathname to our
     * persistence file, if any.
     */
    protected File file() {
        if (pathname == null || pathname.length() == 0) {
            return null;
        }
        File file = new File(pathname);
        if (!file.isAbsolute()) {
            ServletContext servletContext = ((Context) container).getServletContext();
            File tempdir = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
            if (tempdir != null) {
                file = new File(tempdir, pathname);
            }
        }
        return file;
    }
}
