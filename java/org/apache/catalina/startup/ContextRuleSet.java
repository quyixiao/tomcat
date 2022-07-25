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


import org.apache.tomcat.util.digester.Digester;
import org.apache.tomcat.util.digester.RuleSetBase;


/**
 * <p><strong>RuleSet</strong> for processing the contents of a
 * Context definition element.</p>
 *
 * @author Craig R. McClanahan
 */
public class ContextRuleSet extends RuleSetBase {


    // ----------------------------------------------------- Instance Variables


    /**
     * The matching pattern prefix to use for recognizing our elements.
     */
    protected String prefix = null;


    /**
     * Should the context be created.
     */
    protected boolean create = true;


    // ------------------------------------------------------------ Constructor


    /**
     * Construct an instance of this <code>RuleSet</code> with the default
     * matching pattern prefix.
     */
    public ContextRuleSet() {

        this("");

    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public ContextRuleSet(String prefix) {

        super();
        this.namespaceURI = null;
        this.prefix = prefix;

    }


    /**
     * Construct an instance of this <code>RuleSet</code> with the specified
     * matching pattern prefix.
     *
     * @param prefix Prefix for matching pattern rules (including the
     *  trailing slash character)
     */
    public ContextRuleSet(String prefix, boolean create) {

        super();
        this.namespaceURI = null;
        this.prefix = prefix;
        this.create = create;

    }


    // --------------------------------------------------------- Public Methods


    /**
     * <p>Add the set of Rule instances defined in this RuleSet to the
     * specified <code>Digester</code> instance, associating them with
     * our namespace URI (if any).  This method should only be called
     * by a Digester instance.</p>
     *
     * @param digester Digester instance to which the new Rule instances
     *  should be added.
     */
    @Override
    public void addRuleInstances(Digester digester) {
        // prefix是Server/Service/Engine/Host/
        // Context 的解析会根据create属性的不同而有所区别，这主要是由于Context来源于多处， 通过server.xml 配置Context时，create为true
        // 因此需要创建Context 实例，而通过HostConfig自动创建Context 时，create为false， 此时仅需要解析子节点即可，Catalina提供了Context
        // 实现类为org.apache.catalina.core.StandardContext ,Catalina 在创建Context 实例的同时，还添加了一个生命周期监听器
        // ContextConfig ，用于详细配置Context ，如解析web.xml 等 。
        if (create) {
            /*<Host>
            <Context docBase="servelet-test-1.0.war" path="/my-test"></Context>
            </Host> ContextConfig监听器可能在Digester框架解析server.xml文件生成Context对象时添加
            */
            digester.addObjectCreate(prefix + "Context",
                    "org.apache.catalina.core.StandardContext", "className");
            digester.addSetProperties(prefix + "Context");
        } else {
            digester.addRule(prefix + "Context", new SetContextPropertiesRule());
        }

        if (create) {
            // 1. ContextConfig监听器可能在Digester框架解析server.xml文件生成Context对象时添加
            // 2. ContextConfig 监听器可能由HostConfig监听器添加
            digester.addRule(prefix + "Context",
                             new LifecycleListenerRule
                                 ("org.apache.catalina.startup.ContextConfig",
                                  "configClass"));
            digester.addSetNext(prefix + "Context",
                                "addChild",
                                "org.apache.catalina.Container");
        }
        digester.addCallMethod(prefix + "Context/InstanceListener",
                               "addInstanceListener", 0);


        // 为Context 添加生命周期监听器
        digester.addObjectCreate(prefix + "Context/Listener",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Listener");
        digester.addSetNext(prefix + "Context/Listener",
                            "addLifecycleListener",
                            "org.apache.catalina.LifecycleListener");

        // 类加载器
        digester.addObjectCreate(prefix + "Context/Loader",
                            "org.apache.catalina.loader.WebappLoader",
                            "className");
        digester.addSetProperties(prefix + "Context/Loader");
        digester.addSetNext(prefix + "Context/Loader",
                            "setLoader",
                            "org.apache.catalina.Loader");
        // 为Context添加会话管理器
        // 默认实现org.apache.catalina.session.StandardManager ，同时为管理器指定会话存储方式和会话标识生成器， Context 提供了多种
        // 会话管理方式
        digester.addObjectCreate(prefix + "Context/Manager",
                                 "org.apache.catalina.session.StandardManager",
                                 "className");
        digester.addSetProperties(prefix + "Context/Manager");
        digester.addSetNext(prefix + "Context/Manager",
                            "setManager",
                            "org.apache.catalina.Manager");

        digester.addObjectCreate(prefix + "Context/Manager/Store",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Manager/Store");
        digester.addSetNext(prefix + "Context/Manager/Store",
                            "setStore",
                            "org.apache.catalina.Store");

        digester.addObjectCreate(prefix + "Context/Manager/SessionIdGenerator",
                                 "org.apache.catalina.util.StandardSessionIdGenerator",
                                 "className");
        digester.addSetProperties(prefix + "Context/Manager/SessionIdGenerator");
        digester.addSetNext(prefix + "Context/Manager/SessionIdGenerator",
                            "setSessionIdGenerator",
                            "org.apache.catalina.SessionIdGenerator");
        // 为Context 添加初始化参数
        // 通过该配置，为Context 不回初始化参数，我们可以在Context.xml文件中添加初始化参数，以实现所在Web 应用中的复用
        // 而不必每个Web 应用复制配置，当然在Web应用确实允许Tomcat 紧耦合的情况下，我们才推荐使用该方式进行配置，否则会导致Web 应用
        // 适应性非常差
        digester.addObjectCreate(prefix + "Context/Parameter",
                                 "org.apache.catalina.deploy.ApplicationParameter");
        digester.addSetProperties(prefix + "Context/Parameter");
        digester.addSetNext(prefix + "Context/Parameter",
                            "addApplicationParameter",
                            "org.apache.catalina.deploy.ApplicationParameter");

        // 为Context 添加安全配置以及Web资源配置
        digester.addRuleSet(new RealmRuleSet(prefix + "Context/"));

        digester.addObjectCreate(prefix + "Context/Resources",
                                 "org.apache.naming.resources.FileDirContext",
                                 "className");
        digester.addSetProperties(prefix + "Context/Resources");
        digester.addSetNext(prefix + "Context/Resources",
                            "setResources",
                            "javax.naming.directory.DirContext");

        // 为Context 添加资源链接
        // 为Context 添加资源链接ContextResourceLink ，用于J2EE命名服务
        digester.addObjectCreate(prefix + "Context/ResourceLink",
                "org.apache.catalina.deploy.ContextResourceLink");
        digester.addSetProperties(prefix + "Context/ResourceLink");
        digester.addRule(prefix + "Context/ResourceLink",
                new SetNextNamingRule("addResourceLink",
                        "org.apache.catalina.deploy.ContextResourceLink"));

        // 为Context 添加Value
        // 为Context 添加拦截器Value ，具体拦截器类由className属性指定
        digester.addObjectCreate(prefix + "Context/Valve",
                                 null, // MUST be specified in the element
                                 "className");
        digester.addSetProperties(prefix + "Context/Valve");
        digester.addSetNext(prefix + "Context/Valve",
                            "addValve",
                            "org.apache.catalina.Valve");

        //  为Context 添加守护资源配置
        // WatchedResource 标签用于为Context 添加监视资源 ，当这些资源发生变更时，Web 应用将被重新加载，默认为WEB-IN/web.xml
        digester.addCallMethod(prefix + "Context/WatchedResource",
                               "addWatchedResource", 0);
        // WrapperLifecycle标签用于为Context添加一个生命周期监听器类，此类的实例并非添加到Context上，而是添加到Context包含的Wrapper上
        digester.addCallMethod(prefix + "Context/WrapperLifecycle",
                               "addWrapperLifecycle", 0);
        // WrapperListener 标签用于为Context 添加一个容器监听器类（ContainerListener）,此类的实例同样添加到Wrapper上
        digester.addCallMethod(prefix + "Context/WrapperListener",
                               "addWrapperListener", 0);
        // JarScanner 标签用于为Context 添加一个Jar扫描器，Catalina的默认实现org.apache.tomcat.util.scan.StandardJarScanner
        // JarScanner 扫描Web 应用和类加载器的层级的Jar包，主要用于TLD 扫描和web-fragment.xml扫描，通过JarScanFilter 标签
        // 我们还可以为JarScanner 指定一个过滤器扫描和web-fragment.xml 扫描，通过JarScannerFilter标签，我们还可以为JarScanner
        // 指定一个过滤器，只有符合条件的Jar包才会被处理，置为为org.apache.tomcat.util.scan.StandardJarScanFilter
        digester.addObjectCreate(prefix + "Context/JarScanner",
                                 "org.apache.tomcat.util.scan.StandardJarScanner",
                                 "className");
        digester.addSetProperties(prefix + "Context/JarScanner");
        digester.addSetNext(prefix + "Context/JarScanner",
                            "setJarScanner",
                            "org.apache.tomcat.JarScanner");

    }

}
