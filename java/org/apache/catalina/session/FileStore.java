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

import java.io.BufferedOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.util.ArrayList;

import javax.servlet.ServletContext;

import org.apache.catalina.Context;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.juli.logging.Log;

/**
 * Concrete implementation of the <b>Store</b> interface that utilizes
 * a file per saved Session in a configured directory.  Sessions that are
 * saved are still subject to being expired based on inactivity.
 *
 * @author Craig R. McClanahan
 *
 * FileStore 提供以文件形式保存会话，在写入时，会针对每个会话生成一个文件，用于保存此会话相关的信息， 每个会话文件名定义为sessionId + .session
 * 的格式，例如 "326257DA6DB76F8D2E38F2C4540D1DEA.session" 而存放的目录路径则由ServletContext.TEMPDIR 变量指定，一般默认的目录为%CATALINA_HOME%/
 * work/Catalina/localhost/WebName/ ，其实就是Tomcat 安装根目录 + work + enginName +hostName + contextName ，所以假如有1万个会话则会有1万
 * 个会话文件，为了方便操作，写入 直接使用JDK 自带的java.io.ObjectOutputStream 对会话对象进行序列化并写入文件，所以有一点需要注意的，所有的会话
 * 中对象必须实现Serializable 接口。
 *
 * 类似的，加载会话是通过传入一个sessionId ，拼装成sessionId + .session 格式的文件名去寻找对应的会话文件，然后 使用JDK 自带的java.io.ObjectInputStream
 * 将会话对象加载到内存中，其实就是一个反序列化的过程。
 *
 * <Store className = "org.apache.catalina.session.FileStore" directory="sessiondir"/>
 *
 * 如果配置了directory ，则将以%CATALINA_HOME%/work/Catalina/locahost/WebName/sessiondir为存放目录 ， 当然，如果配置了绝对路径，则以配置的绝对路径为存放目录 。
 *
 * 以FileStore 为存储设备使用时， 看起来的文件操作I/O 上的效率相当的低，因为对每个文件操作都是打开，操作，关闭，并未使用任何优化措施，所以Tomcat 在选择使用此
 * 方式时，这很可能会影响整体性能的一个因素，这就要求必须做好充分的性能测试 。
 *
 *
 *
 *
 *
 */
public final class FileStore extends StoreBase {

    // ----------------------------------------------------- Constants

    /**
     * The extension to use for serialized session filenames.
     */
    private static final String FILE_EXT = ".session";


    // ----------------------------------------------------- Instance Variables

    /**
     * The pathname of the directory in which Sessions are stored.
     * This may be an absolute pathname, or a relative path that is
     * resolved against the temporary work directory for this application.
     */
    private String directory = ".";


    /**
     * A File representing the directory in which Sessions are stored.
     */
    private File directoryFile = null;


    /**
     * The descriptive information about this implementation.
     */
    private static final String info = "FileStore/1.0";

    /**
     * Name to register for this Store, used for logging.
     */
    private static final String storeName = "fileStore";

    /**
     * Name to register for the background thread.
     */
    private static final String threadName = "FileStore";


    // ------------------------------------------------------------- Properties

    /**
     * @return The directory path for this Store.
     */
    public String getDirectory() {
        return directory;
    }


    /**
     * Set the directory path for this Store.
     *
     * @param path The new directory path
     */
    public void setDirectory(String path) {
        String oldDirectory = this.directory;
        this.directory = path;
        this.directoryFile = null;
        support.firePropertyChange("directory", oldDirectory, this.directory);
    }


    @Override
    public String getInfo() {
        return info;
    }


    /**
     * @return The thread name for this Store.
     */
    public String getThreadName() {
        return threadName;
    }

    /**
     * Return the name for this Store, used for logging.
     */
    @Override
    public String getStoreName() {
        return storeName;
    }


    /**
     * Return the number of Sessions present in this Store.
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public int getSize() throws IOException {
        // Acquire the list of files in our storage directory
        File file = directory();
        if (file == null) {
            return (0);
        }
        String files[] = file.list();

        // Figure out which files are sessions
        int keycount = 0;
        if (files != null) {
            for (int i = 0; i < files.length; i++) {
                if (files[i].endsWith(FILE_EXT)) {
                    keycount++;
                }
            }
        }
        return keycount;
    }


    // --------------------------------------------------------- Public Methods

    /**
     * Remove all of the Sessions in this Store.
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void clear() throws IOException {
        String[] keys = keys();
        for (int i = 0; i < keys.length; i++) {
            remove(keys[i]);
        }
    }


    /**
     * Return an array containing the session identifiers of all Sessions
     * currently saved in this Store.  If there are no such Sessions, a
     * zero-length array is returned.
     *
     * @exception IOException if an input/output error occurred
     */
    @Override
    public String[] keys() throws IOException {
        // Acquire the list of files in our storage directory
        File file = directory();
        if (file == null) {
            return (new String[0]);
        }

        String files[] = file.list();

        // Bugzilla 32130
        if((files == null) || (files.length < 1)) {
            return (new String[0]);
        }

        // Build and return the list of session identifiers
        ArrayList<String> list = new ArrayList<String>();
        int n = FILE_EXT.length();
        for (int i = 0; i < files.length; i++) {
            if (files[i].endsWith(FILE_EXT)) {
                list.add(files[i].substring(0, files[i].length() - n));
            }
        }
        return list.toArray(new String[list.size()]);
    }


