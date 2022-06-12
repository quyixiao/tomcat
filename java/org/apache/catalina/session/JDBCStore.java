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
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.ObjectInputStream;
import java.io.ObjectOutputStream;
import java.sql.Connection;
import java.sql.Driver;
import java.sql.PreparedStatement;
import java.sql.ResultSet;
import java.sql.SQLException;
import java.util.ArrayList;
import java.util.Properties;

import javax.naming.Context;
import javax.naming.InitialContext;
import javax.naming.NamingException;
import javax.sql.DataSource;

import org.apache.catalina.Container;
import org.apache.catalina.LifecycleException;
import org.apache.catalina.Loader;
import org.apache.catalina.Session;
import org.apache.juli.logging.Log;
import org.apache.tomcat.util.ExceptionUtils;

/**
 * Implementation of the {@link org.apache.catalina.Store Store}
 * interface that stores serialized session objects in a database.
 * Sessions that are saved are still subject to being expired
 * based on inactivity.
 *
 * @author Bip Thelin
 *
 * JDBCStore 提供的是以数据库形式存放的会话，后端可以在任意厂商的数据库，只要有对应的数据库鸡翅程序即可，既然要存放数据，肯定要先在数据库中
 * 创建一张会话表，表的结构必须要是Tomcat 与MySQL 双方约定好的， 例如 ，Tomcat 默认的表名为tomcat$sessions ,表字段一共有6个， 分别为
 * app,id , data, valid , maxinactive, lastaccess,其中，app 字段用于区分哪个web应用，id 字段即会话标识， data 字段用于存放会话对象的
 * 字节串， validd 字段表示此会话是否有效，maxinactive 字段表示最大的存活时间 ， lastaccess字段表示最后的访问时间，其中需要注意的是
 * data字段，因为它的大小直接影响会话对象的大小， 所以需要根据实际设置它的类型， 如果MySQL 可以考虑设置为Blob(65kb) 或MediumBloc (16MB)
 *
 * 这样一来，会话的加载和保存其实就转化为对数据库的读，写操作了， 而获取数据库连接的逻辑先判断Tomcat 容器是否有数据源，如果有，则从数据源中直接
 * 获取一条连接使用，但是如果没有，则会自己通过驱动程序创建连接，需要注意的是， 从数据源中直接获取一条连接使用但是如果没有，则会自己通过
 * 驱动程序创建连接，需要注意的是， 从数据源中获取连接在使用完后放回数据源中，但自己通过驱动程序创建的连接使用完则不会关闭，这个很好理解 。
 * 因为数据源是一个池，重新获取连接很快，  而对于自建的连接，重新创建一般需要消耗数秒时间 ， 这明显会造成大问题。
 *
 * 下面以MySql 数据库为配置一个JDBCStore
 *
 * <Store className="org.apache.catalina.session.JDBCStore" connectionURL="jdbc:mysql://localhost:3306/web_session?user=user&password
 * =password"
 *      driverName="com.mysql.jdbc.Driver"
 *      sessionAppCol="app_name"
 *      sessionDataCol="session_data"
 *      sessionIdCol="session_id"
 *      sessionLastAccessedCol="last_access"
 *      sessionMaxInactiveCol="max_inactive"
 *      sessionTable="tomcat_sessions"
 *      sessionValidCol="valid_session"
 * />
 *
 *
 * 其中半球会话表及其字段的一些属性可以不必配置，直接采用Tomcat默认的配置即可，但驱动程序及连接URL则一定要配置。
 * 以JDBCStore 为存储设备时， 从表面是看起来，这并不会有明显的I/O 性能问题， 因为它使用数据源获取连接，这是一种池化技术，采用长连接模式 。
 * 一般情况下，如果数据源下是非常大，都不会存在性能问题。
 *
 * 介绍完存储设备后，接着看持久化会话管理器， 其实持久化会话管理器主要实现的就是三种逻辑下会话进行持久化操作。
 *
 * 1. 当会话对象数量超过指定的阀值时，则将超出会话对象的转换出（保存在存储设备中并把内存中的此对象删除）到存储设备中。
 * 2. 当会话空闲时间超过指定的阀值时， 则将此会话对象换出。
 * 3. 当会话空闲时间超过指定的阀值址， 则将此会话进行备份 （保存到存储设备中并且内存中还存在此对象 ）
 *
 * 实现上面的逻辑只需要对所有的会话集合进行遍历即可，把符合条件的通过存储设备保存，由于有些会话被持久化到存储设备中， 所以通过ID查找会话
 * 时需要先从内存中查找再往存储设备中查找 。
 *
 * 下面是一个配置例子，会话数大于 1000 时，则将空闲的时间大于60秒的会话转移到存储设备中，直到会话数量控制在1000，超出120秒的空闲时间会话被
 * 找出到存储设备中，超出180秒的空闲时间的会话将备份到存储设备中。
 *
 * <Manager className="org.apache.catalina.session.PersistentManager
 *      maxActiveSessions="1000"
 *      minIdleSwap="60"
 *      maxIdleSwap="120"
 *      maxIdleBackup="180"
 * >
 *  <Store className="org.apache.catalina.session.FileStore" directory="sessiondir" />
 * </Manager>
 *
 * 所以在了解两种存储设备后对持久化会话管理器的实现原理机制就相当的清楚了， 其实它就是提供了两种会话保存方式并提供了管理这些会话的操作。
 * 它提高了Tomcat 状态处理方面的容错能力 。
 *
 *
 *
 *
 *
 *
 *
 */
