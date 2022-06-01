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


package org.apache.juli.logging;

/**
 * <p>A simple logging interface abstracting logging APIs.  In order to be
 * instantiated successfully by {@link LogFactory}, classes that implement
 * this interface must have a constructor that takes a single String
 * parameter representing the "name" of this Log.</p>
 *
 * <p> The six logging levels used by <code>Log</code> are (in order):</p>
 * <ol>
 * <li>trace (the least serious)</li>
 * <li>debug</li>
 * <li>info</li>
 * <li>warn</li>
 * <li>error</li>
 * <li>fatal (the most serious)</li>
 * </ol>
 * <p>The mapping of these log levels to the concepts used by the underlying
 * logging system is implementation dependent.
 * The implementation should ensure, though, that this ordering behaves
 * as expected.</p>
 *
 * <p>Performance is often a logging concern.
 * By examining the appropriate property,
 * a component can avoid expensive operations (producing information
 * to be logged).</p>
 *
 * <p> For example,
 * <code>
 *    if (log.isDebugEnabled()) {
 *        ... do something expensive ...
 *        log.debug(theResult);
 *    }
 * </code>
 * </p>
 *
 * <p>Configuration of the underlying logging system will generally be done
 * external to the Logging APIs, through whatever mechanism is supported by
 * that system.</p>
 *
 * @author <a href="mailto:sanders@apache.org">Scott Sanders</a>
 * @author Rod Waldhoff
 * 日志对每一个系统来说都是必不可少的一部分，它可以记录运行时报错的信息，也可以在调试时使用，使用好日志对我们后期的系统维护是相当重要的。
 * 像Tomcat 这么大的系统，对日志的处理是非常重要，Tomcat提供了一个日志的接口Log 供系统使用，这个接口只提供了使用方法，而不管具体使用什么方法 。
 * 实现日志记录，接口中提供了很多种的方法来记录日志，每种方法代表不一样的日志级别，实际使用中根据不同的级别使用不同的方法 。
 *
 * Tomcat 中使用日志实现类是DirectJDKLog，从名字上来看，就大概知道使用JDK 自带的日志工具，实际上它是JDK 的java.util.logging 日志包的基础上
 * 进行封装，日志架构使用了工厂模式进行设置，LogFactory则通过getLog方法返回DirectJDKLog对象，这样，以后如果要改用别的日志包，只须要另外
 * 添加一个实现了Log 接口的XXXLog类，然后通过getLog方法返回即可 。
 *
 *
 * Tomcat 采用JDK的日志工具可以不引入第三方Jar 包和配置，可以与JDK更紧密的结合，日志的使用简单方便，通过配置logging.properties 即可以
 * 满足大多数的要求，引配置文件路径为%JAVA_HOME%/jre/lob/logging.properties， 该配置文件 逻辑判断如下 。
 * String fname = System.getProperty("java.util.logging.config.file");
 *      if(fname == null){
 *          fname = System.getProperty("java.home");
 *          if(fname == null){
 *          throw new Error("Can t find java.home");
 *          }
 *          File f = new File(fname ,"lib");
 *          f = new File(f,"logging.properties");
 *          fname = f.getCanonicalPath();
 *      }
 * }
 * 先从System获取java.util.logging.config.file属性值，如果存在，则直接作为配置文件路径，否则，获取System的java.home 属性，这个属性
 * 的值为%JAVA_HOME%\jre ，最后组成jdk 自带的日志工作的配置文件路径 。
 * 在实际运行时，tomcat 并没有直接使用默认的配置，从启动批处理文件，catalina.bat 可以看到下面的两行代码 。
 *
 * -Djava.util.logging.config.file = %CATALINA_BASE%\config\config.properties
 * -Djava.util.logging.manager=org.apache.juli.ClassLoaderLogManager
 *
 * Tomcat 所logging.properties 配置文件放到 %CATALINA_BASE%\config 下，并且把java.util.logging.manager 属性配置成org.apache.juli.ClassLoaderLogManager
 * ，即重写一个LogManager
 *
 * 理解了上面的几点，Tomcat 的日志框架就基本明朗了，Tomcat 采用工厂模式生成日志对象，底层使用了JDK自带的日志工具，而没有用第三方的日志工具。
 * 以减少包的引用，没有采用JDK日志工具的默认配置，是通过配置系统变量和重写某些类达到特定的效果 。
 *
 *
 *
 *
 *
 *
 *
 *
 *
 *
 */
public interface Log {


    // ----------------------------------------------------- Logging Properties


    /**
     * <p> Is debug logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than debug. </p>
     *
     * @return <code>true</code> if debug level logging is enabled, otherwise
     *         <code>false</code>
     */
    public boolean isDebugEnabled();


    /**
     * <p> Is error logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than error. </p>
     *
     * @return <code>true</code> if error level logging is enabled, otherwise
     *         <code>false</code>
     */
    public boolean isErrorEnabled();


    /**
     * <p> Is fatal logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than fatal. </p>
     *
     * @return <code>true</code> if fatal level logging is enabled, otherwise
     *         <code>false</code>
     */
    public boolean isFatalEnabled();


    /**
     * <p> Is info logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than info. </p>
     *
     * @return <code>true</code> if info level logging is enabled, otherwise
     *         <code>false</code>
     */
    public boolean isInfoEnabled();


    /**
     * <p> Is trace logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than trace. </p>
     *
     * @return <code>true</code> if trace level logging is enabled, otherwise
     *         <code>false</code>
     */
    public boolean isTraceEnabled();


    /**
     * <p> Is warn logging currently enabled? </p>
     *
     * <p> Call this method to prevent having to perform expensive operations
     * (for example, <code>String</code> concatenation)
     * when the log level is more than warn. </p>
     *
     * @return <code>true</code> if warn level logging is enabled, otherwise
     *         <code>false</code>
     */
    public boolean isWarnEnabled();


    // -------------------------------------------------------- Logging Methods


    /**
     * <p> Log a message with trace log level. </p>
     *
     * @param message log this message
     */
    public void trace(Object message);


    /**
     * <p> Log an error with trace log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    public void trace(Object message, Throwable t);


    /**
     * <p> Log a message with debug log level. </p>
     *
     * @param message log this message
     */
    public void debug(Object message);


    /**
     * <p> Log an error with debug log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    public void debug(Object message, Throwable t);


    /**
     * <p> Log a message with info log level. </p>
     *
     * @param message log this message
     */
    public void info(Object message);


    /**
     * <p> Log an error with info log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    public void info(Object message, Throwable t);


    /**
     * <p> Log a message with warn log level. </p>
     *
     * @param message log this message
     */
    public void warn(Object message);


    /**
     * <p> Log an error with warn log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    public void warn(Object message, Throwable t);


    /**
     * <p> Log a message with error log level. </p>
     *
     * @param message log this message
     */
    public void error(Object message);


    /**
     * <p> Log an error with error log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    public void error(Object message, Throwable t);


    /**
     * <p> Log a message with fatal log level. </p>
     *
     * @param message log this message
     */
    public void fatal(Object message);


    /**
     * <p> Log an error with fatal log level. </p>
     *
     * @param message log this message
     * @param t log this cause
     */
    public void fatal(Object message, Throwable t);


}