    /**
     * Load and return the Session associated with the specified session
     * identifier from this Store, without removing it.  If there is no
     * such stored Session, return <code>null</code>.
     *
     * @param id Session identifier of the session to load
     *
     * @exception ClassNotFoundException if a deserialization error occurs
     * @exception IOException if an input/output error occurs
     */
    @Override
    public Session load(String id) throws ClassNotFoundException, IOException {
        // Open an input stream to the specified pathname, if any
        File file = file(id);
        if (file == null) {
            return null;
        }

        if (!file.exists()) {
            return null;
        }

        Context context = (Context) getManager().getContainer();
        Log containerLog = context.getLogger();

        if (containerLog.isDebugEnabled()) {
            containerLog.debug(sm.getString(getStoreName()+".loading", id, file.getAbsolutePath()));
        }

        FileInputStream fis = null;
        ObjectInputStream ois = null;
        Loader loader = null;
        ClassLoader classLoader = null;
        ClassLoader oldThreadContextCL = Thread.currentThread().getContextClassLoader();
        try {
            fis = new FileInputStream(file.getAbsolutePath());
            loader = context.getLoader();
            if (loader != null) {
                classLoader = loader.getClassLoader();
            }
            if (classLoader != null) {
                Thread.currentThread().setContextClassLoader(classLoader);
            }
            ois = getObjectInputStream(fis);

            StandardSession session = (StandardSession) manager.createEmptySession();
            session.readObjectData(ois);
            session.setManager(manager);
            return session;
        } catch (FileNotFoundException e) {
            if (containerLog.isDebugEnabled()) {
                containerLog.debug("No persisted data file found");
            }
            return null;
        } finally {
            if (fis != null) {
                try {
                    fis.close();
                } catch (IOException f) {
                    // Ignore
                }
            }
            if (ois != null) {
                // Close the input stream
                try {
                    ois.close();
                } catch (IOException f) {
                    // Ignore
                }
            }
            Thread.currentThread().setContextClassLoader(oldThreadContextCL);
        }
    }


    /**
     * Remove the Session with the specified session identifier from
     * this Store, if present.  If no such Session is present, this method
     * takes no action.
     *
     * @param id Session identifier of the Session to be removed
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void remove(String id) throws IOException {
        File file = file(id);
        if (file == null) {
            return;
        }
        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(sm.getString(getStoreName()+".removing",
                             id, file.getAbsolutePath()));
        }
        file.delete();
    }


    /**
     * Save the specified Session into this Store.  Any previously saved
     * information for the associated session identifier is replaced.
     *
     * @param session Session to be saved
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void save(Session session) throws IOException {
        // Open an output stream to the specified pathname, if any
        File file = file(session.getIdInternal());
        if (file == null) {
            return;
        }
        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(sm.getString(getStoreName()+".saving",
                             session.getIdInternal(), file.getAbsolutePath()));
        }
        FileOutputStream fos = null;
        ObjectOutputStream oos = null;
        try {
            fos = new FileOutputStream(file.getAbsolutePath());
            oos = new ObjectOutputStream(new BufferedOutputStream(fos));
        } catch (IOException e) {
            if (fos != null) {
                try {
                    fos.close();
                } catch (IOException f) {
                    // Ignore
                }
            }
            throw e;
        }

        try {
            ((StandardSession)session).writeObjectData(oos);
        } finally {
            oos.close();
        }

    }


    // -------------------------------------------------------- Private Methods

    /**
     * Return a File object representing the pathname to our
     * session persistence directory, if any.  The directory will be
     * created if it does not already exist.
     */
    private File directory() throws IOException {
        if (this.directory == null) {
            return null;
        }
        if (this.directoryFile != null) {
            // NOTE:  Race condition is harmless, so do not synchronize
            return this.directoryFile;
        }
        File file = new File(this.directory);
        if (!file.isAbsolute()) {
            Context context = (Context) manager.getContainer();
            ServletContext servletContext = context.getServletContext();
            File work = (File) servletContext.getAttribute(ServletContext.TEMPDIR);
            file = new File(work, this.directory);
        }
        if (!file.exists() || !file.isDirectory()) {
            if (!file.delete() && file.exists()) {
                throw new IOException(sm.getString("fileStore.deleteFailed", file));
            }
            if (!file.mkdirs() && !file.isDirectory()) {
                throw new IOException(sm.getString("fileStore.createFailed", file));
            }
        }
        this.directoryFile = file;
        return file;
    }


    /**
     * Return a File object representing the pathname to our
     * session persistence file, if any.
     *
     * @param id The ID of the Session to be retrieved. This is
     *    used in the file naming.
     */
    private File file(String id) throws IOException {
        if (this.directory == null) {
            return null;
        }
        String filename = id + FILE_EXT;
        File file = new File(directory(), filename);
        return file;
    }
}