public class JDBCStore extends StoreBase {

    /**
     * The descriptive information about this implementation.
     */
    protected static final String info = "JDBCStore/1.0";

    /**
     * Context name associated with this Store
     */
    private String name = null;

    /**
     * Name to register for this Store, used for logging.
     */
    protected static String storeName = "JDBCStore";

    /**
     * Name to register for the background thread.
     */
    protected String threadName = "JDBCStore";

    /**
     * The connection username to use when trying to connect to the database.
     */
    protected String connectionName = null;


    /**
     * The connection URL to use when trying to connect to the database.
     */
    protected String connectionPassword = null;

    /**
     * Connection string to use when connecting to the DB.
     * 所用的JDBC 连接URL
     */
    protected String connectionURL = null;

    /**
     * The database connection.
     */
    private Connection dbConnection = null;

    /**
     * Instance of the JDBC Driver class we use as a connection factory.
     */
    protected Driver driver = null;

    /**
     * Driver to use.
     * JDBC 驱动程序所用的完整的Java类名。 默认为org.apache.catalina.session.JDBCStore
     */
    protected String driverName = null;

    /**
     * name of the JNDI resource
     */
    protected String dataSourceName = null;

    /**
     * DataSource to use
     */
    protected DataSource dataSource = null;


    // ------------------------------------------------------------ Table & cols

    /**
     * Table to use.
     */
    protected String sessionTable = "tomcat$sessions";

    /**
     * Column to use for /Engine/Host/Context name
     */
    protected String sessionAppCol = "app";

    /**
     * Id column to use.
     */
    protected String sessionIdCol = "id";

    /**
     * Data column to use.
     */
    protected String sessionDataCol = "data";

    /**
     * {@code Is Valid} column to use.
     */
    protected String sessionValidCol = "valid";

    /**
     * Max Inactive column to use.
     */
    protected String sessionMaxInactiveCol = "maxinactive";

    /**
     * Last Accessed column to use.
     */
    protected String sessionLastAccessedCol = "lastaccess";


    // ----------------------------------------------------------- SQL Variables

    /**
     * Variable to hold the <code>getSize()</code> prepared statement.
     */
    protected PreparedStatement preparedSizeSql = null;

    /**
     * Variable to hold the <code>save()</code> prepared statement.
     */
    protected PreparedStatement preparedSaveSql = null;

    /**
     * Variable to hold the <code>clear()</code> prepared statement.
     */
    protected PreparedStatement preparedClearSql = null;

    /**
     * Variable to hold the <code>remove()</code> prepared statement.
     */
    protected PreparedStatement preparedRemoveSql = null;

    /**
     * Variable to hold the <code>load()</code> prepared statement.
     */
    protected PreparedStatement preparedLoadSql = null;


    // -------------------------------------------------------------- Properties

    /**
     * @return the info for this Store.
     */
    @Override
    public String getInfo() {
        return info;
    }

