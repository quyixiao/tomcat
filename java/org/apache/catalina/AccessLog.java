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

package org.apache.catalina;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;


/**
 * Intended for use by a {@link Valve} to indicate that the {@link Valve}
 * provides access logging. It is used by the Tomcat internals to identify a
 * Valve that logs access requests so requests that are rejected
 * earlier in the processing chain can still be added to the access log.
 * Implementations of this interface should be robust against the provided
 * {@link Request} and {@link Response} objects being null, having null
 * attributes or any other 'oddness' that may result from attempting to log
 * a request that was almost certainly rejected because it was mal-formed.
 * 对任何一个系统，一个强大的日志记录功能是相当重要且必要的，根据日志的记录可及时常所系统运行时的健康状态及故障定位，然而，作为Web 容器存在
 * 另外一种日志，访问日志，访问日志一般会记录客户端访问相关的信息。包括客户端IP , 请求时间，请求协议，请求方法，请求字节数，响应码，会话ID
 * 处理时间 ，通过访问日志可以统计用户访问量，访问时间分布等规律以及个人爱好，而这些数据可以帮助公司的运营策略上做出rvun，这一节主要就是
 * 探究Tomcat 的访问日志组件 。
 *
 * 如果让你来设计一个访问日志的组件，你会如何来设计 ，你应该会很快就想到访问日志的核心功能就是将信息记录下来，至于要记录到哪里，以哪种
 * 形式来记录，我们先不管，于是很快想到了以以面向接口编程的方式定义一个接口AccessLog ，方法名命名为log，需要传递参数包含请求对象和响应对象 。
 * 代码如下：
 *
 *  public interface AccessLog{
 *      public void log(Request request,Response );
 *  }
 *  定义好一个接口的良好开始，接下来要考虑的事就是需要哪些类型的组件，针对前面的记录到哪里了，以哪种形式的记录，我们最熟悉也最先想到的肯定就是以文件
 *  形式记录到磁盘里，于是我们实现了一个文件记录的日志访问组件 。
 *
 *  public class FileAcessLog implements AccessLog{
 *      String message  = request 与response 中的值拼组成你要的字符串。
 *      PrintWriter writer = new PrintWriter(new BufferedWriter(new FileOutputStream("c://accesslog.log",true),charset),128000),false );
 *      writer.println(message);
 *      writer.flush();
 *  }
 *  看起来这是一个简单且不错的文件记录访问日志的组件的实现，其中 代码简单明了，采用PrintWriter 对象用于写入操作，而且使用了BufferedWriter
 *  对象实现缓冲，之所以把缓冲器的大小设置为128000是根据经验来得出的一个合适的值，OutputStreamWriter 则可以对字符进行编码。 此处使用CharSet
 *  工具提供的默认编码，FileOutputStream 则指定写入文件的路径及文件名，而true 表明追加的日志而非覆盖 。
 *  假如你觉得用SQL语言来统计日志的信息让你更加得心应手，那么写文件就不符合你的需求，我们需要另外一个实现，通过JDBC将昌盛记录到数据库中。
 * 于是你必须另外创建一个JDBCAccessLog 类并重新实现log 方法，使用JDBC操作数据库大家再熟悉不过了，受篇幅限制，这里就不再详细实现细节 。
 * 但有一个前提就是你必须告诉数据库创建一张特定的表且表的结构要根据访问的信息定义好。
 *
 * public class JDBCAccesslog implements AccessLog{
 *     public void log(Request request ,Response response){
 *         通过JDBC 把request 和response 包含的访问信息组成一个SQL语句插入到数据库。
 *     }
 * }
 *
 * 你还可以根据自己的需求定义各种各样的访问组件，只需要实现AcessLog 接口，但有时你也会使用多个访问日志组件，例如又写入文件又持久化到数据库。
 * 这时我们还可以提供一个适配器给它
 *
 * public class AcessLogAdapter implements AccessLog{
 *     private AccessLog[] logs ;
 *     public AccessLogAdapter(AcessLog log){
 *         logs = new AccessLog[]{log};
 *     }
 *     public void add(AcessLog log ){
 *         AccessLog newArray[] = Arrays.copyOf(logs,logs.length + 1 );
 *         newArray[newArray.length -1 ] = log ;
 *         logs = newArray;
 *     }
 *     public void log(Request request ,Response response){
 *         for(AccessLog log : logs){
 *             log.log(request,response);
 *         }
 *     }
 * }
 *
 * 经过以上的设计一个良好的访问日志组件已经成型，而这也是Tomcat 的访问日志组件的设计思路，另外，Tomcat 考虑到模块化和可配置扩展，它把访问日志组件作为一个
 * 管道中的阀门，这样就可以通过Tomcat的服务器配置文件配置实现访问日志的记录功能，这可以在任意容器中进行配置。
 *
 *
 *
 *
 *
 */
public interface AccessLog {

    /**
     * Name of request attribute used to override the remote address recorded by
     * the AccessLog.
     */
    public static final String REMOTE_ADDR_ATTRIBUTE =
            "org.apache.catalina.AccessLog.RemoteAddr";

    /**
     * Name of request attribute used to override remote host name recorded by
     * the AccessLog.
     */
    public static final String REMOTE_HOST_ATTRIBUTE =
            "org.apache.catalina.AccessLog.RemoteHost";

    /**
     * Name of request attribute used to override the protocol recorded by the
     * AccessLog.
     */
    public static final String PROTOCOL_ATTRIBUTE =
            "org.apache.catalina.AccessLog.Protocol";

    /**
     * Name of request attribute used to override the server name recorded by
     * the AccessLog.
     */
    public static final String SERVER_NAME_ATTRIBUTE =
            "org.apache.catalina.AccessLog.ServerName";

    /**
     * Name of request attribute used to override the server port recorded by
     * the AccessLog.
     */
    public static final String SERVER_PORT_ATTRIBUTE =
            "org.apache.catalina.AccessLog.ServerPort";


    /**
     * Add the request/response to the access log using the specified processing
     * time.
     *
     * @param request   Request (associated with the response) to log
     * @param response  Response (associated with the request) to log
     * @param time      Time taken to process the request/response in
     *                  milliseconds (use 0 if not known)
     */
    public void log(Request request, Response response, long time);

    /**
     * Should this valve set request attributes for IP address, hostname,
     * protocol and port used for the request? This are typically used in
     * conjunction with the {@link org.apache.catalina.valves.AccessLogValve}
     * which will otherwise log the original values.
     *
     * The attributes set are:
     * <ul>
     * <li>org.apache.catalina.RemoteAddr</li>
     * <li>org.apache.catalina.RemoteHost</li>
     * <li>org.apache.catalina.Protocol</li>
     * <li>org.apache.catalina.ServerPost</li>
     * </ul>
     *
     * @param requestAttributesEnabled  <code>true</code> causes the attributes
     *                                  to be set, <code>false</code> disables
     *                                  the setting of the attributes.
     */
    public void setRequestAttributesEnabled(boolean requestAttributesEnabled);

    /**
     * @see #setRequestAttributesEnabled(boolean)
     * @return <code>true</code> if the attributes will be logged, otherwise
     *         <code>false</code>
     */
    public boolean getRequestAttributesEnabled();
}
