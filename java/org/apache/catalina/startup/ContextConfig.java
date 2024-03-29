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
package org.apache.catalina.startup;

import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URISyntaxException;
import java.net.URL;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Enumeration;
import java.util.HashMap;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Properties;
import java.util.Set;
import java.util.StringTokenizer;
import java.util.concurrent.ConcurrentHashMap;

import javax.naming.Binding;
import javax.naming.NameNotFoundException;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.annotation.HandlesTypes;

import org.apache.catalina.Authenticator;
import org.apache.catalina.Container;
import org.apache.catalina.Context;
import org.apache.catalina.Engine;
import org.apache.catalina.Globals;
import org.apache.catalina.Host;
import org.apache.catalina.Lifecycle;
import org.apache.catalina.LifecycleEvent;
import org.apache.catalina.LifecycleListener;
import org.apache.catalina.Pipeline;
import org.apache.catalina.Server;
import org.apache.catalina.Service;
import org.apache.catalina.Valve;
import org.apache.catalina.Wrapper;
import org.apache.catalina.core.ContainerBase;
import org.apache.catalina.core.StandardContext;
import org.apache.catalina.core.StandardEngine;
import org.apache.catalina.core.StandardHost;
import org.apache.catalina.deploy.ErrorPage;
import org.apache.catalina.deploy.FilterDef;
import org.apache.catalina.deploy.FilterMap;
import org.apache.catalina.deploy.LoginConfig;
import org.apache.catalina.deploy.SecurityConstraint;
import org.apache.catalina.deploy.ServletDef;
import org.apache.catalina.deploy.WebXml;
import org.apache.catalina.util.ContextName;
import org.apache.catalina.util.Introspection;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.naming.resources.DirContextURLConnection;
import org.apache.naming.resources.FileDirContext;
import org.apache.naming.resources.ResourceAttributes;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.bcel.classfile.AnnotationElementValue;
import org.apache.tomcat.util.bcel.classfile.AnnotationEntry;
import org.apache.tomcat.util.bcel.classfile.ArrayElementValue;
import org.apache.tomcat.util.bcel.classfile.ClassFormatException;
import org.apache.tomcat.util.bcel.classfile.ClassParser;
import org.apache.tomcat.util.bcel.classfile.ElementValue;
import org.apache.tomcat.util.bcel.classfile.ElementValuePair;
import org.apache.tomcat.util.bcel.classfile.JavaClass;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.descriptor.DigesterFactory;
import org.apache.tomcat.util.descriptor.InputSourceUtil;
import org.apache.tomcat.util.descriptor.XmlErrorHandler;
import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSet;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.scan.Jar;
import org.apache.tomcat.util.scan.JarFactory;
import org.xml.sax.InputSource;
import org.xml.sax.SAXParseException;

/**
 * Startup event listener for a <b>Context</b> that configures the properties
 * of that Context, and the associated defined servlets.
 *
 * @author Craig R. McClanahan
 * @author Jean-Francois Arcand
 * ContextConfig监听器感兴趣的几个事件为，AFTER_INIT_EVENT,BEFORE_START_EVENT ,  CCOPNFIGURE_START_EVENT ,AFTER_START_EVENT
 * CONFIGURE_STOP_EVENT 和 AFTER_DESTORY_EVENT等，按照TOMCAT 的生命同期，这些事件的顺序为AFTER_INIT_EVENT,BEFORE_START_event
 * ,configure_start_event , after_start_event , configure_stop_event ,after_destory_evnet ,根据不同的响应事件，Config 监听器都做不同的事情 。
 *
 * 1. 当after_init_event 事件发生，会调用ContextConfig 监听器的init 方法，init方法的主要工作如下。
 *
 * 创建degester 对象，指定解析规则，因为在HostConfig jkkd中只是根据 Context 节点的属性创建了一个Context 对象，指定解析规则，因为在HostConfig
 * 监听器中只是根据Context 节点属性创建的一个Context对象，但其实<Context>节点还有很多的
 *
 * 当AFTER_INIT_EVENT 事件发生时，会调用ContextConfig 监听器的init 方法，init 方法的工作如下
 *  1创建 Digester 对象，指定解析规则，因为HostConfig 监听器中只是根据<Context> 节点属性创建一个Context 对象，但其实<Context> 节点还有很多子节点
 *  需要解析并设置到Context 对象中，另外，Tomcat 中还有两个默认的Context 配置文件需要设置到Context 对象作为默认的属性，一个为config/context.xml
 *  文件，另外一个为config/[EngineName]/[HostName]/context.default 文件，所以Digester 的解析工作分为两部分，一部分是解析默认的配置文件，第二部分
 *  是解析<Context> 子节，子节点包括InstanceListener ,Listener,Loader ,Manager ,Store ,Paramter ,Realm ,Resources, ResourceLink
 *  Value ,WatchedResource ,WrapperLifecycle , WrapperListener ,JarScaner ,Ejb , Environment,LocalEjb,Resource ,
 *  ResourceEnvRef ,ServiceRef , Transaction 元素
 *
 *  2. 用第1步创建的Digester 对象按顺序解析，conf/context.xml,config/[EngineName]/[HostName]/context.xml.default ,/MeTA-INF/context.xml
 *  等文件，必须按这个顺序，先用全局配置设置默认属性，再用Host 级别的配置设置属性，最后用Context 级别的配置设置属性，这种顺序保证了特定的属性值
 *  可以覆盖默认的属性值，例如对相同的属性值reloadable ，Context 级别的配置文件设置为true ，而全局配置文件为false , 于是Context 的reloadable
 *  属性最终的值为true , 创建用于解析web.xml 文件的Digester 对象 。
 *  根据Context 对象的doBase 属性做一些调整工作，例如默认把WAR 包解压成相应的目录形式，对于不解压的WAR 包则要检验WAR 包的合法性。
 *  当BEFORE_START_EVENT 事件发生时，会调用ContextConfig监听器的beforeStart方法，beforeStart主要的工作如下 。
 *  根据配置属性做一些预防JAR包被锁定的工作，由于Windows 系统可能会将某些JAR包锁定，从而导致重新加载失败，这是因为重新加载需要把原来的Web
 *  应用完全删除后，再把新的Web应用加载进来，但是假如某些Jar包被锁定了，就不能被删除了，除非把整个Tomcat停止，这里解决思路是：将Web 应用
 *  项目根据部署的次数重命名并复制到%CTALINA_HOME%/temp 临时目录下（例如第一次部署就是1-myTomcat.war） ，并把Context对象的docBase
 *  指向临时目录下的Web项目，这样每次重新部署都有一个新的应用名，就算原来的应用的某些Jar 包被锁定也不会导致部署失败。
 *
 * 当CONFIGURE_START_EVENT 事件发生时，会调用ContextConfig 监听器的configureStart 方法，configureStart 主要工作就是为了扫描
 * Web 应用部署描述文件web.xml ，并且使用规范将它们合并起来，定义范围比较大的配置会被范围较小的配置覆盖，例如，Web 容器的全局配置文件 。
 * web.xml 会被Host 级别或Web 应用级别的web.xml覆盖 ，详细步骤如下 。
 * 1.将Context 级别的web.xml解析复制到WebXml对象中。
 * 2.扫描Web应用/WEB-INF/lib目录下的所有Jar包里面的/META-INF/web-fragment.xml 文件生成多个Web Fragment ，Web Fragment 本质上还是WebXML对象 。
 * 3.将这些Web Fragment 根据Servlet规范进行排序，主要是根据绝对顺序和相对顺序 。
 * 4.根据Web Fragment 扫描每个Jar 包中的ServletContainerInitializer ，把它们添加到Context容器中的Map中，方便 Context容器初始化时调用它们 。
 * 5.解析Web应用/WEB-INF/classes 目录下被注解Servlet ，Filter 或Listener ，对应的注解符为@WebServlet, @WebFilter 和@WebListener
 * 6.解析Web 应用相关的JAr 包里的注解，其中同样包含了Servlet,Filter 和Listener
 * 7.合并Web Fragment 到总WebXml对象中。
 * 8.合并默认的web.xml 对应的WebXml到总的WebXml对象中，默认的web.xml 包括Web 容器全局的web.xml，Host容器级别的Web.xml ,路径分别 为Tomcat
 * 根目录中的/config/web.xml 文件和config/engine name/host name /web.xml.default 文件 。
 * 9.将有些使用了<jsp-file> 元素定义的Servlet 转化为JspServlet模式 。
 * 10. 将WebXml 对象中的上下文参数，EJB，环境变量，错误页面，Filter ，监听器，安全认证，会话配置，标签，欢迎页，JSP属性组成等设置到Context
 * 容器中，而Servlet则转换成Wrapper 放到 Context 中。
 * 11.把合并后的WebXml字符串格式以org.apache.tomcat.util.scan.Constansts.MERGERD_WEB_XML 作为属性键存放到Context属性中。
 * 12.扫描每个Jar包的/META-INF/resources/目录，将此目录下的静态资源添加到Context 容器中。
 * 13.将ServletContainerInitializer添加到Context 容器中。
 * 14.处理Servlet ，Filter 或Listener类中的注解，有三种注解，分别为类注解，字段注解，方法注解，分别把它们转化成对应的资源放到Context
 * 容器中，实例化这些对象时需要将注解注入到对象中。
 *
 *
 *
 * ContextConfig
 * 在3.3.4 节中我们讲到，Context 创建时会默认添加一个生命周期监听器， ContextConfig 该监听器一共处理6类事件，此处我们仅讲解其中与Context
 * 启动关系重大的3类，AFTER_INIT_EVENT， BEFORE_START_EVENT, CONFIGURE_START_EVENT ， 以便读取可以了解该类的Context 启动中的扮演的角色 。
 *
 */
public class ContextConfig implements LifecycleListener {

    private static final Log log = LogFactory.getLog( ContextConfig.class );


    /**
     * The string resources for this package.
     */
    protected static final StringManager sm =
        StringManager.getManager(Constants.Package);


    protected static final LoginConfig DUMMY_LOGIN_CONFIG =
        new LoginConfig("NONE", null, null, null);



    /**
     * The set of Authenticators that we know how to configure.  The key is
     * the name of the implemented authentication method, and the value is
     * the fully qualified Java class name of the corresponding Valve.
     */
    protected static final Properties authenticators;

    /**
     * The list of JARs that will be skipped when scanning a web application
     * for JARs. This means the JAR will not be scanned for web fragments, SCIs,
     * annotations or classes that match @HandlesTypes.
     */
    private static final Set<String> pluggabilityJarsToSkip =
            new HashSet<String>();