    /**
     * @return the name for this instance (built from container name)
     */
    public String getName() {
        if (name == null) {
            Container container = manager.getContainer();
            String contextName = container.getName();
            if (!contextName.startsWith("/")) {
                contextName = "/" + contextName;
            }
            String hostName = "";
            String engineName = "";

            if (container.getParent() != null) {
                Container host = container.getParent();
                hostName = host.getName();
                if (host.getParent() != null) {
                    engineName = host.getParent().getName();
                }
            }
            name = "/" + engineName + "/" + hostName + contextName;
        }
        return name;
    }

    /**
     * @return the thread name for this Store.
     */
    public String getThreadName() {
        return threadName;
    }

    /**
     * @return the name for this Store, used for logging.
     */
    @Override
    public String getStoreName() {
        return storeName;
    }

    /**
     * Set the driver for this Store.
     *
     * @param driverName The new driver
     */
    public void setDriverName(String driverName) {
        String oldDriverName = this.driverName;
        this.driverName = driverName;
        support.firePropertyChange("driverName",
                oldDriverName,
                this.driverName);
        this.driverName = driverName;
    }

    /**
     * @return the driver for this Store.
     */
    public String getDriverName() {
        return driverName;
    }

    /**
     * @return the username to use to connect to the database.
     */
    public String getConnectionName() {
        return connectionName;
    }

    /**
     * Set the username to use to connect to the database.
     *
     * @param connectionName Username
     */
    public void setConnectionName(String connectionName) {
        this.connectionName = connectionName;
    }

    /**
     * @return the password to use to connect to the database.
     */
    public String getConnectionPassword() {
        return connectionPassword;
    }

    /**
     * Set the password to use to connect to the database.
     *
     * @param connectionPassword User password
     */
    public void setConnectionPassword(String connectionPassword) {
        this.connectionPassword = connectionPassword;
    }

    /**
     * Set the Connection URL for this Store.
     *
     * @param connectionURL The new Connection URL
     */
    public void setConnectionURL(String connectionURL) {
        String oldConnString = this.connectionURL;
        this.connectionURL = connectionURL;
        support.firePropertyChange("connectionURL",
                oldConnString,
                this.connectionURL);
    }

    /**
     * @return the Connection URL for this Store.
     */
    public String getConnectionURL() {
        return connectionURL;
    }

    /**
     * Set the table for this Store.
     *
     * @param sessionTable The new table
     */
    public void setSessionTable(String sessionTable) {
        String oldSessionTable = this.sessionTable;
        this.sessionTable = sessionTable;
        support.firePropertyChange("sessionTable",
                oldSessionTable,
                this.sessionTable);
    }

    /**
     * @return the table for this Store.
     */
    public String getSessionTable() {
        return sessionTable;
    }

    /**
     * Set the App column for the table.
     *
     * @param sessionAppCol the column name
     */
    public void setSessionAppCol(String sessionAppCol) {
        String oldSessionAppCol = this.sessionAppCol;
        this.sessionAppCol = sessionAppCol;
        support.firePropertyChange("sessionAppCol",
                oldSessionAppCol,
                this.sessionAppCol);
    }

    /**
     * @return the web application name column for the table.
     */
    public String getSessionAppCol() {
        return this.sessionAppCol;
    }

    /**
     * Set the Id column for the table.
     *
     * @param sessionIdCol the column name
     */
    public void setSessionIdCol(String sessionIdCol) {
        String oldSessionIdCol = this.sessionIdCol;
        this.sessionIdCol = sessionIdCol;
        support.firePropertyChange("sessionIdCol",
                oldSessionIdCol,
                this.sessionIdCol);
    }

    /**
     * @return the Id column for the table.
     */
    public String getSessionIdCol() {
        return this.sessionIdCol;
    }

    /**
     * Set the Data column for the table
     *
     * @param sessionDataCol the column name
     */
    public void setSessionDataCol(String sessionDataCol) {
        String oldSessionDataCol = this.sessionDataCol;
        this.sessionDataCol = sessionDataCol;
        support.firePropertyChange("sessionDataCol",
                oldSessionDataCol,
                this.sessionDataCol);
    }

    /**
     * @return the data column for the table
     */
    public String getSessionDataCol() {
        return this.sessionDataCol;
    }

    /**
     * Set the {@code Is Valid} column for the table
     *
     * @param sessionValidCol The column name
     */
    public void setSessionValidCol(String sessionValidCol) {
        String oldSessionValidCol = this.sessionValidCol;
        this.sessionValidCol = sessionValidCol;
        support.firePropertyChange("sessionValidCol",
                oldSessionValidCol,
                this.sessionValidCol);
    }

    /**
     * @return the {@code Is Valid} column
     */
    public String getSessionValidCol() {
        return this.sessionValidCol;
    }

    /**
     * Set the {@code Max Inactive} column for the table
     *
     * @param sessionMaxInactiveCol The column name
     */
    public void setSessionMaxInactiveCol(String sessionMaxInactiveCol) {
        String oldSessionMaxInactiveCol = this.sessionMaxInactiveCol;
        this.sessionMaxInactiveCol = sessionMaxInactiveCol;
        support.firePropertyChange("sessionMaxInactiveCol",
                oldSessionMaxInactiveCol,
                this.sessionMaxInactiveCol);
    }

    /**
     * @return the {@code Max Inactive} column
     */
    public String getSessionMaxInactiveCol() {
        return this.sessionMaxInactiveCol;
    }

    /**
     * Set the {@code Last Accessed} column for the table
     *
     * @param sessionLastAccessedCol The column name
     */
    public void setSessionLastAccessedCol(String sessionLastAccessedCol) {
        String oldSessionLastAccessedCol = this.sessionLastAccessedCol;
        this.sessionLastAccessedCol = sessionLastAccessedCol;
        support.firePropertyChange("sessionLastAccessedCol",
                oldSessionLastAccessedCol,
                this.sessionLastAccessedCol);
    }

    /**
     * @return the {@code Last Accessed} column
     */
    public String getSessionLastAccessedCol() {
        return this.sessionLastAccessedCol;
    }

    /**
     * Set the JNDI name of a DataSource-factory to use for db access
     *
     * @param dataSourceName The JNDI name of the DataSource-factory
     */
    public void setDataSourceName(String dataSourceName) {
        if (dataSourceName == null || "".equals(dataSourceName.trim())) {
            manager.getContainer().getLogger().warn(
                    sm.getString(getStoreName() + ".missingDataSourceName"));
            return;
        }
        this.dataSourceName = dataSourceName;
    }

    /**
     * @return the name of the JNDI DataSource-factory
     */
    public String getDataSourceName() {
        return this.dataSourceName;
    }


    // --------------------------------------------------------- Public Methods

    @Override
    public String[] expiredKeys() throws IOException {
        return keys(true);
    }

    @Override
    public String[] keys() throws IOException {
        return keys(false);
    }