    static {
        // Load our mapping properties for the standard authenticators
        Properties props = new Properties();
        InputStream is = null;
        try {
            is = ContextConfig.class.getClassLoader().getResourceAsStream(
                    "org/apache/catalina/startup/Authenticators.properties");
            if (is != null) {
                props.load(is);
            }
        } catch (IOException ioe) {
            props = null;
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                }
            }
        }
        authenticators = props;
        // Load the list of JARS to skip
        addJarsToSkip(Constants.DEFAULT_JARS_TO_SKIP);
        addJarsToSkip(Constants.PLUGGABILITY_JARS_TO_SKIP);
    }

    public ContextConfig(){
        System.out.println("初始化 ");
    }

    private static void addJarsToSkip(String systemPropertyName) {
        String jarList = System.getProperty(systemPropertyName);
        if (jarList != null) {
            StringTokenizer tokenizer = new StringTokenizer(jarList, ",");
            while (tokenizer.hasMoreElements()) {
                String token = tokenizer.nextToken().trim();
                if (token.length() > 0) {
                    pluggabilityJarsToSkip.add(token);
                }
            }
        }

    }


    /**
     * Deployment count.
     */
    protected static long deploymentCount = 0L;


    /**
     * Cache of default web.xml fragments per Host
     */
    protected static final Map<Host,DefaultWebXmlCacheEntry> hostWebXmlCache =
        new ConcurrentHashMap<Host,DefaultWebXmlCacheEntry>();


    /**
     * Set used as the value for {@code JavaClassCacheEntry.sciSet} when there
     * are no SCIs associated with a class.
     */
    private static final Set<ServletContainerInitializer> EMPTY_SCI_SET = Collections.emptySet();


    // ----------------------------------------------------- Instance Variables
    /**
     * Custom mappings of login methods to authenticators
     */
    protected Map<String,Authenticator> customAuthenticators;


    /**
     * The Context we are associated with.
     */
    protected Context context = null;


    /**
     * The default web application's context file location.
     * @deprecated Unnecessary
     */
    @Deprecated
    protected String defaultContextXml = null;


    /**
     * The default web application's deployment descriptor location.
     */
    protected String defaultWebXml = null;


    /**
     * Track any fatal errors during startup configuration processing.
     */
    protected boolean ok = false;


    /**
     * Original docBase.
     */
    protected String originalDocBase = null;


    /**
     * Anti-locking docBase. It is a path to a copy of the web application
     * in the java.io.tmpdir directory. This path is always an absolute one.
     */
    private File antiLockingDocBase = null;


    /**
     * Map of ServletContainerInitializer to classes they expressed interest in.
     */
    protected final Map<ServletContainerInitializer, Set<Class<?>>> initializerClassMap =
            new LinkedHashMap<ServletContainerInitializer, Set<Class<?>>>();

    /**
     * Map of Types to ServletContainerInitializer that are interested in those
     * types.
     */
    protected final Map<Class<?>, Set<ServletContainerInitializer>> typeInitializerMap =
            new HashMap<Class<?>, Set<ServletContainerInitializer>>();

    /**
     * Cache of JavaClass objects (byte code) by fully qualified class name.
     * Only populated if it is necessary to scan the super types and interfaces
     * as part of the processing for {@link HandlesTypes}.
     */
    protected final Map<String,JavaClassCacheEntry> javaClassCache =
            new HashMap<String,JavaClassCacheEntry>();

    /**
     * Flag that indicates if at least one {@link HandlesTypes} entry is present
     * that represents an annotation.
     */
    protected boolean handlesTypesAnnotations = false;

    /**
     * Flag that indicates if at least one {@link HandlesTypes} entry is present
     * that represents a non-annotation.
     */
    protected boolean handlesTypesNonAnnotations = false;

    /**
     * The <code>Digester</code> we will use to process web application
     * deployment descriptor files.
     */
    protected Digester webDigester = null;
    protected WebRuleSet webRuleSet = null;

    /**
     * The <code>Digester</code> we will use to process web fragment
     * deployment descriptor files.
     */
    protected Digester webFragmentDigester = null;
    protected WebRuleSet webFragmentRuleSet = null;


    // ------------------------------------------------------------- Properties
    /**
     * Return the location of the default deployment descriptor
     */
    public String getDefaultWebXml() {
        if( defaultWebXml == null ) {
            defaultWebXml=Constants.DefaultWebXml;
        }

        return (this.defaultWebXml);

    }


    /**
     * Set the location of the default deployment descriptor
     *
     * @param path Absolute/relative path to the default web.xml
     */
    public void setDefaultWebXml(String path) {

        this.defaultWebXml = path;

    }


    /**
     * Return the location of the default context file
     * @deprecated Never changed from default
     */
    @Deprecated
    public String getDefaultContextXml() {
        if( defaultContextXml == null ) {
            defaultContextXml=Constants.DefaultContextXml;
        }

        return (this.defaultContextXml);

    }


    /**
     * Set the location of the default context file
     *
     * @param path Absolute/relative path to the default context.xml
     * @deprecated Unused
     */
    @Deprecated
    public void setDefaultContextXml(String path) {

        this.defaultContextXml = path;

    }


    /**
     * Sets custom mappings of login methods to authenticators.
     *
     * @param customAuthenticators Custom mappings of login methods to
     * authenticators
     */
    public void setCustomAuthenticators(
            Map<String,Authenticator> customAuthenticators) {
        this.customAuthenticators = customAuthenticators;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Process events for an associated Context.
     *
     * @param event The lifecycle event that has occurred
     */
    @Override
    public void lifecycleEvent(LifecycleEvent event) {

        // Identify the context we are associated with
        try {
            context = (Context) event.getLifecycle();
        } catch (ClassCastException e) {
            log.error(sm.getString("contextConfig.cce", event.getLifecycle()), e);
            return;
        }

        // Process the event that has occurred
        if (event.getType().equals(Lifecycle.CONFIGURE_START_EVENT)) {
            // StandardContext启动过程中触发，解析web.xml文件，将Servlet、Filter等添加到Context中
            configureStart();
        } else if (event.getType().equals(Lifecycle.BEFORE_START_EVENT)) {
            // StandardContext启动之前触发
            beforeStart();
        } else if (event.getType().equals(Lifecycle.AFTER_START_EVENT)) {
            // StandardContext启动之后触发
            // Restore docBase for management tools
            // 1. 当AFTER_START_EVENT事件发生时处理的逻辑比较简单，只是把Context容器的docBase指向另外一个目录上， 为了解决重新部署导致的
            // 锁问题， 而把项目复制到另外一个目录上，而docBase要指向它。
            if (originalDocBase != null) {
                context.setDocBase(originalDocBase);
            }
        } else if (event.getType().equals(Lifecycle.CONFIGURE_STOP_EVENT)) {
            // StandardContext停止过程中触发，将Servlet、Filter等从Context中移除
            configureStop();
        } else if (event.getType().equals(Lifecycle.AFTER_INIT_EVENT)) {
            // StandardContext初始化后触发，创建context.xml文件的解析器contextDigester，并解析context.xml文件，并且创建web.xml文件的解析器webDigester
            init();
        } else if (event.getType().equals(Lifecycle.AFTER_DESTROY_EVENT)) {
            // StandardContext销毁后触发，删除workDir ， destory方法主要用于删除工作目录
            destroy();
        }

    }


    // -------------------------------------------------------- protected Methods


    /**
     * Process the application classes annotations, if it exists.
     */
    protected void applicationAnnotationsConfig() {

        long t1=System.currentTimeMillis();

        WebAnnotationSet.loadApplicationAnnotations(context);

        long t2=System.currentTimeMillis();
        if (context instanceof StandardContext) {
            ((StandardContext) context).setStartupTime(t2-t1+
                    ((StandardContext) context).getStartupTime());
        }
    }


    /**
     * Set up an Authenticator automatically if required, and one has not
     * already been configured.
     * 基于解析完Web容器，检测Web应用部署描述中使用的安全角色名，当发现使用示定义的角色时，提示警告将未定义的角色添加到Context 安全角色列表中。
     */
    protected void authenticatorConfig() {

        LoginConfig loginConfig = context.getLoginConfig();
        if (loginConfig == null) {
            // Need an authenticator to support HttpServletRequest.login()
            loginConfig = DUMMY_LOGIN_CONFIG;
            context.setLoginConfig(loginConfig);
        }

        // Has an authenticator been configured already?
        if (context.getAuthenticator() != null)
            return;

        if (!(context instanceof ContainerBase)) {
            return;     // Cannot install a Valve even if it would be needed
        }

        // Has a Realm been configured for us to authenticate against?
        if (context.getRealm() == null) {
            log.error(sm.getString("contextConfig.missingRealm"));
            ok = false;
            return;
        }

        /*
         * First check to see if there is a custom mapping for the login
         * method. If so, use it. Otherwise, check if there is a mapping in
         * org/apache/catalina/startup/Authenticators.properties.
         */
        Valve authenticator = null;
        if (customAuthenticators != null) {
            authenticator = (Valve)
                customAuthenticators.get(loginConfig.getAuthMethod());
        }
        // 当Context 需要进行安全认证但是没有指定具体的Authenticator时，根据服务器配置自动创建默认的实例。
        if (authenticator == null) {
            if (authenticators == null) {
                log.error(sm.getString("contextConfig.authenticatorResources"));
                ok = false;
                return;
            }

            // Identify the class name of the Valve we should configure
            String authenticatorName = null;
            authenticatorName =
                    authenticators.getProperty(loginConfig.getAuthMethod());
            if (authenticatorName == null) {
                log.error(sm.getString("contextConfig.authenticatorMissing",
                                 loginConfig.getAuthMethod()));
                ok = false;
                return;
            }

            // Instantiate and install an Authenticator of the requested class
            try {
                Class<?> authenticatorClass = Class.forName(authenticatorName);
                authenticator = (Valve) authenticatorClass.newInstance();
            } catch (Throwable t) {
                ExceptionUtils.handleThrowable(t);
                log.error(sm.getString(
                                    "contextConfig.authenticatorInstantiate",
                                    authenticatorName),
                          t);
                ok = false;
            }
        }

        if (authenticator != null && context instanceof ContainerBase) {
            Pipeline pipeline = ((ContainerBase) context).getPipeline();
            if (pipeline != null) {
                ((ContainerBase) context).getPipeline().addValve(authenticator);
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                                    "contextConfig.authenticatorConfigured",
                                    loginConfig.getAuthMethod()));
                }
            }
        }

    }


    /**
     * Create and return a Digester configured to process the
     * web application deployment descriptor (web.xml).
     */
    public void createWebXmlDigester(boolean namespaceAware,
            boolean validation) {

        boolean blockExternal = context.getXmlBlockExternal();

        webRuleSet = new WebRuleSet(false);
        webDigester = DigesterFactory.newDigester(validation,
                namespaceAware, webRuleSet, blockExternal);
        webDigester.getParser();

        webFragmentRuleSet = new WebRuleSet(true);
        webFragmentDigester = DigesterFactory.newDigester(validation,
                namespaceAware, webFragmentRuleSet, blockExternal);
        webFragmentDigester.getParser();
    }


    /**
     * Create (if necessary) and return a Digester configured to process the
     * context configuration descriptor for an application.
     */
    protected Digester createContextDigester() {
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.setRulesValidation(true);
        Map<Class<?>, List<String>> fakeAttributes = new HashMap<Class<?>, List<String>>();
        List<String> objectAttrs = new ArrayList<String>();
        objectAttrs.add("className");
        fakeAttributes.put(Object.class, objectAttrs);
        // Ignore attribute added by Eclipse for its internal tracking
        List<String> contextAttrs = new ArrayList<String>();
        contextAttrs.add("source");
        fakeAttributes.put(StandardContext.class, contextAttrs);
        digester.setFakeAttributes(fakeAttributes);
        RuleSet contextRuleSet = new ContextRuleSet("", false);

        digester.addRuleSet(contextRuleSet);
        RuleSet namingRuleSet = new NamingRuleSet("Context/");
        digester.addRuleSet(namingRuleSet);
        return digester;
    }


    // 先获取Engine上配置的baseDir
    // 如果Engine上没有配置baseDir，那么则获取环境变量catalina.base所指定的路径
    // 如果catalina.base没有指定路径，那么则获取环境变量catalina.home所指定的路径
    protected String getBaseDir() {
        Container engineC=context.getParent().getParent();
        if( engineC instanceof StandardEngine ) {
            // 注意getBaseDir()方法内的实现
            return ((StandardEngine)engineC).getBaseDir();
        }
        return System.getProperty(Globals.CATALINA_BASE_PROP);
    }


    /**
     * Process the default configuration file, if it exists.
     */
    protected void contextConfig(Digester digester) {
        // 可以单独使用文件的方式配置Context的属性
        // 1. 在Context节点上可以配置defaultContextXml属性，指明配置context.xml文件的位置
        // 2. 默认情况下会取catalina.base/conf/context.xml
        // 3. 默认情况下也会取catalina.base/conf/engine名称/host名称/context.xml.default文件
        // 4. 取catalina.base/conf/engine名称/host名称/Context名称.xml

        // Open the default context.xml file, if it exists
        // 先看Context节点上是否配置了defaultContextXml属性
        if( defaultContextXml==null && context instanceof StandardContext ) {
            defaultContextXml = ((StandardContext)context).getDefaultContextXml();
        }
        // set the default if we don't have any overrides
        // 如果Context节点上没有配置defaultContextXml属性的话，那么则取默认值 conf/context.xml
        if( defaultContextXml==null ) getDefaultContextXml();

        if (!context.getOverride()) {
            File defaultContextFile = new File(defaultContextXml);
            // defaultContextXml如果是相对于路径，那么则相对于getBaseDir()
            // 所以默认请求情况下就是取得catalina.base路径下得conf/context.xml
            if (!defaultContextFile.isAbsolute()) {
                defaultContextFile =new File(getBaseDir(), defaultContextXml);
            }

            // 解析context.xml文件
            if (defaultContextFile.exists()) {
                try {
                    URL defaultContextUrl = defaultContextFile.toURI().toURL();
                    processContextConfig(digester, defaultContextUrl);
                } catch (MalformedURLException e) {
                    log.error(sm.getString(
                            "contextConfig.badUrl", defaultContextFile), e);
                }
            }

            // 取Host级别下的context.xml.default文件
            File hostContextFile = new File(getHostConfigBase(), Constants.HostContextXml);
            // 解析context.xml.default文件
            if (hostContextFile.exists()) {
                try {
                    URL hostContextUrl = hostContextFile.toURI().toURL();
                    processContextConfig(digester, hostContextUrl);
                } catch (MalformedURLException e) {
                    log.error(sm.getString(
                            "contextConfig.badUrl", hostContextFile), e);
                }
            }
        }

        // configFile属性是在Tomcat部署应用时设置的，对应的文件比如：catalina.base\conf\Catalina\localhost\ContextName.xml
        // 解析configFile文件
        if (context.getConfigFile() != null) {
            System.out.println(context.getConfigFile());
            processContextConfig(digester, context.getConfigFile());
        }
    }


    /**
     * Process a context.xml.
     */
    protected void processContextConfig(Digester digester, URL contextXml) {

        if (log.isDebugEnabled())
            log.debug("Processing context [" + context.getName()
                    + "] configuration file [" + contextXml + "]");

        InputSource source = null;
        InputStream stream = null;

        try {
            source = new InputSource(contextXml.toString());
            URLConnection xmlConn = contextXml.openConnection();
            xmlConn.setUseCaches(false);
            stream = xmlConn.getInputStream();
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.contextMissing",
                      contextXml) , e);
        }

        if (source == null)
            return;

        try {
            source.setByteStream(stream);
            digester.setClassLoader(this.getClass().getClassLoader());
            digester.setUseContextClassLoader(false);
            digester.push(context.getParent());
            digester.push(context);
            XmlErrorHandler errorHandler = new XmlErrorHandler();
            digester.setErrorHandler(errorHandler);
            digester.parse(source);
            if (errorHandler.getWarnings().size() > 0 ||
                    errorHandler.getErrors().size() > 0) {
                errorHandler.logFindings(log, contextXml.toString());
                ok = false;
            }
            if (log.isDebugEnabled()) {
                log.debug("Successfully processed context [" + context.getName()
                        + "] configuration file [" + contextXml + "]");
            }
        } catch (SAXParseException e) {
            log.error(sm.getString("contextConfig.contextParse",
                    context.getName()), e);
            log.error(sm.getString("contextConfig.defaultPosition",
                             "" + e.getLineNumber(),
                             "" + e.getColumnNumber()));
            ok = false;
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.contextParse",
                    context.getName()), e);
            ok = false;
        } finally {
            try {
                if (stream != null) {
                    stream.close();
                }
            } catch (IOException e) {
                log.error(sm.getString("contextConfig.contextClose"), e);
            }
        }
    }


    /**
     * Adjust docBase.
     */
    protected void fixDocBase()
        throws IOException {

        Host host = (Host) context.getParent();
        String appBase = host.getAppBase();

        File canonicalAppBase = new File(appBase);
        if (canonicalAppBase.isAbsolute()) {
            canonicalAppBase = canonicalAppBase.getCanonicalFile();
        } else {
            canonicalAppBase =
                new File(getBaseDir(), appBase)
                .getCanonicalFile();
        }

        String docBase = context.getDocBase();
        if (docBase == null) {
            // Trying to guess the docBase according to the path
            String path = context.getPath();
            if (path == null) {
                return;
            }
            ContextName cn = new ContextName(path, context.getWebappVersion());
            docBase = cn.getBaseName();
        }

        File file = new File(docBase);
        if (!file.isAbsolute()) {
            docBase = (new File(canonicalAppBase, docBase)).getCanonicalPath();
        } else {
            docBase = file.getCanonicalPath();
        }
        file = new File(docBase);
        String origDocBase = docBase;

        ContextName cn = new ContextName(context.getPath(),
                context.getWebappVersion());
        String pathName = cn.getBaseName();

        boolean unpackWARs = true;
        if (host instanceof StandardHost) {
            unpackWARs = ((StandardHost) host).isUnpackWARs();
            if (unpackWARs && context instanceof StandardContext) {
                unpackWARs =  ((StandardContext) context).getUnpackWAR();
            }
        }

        if (docBase.toLowerCase(Locale.ENGLISH).endsWith(".war") && !file.isDirectory()) {
            URL war = UriUtil.buildJarUrl(new File(docBase));
            if (unpackWARs) {
                docBase = ExpandWar.expand(host, war, pathName);
                file = new File(docBase);
                docBase = file.getCanonicalPath();
                if (context instanceof StandardContext) {
                    ((StandardContext) context).setOriginalDocBase(origDocBase);
                }
            } else {
                // 如果不需要解压，则验证war包
                ExpandWar.validate(host, war, pathName);
            }
        } else {

            File docDir = new File(docBase);
            if (!docDir.exists()) {
                // 如果docBase为一个有效的目录，而且存在与该目录同名的WAR包，同时需要解压部署，则重新解压WAR包
                File warFile = new File(docBase + ".war");
                if (warFile.exists()) {
                    URL war = UriUtil.buildJarUrl(warFile);
                    // 3.如果docBase为一个有效目录，而且存在与该目录同名的WAR包，同时需要解压部署
                    if (unpackWARs) {
                        // 3.1 解压WAR文件
                        docBase = ExpandWar.expand(host, war, pathName);
                        file = new File(docBase);
                        // 3.2 将Context的docBase更新为解压后的路径 （基于appBase的相对路径）
                        docBase = file.getCanonicalPath();
                    } else {
                        // 3.3 如果不需要解压部署，只检测WAR包，docBase为WAR包路径
                        docBase = warFile.getCanonicalPath();
                        ExpandWar.validate(host, war, pathName);
                    }
                }
                if (context instanceof StandardContext) {
                    ((StandardContext) context).setOriginalDocBase(origDocBase);
                }
            }
        }

        if (docBase.startsWith(canonicalAppBase.getPath() + File.separatorChar)) {
            docBase = docBase.substring(canonicalAppBase.getPath().length());
            docBase = docBase.replace(File.separatorChar, '/');
            if (docBase.startsWith("/")) {
                docBase = docBase.substring(1);
            }
        } else {
            docBase = docBase.replace(File.separatorChar, '/');
        }

        context.setDocBase(docBase);

    }

    // 根据配置属性做一些预防Jar包被锁定的工作，由于Windows系统可能会将某些Jar锁定，从而导致重加载失败，这是因为重加载需要把原来的Web应用
    // 完全删除后，再把新的Web应用重新加载进来 ， 但假如某些Jar包被锁定了，就不能删除了，这非把整个Tomcat停止了，这里解决的思路是：将Web
    // 应用项目根据部署次数重命名并复制到%CATALIN_HOME%/temp临时目录下（例如第一次部署就是1-myTomcat.war）,并把Context对象的docBase
    // 指向临时目录下的Web项目，这样每次重新热部署都有一个新的应用名， 就算原来应用的某些Jar包被锁定也不会导致部署失败。
    protected void antiLocking() {

        if ((context instanceof StandardContext)
                // 当Context的antiResourceLocking属性为true时，Tomcat会将当前的Web应用目录复制到临时文件夹下，以避免对原目录资源加锁。
            && ((StandardContext) context).getAntiResourceLocking()) {
            // 1 根据Host的appBase以及Context 的docBase计算docBase的绝对路径
            Host host = (Host) context.getParent();
            String appBase = host.getAppBase();
            String docBase = context.getDocBase();
            if (docBase == null)
                return;
            originalDocBase = docBase;

            File docBaseFile = new File(docBase);
            if (!docBaseFile.isAbsolute()) {
                File file = new File(appBase);
                if (!file.isAbsolute()) {
                    file = new File(getBaseDir(), appBase);
                }
                docBaseFile = new File(file, docBase);
            }

            String path = context.getPath();
            if (path == null) {
                return;
            }
            ContextName cn = new ContextName(path, context.getWebappVersion());
            docBase = cn.getBaseName();
            // 2. 计算临时文件夹的Web应用根目录或WAR包名
            // 2.1 Web目录：${Context生命周期内的部署次数}-${目录名}
            // 2.2 WAR包：${Context生命周期内部署次数}-${WAR包名}
            if (originalDocBase.toLowerCase(Locale.ENGLISH).endsWith(".war")) {
                antiLockingDocBase = new File(
                        System.getProperty("java.io.tmpdir"),
                        deploymentCount++ + "-" + docBase + ".war");
            } else {
                antiLockingDocBase = new File(
                        System.getProperty("java.io.tmpdir"),
                        deploymentCount++ + "-" + docBase);
            }
            antiLockingDocBase = antiLockingDocBase.getAbsoluteFile();

            if (log.isDebugEnabled())
                log.debug("Anti locking context[" + context.getName()
                        + "] setting docBase to " +
                        antiLockingDocBase.getPath());

            // Cleanup just in case an old deployment is lying around
            ExpandWar.delete(antiLockingDocBase);
            // 3. 复制Web目录或者WAR包到临时目录
            if (ExpandWar.copy(docBaseFile, antiLockingDocBase)) {
                // 4. 将Context 的docBase更新为临时目录下的Web应用目录或者WAR包路径
                context.setDocBase(antiLockingDocBase.getPath());
            }
            // 通过上面的讲解我们知道，无论是AFTER_INIT_EVNT还是BEFORE_START_EVENT处理，仍然属于启动前准备工作，以确保Context
            // 相关属性的准确性，而真正创建Wrapper的则是CONFIGURE_START_EVENT事件
        }
    }


    /**
     * Process a "init" event for this Context.
     * AFTER_INIT_EVENT 事件
     * 严格意义上来讲，该事件属于Context初始化阶段，它主要用于Context属性配置工作 。
     * 通过前面的讲解，我们可知道，Context 的创建可以有如下几个来源 。
     * 1.在实例化Server 时，解析server.xml 文中的的Context元素创建 。
     * 2.在HostConfig部署web应用时，解析Web应用（目录或者WAR包），根目录下的META-IN/context.xml文件创建，如果不存在该文件，则自动创建
     * 一个Context对象，仅对设置了path，docBase等少数几个属性。
     * 3.在Host部署Web应用时，解析$CATALINA_BASE/conf/<Engine名称>/<Host名称>下的Context 部署描述文件。
     *
     * 除了Context 创建时属性配置，将Tomcat提供的默认配置也一并添加到Context实例，如果Context 没有显式的配置这些属性，这部分工作即
     * 由该事件完成，具体过程如下 。
     *
     * 如果Context 的override属性为false ，即使用默认的配置。
     * 1. 如果存在 conf/context.xml 文件 （Catalina容器级默认配置），那么解析该文件，更新当前Context 实例属性；
     * 2. 如果存在conf/<Context名称>/<Host名称>/context.xml.default 文件（Host 级默认配置），那么解析该文件，更新当前 Context 实例属性。
     *
     * 如果Context 的configFile属性不为空，那么解析该文件，更新当前Context实例属性。
     *
     * 【注意】此处我们可能会产生疑问，为什么最后一步还要解析configFile呢？因为在服务器独立运行时，该文件和创建Context 时解析的文件是相同的。
     * 这是由于Digester解析时会将原有的属性覆盖，试想一下，如果在创建Context时，我们指定了crossContext属性，而这个属性恰好在默认的配置中
     * 也存在，此时我们希望的效果当然是忽略默认的属性，而如果不在最后一步解析configFile，此时的结果将会是默认属性覆盖指定属性，险此之外，
     * 在嵌入式启动Tomcat 时，Context 为手动创建，即使存在META-INF/context.xml文件，此时也需要解析configFile文件（即META-INF/context.xml文件）
     * 以此更新其属性。
     *
     * 通过上面的执行顺序，我们可以知道，Tomcat 中的Context 属性的优先级为：configFile,conf/<Engine名称>/<Host名称>/context.xml.default
     * conf/context.xml ，即Web应用配置优先级最高，其次为Host配置，Catalina容器的配置优先级最低 。
     *
     *
     *
     */
    protected void init() {
        // Called from StandardContext.init()
        // 1. 创建Digester对象，指定解析规则，因为在HostConfig监听器中只是根据<Context>节点属性创建了一个Context对象，但其实<Context>
        // 节点还有很多的子节点需要解析并设置到Context对象中，另外，Tomcat 中还有两个默认的Context配置文件需要设置到Context对象作为默认的
        // 属性，一个为conf/context.xml文件，另一个为config/[EngineName]/[HostName]/context.xml.default文件，所以Digester的解析
        // 工作分为两部分，一部分是解析默认配置文件，二部分为解析<Context>子节点，子节点包括InstanceListener,Listener,Loader，Manager
        // ,Store,Parameter,Realm,Resources , ResourceLink， Value, WatchedResource , WrapperLifecycle,WrapperListener ,
        // JarScanner,Ejb,Environment,LocalEjb,Resource ,ResourceEnvRef,ServiceRef,Transaction元素
        Digester contextDigester = createContextDigester();
        contextDigester.getParser();

        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.init"));
        context.setConfigured(false);
        ok = true;

        // 解析context.xml文件，注意，并不是<Context>节点，<Context>节点在解析Server.xml的时候就被解析了
        // 2. 用第1步创建的Digester对象按顺序解析conf/context.xml , conf/[EngineName]/[HostName]/context.xml.default
        // /META-INF/context.xml等文件，必须按这个顺序，先用全局配置设置默认属性，再用Host级别配置设置属性，最后用Context级别配置设置属性
        // ,这种顺序保证了特定属性值可以覆盖默认属性值，例如对于相同的属性reloadable ，Context级别配置文件设为true,而全局配置文件设为
        // false, 于是Context的reloadable属性最终的值为true.
        // 3. 创建用于解析web.xml文件的Digester对象 。
        // 4. 根据Context对象的docBase属性做一些调整工作，例如，默认把WAR包解压成相应目录形式，对于不解压的WAR包则要检验WAR包的合法性。
        contextConfig(contextDigester);

        // 创建解析web.xml文件的wDigester
        createWebXmlDigester(context.getXmlNamespaceAware(),
                context.getXmlValidation());
    }


    /**
     * Process a "before start" event for this Context.
     * BEFORE_START_EVENT事件 。
     * 该事件在Context 启动之前触发，用于更新Context的docBase属性和解决Web 目录锁的问题。
     *      更新Context的docBase属性主要是为了满足WAR部署的情况，当Web应用为一个WAR压缩包且需要解压部署（Host的unpackWAR为true,且Context
     * 的unpcakWAR为true）时，docBase属性指向的是解压后的文件目录，而非WAR包路径 。
     *
     *
     */
    protected synchronized void beforeStart() {

        try {
            fixDocBase();
        } catch (IOException e) {
            log.error(sm.getString(
                    "contextConfig.fixDocBase", context.getName()), e);
        }

        antiLocking();
    }

    /**
     * Process a "contextConfig" event for this Context.
     * Context在启动节点之前，触发了CONFIGURE_START_EVENT事件，ContextConfig正是通过该事件解析Web.xml，创建Wrapper（Servlet）
     * Filter,ServletContextListener等一系列的Web容器相关的对象，完成Web容器的初始化。
     *
     * configureStart 主要的工作就是扫描Web应用部署描述文件web.xml , 并且使用规范将它们合并起来，定义范围比较大的配置会被范围比较小的
     * 配置覆盖，例如，Web容器的全局配置文件web.xml会被Host级别或Web应用级别覆盖 。
     */
    protected synchronized void configureStart() {
        // Called from StandardContext.start()
        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.start"));

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("contextConfig.xmlSettings",
                    context.getName(),
                    Boolean.valueOf(context.getXmlValidation()),
                    Boolean.valueOf(context.getXmlNamespaceAware())));
        }
        // 根据web.xml文件对Context进行配置
        webConfig();
        // 如果StandardContext的ignoreAnnotations为false,则解析应用程序注解配置，添加相关的JNDI资源引用
        if (!context.getIgnoreAnnotations()) {
            applicationAnnotationsConfig();
        }
        if (ok) {
            validateSecurityRoles();
        }
        // Configure an authenticator if we need one
        // 用户验证配置
        if (ok)
            authenticatorConfig();

        // Dump the contents of this pipeline if requested
        if ((log.isDebugEnabled()) && (context instanceof ContainerBase)) {
            log.debug("Pipeline Configuration:");
            Pipeline pipeline = ((ContainerBase) context).getPipeline();
            Valve valves[] = null;
            if (pipeline != null)
                valves = pipeline.getValves();
            if (valves != null) {
                for (int i = 0; i < valves.length; i++) {
                    log.debug("  " + valves[i].getInfo());
                }
            }
            log.debug("======================");
        }

        // Make our application available if no problems were encountered
        // Context已经配置成功，可以使用了
        if (ok)
            context.setConfigured(true);
        else {
            log.error(sm.getString("contextConfig.unavailable"));
            context.setConfigured(false);
        }

    }


    /**
     * Process a "stop" event for this Context.
     * configureStop主要的工作是在停止前将相关的属性从Context容器中移除掉，包括Servlet,Listener , Filter ，欢迎页，认证配置等
     */
    protected synchronized void configureStop() {

        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.stop"));

        int i;

        // Removing children
        Container[] children = context.findChildren();
        for (i = 0; i < children.length; i++) {
            context.removeChild(children[i]);
        }

        // Removing application parameters
        /*
        ApplicationParameter[] applicationParameters =
            context.findApplicationParameters();
        for (i = 0; i < applicationParameters.length; i++) {
            context.removeApplicationParameter
                (applicationParameters[i].getName());
        }
        */

        // Removing security constraints
        SecurityConstraint[] securityConstraints = context.findConstraints();
        for (i = 0; i < securityConstraints.length; i++) {
            context.removeConstraint(securityConstraints[i]);
        }

        // Removing Ejbs
        /*
        ContextEjb[] contextEjbs = context.findEjbs();
        for (i = 0; i < contextEjbs.length; i++) {
            context.removeEjb(contextEjbs[i].getName());
        }
        */

        // Removing environments
        /*
        ContextEnvironment[] contextEnvironments = context.findEnvironments();
        for (i = 0; i < contextEnvironments.length; i++) {
            context.removeEnvironment(contextEnvironments[i].getName());
        }
        */

        // Removing errors pages
        ErrorPage[] errorPages = context.findErrorPages();
        for (i = 0; i < errorPages.length; i++) {
            context.removeErrorPage(errorPages[i]);
        }

        // Removing filter defs
        FilterDef[] filterDefs = context.findFilterDefs();
        for (i = 0; i < filterDefs.length; i++) {
            context.removeFilterDef(filterDefs[i]);
        }

        // Removing filter maps
        FilterMap[] filterMaps = context.findFilterMaps();
        for (i = 0; i < filterMaps.length; i++) {
            context.removeFilterMap(filterMaps[i]);
        }

        // Removing local ejbs
        /*
        ContextLocalEjb[] contextLocalEjbs = context.findLocalEjbs();
        for (i = 0; i < contextLocalEjbs.length; i++) {
            context.removeLocalEjb(contextLocalEjbs[i].getName());
        }
        */

        // Removing Mime mappings
        String[] mimeMappings = context.findMimeMappings();
        for (i = 0; i < mimeMappings.length; i++) {
            context.removeMimeMapping(mimeMappings[i]);
        }

        // Removing parameters
        String[] parameters = context.findParameters();
        for (i = 0; i < parameters.length; i++) {
            context.removeParameter(parameters[i]);
        }

        // Removing resource env refs
        /*
        String[] resourceEnvRefs = context.findResourceEnvRefs();
        for (i = 0; i < resourceEnvRefs.length; i++) {
            context.removeResourceEnvRef(resourceEnvRefs[i]);
        }
        */

        // Removing resource links
        /*
        ContextResourceLink[] contextResourceLinks =
            context.findResourceLinks();
        for (i = 0; i < contextResourceLinks.length; i++) {
            context.removeResourceLink(contextResourceLinks[i].getName());
        }
        */

        // Removing resources
        /*
        ContextResource[] contextResources = context.findResources();
        for (i = 0; i < contextResources.length; i++) {
            context.removeResource(contextResources[i].getName());
        }
        */

        // Removing security role
        String[] securityRoles = context.findSecurityRoles();
        for (i = 0; i < securityRoles.length; i++) {
            context.removeSecurityRole(securityRoles[i]);
        }

        // Removing servlet mappings
        String[] servletMappings = context.findServletMappings();
        for (i = 0; i < servletMappings.length; i++) {
            context.removeServletMapping(servletMappings[i]);
        }

        // FIXME : Removing status pages

        // Removing welcome files
        String[] welcomeFiles = context.findWelcomeFiles();
        for (i = 0; i < welcomeFiles.length; i++) {
            context.removeWelcomeFile(welcomeFiles[i]);
        }

        // Removing wrapper lifecycles
        String[] wrapperLifecycles = context.findWrapperLifecycles();
        for (i = 0; i < wrapperLifecycles.length; i++) {
            context.removeWrapperLifecycle(wrapperLifecycles[i]);
        }

        // Removing wrapper listeners
        String[] wrapperListeners = context.findWrapperListeners();
        for (i = 0; i < wrapperListeners.length; i++) {
            context.removeWrapperListener(wrapperListeners[i]);
        }

        // Remove (partially) folders and files created by antiLocking
        if (antiLockingDocBase != null) {
            // No need to log failure - it is expected in this case
            ExpandWar.delete(antiLockingDocBase, false);
        }

        // Reset ServletContextInitializer scanning
        initializerClassMap.clear();
        typeInitializerMap.clear();

        ok = true;

    }


    /**
     * Process a "destroy" event for this Context.
     */
    protected synchronized void destroy() {
        // Called from StandardContext.destroy()
        if (log.isDebugEnabled())
            log.debug(sm.getString("contextConfig.destroy"));

        // Skip clearing the work directory if Tomcat is being shutdown
        Server s = getServer();
        if (s != null && !s.getState().isAvailable()) {
            return;
        }

        // Changed to getWorkPath per Bugzilla 35819.
        if (context instanceof StandardContext) {
            String workDir = ((StandardContext) context).getWorkPath();
            if (workDir != null) {
                ExpandWar.delete(new File(workDir));
            }
        }
    }


    private Server getServer() {
        Container c = context;
        while (c != null && !(c instanceof Engine)) {
            c = c.getParent();
        }

        if (c == null) {
            return null;
        }

        Service s = ((Engine)c).getService();

        if (s == null) {
            return null;
        }

        return s.getServer();
    }

    /**
     * Validate the usage of security role names in the web application
     * deployment descriptor.  If any problems are found, issue warning
     * messages (for backwards compatibility) and add the missing roles.
     * (To make these problems fatal instead, simply set the <code>ok</code>
     * instance variable to <code>false</code> as well).
     */
    protected void validateSecurityRoles() {

        // Check role names used in <security-constraint> elements
        SecurityConstraint constraints[] = context.findConstraints();
        for (int i = 0; i < constraints.length; i++) {
            String roles[] = constraints[i].findAuthRoles();
            for (int j = 0; j < roles.length; j++) {
                if (!"*".equals(roles[j]) &&
                    !context.findSecurityRole(roles[j])) {
                    log.warn(sm.getString("contextConfig.role.auth", roles[j]));
                    context.addSecurityRole(roles[j]);
                }
            }
        }

        // Check role names used in <servlet> elements
        Container wrappers[] = context.findChildren();
        for (int i = 0; i < wrappers.length; i++) {
            Wrapper wrapper = (Wrapper) wrappers[i];
            String runAs = wrapper.getRunAs();
            if ((runAs != null) && !context.findSecurityRole(runAs)) {
                log.warn(sm.getString("contextConfig.role.runas", runAs));
                context.addSecurityRole(runAs);
            }
            String names[] = wrapper.findSecurityReferences();
            for (int j = 0; j < names.length; j++) {
                String link = wrapper.findSecurityReference(names[j]);
                if ((link != null) && !context.findSecurityRole(link)) {
                    log.warn(sm.getString("contextConfig.role.link", link));
                    context.addSecurityRole(link);
                }
            }
        }

    }


    /**
     * Get config base.
     *
     * @deprecated  Unused - will be removed in 8.0.x
     */
    @Deprecated
    protected File getConfigBase() {
        File configBase = new File(getBaseDir(), "conf");
        if (!configBase.exists()) {
            return null;
        }
        return configBase;
    }

    // 确定Host配置文件基本路径----就是我要找某个配置文件时，要从这个目录下去找
    // 步骤如下：
    // 1. 如果Host上配置了xmlBase属性，并且该属性是绝对路径的话，将直接以该路径作为基本路径
    // 2. 如果Host上配置的xmlBase属性是相对路径，那么将以getBaseDir()+xmlBase做为基本路径
    // 3. 如果Host上没有配置xmlBase属性，那么将以getBaseDir() + "/conf" + engine名字 + "/" + host名字作为基本路径
    protected File getHostConfigBase() {
        File file = null;
        Container container = context;
        Host host = null;
        Engine engine = null;
        // 赋值host和engine
        while (container != null) {
            if (container instanceof Host) {
                host = (Host)container;
            }
            if (container instanceof Engine) {
                engine = (Engine)container;
            }
            container = container.getParent();
        }
        // 如果host配置了xmlBase
        if (host != null && host.getXmlBase()!=null) {
            String xmlBase = host.getXmlBase();
            file = new File(xmlBase);
            if (!file.isAbsolute())
                file = new File(getBaseDir(), xmlBase);
        } else {
            StringBuilder result = new StringBuilder();
            if (engine != null) {
                result.append(engine.getName()).append('/');
            }
            if (host != null) {
                result.append(host.getName()).append('/');
            }
            // 如果host没有配置xmlBase，那么则获取getBaseDir()下conf目录下的--engine名字+"/"+host名字--的配置文件
            file = new File (getConfigBase(), result.toString());
        }
        try {
            // 返回绝对路径
            return file.getCanonicalFile();
        } catch (IOException e) {
            return file;
        }
    }

    /**
     * Scan the web.xml files that apply to the web application and merge them
     * using the rules defined in the spec. For the global web.xml files,
     * where there is duplicate configuration, the most specific level wins. ie
     * an application's web.xml takes precedence over the host level or global
     * web.xml file.
     *
     * 应用程序的web.xml优先于主机级别或全局级别的
     * 根据配置创建Wrapper(Servlet),Filter,ServletContextListener等，完成Web容器的初始化，除了解析Web应用目录下的Web.xml外，还
     * 包括Tomcat默认配置，web-fragment.xml ，ServletContainerInitializer，以及相关的XML文件的排序和合并。
     */
    protected void webConfig() {
        /*
         * Anything and everything can override the global and host defaults.
         * This is implemented in two parts
         * - Handle as a web fragment that gets added after everything else so
         *   everything else takes priority
         * - Mark Servlets as overridable so SCI configuration can replace
         *   configuration from the defaults
         */

        /*
         * The rules for annotation scanning are not as clear-cut as one might
         * think. Tomcat implements the following process:
         * - As per SRV.1.6.2, Tomcat will scan for annotations regardless of
         *   which Servlet spec version is declared in web.xml. The EG has
         *   confirmed this is the expected behaviour.
         * - As per http://java.net/jira/browse/SERVLET_SPEC-36, if the main
         *   web.xml is marked as metadata-complete, JARs are still processed
         *   for SCIs.
         * - If metadata-complete=true and an absolute ordering is specified,
         *   JARs excluded from the ordering are also excluded from the SCI
         *   processing.
         * - If an SCI has a @HandlesType annotation then all classes (except
         *   those in JARs excluded from an absolute ordering) need to be
         *   scanned to check if they match.
         */
        Set<WebXml> defaults = new HashSet<WebXml>();
        // 1. 解析默认的配置，生成WebXml对象（Tomcat使用该对象表示web.xml的解析结果），先解析容器级别的配置，然后再解析Host级别的配置。
        // 这样对于同名的配置，Host级别将覆盖容器级别，为了便于后续过程描述，我们暂且称为 "默认WebXml"，为了提升性能，ContextConfig
        // 对默认的WebXml进行了缓存，以避免重复解析
        defaults.add(getDefaultWebXmlFragment());
        WebXml webXml = createWebXml();

        // Parse context level web.xml
        // 2. 解析web.xml文件，如果StandardContext的altDDName不为空，则将该属性指向文件作为web.xml，否则使用默认的路径，即WEB-INF/web.xml
        // 解析结果同样为WebXml对象（此时创建的对象主为主WebXml）,其他的解析结果要合并到该对象上来，暂时将其称为主WebXml
        InputSource contextWebXml = getContextWebXmlSource();
        parseWebXml(contextWebXml, webXml, false);

        ServletContext sContext = context.getServletContext();

        // Ordering is important here

        // Step 1. Identify all the JARs packaged with the application
        // If the JARs have a web-fragment.xml it will be parsed at this
        // point.
        // 3. 扫描Web应用所有的Jar包，如果包含META-INF/web-fragment.xml，则解析文件并创建WebXml对象，暂时将其称为片断WebXml
        Map<String,WebXml> fragments = processJarsForWebFragments(webXml);

        // Step 2. Order the fragments.
        Set<WebXml> orderedFragments = null;
        // 4. 将web-fragment.xml创建的WebXml对象按照Servlet规范进行排序，同时将排序结果对应的JAR文件名列表设置到ServletContext属性中
        // 属性名为javax.servlet.context.orderedLibs，该排序非常重要，因为这决定了Filter等执行顺序 。
        // 【注意】：尽管Servlet规范定义了web-fragment.xml的排序（绝对排序和相对排序），但是为了降低各个模块的耦合度，Web应用在定义web-fragment.xml
        // 时应尽量保证相对独立性，减少相互间的依赖，将产生依赖过多的配置尝试放到web.xml中
        orderedFragments =
                WebXml.orderWebFragments(webXml, fragments, sContext);

        // Step 3. Look for ServletContainerInitializer implementations
        // 处理ServletContainerInitializers
        if (ok) {
            // 5.查找ServletContainerInitializer实现，并创建实例，查找范围分为两部分。
            // 5.1 Web应用下的包，如果javax.servlet.context.orderedLibs不为空，仅搜索该属性包含的包，否则搜索WEB-INF/lib下所有的包
            // 5.2 容器包：搜索所有的包
            // Tomcat返回查找结果列表时，确保Web应用的顺序的容器后，因此容器中实现将先加载 。
            // 6. 根据ServletContainerInitializer查询结果以及javax.servlet.annotation.HandleTypes 注解配置，初始化typeInitializerMap和
            // initializerClassMap 两个映射（主要用于后续注解检测），前者表示类对应ServletContainerInitializer集合，而后者表示每个
            // ServletContainerInitializer 对应的类的集合，具体类由javax.servlet.annotation.HandleTypes注解指定。
            processServletContainerInitializers();
        }
        // 7. 当主WebXml 的metadataComplete为false,或者typeInitializerMap不为空时
        if  (!webXml.isMetadataComplete() || typeInitializerMap.size() > 0) {
            // Steps 4 & 5.
            // 检测javax.servlet.annotation.HandlesTypes注解
            // 当WebXml的metadataComplete为false, 查找javax.servlet.annotation.WebServlet ，javax.servlet.annotation.WebFilter
            // javax.servlet.annotation.WebListener注解配置， 将其合并到WebXml
            // 处理JAR包内的注解，只处理包含web-fragment.xml的JAR,对于JAR包中的每个类做如下处理。
            // 检测javax.servlet.annotation.HandlesTypes注解
            // 当 "主WebXml"和片段"WebXml"的metadataComplete均为false,查找javax.servlet.annotation.WebServlet,javax.servlet.annotation.WebFilter
            // javax.servlet.annotation.WebListener注解配置，将其合并到"片段WebXml"
            processClasses(webXml, orderedFragments);
        }

        if (!webXml.isMetadataComplete()) {
            // Step 6. Merge web-fragment.xml files into the main web.xml
            // file.
            if (ok) {
                // 如果"主WebXml"的metadataComple为false, 将所有的片段WebXml按照排序合并到"WebXml"中
                ok = webXml.merge(orderedFragments);
            }

            // Step 7. Apply global defaults
            // Have to merge defaults before JSP conversion since defaults
            // provide JSP servlet definition.
            // 9 将默认的"WebXml" 合并到"主WebXml"中
            webXml.merge(defaults);

            // Step 8. Convert explicitly mentioned jsps to servlets
            if (ok) {
                // 配置JspServlet，对于当前Web应用中JspFile属性不为空的Servlet，将其servletClass设置为org.apache.jsper.servlet.JspServlet
                // (Tomcat提供了JSP引擎)，将JspFile设置为Servlet初始化参数，同时将名称 "jsp" 的Servlet(见conf/web.xml) 的初始化参数也
                // 复制到该Servlet中
                convertJsps(webXml);
            }

            // Step 9. Apply merged web.xml to Context
            if (ok) {
                // 使用"主WebXml"配置当前StandardContext ，包括Servlet,Filter,Listener 等Servlet 规范中支持的组件，对于ServletContext
                // 层级对象，直接由StandardContext维护，对于Servlet,则创建StandardWrapper子对象，并添加StandardContext实例。
                webXml.configureContext(context);
            }
        } else {
            webXml.merge(defaults); // 默认情况下, defaults就是conf/web.xml文件对应的WebXml对象
            convertJsps(webXml);    // 将jsp转化为Servlet
            webXml.configureContext(context);   // 根据webxml配置context，比如把定义的servlet转化为wrapper,然后添加到StandardContext中，还包括很多其他的
        }

        // Step 9a. Make the merged web.xml available to other
        // components, specifically Jasper, to save those components
        // from having to re-generate it.
        // TODO Use a ServletContainerInitializer for Jasper
        // 将合并后的WebXml保存到ServletContext属性中，便于后续处理复用，属性名为org.apache.tomcat.util.scan.MergeWebXml
        String mergedWebXml = webXml.toXml();
        sContext.setAttribute(
               org.apache.tomcat.util.scan.Constants.MERGED_WEB_XML,
               mergedWebXml);
        if (false || context.getLogEffectiveWebXml()) {
            log.info("web.xml:\n" + mergedWebXml);
        }

        // Always need to look for static resources
        // Step 10. Look for static resources packaged in JARs
        if (ok) {
            // Spec does not define an order.
            // Use ordered JARs followed by remaining JARs
            Set<WebXml> resourceJars = new LinkedHashSet<WebXml>();
            for (WebXml fragment : orderedFragments) {
                //
                resourceJars.add(fragment);
            }
            for (WebXml fragment : fragments.values()) {
                if (!resourceJars.contains(fragment)) {
                    resourceJars.add(fragment);
                }
            }
            // 查找JAR 包的"META-INF/resource/"下的静态资源，并添加到StandardContext中
            processResourceJARs(resourceJars);
            // See also StandardContext.resourcesStart() for
            // WEB-INF/classes/META-INF/resources configuration
        }

        // Step 11. Apply the ServletContainerInitializer config to the
        // context
        if (ok) {
            // 将ServletContainerInitializer 扫描结果添加到StandardContext，以便StandardContext启动时使用
            for (Map.Entry<ServletContainerInitializer,
                    Set<Class<?>>> entry :
                        initializerClassMap.entrySet()) {
                if (entry.getValue().isEmpty()) {
                    context.addServletContainerInitializer(
                            entry.getKey(), null);
                } else {
                    context.addServletContainerInitializer(
                            entry.getKey(), entry.getValue());
                }
            }
        }
        // 至此，StandardContext 在正式启动StandardWrapper子对象之前，完成了Web 应用容器的初始化，包括Servlet规范中的各类组件，注解
        // 以及可编程方式的支持
        // 应用程序注解配置
        // 当StandardContext 的ignoreAnnotations 为false时，Tomcat 支持读取如下接口的Java命名服务注解配置，添加相关的JNDI 引用，以便
        // 在实例化相关的接口时，进行JNDI 资源依赖注入 .
        // 支持读取接口如下 ：
        // Web应用程序监听器
        // javax.servlet.ServletContextAttributeListener
        // javax.servlet.ServletRequestListener
        // javax.servlet.http.HttpSessionAttributeListener
        // javax.servlet.http.HttpSessionListener
        // javax.servlet.ServletContextListener
        // javax.servlet.Filter
        // javax.servlet. Servlet
        // 支持读取注解包括注解，属性注解，方法注解，具体注解如下
        // 类：javax.annotion.Resource ,javax.annotation.Resources
        // 属性和方法：javax.annotation.Resource
    }


    protected void processClasses(WebXml webXml, Set<WebXml> orderedFragments) {
        // Step 4. Process /WEB-INF/classes for annotations
        if (ok) {
            // Hack required by Eclipse's "serve modules without
            // publishing" feature since this backs WEB-INF/classes by
            // multiple locations rather than one.
            NamingEnumeration<Binding> listBindings = null;
            try {
                try {
                    listBindings = context.getResources().listBindings(
                            "/WEB-INF/classes");
                } catch (NameNotFoundException ignore) {
                    // Safe to ignore
                }
                while (listBindings != null &&
                        listBindings.hasMoreElements()) {
                    Binding binding = listBindings.nextElement();
                    if (binding.getObject() instanceof FileDirContext) {
                        File webInfClassDir = new File(
                                ((FileDirContext) binding.getObject()).getDocBase());
                        processAnnotationsFile(webInfClassDir, webXml,
                                webXml.isMetadataComplete());
                    } else if ("META-INF".equals(binding.getName())) {
                        // Skip the META-INF directory from any JARs that have been
                        // expanded in to WEB-INF/classes (sometimes IDEs do this).
                    } else {
                        String resource =
                                "/WEB-INF/classes/" + binding.getName();
                        try {
                            URL url = context.getServletContext().getResource(resource);
                            processAnnotationsUrl(url, webXml,
                                    webXml.isMetadataComplete());
                        } catch (MalformedURLException e) {
                            log.error(sm.getString(
                                    "contextConfig.webinfClassesUrl",
                                    resource), e);
                        }
                    }
                }
            } catch (NamingException e) {
                log.error(sm.getString(
                        "contextConfig.webinfClassesUrl",
                        "/WEB-INF/classes"), e);
            }
        }

        // Step 5. Process JARs for annotations - only need to process
        // those fragments we are going to use
        if (ok) {
            processAnnotations(
                    orderedFragments, webXml.isMetadataComplete());
        }

        // Cache, if used, is no longer required so clear it
        javaClassCache.clear();
    }

    // 获取默认的WebXml对象
    private WebXml getDefaultWebXmlFragment() {

        // Host should never be null
        Host host = (Host) context.getParent();

        // 从缓存里面获取WebXml对象
        DefaultWebXmlCacheEntry entry = hostWebXmlCache.get(host);

        InputSource globalWebXml = getGlobalWebXmlSource(); // 获取全局范围内的web.xml文件InputSource，在默认情况下就是取catalina.base目录下的conf/web.xml文件
        InputSource hostWebXml = getHostWebXmlSource();     // 获取Host范围内的web.xml文件InputSource，在默认情况下就是取catalina.base目录下的conf/engine名字/host名字/web.xml.default

        long globalTimeStamp = 0;
        long hostTimeStamp = 0;

        // 寻找globalWebXml文件的最近修改时间
        if (globalWebXml != null) {
            URLConnection uc = null;
            try {
                URL url = new URL(globalWebXml.getSystemId());
                uc = url.openConnection();
                globalTimeStamp = uc.getLastModified();
            } catch (IOException e) {
                globalTimeStamp = -1;
            } finally {
                if (uc != null) {
                    try {
                        uc.getInputStream().close();
                    } catch (IOException e) {
                        ExceptionUtils.handleThrowable(e);
                        globalTimeStamp = -1;
                    }
                }
            }
        }

        // 寻找hostWebXml文件的最近修改时间
        if (hostWebXml != null) {
            URLConnection uc = null;
            try {
                URL url = new URL(hostWebXml.getSystemId());
                uc = url.openConnection();
                hostTimeStamp = uc.getLastModified();
            } catch (IOException e) {
                hostTimeStamp = -1;
            } finally {
                if (uc != null) {
                    try {
                        uc.getInputStream().close();
                    } catch (IOException e) {
                        ExceptionUtils.handleThrowable(e);
                        hostTimeStamp = -1;
                    }
                }
            }
        }

        // 如果发现找出来的webxml文件和缓存中的webxml文件最近的修改时间相等，就代表没有修改过，那么直接返回缓存中的WebXml对象
        if (entry != null && entry.getGlobalTimeStamp() == globalTimeStamp &&
                entry.getHostTimeStamp() == hostTimeStamp) {
            InputSourceUtil.close(globalWebXml);
            InputSourceUtil.close(hostWebXml);
            return entry.getWebXml();
        }

        // Parsing global web.xml is relatively expensive. Use a sync block to
        // make sure it only happens once. Use the pipeline since a lock will
        // already be held on the host by another thread
        // 解析web.xml文件比较耗时。使用同步块确保只会执行一次。为什么用host.getPipeline()作为锁？
        synchronized (host.getPipeline()) {
            // 再一次判断是不是可以直接返回缓存中的WebXML对象，这里使用的是双重判断检查
            entry = hostWebXmlCache.get(host);
            if (entry != null && entry.getGlobalTimeStamp() == globalTimeStamp &&
                    entry.getHostTimeStamp() == hostTimeStamp) {
                return entry.getWebXml();
            }

            // 解析web.xml为WebXml对象
            WebXml webXmlDefaultFragment = createWebXml();
            webXmlDefaultFragment.setOverridable(true);
            // Set to distributable else every app will be prevented from being
            // distributable when the default fragment is merged with the main
            // web.xml
            webXmlDefaultFragment.setDistributable(true);
            // When merging, the default welcome files are only used if the app has
            // not defined any welcomes files.
            webXmlDefaultFragment.setAlwaysAddWelcomeFiles(false);

            // Parse global web.xml if present
            if (globalWebXml == null) {
                // This is unusual enough to log
                log.info(sm.getString("contextConfig.defaultMissing"));
            } else {
                parseWebXml(globalWebXml, webXmlDefaultFragment, false);
            }

            // Parse host level web.xml if present
            // Additive apart from welcome pages
            webXmlDefaultFragment.setReplaceWelcomeFiles(true);

            parseWebXml(hostWebXml, webXmlDefaultFragment, false);

            // Don't update the cache if an error occurs
            if (globalTimeStamp != -1 && hostTimeStamp != -1) {
                entry = new DefaultWebXmlCacheEntry(webXmlDefaultFragment,
                        globalTimeStamp, hostTimeStamp);
                hostWebXmlCache.put(host, entry);
            }

            return webXmlDefaultFragment;
        }
    }


    private void convertJsps(WebXml webXml) {
        Map<String,String> jspInitParams;
        ServletDef jspServlet = webXml.getServlets().get("jsp");
        if (jspServlet == null) {
            jspInitParams = new HashMap<String,String>();
            Wrapper w = (Wrapper) context.findChild("jsp");
            if (w != null) {
                String[] params = w.findInitParameters();
                for (String param : params) {
                    jspInitParams.put(param, w.findInitParameter(param));
                }
            }
        } else {
            jspInitParams = jspServlet.getParameterMap();
        }
        for (ServletDef servletDef: webXml.getServlets().values()) {
            if (servletDef.getJspFile() != null) {
                convertJsp(servletDef, jspInitParams);
            }
        }
    }

    private void convertJsp(ServletDef servletDef,
            Map<String,String> jspInitParams) {
        servletDef.setServletClass(org.apache.catalina.core.Constants.JSP_SERVLET_CLASS);
        String jspFile = servletDef.getJspFile();
        if ((jspFile != null) && !jspFile.startsWith("/")) {
            if (context.isServlet22()) {
                if(log.isDebugEnabled())
                    log.debug(sm.getString("contextConfig.jspFile.warning",
                                       jspFile));
                jspFile = "/" + jspFile;
            } else {
                throw new IllegalArgumentException
                    (sm.getString("contextConfig.jspFile.error", jspFile));
            }
        }
        servletDef.getParameterMap().put("jspFile", jspFile);
        servletDef.setJspFile(null);
        for (Map.Entry<String, String> initParam: jspInitParams.entrySet()) {
            servletDef.addInitParameter(initParam.getKey(), initParam.getValue());
        }
    }

    protected WebXml createWebXml() {
        return new WebXml();
    }

    /**
     * Scan JARs for ServletContainerInitializer implementations.
     */
    protected void processServletContainerInitializers() {

        List<ServletContainerInitializer> detectedScis;
        try {
            WebappServiceLoader<ServletContainerInitializer> loader =
                    new WebappServiceLoader<ServletContainerInitializer>(
                            context);
            // 1. Tomcat容器的ServletContainerInitializer机制，主要交由Context 容器和ContextConfig监听器共同实现， ContextConfig
            // 监听器首先负责在容器启动时读取每个Web应用的WEB-INF/lib目录下包含的Jar包的META-INF/lib/javax.servlet.ServletContainerInitializer
            // 2. 以及Web根目录下的META-INF/services/javax.servlet.ServletContainerInitializer ,通过反射完成这些ServletContainerInitializer
            // 的实例化，然后再设置到Context 容器中
            detectedScis = loader.load(ServletContainerInitializer.class);
        } catch (IOException e) {
            log.error(sm.getString(
                    "contextConfig.servletContainerInitializerFail",
                    context.getName()),
                e);
            ok = false;
            return;
        }

        for (ServletContainerInitializer sci : detectedScis) {
            initializerClassMap.put(sci, new HashSet<Class<?>>());

            HandlesTypes ht;
            try {
                ht = sci.getClass().getAnnotation(HandlesTypes.class);
            } catch (Exception e) {
                if (log.isDebugEnabled()) {
                    log.info(sm.getString("contextConfig.sci.debug",
                                    sci.getClass().getName()),
                            e);
                } else {
                    log.info(sm.getString("contextConfig.sci.info",
                            sci.getClass().getName()));
                }
                continue;
            }
            if (ht == null) {
                continue;
            }
            // 假如读出来的内容为com.seaboat.mytomcat.CustomServletContainerInitializer ，则通过反射实例化一个CustomServletContainerInitializer
            // 这里涉及到一个@HandlerTypes 注解的处理，被它标明的类需要作为参数传入 onStartup 方法中， 如下所示
            // @HandlesTypes({HttpServlet.class,Filter.class})
            // public class CustomServletContainerInitializer implements ServletContainerInitializer{
            //      public void onStartup(Set<Class<?>> classes, ServletContext servletContext) throws ServletException{
            //          for(Class c : classes ){
            //              System.out.println(c.getName());
            //          }
            //      }
            // }
            //  其中@HandlesTypes 标明的HttpServlet和Filter两个Class被注入onStartup方法中，所以这个注解也需要在 ContextConfig监听器
            // 中处理，前面已经介绍了注解的实现原理，由于有了编译器的协助，因此我们可以方便的通过ServletContainerInitializer 的Class
            // 对象获取到HandlesTypes对象，进而再获取到注解声明的类数组，下面就是代码的实现的地方
            // 获取到HttpServlet和Filter的Class对象数组，后面的Context 容器调用了CustomServletContainerInitializer对象的onStartup
            // 方法时作为参数传入，至此，即完成了Servlet规范的ServeltContainerInitializer 初始化smsm。
            Class<?>[] types = ht.value();
            if (types == null) {
                continue;
            }

            for (Class<?> type : types) {
                if (type.isAnnotation()) {
                    handlesTypesAnnotations = true;
                } else {
                    handlesTypesNonAnnotations = true;
                }
                Set<ServletContainerInitializer> scis =
                        typeInitializerMap.get(type);
                if (scis == null) {
                    scis = new HashSet<ServletContainerInitializer>();
                    typeInitializerMap.put(type, scis);
                }
                scis.add(sci);
            }
        }
    }


    /**
     * Scan JARs that contain web-fragment.xml files that will be used to
     * configure this application to see if they also contain static resources.
     * If static resources are found, add them to the context. Resources are
     * added in web-fragment.xml priority order.
     */
    protected void processResourceJARs(Set<WebXml> fragments) {
        for (WebXml fragment : fragments) {
            URL url = fragment.getURL();
            Jar jar = null;
            try {
                // Note: Ignore file URLs for now since only jar URLs will be accepted
                if ("jar".equals(url.getProtocol())) {
                    jar = JarFactory.newInstance(url);
                    //System.out.println("+++++++++++++++++++++++++++++++++"+url.getPath() );
                    jar.nextEntry();
                    String entryName = jar.getEntryName();
                    //System.out.println("========processResourceJARs========"+entryName );
                    while (entryName != null) {
                        if (entryName.startsWith("META-INF/resources/")) {
                            context.addResourceJarUrl(url);
                            break;
                        }
                        jar.nextEntry();
                        entryName = jar.getEntryName();
                    }
                } else if ("file".equals(url.getProtocol())) {
                    FileDirContext fileDirContext = new FileDirContext();
                    fileDirContext.setDocBase(new File(url.toURI()).getAbsolutePath());
                    try {
                        fileDirContext.lookup("META-INF/resources/");
                        //lookup succeeded
                        if(context instanceof StandardContext){
                            ((StandardContext)context).addResourcesDirContext(fileDirContext);
                        }
                    } catch (NamingException e) {
                        //not found, ignore
                    }
                }
            } catch (IOException ioe) {
                log.error(sm.getString("contextConfig.resourceJarFail", url,
                        context.getName()));
            } catch (URISyntaxException e) {
                log.error(sm.getString("contextConfig.resourceJarFail", url,
                    context.getName()));
            } finally {
                if (jar != null) {
                    jar.close();
                }
            }
        }
    }


    /**
     * Identify the default web.xml to be used and obtain an input source for
     * it.
     *
     * 查找全局webxml文件的流程:
     * 1. 先确定查找的目录
     * 2. 再确定查找的文件名
     *
     * 确定查找目录：
     * 1. 如果在Engine上配置了baseDir属性，那么将直接在属性对应的目录下查找
     * 2. 如果在Engine上没有配置baseDir属性，那么将在catalina.base所对应的目录下查找
     * 3. 如果catalina.base为空，那么将在catalina.home所对应的目录下查找
     *
     * 查找并不是直接找目录下的web.xml文件，而是：
     * 1. 如果Context配置了defaultWebXml属性，那么将查找该属性所对应的文件，前提是该属性配置的是相对路径
     * 2. 如果Context配置的defaultWebXml属性是绝对路径，那么将直接取该绝对路径所对应的文件
     * 3. 如果Context没有配置defaultWebXml属性，那么将查找conf/web.xml
     *
     */
    protected InputSource getGlobalWebXmlSource() {
        // 首先获取Context上有没有指定defaultWebXml
        // 如果没有，则获取conf/web.xml目录下的文件
        // 将文件转化为InputSource，后续会解析xml

        // Is a default web.xml specified for the Context?
        if (defaultWebXml == null && context instanceof StandardContext) {
            defaultWebXml = ((StandardContext) context).getDefaultWebXml();
        }
        // Set the default if we don't have any overrides
        if (defaultWebXml == null) getDefaultWebXml();

        // Is it explicitly suppressed, e.g. in embedded environment?
        if (Constants.NoDefaultWebXml.equals(defaultWebXml)) {
            return null;
        }
        return getWebXmlSource(defaultWebXml, getBaseDir());
    }


    /**
     * Identify the host web.xml to be used and obtain an input source for
     * it.
     *  1. 如果Host上配置了xmlBase属性，并且该属性是绝对路径的话，将直接把该路径下的web.xml.default文件返回
     *  2. 如果Host上配置的xmlBase属性是相对路径，那么将把getBaseDir()+xmlBase路径下的web.xml.default文件返回
     *  3. 如果Host上没有配置xmlBase属性，那么将把getBaseDir() + "/conf" + engine名字 + "/" + host名字路径下的web.xml.default文件返回
     */
    protected InputSource getHostWebXmlSource() {
        File hostConfigBase = getHostConfigBase();
        if (!hostConfigBase.exists())
            return null;
        // 获取hostConfigBase下的web.xml.default
        return getWebXmlSource(Constants.HostWebXml, hostConfigBase.getPath());
    }

    /**
     * Identify the application web.xml to be used and obtain an input source
     * for it.
     */
    protected InputSource getContextWebXmlSource() {
        InputStream stream = null;
        InputSource source = null;
        URL url = null;

        String altDDName = null;

        // Open the application web.xml file, if it exists
        ServletContext servletContext = context.getServletContext();
        try {
            if (servletContext != null) {
                altDDName = (String)servletContext.getAttribute(Globals.ALT_DD_ATTR);
                if (altDDName != null) {
                    try {
                        stream = new FileInputStream(altDDName);
                        url = new File(altDDName).toURI().toURL();
                    } catch (FileNotFoundException e) {
                        log.error(sm.getString("contextConfig.altDDNotFound",
                                               altDDName));
                    } catch (MalformedURLException e) {
                        log.error(sm.getString("contextConfig.applicationUrl"));
                    }
                }
                else {
                    //一个 Tomcat 部署必须有一个主机如果该上下文使用 ContextConfig 来配置。原 因如下:
                    //ContextConfig 需要应用文件 web.xml 的位置，它在它的 applicationConfig 方 法中尝试打开该文件，下面是该方法的片段:
                    stream = servletContext.getResourceAsStream
                        (Constants.ApplicationWebXml);
                    try {
                        url = servletContext.getResource(
                                Constants.ApplicationWebXml);
                    } catch (MalformedURLException e) {
                        log.error(sm.getString("contextConfig.applicationUrl"));
                    }
                }
            }
            if (stream == null || url == null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("contextConfig.applicationMissing") + " " + context);
                }
            } else {
                source = new InputSource(url.toExternalForm());
                source.setByteStream(stream);
            }
        } finally {
            if (source == null && stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        return source;
    }

    /**
     *
     * @param filename  Name of the file (possibly with one or more leading path
     *                  segments) to read
     * @param path      Location that filename is relative to
     */
    protected InputSource getWebXmlSource(String filename, String path) {
        File file = new File(filename);
        // 如果filename是相对路径
        if (!file.isAbsolute()) {
            file = new File(path, filename);
        }

        InputStream stream = null;
        InputSource source = null;

        try {
            if (!file.exists()) {
                // Use getResource and getResourceAsStream
                stream =
                    getClass().getClassLoader().getResourceAsStream(filename);
                if(stream != null) {
                    source =
                        new InputSource(getClass().getClassLoader().getResource(
                                filename).toURI().toString());
                }
            } else {
                source = new InputSource(file.getAbsoluteFile().toURI().toString());
                stream = new FileInputStream(file);
            }

            if (stream != null && source != null) {
                source.setByteStream(stream);
            }
        } catch (Exception e) {
            log.error(sm.getString(
                    "contextConfig.defaultError", filename, file), e);
        } finally {
            if (source == null && stream != null) {
                try {
                    stream.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }

        return source;
    }


    /**
     * Parses the given source and stores the parsed data in the given web.xml
     * representation. The byte stream will be closed at the end of the parse
     * operation.
     *
     * @param source Input source containing the XML data to be parsed
     * @param dest The object representation of common elements of web.xml and
     *             web-fragment.xml
     * @param fragment Specifies whether the source is web-fragment.xml or
     *                 web.xml
     */
    protected void parseWebXml(InputSource source, WebXml dest,
            boolean fragment) {

        if (source == null) return;

        XmlErrorHandler handler = new XmlErrorHandler();

        Digester digester;
        WebRuleSet ruleSet;
        if (fragment) {
            digester = webFragmentDigester;
            ruleSet = webFragmentRuleSet;
        } else {
            digester = webDigester;
            ruleSet = webRuleSet;
        }

        digester.push(dest);
        digester.setErrorHandler(handler);

        if(log.isDebugEnabled()) {
            log.debug(sm.getString("contextConfig.applicationStart",
                    source.getSystemId()));
        }

        try {
            digester.parse(source);

            if (handler.getWarnings().size() > 0 ||
                    handler.getErrors().size() > 0) {
                ok = false;
                handler.logFindings(log, source.getSystemId());
            }
        } catch (SAXParseException e) {
            log.error(sm.getString("contextConfig.applicationParse",
                    source.getSystemId()), e);
            log.error(sm.getString("contextConfig.applicationPosition",
                             "" + e.getLineNumber(),
                             "" + e.getColumnNumber()));
            ok = false;
        } catch (Exception e) {
            log.error(sm.getString("contextConfig.applicationParse",
                    source.getSystemId()), e);
            ok = false;
        } finally {
            digester.reset();
            ruleSet.recycle();
            InputSourceUtil.close(source);
        }
    }


    /**
     * Scan /WEB-INF/lib for JARs and for each one found add it and any
     * /META-INF/web-fragment.xml to the resulting Map. web-fragment.xml files
     * will be parsed before being added to the map. Every JAR will be added and
     * <code>null</code> will be used if no web-fragment.xml was found. Any JARs
     * known not contain fragments will be skipped.
     *
     * @return A map of JAR name to processed web fragment (if any)
     */
    protected Map<String,WebXml> processJarsForWebFragments(WebXml application) {

        JarScanner jarScanner = context.getJarScanner();

        boolean parseRequired = true;
        Set<String> absoluteOrder = application.getAbsoluteOrdering();
        if (absoluteOrder != null && absoluteOrder.isEmpty() &&
                !context.getXmlValidation()) {
            // Skip parsing when there is an empty absolute ordering and
            // validation is not enabled
            parseRequired = false;
        }

        FragmentJarScannerCallback callback =
                new FragmentJarScannerCallback(parseRequired);

        jarScanner.scan(context.getServletContext(),
                context.getLoader().getClassLoader(), callback,
                pluggabilityJarsToSkip);

        return callback.getFragments();
    }

    protected void processAnnotations(Set<WebXml> fragments,
            boolean handlesTypesOnly) {
        for(WebXml fragment : fragments) {
            WebXml annotations = new WebXml();
            // no impact on distributable
            annotations.setDistributable(true);
            URL url = fragment.getURL();
            if(url.getPath().contains("web-fragment-test-2.0-SNAPSHOT.jar")){
                System.out.println("============path="+url.getPath());
            }

            processAnnotationsUrl(url, annotations,
                    (handlesTypesOnly || fragment.isMetadataComplete()));
            Set<WebXml> set = new HashSet<WebXml>();
            set.add(annotations);
            // Merge annotations into fragment - fragment takes priority
            fragment.merge(set);
        }
    }

    protected void processAnnotationsUrl(URL url, WebXml fragment,
            boolean handlesTypesOnly) {
        if (url == null) {
            // Nothing to do.
            return;
        } else if ("jar".equals(url.getProtocol())) {
            processAnnotationsJar(url, fragment, handlesTypesOnly);
        } else if ("jndi".equals(url.getProtocol())) {
            processAnnotationsJndi(url, fragment, handlesTypesOnly);
        } else if ("file".equals(url.getProtocol())) {
            try {
                processAnnotationsFile(
                        new File(url.toURI()), fragment, handlesTypesOnly);
            } catch (URISyntaxException e) {
                log.error(sm.getString("contextConfig.fileUrl", url), e);
            }
        } else {
            log.error(sm.getString("contextConfig.unknownUrlProtocol",
                    url.getProtocol(), url));
        }

    }


    protected void processAnnotationsJar(URL url, WebXml fragment,
            boolean handlesTypesOnly) {

        Jar jar = null;
        InputStream is;

        try {
            jar = JarFactory.newInstance(url);

            if (log.isDebugEnabled()) {
                log.debug(sm.getString(
                        "contextConfig.processAnnotationsJar.debug", url));
            }

            jar.nextEntry();
            String entryName = jar.getEntryName();
            while (entryName != null) {
                if (entryName.endsWith(".class")) {
                    is = null;
                    try {
                        is = jar.getEntryInputStream();
                        processAnnotationsStream(
                                is, fragment, handlesTypesOnly);
                    } catch (IOException e) {
                        log.error(sm.getString("contextConfig.inputStreamJar",
                                entryName, url),e);
                    } catch (ClassFormatException e) {
                        log.error(sm.getString("contextConfig.inputStreamJar",
                                entryName, url),e);
                    } finally {
                        if (is != null) {
                            try {
                                is.close();
                            } catch (IOException ioe) {
                                // Ignore
                            }
                        }
                    }
                }
                jar.nextEntry();
                entryName = jar.getEntryName();
            }
        } catch (IOException e) {
            log.error(sm.getString("contextConfig.jarFile", url), e);
        } finally {
            if (jar != null) {
                jar.close();
            }
        }
    }


    protected void processAnnotationsJndi(URL url, WebXml fragment,
            boolean handlesTypesOnly) {
        try {
            URLConnection urlConn = url.openConnection();
            DirContextURLConnection dcUrlConn;
            if (!(urlConn instanceof DirContextURLConnection)) {
                // This should never happen
                sm.getString("contextConfig.jndiUrlNotDirContextConn", url);
                return;
            }

            dcUrlConn = (DirContextURLConnection) urlConn;
            dcUrlConn.setUseCaches(false);

            String type = dcUrlConn.getHeaderField(ResourceAttributes.TYPE);
            if (ResourceAttributes.COLLECTION_TYPE.equals(type)) {
                // Collection
                Enumeration<String> dirs = dcUrlConn.list();

                if (log.isDebugEnabled() && dirs.hasMoreElements()) {
                    log.debug(sm.getString(
                            "contextConfig.processAnnotationsWebDir.debug",
                            url));
                }

                while (dirs.hasMoreElements()) {
                    String dir = dirs.nextElement();
                    URL dirUrl = new URL(url.toString() + '/' + dir);
                    processAnnotationsJndi(dirUrl, fragment, handlesTypesOnly);
                }

            } else {
                // Single file
                if (url.getPath().endsWith(".class")) {
                    InputStream is = null;
                    try {
                        is = dcUrlConn.getInputStream();
                        processAnnotationsStream(
                                is, fragment, handlesTypesOnly);
                    } catch (IOException e) {
                        log.error(sm.getString("contextConfig.inputStreamJndi",
                                url),e);
                    } catch (ClassFormatException e) {
                        log.error(sm.getString("contextConfig.inputStreamJndi",
                                url),e);
                    } finally {
                        if (is != null) {
                            try {
                                is.close();
                            } catch (Throwable t) {
                                ExceptionUtils.handleThrowable(t);
                            }
                        }
                    }
                }
            }
        } catch (IOException e) {
            log.error(sm.getString("contextConfig.jndiUrl", url), e);
        }
    }


    protected void processAnnotationsFile(File file, WebXml fragment,
            boolean handlesTypesOnly) {

        if (file.isDirectory()) {
            // Returns null if directory is not readable
            String[] dirs = file.list();
            if (dirs != null) {
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString(
                            "contextConfig.processAnnotationsDir.debug", file));
                }
                for (String dir : dirs) {
                    processAnnotationsFile(
                            new File(file,dir), fragment, handlesTypesOnly);
                }
            }
        } else if (file.getName().endsWith(".class") && file.canRead()) {
            FileInputStream fis = null;
            try {
                fis = new FileInputStream(file);
                processAnnotationsStream(fis, fragment, handlesTypesOnly);
            } catch (IOException e) {
                log.error(sm.getString("contextConfig.inputStreamFile",
                        file.getAbsolutePath()),e);
            } catch (ClassFormatException e) {
                log.error(sm.getString("contextConfig.inputStreamFile",
                        file.getAbsolutePath()),e);
            } finally {
                if (fis != null) {
                    try {
                        fis.close();
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                    }
                }
            }
        }
    }


    protected void processAnnotationsStream(InputStream is, WebXml fragment,
            boolean handlesTypesOnly)
            throws ClassFormatException, IOException {

        ClassParser parser = new ClassParser(is);
        JavaClass clazz = parser.parse();
        checkHandlesTypes(clazz);

        if (handlesTypesOnly) {
            return;
        }

        processClass(fragment, clazz);
    }


    // 处理注解
    protected void processClass(WebXml fragment, JavaClass clazz) {
        AnnotationEntry[] annotationsEntries = clazz.getAnnotationEntries();
        if (annotationsEntries != null) {
            String className = clazz.getClassName();
            for (AnnotationEntry ae : annotationsEntries) {
                String type = ae.getAnnotationType();
                if ("Ljavax/servlet/annotation/WebServlet;".equals(type)) {
                    processAnnotationWebServlet(className, ae, fragment);
                }else if ("Ljavax/servlet/annotation/WebFilter;".equals(type)) {
                    processAnnotationWebFilter(className, ae, fragment);
                }else if ("Ljavax/servlet/annotation/WebListener;".equals(type)) {
                    fragment.addListener(className);
                } else {
                    // Unknown annotation - ignore
                }
            }
        }
    }


    /**
     * For classes packaged with the web application, the class and each
     * super class needs to be checked for a match with {@link HandlesTypes} or
     * for an annotation that matches {@link HandlesTypes}.
     * @param javaClass
     */
    protected void checkHandlesTypes(JavaClass javaClass) {

        // Skip this if we can
        if (typeInitializerMap.size() == 0)
            return;

        if ((javaClass.getAccessFlags() &
                org.apache.tomcat.util.bcel.Const.ACC_ANNOTATION) != 0) {
            // Skip annotations.
            return;
        }

        String className = javaClass.getClassName();

        Class<?> clazz = null;
        if (handlesTypesNonAnnotations) {
            // This *might* be match for a HandlesType.
            populateJavaClassCache(className, javaClass);
            JavaClassCacheEntry entry = javaClassCache.get(className);
            if (entry.getSciSet() == null) {
                try {
                    populateSCIsForCacheEntry(entry);
                } catch (StackOverflowError soe) {
                    throw new IllegalStateException(sm.getString(
                            "contextConfig.annotationsStackOverflow",
                            context.getName(),
                            classHierarchyToString(className, entry)));
                }
            }
            if (!entry.getSciSet().isEmpty()) {
                // Need to try and load the class
                clazz = Introspection.loadClass(context, className);
                if (clazz == null) {
                    // Can't load the class so no point continuing
                    return;
                }

                for (ServletContainerInitializer sci : entry.getSciSet()) {
                    Set<Class<?>> classes = initializerClassMap.get(sci);
                    if (classes == null) {
                        classes = new HashSet<Class<?>>();
                        initializerClassMap.put(sci, classes);
                    }
                    classes.add(clazz);
                }
                System.out.println(initializerClassMap);
            }
        }

        if (handlesTypesAnnotations) {
            for (Map.Entry<Class<?>, Set<ServletContainerInitializer>> entry :
                    typeInitializerMap.entrySet()) {
                if (entry.getKey().isAnnotation()) {
                    AnnotationEntry[] annotationEntries = javaClass.getAnnotationEntries();
                    if (annotationEntries != null) {
                        for (AnnotationEntry annotationEntry : annotationEntries) {

                            if (entry.getKey().getName().equals(
                                    getClassName(annotationEntry.getAnnotationType()))) {
                                if (clazz == null) {
                                    clazz = Introspection.loadClass(
                                            context, className);
                                    if (clazz == null) {
                                        // Can't load the class so no point
                                        // continuing
                                        return;
                                    }
                                }
                                for (ServletContainerInitializer sci : entry.getValue()) {
                                    if("com.example.servelettest.AnnoContainerInitializerImpl".equals(javaClass.getClassName())){
                                        System.out.println("============="+javaClass.getClassName());
                                    }
                                    initializerClassMap.get(sci).add(clazz);
                                }
                                break;
                            }
                        }
                    }
                }
            }

        }
    }


    private String classHierarchyToString(String className,
            JavaClassCacheEntry entry) {
        JavaClassCacheEntry start = entry;
        StringBuilder msg = new StringBuilder(className);
        msg.append("->");

        String parentName = entry.getSuperclassName();
        JavaClassCacheEntry parent = javaClassCache.get(parentName);
        int count = 0;

        while (count < 100 && parent != null && parent != start) {
            msg.append(parentName);
            msg.append("->");

            count ++;
            parentName = parent.getSuperclassName();
            parent = javaClassCache.get(parentName);
        }

        msg.append(parentName);

        return msg.toString();
    }

    private void populateJavaClassCache(String className, JavaClass javaClass) {
        if (javaClassCache.containsKey(className)) {
            return;
        }

        // Add this class to the cache
        javaClassCache.put(className, new JavaClassCacheEntry(javaClass));

        populateJavaClassCache(javaClass.getSuperclassName());

        for (String interfaceName : javaClass.getInterfaceNames()) {
            populateJavaClassCache(interfaceName);
        }
    }

    private void populateJavaClassCache(String className) {
        if (!javaClassCache.containsKey(className)) {
            String name = className.replace('.', '/') + ".class";
            InputStream is =
                    context.getLoader().getClassLoader().getResourceAsStream(name);
            if (is == null) {
                return;
            }
            ClassParser parser = new ClassParser(is);
            try {
                JavaClass clazz = parser.parse();
                populateJavaClassCache(clazz.getClassName(), clazz);
            } catch (ClassFormatException e) {
                log.debug(sm.getString("contextConfig.invalidSciHandlesTypes",
                        className), e);
            } catch (IOException e) {
                log.debug(sm.getString("contextConfig.invalidSciHandlesTypes",
                        className), e);
            } finally {
                try {
                    is.close();
                } catch (IOException e) {
                    // ignore
                }
            }
        }
    }

    private void populateSCIsForCacheEntry(JavaClassCacheEntry cacheEntry) {
        Set<ServletContainerInitializer> result =
                new HashSet<ServletContainerInitializer>();

        // Super class
        String superClassName = cacheEntry.getSuperclassName();
        JavaClassCacheEntry superClassCacheEntry =
                javaClassCache.get(superClassName);

        // Avoid an infinite loop with java.lang.Object
        if (cacheEntry.equals(superClassCacheEntry)) {
            cacheEntry.setSciSet(EMPTY_SCI_SET);
            return;
        }

        // May be null of the class is not present or could not be loaded.
        if (superClassCacheEntry != null) {
            if (superClassCacheEntry.getSciSet() == null) {
                populateSCIsForCacheEntry(superClassCacheEntry);
            }
            result.addAll(superClassCacheEntry.getSciSet());
        }
        result.addAll(getSCIsForClass(superClassName));

        // Interfaces
        for (String interfaceName : cacheEntry.getInterfaceNames()) {
            JavaClassCacheEntry interfaceEntry =
                    javaClassCache.get(interfaceName);
            // A null could mean that the class not present in application or
            // that there is nothing of interest. Either way, nothing to do here
            // so move along
            if (interfaceEntry != null) {
                if (interfaceEntry.getSciSet() == null) {
                    populateSCIsForCacheEntry(interfaceEntry);
                }
                result.addAll(interfaceEntry.getSciSet());
            }
            result.addAll(getSCIsForClass(interfaceName));
        }

        cacheEntry.setSciSet(result.isEmpty() ? EMPTY_SCI_SET : result);
    }

    private Set<ServletContainerInitializer> getSCIsForClass(String className) {
        for (Map.Entry<Class<?>, Set<ServletContainerInitializer>> entry :
                typeInitializerMap.entrySet()) {
            Class<?> clazz = entry.getKey();
            if (!clazz.isAnnotation()) {
                if (clazz.getName().equals(className)) {
                    return entry.getValue();
                }
            }
        }
        return EMPTY_SCI_SET;
    }

    private static final String getClassName(String internalForm) {
        if (!internalForm.startsWith("L")) {
            return internalForm;
        }

        // Assume starts with L, ends with ; and uses / rather than .
        return internalForm.substring(1,
                internalForm.length() - 1).replace('/', '.');
    }

    /**
     *
     * @param className 被@WebServlet注解的类名
     * @param ae        @WebServlet注解对象
     * @param fragment
     */
    protected void processAnnotationWebServlet(String className,
            AnnotationEntry ae, WebXml fragment) {
        String servletName = null;
        // must search for name s. Spec Servlet API 3.0 - 8.2.3.3.n.ii page 81
        List<ElementValuePair> evps = ae.getElementValuePairs();
        // 遍历注解上配置的name:value对
        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            // @WebServlet中的name为servletName
            if ("name".equals(name)) {
                servletName = evp.getValue().stringifyValue();
                break;
            }
        }
        // 如果没有配置name,那么servletName为类名
        if (servletName == null) {
            // classname is default servletName as annotation has no name!
            servletName = className;
        }
        // 查看该servletName是否在webxml中存在
        ServletDef servletDef = fragment.getServlets().get(servletName);

        boolean isWebXMLservletDef;
        // 如果没有在webxml中定义，那么就定义一个Servlet
        if (servletDef == null) {
            servletDef = new ServletDef();
            servletDef.setServletName(servletName);
            servletDef.setServletClass(className);
            isWebXMLservletDef = false;
        } else {
            isWebXMLservletDef = true;
        }

        boolean urlPatternsSet = false;
        String[] urlPatterns = null;  // 可以配置多个urlPatterns

        // List<ElementValuePair> evps = ae.getElementValuePairs();
        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            if ("value".equals(name) || "urlPatterns".equals(name)) {
                if (urlPatternsSet) {
                    throw new IllegalArgumentException(sm.getString(
                            "contextConfig.urlPatternValue", "WebServlet", className));
                }
                urlPatternsSet = true;
                urlPatterns = processAnnotationsStringArray(evp.getValue());
            } else if ("description".equals(name)) {
                if (servletDef.getDescription() == null) {
                    servletDef.setDescription(evp.getValue().stringifyValue());
                }
            } else if ("displayName".equals(name)) {
                if (servletDef.getDisplayName() == null) {
                    servletDef.setDisplayName(evp.getValue().stringifyValue());
                }
            } else if ("largeIcon".equals(name)) {
                if (servletDef.getLargeIcon() == null) {
                    servletDef.setLargeIcon(evp.getValue().stringifyValue());
                }
            } else if ("smallIcon".equals(name)) {
                if (servletDef.getSmallIcon() == null) {
                    servletDef.setSmallIcon(evp.getValue().stringifyValue());
                }
            } else if ("asyncSupported".equals(name)) {
                if (servletDef.getAsyncSupported() == null) {
                    servletDef.setAsyncSupported(evp.getValue()
                            .stringifyValue());
                }
            } else if ("loadOnStartup".equals(name)) {
                if (servletDef.getLoadOnStartup() == null) {
                    servletDef
                            .setLoadOnStartup(evp.getValue().stringifyValue());
                }
            } else if ("initParams".equals(name)) {
                // 初始化参数键值对
                Map<String, String> initParams = processAnnotationWebInitParams(evp
                        .getValue());
                if (isWebXMLservletDef) {
                    // 如果该servlet在webxml中也定义了,将注解上定义的initparams和webxml中定义的initparams合并
                    Map<String, String> webXMLInitParams = servletDef
                            .getParameterMap();
                    for (Map.Entry<String, String> entry : initParams
                            .entrySet()) {
                        if (webXMLInitParams.get(entry.getKey()) == null) {
                            servletDef.addInitParameter(entry.getKey(), entry
                                    .getValue());
                        }
                    }
                } else {
                    for (Map.Entry<String, String> entry : initParams
                            .entrySet()) {
                        servletDef.addInitParameter(entry.getKey(), entry
                                .getValue());
                    }
                }
            }
        }
        if (!isWebXMLservletDef && urlPatterns != null) {
            fragment.addServlet(servletDef);
        }
        if (urlPatterns != null) {
            // 如果webxml中对当前servletname没有配置mapping关系
            if (!fragment.getServletMappings().containsValue(servletName)) {
                for (String urlPattern : urlPatterns) {
                    fragment.addServletMapping(urlPattern, servletName);
                }
            }
        }

    }

    /**
     * process filter annotation and merge with existing one!
     * FIXME: refactoring method too long and has redundant subroutines with
     *        processAnnotationWebServlet!
     * @param className
     * @param ae
     * @param fragment
     */








    protected void processAnnotationWebFilter(String className,
            AnnotationEntry ae, WebXml fragment) {
        String filterName = null;
        // must search for name s. Spec Servlet API 3.0 - 8.2.3.3.n.ii page 81
        List<ElementValuePair> evps = ae.getElementValuePairs();
        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            if ("filterName".equals(name)) {
                filterName = evp.getValue().stringifyValue();
                break;
            }
        }
        if (filterName == null) {
            // classname is default filterName as annotation has no name!
            filterName = className;
        }
        FilterDef filterDef = fragment.getFilters().get(filterName);
        FilterMap filterMap = new FilterMap();

        boolean isWebXMLfilterDef;
        if (filterDef == null) {
            filterDef = new FilterDef();
            filterDef.setFilterName(filterName);
            filterDef.setFilterClass(className);
            isWebXMLfilterDef = false;
        } else {
            isWebXMLfilterDef = true;
        }

        boolean urlPatternsSet = false;
        boolean servletNamesSet = false;
        boolean dispatchTypesSet = false;
        String[] urlPatterns = null;

        for (ElementValuePair evp : evps) {
            String name = evp.getNameString();
            if ("value".equals(name) || "urlPatterns".equals(name)) {
                if (urlPatternsSet) {
                    throw new IllegalArgumentException(sm.getString(
                            "contextConfig.urlPatternValue", "WebFilter", className));
                }
                urlPatterns = processAnnotationsStringArray(evp.getValue());
                urlPatternsSet = urlPatterns.length > 0;
                for (String urlPattern : urlPatterns) {
                    filterMap.addURLPattern(urlPattern);
                }
            } else if ("servletNames".equals(name)) {
                String[] servletNames = processAnnotationsStringArray(evp
                        .getValue());
                servletNamesSet = servletNames.length > 0;
                for (String servletName : servletNames) {
                    filterMap.addServletName(servletName);
                }
            } else if ("dispatcherTypes".equals(name)) {
                String[] dispatcherTypes = processAnnotationsStringArray(evp
                        .getValue());
                dispatchTypesSet = dispatcherTypes.length > 0;
                for (String dispatcherType : dispatcherTypes) {
                    filterMap.setDispatcher(dispatcherType);
                }
            } else if ("description".equals(name)) {
                if (filterDef.getDescription() == null) {
                    filterDef.setDescription(evp.getValue().stringifyValue());
                }
            } else if ("displayName".equals(name)) {
                if (filterDef.getDisplayName() == null) {
                    filterDef.setDisplayName(evp.getValue().stringifyValue());
                }
            } else if ("largeIcon".equals(name)) {
                if (filterDef.getLargeIcon() == null) {
                    filterDef.setLargeIcon(evp.getValue().stringifyValue());
                }
            } else if ("smallIcon".equals(name)) {
                if (filterDef.getSmallIcon() == null) {
                    filterDef.setSmallIcon(evp.getValue().stringifyValue());
                }
            } else if ("asyncSupported".equals(name)) {
                if (filterDef.getAsyncSupported() == null) {
                    filterDef
                            .setAsyncSupported(evp.getValue().stringifyValue());
                }
            } else if ("initParams".equals(name)) {
                Map<String, String> initParams = processAnnotationWebInitParams(evp
                        .getValue());
                if (isWebXMLfilterDef) {
                    Map<String, String> webXMLInitParams = filterDef
                            .getParameterMap();
                    for (Map.Entry<String, String> entry : initParams
                            .entrySet()) {
                        if (webXMLInitParams.get(entry.getKey()) == null) {
                            filterDef.addInitParameter(entry.getKey(), entry
                                    .getValue());
                        }
                    }
                } else {
                    for (Map.Entry<String, String> entry : initParams
                            .entrySet()) {
                        filterDef.addInitParameter(entry.getKey(), entry
                                .getValue());
                    }
                }

            }
        }
        if (!isWebXMLfilterDef) {
            fragment.addFilter(filterDef);
            if (urlPatternsSet || servletNamesSet) {
                filterMap.setFilterName(filterName);
                fragment.addFilterMapping(filterMap);
            }
        }
        if (urlPatternsSet || dispatchTypesSet) {
            Set<FilterMap> fmap = fragment.getFilterMappings();
            FilterMap descMap = null;
            for (FilterMap map : fmap) {
                if (filterName.equals(map.getFilterName())) {
                    descMap = map;
                    break;
                }
            }
            if (descMap != null) {
                String[] urlsPatterns = descMap.getURLPatterns();
                if (urlPatternsSet
                        && (urlsPatterns == null || urlsPatterns.length == 0)) {
                    for (String urlPattern : filterMap.getURLPatterns()) {
                        descMap.addURLPattern(urlPattern);
                    }
                }
                String[] dispatcherNames = descMap.getDispatcherNames();
                if (dispatchTypesSet
                        && (dispatcherNames == null || dispatcherNames.length == 0)) {
                    for (String dis : filterMap.getDispatcherNames()) {
                        descMap.setDispatcher(dis);
                    }
                }
            }
        }

    }

    protected String[] processAnnotationsStringArray(ElementValue ev) {
        ArrayList<String> values = new ArrayList<String>();
        if (ev instanceof ArrayElementValue) {
            ElementValue[] arrayValues =
                ((ArrayElementValue) ev).getElementValuesArray();
            for (ElementValue value : arrayValues) {
                values.add(value.stringifyValue());
            }
        } else {
            values.add(ev.stringifyValue());
        }
        String[] result = new String[values.size()];
        return values.toArray(result);
    }

    protected Map<String,String> processAnnotationWebInitParams(
            ElementValue ev) {
        Map<String, String> result = new HashMap<String,String>();
        if (ev instanceof ArrayElementValue) {
            ElementValue[] arrayValues =
                ((ArrayElementValue) ev).getElementValuesArray();
            for (ElementValue value : arrayValues) {
                if (value instanceof AnnotationElementValue) {
                    List<ElementValuePair> evps = ((AnnotationElementValue) value)
                            .getAnnotationEntry().getElementValuePairs();
                    String initParamName = null;
                    String initParamValue = null;
                    for (ElementValuePair evp : evps) {
                        if ("name".equals(evp.getNameString())) {
                            initParamName = evp.getValue().stringifyValue();
                        } else if ("value".equals(evp.getNameString())) {
                            initParamValue = evp.getValue().stringifyValue();
                        } else {
                            // Ignore
                        }
                    }
                    result.put(initParamName, initParamValue);
                }
            }
        }
        return result;
    }

    private class FragmentJarScannerCallback implements JarScannerCallback {

        private static final String FRAGMENT_LOCATION =
            "META-INF/web-fragment.xml";
        private Map<String,WebXml> fragments = new HashMap<String,WebXml>();
        private final boolean parseRequired;

        public FragmentJarScannerCallback(boolean parseRequired) {
            this.parseRequired = parseRequired;
        }

        @Override
        public void scan(JarURLConnection jarConn) throws IOException {

            URL url = jarConn.getURL();
            URL resourceURL = jarConn.getJarFileURL();
            Jar jar = null;
            InputStream is = null;
            WebXml fragment = new WebXml();

            try {
                jar = JarFactory.newInstance(url);
                if (parseRequired || context.getXmlValidation()) {
                    is = jar.getInputStream(FRAGMENT_LOCATION);
                }

                if (is == null) {
                    // If there is no web-fragment.xml to process there is no
                    // impact on distributable
                    fragment.setDistributable(true);
                } else {
                    InputSource source = new InputSource(
                            "jar:" + resourceURL.toString() + "!/" +
                            FRAGMENT_LOCATION);
                    System.out.println("==============" + resourceURL.toString());
                    source.setByteStream(is);
                    parseWebXml(source, fragment, true);
                }
            } finally {
                if (jar != null) {
                    jar.close();
                }
                addFragment(fragment, url);
            }
        }

        private String extractJarFileName(URL input) {
            String url = input.toString();
            if (url.endsWith("!/")) {
                // Remove it
                url = url.substring(0, url.length() - 2);
            }

            // File name will now be whatever is after the final /
            return url.substring(url.lastIndexOf('/') + 1);
        }

        @Override
        public void scan(File file) throws IOException {

            InputStream stream = null;
            WebXml fragment = new WebXml();

            try {
                File fragmentFile = new File(file, FRAGMENT_LOCATION);
                if (fragmentFile.isFile()) {
                    stream = new FileInputStream(fragmentFile);
                    InputSource source =
                        new InputSource(fragmentFile.toURI().toURL().toString());
                    source.setByteStream(stream);
                    parseWebXml(source, fragment, true);
                } else {
                    // If there is no web.xml, normal folder no impact on
                    // distributable
                    fragment.setDistributable(true);
                }
            } finally {
                if (stream != null) {
                    try {
                        stream.close();
                    } catch (IOException e) {
                    }
                }
                addFragment(fragment, file.toURI().toURL());
            }
        }

        private void addFragment(WebXml fragment, URL url) {
            fragment.setURL(url);
            if (fragment.getName() == null) {
                fragment.setName(url.toString());
            }
            fragment.setJarName(extractJarFileName(url));
            if (fragments.containsKey(fragment.getName())) {
                // Duplicate. Mark the fragment that has already been found with
                // this name as having a duplicate so Tomcat can handle it
                // correctly when the fragments are being ordered.
                String duplicateName = fragment.getName();
                fragments.get(duplicateName).setDuplicated(true);
                // Rename the current fragment so it doesn't clash
                fragment.setName(url.toString());
             }
            fragments.put(fragment.getName(), fragment);
        }

        public Map<String,WebXml> getFragments() {
            return fragments;
        }
    }

    private static class DefaultWebXmlCacheEntry {
        private final WebXml webXml;
        private final long globalTimeStamp;
        private final long hostTimeStamp;

        public DefaultWebXmlCacheEntry(WebXml webXml, long globalTimeStamp,
                long hostTimeStamp) {
            this.webXml = webXml;
            this.globalTimeStamp = globalTimeStamp;
            this.hostTimeStamp = hostTimeStamp;
        }

        public WebXml getWebXml() {
            return webXml;
        }

        public long getGlobalTimeStamp() {
            return globalTimeStamp;
        }

        public long getHostTimeStamp() {
            return hostTimeStamp;
        }
    }

    private static class JavaClassCacheEntry {
        public final String superclassName;

        public final String[] interfaceNames;

        private Set<ServletContainerInitializer> sciSet = null;

        public JavaClassCacheEntry(JavaClass javaClass) {
            superclassName = javaClass.getSuperclassName();
            interfaceNames = javaClass.getInterfaceNames();
        }

        public String getSuperclassName() {
            return superclassName;
        }

        public String[] getInterfaceNames() {
            return interfaceNames;
        }

        public Set<ServletContainerInitializer> getSciSet() {
            return sciSet;
        }

        public void setSciSet(Set<ServletContainerInitializer> sciSet) {
            this.sciSet = sciSet;
        }
    }
}