    /**
     * Return an array containing the session identifiers of all Sessions
     * currently saved in this Store.  If there are no such Sessions, a
     * zero-length array is returned.
     *
     * @param expiredOnly flag, whether only keys of expired sessions should
     *        be returned
     * @return array containing the list of session IDs
     *
     * @exception IOException if an input/output error occurred
     */
    private String[] keys(boolean expiredOnly) throws IOException {
        String keys[] = null;
        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {

                Connection _conn = getConnection();
                if (_conn == null) {
                    return new String[0];
                }
                try {

                    String keysSql = "SELECT " + sessionIdCol + " FROM "
                            + sessionTable + " WHERE " + sessionAppCol + " = ?";
                    if (expiredOnly) {
                        keysSql += " AND (" + sessionLastAccessedCol + " + "
                                + sessionMaxInactiveCol + " * 1000 < ?)";
                    }
                    PreparedStatement preparedKeysSql = _conn.prepareStatement(keysSql);
                    try {
                        preparedKeysSql.setString(1, getName());
                        if (expiredOnly) {
                            preparedKeysSql.setLong(2, System.currentTimeMillis());
                        }
                        ResultSet rst = preparedKeysSql.executeQuery();
                        try {
                            ArrayList<String> tmpkeys = new ArrayList<String>();
                            if (rst != null) {
                                while (rst.next()) {
                                    tmpkeys.add(rst.getString(1));
                                }
                            }
                            keys = tmpkeys.toArray(new String[tmpkeys.size()]);
                            // Break out after the finally block
                            numberOfTries = 0;
                        } finally {
                            if (rst != null) {
                                rst.close();
                            }
                        }

                    } finally {
                        preparedKeysSql.close();
                    }
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    keys = new String[0];
                    // Close the connection so that it gets reopened next time
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }

        return keys;
    }

    /**
     * Return an integer containing a count of all Sessions
     * currently saved in this Store.  If there are no Sessions,
     * <code>0</code> is returned.
     *
     * @return the count of all sessions currently saved in this Store
     *
     * @exception IOException if an input/output error occurred
     */
    @Override
    public int getSize() throws IOException {
        int size = 0;
        ResultSet rst = null;

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();

                if (_conn == null) {
                    return size;
                }

                try {
                    if (preparedSizeSql == null) {
                        String sizeSql = "SELECT COUNT(" + sessionIdCol
                                + ") FROM " + sessionTable + " WHERE "
                                + sessionAppCol + " = ?";
                        preparedSizeSql = _conn.prepareStatement(sizeSql);
                    }

                    preparedSizeSql.setString(1, getName());
                    rst = preparedSizeSql.executeQuery();
                    if (rst.next()) {
                        size = rst.getInt(1);
                    }
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    try {
                        if (rst != null)
                            rst.close();
                    } catch (SQLException e) {
                        // Ignore
                    }

                    release(_conn);
                }
                numberOfTries--;
            }
        }
        return size;
    }

    /**
     * Load the Session associated with the id <code>id</code>.
     * If no such session is found <code>null</code> is returned.
     *
     * @param id a value of type <code>String</code>
     * @return the stored <code>Session</code>
     * @exception ClassNotFoundException if an error occurs
     * @exception IOException if an input/output error occurred
     */
    @Override
    public Session load(String id)
            throws ClassNotFoundException, IOException {
        ResultSet rst = null;
        StandardSession _session = null;
        ClassLoader classLoader = null;
        ObjectInputStream ois = null;
        org.apache.catalina.Context context = (org.apache.catalina.Context) manager.getContainer();
        Log containerLog = context.getLogger();
        Loader loader = context.getLoader();
        if (loader != null) {
            classLoader = loader.getClassLoader();
        }

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return null;
                }

                ClassLoader oldThreadContextCL = Thread.currentThread().getContextClassLoader();
                try {
                    if (preparedLoadSql == null) {
                        String loadSql = "SELECT " + sessionIdCol + ", "
                                + sessionDataCol + " FROM " + sessionTable
                                + " WHERE " + sessionIdCol + " = ? AND "
                                + sessionAppCol + " = ?";
                        preparedLoadSql = _conn.prepareStatement(loadSql);
                    }

                    preparedLoadSql.setString(1, id);
                    preparedLoadSql.setString(2, getName());
                    rst = preparedLoadSql.executeQuery();
                    if (rst.next()) {
                        if (classLoader != null) {
                            Thread.currentThread().setContextClassLoader(classLoader);
                        }
                        ois = getObjectInputStream(rst.getBinaryStream(2));

                        if (containerLog.isDebugEnabled()) {
                            containerLog.debug(
                                    sm.getString(getStoreName() + ".loading", id, sessionTable));
                        }

                        _session = (StandardSession) manager.createEmptySession();
                        _session.readObjectData(ois);
                        _session.setManager(manager);
                    } else if (containerLog.isDebugEnabled()) {
                        containerLog.debug(getStoreName() + ": No persisted data object found");
                    }
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    containerLog.error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    try {
                        if (rst != null) {
                            rst.close();
                        }
                    } catch (SQLException e) {
                        // Ignore
                    }
                    if (ois != null) {
                        try {
                            ois.close();
                        } catch (IOException e) {
                            // Ignore
                        }
                    }
                    Thread.currentThread().setContextClassLoader(oldThreadContextCL);
                    release(_conn);
                }
                numberOfTries--;
            }
        }

        return _session;
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

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();

                if (_conn == null) {
                    return;
                }

                try {
                    remove(id, _conn);
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }

        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(sm.getString(getStoreName() + ".removing", id, sessionTable));
        }
    }

    /**
     * Remove the Session with the specified session identifier from
     * this Store, if present.  If no such Session is present, this method
     * takes no action.
     *
     * @param id Session identifier of the Session to be removed
     * @param _conn open connection to be used
     * @throws SQLException if an error occurs while talking to the database
     */
    private void remove(String id, Connection _conn) throws SQLException {
        if (preparedRemoveSql == null) {
            String removeSql = "DELETE FROM " + sessionTable
                    + " WHERE " + sessionIdCol + " = ?  AND "
                    + sessionAppCol + " = ?";
            preparedRemoveSql = _conn.prepareStatement(removeSql);
        }

        preparedRemoveSql.setString(1, id);
        preparedRemoveSql.setString(2, getName());
        preparedRemoveSql.execute();
    }

    /**
     * Remove all of the Sessions in this Store.
     *
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void clear() throws IOException {

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return;
                }

                try {
                    if (preparedClearSql == null) {
                        String clearSql = "DELETE FROM " + sessionTable
                             + " WHERE " + sessionAppCol + " = ?";
                        preparedClearSql = _conn.prepareStatement(clearSql);
                    }

                    preparedClearSql.setString(1, getName());
                    preparedClearSql.execute();
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } finally {
                    release(_conn);
                }
                numberOfTries--;
            }
        }
    }

    /**
     * Save a session to the Store.
     *
     * @param session the session to be stored
     * @exception IOException if an input/output error occurs
     */
    @Override
    public void save(Session session) throws IOException {
        ObjectOutputStream oos = null;
        ByteArrayOutputStream bos = null;
        ByteArrayInputStream bis = null;
        InputStream in = null;

        synchronized (this) {
            int numberOfTries = 2;
            while (numberOfTries > 0) {
                Connection _conn = getConnection();
                if (_conn == null) {
                    return;
                }

                try {
                    // If sessions already exist in DB, remove and insert again.
                    // TODO:
                    // * Check if ID exists in database and if so use UPDATE.
                    remove(session.getIdInternal(), _conn);

                    bos = new ByteArrayOutputStream();
                    oos = new ObjectOutputStream(new BufferedOutputStream(bos));

                    ((StandardSession) session).writeObjectData(oos);
                    oos.close();
                    oos = null;
                    byte[] obs = bos.toByteArray();
                    int size = obs.length;
                    bis = new ByteArrayInputStream(obs, 0, size);
                    in = new BufferedInputStream(bis, size);

                    if (preparedSaveSql == null) {
                        String saveSql = "INSERT INTO " + sessionTable + " ("
                           + sessionIdCol + ", " + sessionAppCol + ", "
                           + sessionDataCol + ", " + sessionValidCol
                           + ", " + sessionMaxInactiveCol + ", "
                           + sessionLastAccessedCol
                           + ") VALUES (?, ?, ?, ?, ?, ?)";
                       preparedSaveSql = _conn.prepareStatement(saveSql);
                    }

                    preparedSaveSql.setString(1, session.getIdInternal());
                    preparedSaveSql.setString(2, getName());
                    preparedSaveSql.setBinaryStream(3, in, size);
                    preparedSaveSql.setString(4, session.isValid() ? "1" : "0");
                    preparedSaveSql.setInt(5, session.getMaxInactiveInterval());
                    preparedSaveSql.setLong(6, session.getLastAccessedTime());
                    preparedSaveSql.execute();
                    // Break out after the finally block
                    numberOfTries = 0;
                } catch (SQLException e) {
                    manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".SQLException", e));
                    if (dbConnection != null)
                        close(dbConnection);
                } catch (IOException e) {
                    // Ignore
                } finally {
                    if (oos != null) {
                        oos.close();
                    }
                    if (bis != null) {
                        bis.close();
                    }
                    if (in != null) {
                        in.close();
                    }

                    release(_conn);
                }
                numberOfTries--;
            }
        }

        if (manager.getContainer().getLogger().isDebugEnabled()) {
            manager.getContainer().getLogger().debug(sm.getString(getStoreName() + ".saving",
                    session.getIdInternal(), sessionTable));
        }
    }


    // --------------------------------------------------------- Protected Methods

    /**
     * Check the connection associated with this store, if it's
     * <code>null</code> or closed try to reopen it.
     * Returns <code>null</code> if the connection could not be established.
     *
     * @return <code>Connection</code> if the connection succeeded
     */
    protected Connection getConnection() {
        Connection conn = null;
        try {
            conn = open();
            if (conn == null || conn.isClosed()) {
                manager.getContainer().getLogger().info(sm.getString(getStoreName() + ".checkConnectionDBClosed"));
                conn = open();
                if (conn == null || conn.isClosed()) {
                    manager.getContainer().getLogger().info(sm.getString(getStoreName() + ".checkConnectionDBReOpenFail"));
                }
            }
        } catch (SQLException ex) {
            manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".checkConnectionSQLException",
                    ex.toString()));
        }

        return conn;
    }

    /**
     * Open (if necessary) and return a database connection for use by
     * this Store.
     *
     * @return database connection ready to use
     *
     * @exception SQLException if a database error occurs
     */
    protected Connection open() throws SQLException {

        // Do nothing if there is a database connection already open
        if (dbConnection != null)
            return dbConnection;

        if (dataSourceName != null && dataSource == null) {
            Context initCtx;
            try {
                initCtx = new InitialContext();
                Context envCtx = (Context) initCtx.lookup("java:comp/env");
                this.dataSource = (DataSource) envCtx.lookup(this.dataSourceName);
            } catch (NamingException e) {
                manager.getContainer().getLogger().error(
                        sm.getString(getStoreName() + ".wrongDataSource",
                                this.dataSourceName), e);
           }
        }

        if (dataSource != null) {
            return dataSource.getConnection();
        }

        // Instantiate our database driver if necessary
        if (driver == null) {
            try {
                Class<?> clazz = Class.forName(driverName);
                driver = (Driver) clazz.newInstance();
            } catch (ClassNotFoundException ex) {
                manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".checkConnectionClassNotFoundException",
                        ex.toString()));
                throw new SQLException(ex);
            } catch (InstantiationException ex) {
                manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".checkConnectionClassNotFoundException",
                        ex.toString()));
                throw new SQLException(ex);
            } catch (IllegalAccessException ex) {
                manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".checkConnectionClassNotFoundException",
                        ex.toString()));
                throw new SQLException(ex);
            }
        }

        // Open a new connection
        Properties props = new Properties();
        if (connectionName != null)
            props.put("user", connectionName);
        if (connectionPassword != null)
            props.put("password", connectionPassword);
        dbConnection = driver.connect(connectionURL, props);
        dbConnection.setAutoCommit(true);
        return dbConnection;

    }

    /**
     * Close the specified database connection.
     *
     * @param dbConnection The connection to be closed
     */
    protected void close(Connection dbConnection) {

        // Do nothing if the database connection is already closed
        if (dbConnection == null)
            return;

        // Close our prepared statements (if any)
        try {
            preparedSizeSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }
        this.preparedSizeSql = null;

        try {
            preparedSaveSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }
        this.preparedSaveSql = null;

        try {
            preparedClearSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }

        try {
            preparedRemoveSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }
        this.preparedRemoveSql = null;

        try {
            preparedLoadSql.close();
        } catch (Throwable f) {
            ExceptionUtils.handleThrowable(f);
        }
        this.preparedLoadSql = null;

        // Commit if autoCommit is false
        try {
            if (!dbConnection.getAutoCommit()) {
                dbConnection.commit();
            }
        } catch (SQLException e) {
            manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".commitSQLException"), e);
        }

        // Close this database connection, and log any errors
        try {
            dbConnection.close();
        } catch (SQLException e) {
            manager.getContainer().getLogger().error(sm.getString(getStoreName() + ".close", e.toString())); // Just log it here
        } finally {
            this.dbConnection = null;
        }

    }

    /**
     * Release the connection, if it
     * is associated with a connection pool.
     *
     * @param conn The connection to be released
     */
    protected void release(Connection conn) {
        if (dataSource != null) {
            close(conn);
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

        if (dataSourceName == null) {
            // If not using a connection pool, open a connection to the database
            this.dbConnection = getConnection();
        }

        super.startInternal();
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

        super.stopInternal();

        // Close and release everything associated with our db.
        if (dbConnection != null) {
            try {
                dbConnection.commit();
            } catch (SQLException e) {
                // Ignore
            }
            close(dbConnection);
        }
    }
}
