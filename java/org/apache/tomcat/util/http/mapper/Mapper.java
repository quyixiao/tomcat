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

package org.apache.tomcat.util.http.mapper;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collection;
import java.util.List;
import java.util.concurrent.CopyOnWriteArrayList;

import javax.naming.NamingException;
import javax.naming.directory.DirContext;

import org.apache.tomcat.util.buf.Ascii;
import org.apache.tomcat.util.buf.CharChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.res.StringManager;

/**
 * Mapper, which implements the servlet API mapping rules (which are derived
 * from the HTTP rules).
 *
 * @author Remy Maucherat
 * Context 容器包含了一个请求路由映射器（Mapper）组件，它属于局部路径映射器，它只能负责本Context 容器内的路由导航，即每个Web 应用
 * 包含若干个Servlet ，而当对请求使用了请求分发器RequestDispatcher 以分发到不同的Servlet上处理时，就用了此映射器。
 *
 *
 *
 *
 * Mapper 组件主要的职责是负责Tomcat的请求路由，每个客户端的请求到达Tomcat后，都将由Mapper路由到对应的处理逻辑上，，在Tomcat 结构中有
 * 两部分会包含Mapper组件，一个是Connector 组件，称为全局路由Mapper；另外一个是Context 组件，称为局部路由Mapper, 本章将深入探讨Tomcat
 * 路由模块Mapper组件 。
 *
 * 对于 Web容器来说，根据请求客户端路径路由到对应的资源属于其核心功能，假设用户在自己的电脑上使用浏览器输入网址http://www.test.com/test/index.jsp
 * 报文通过互联网到达该主机服务器，服务器应该将其转到test 应用的index.jsp页面中进行处理，然后再返回。
 *
 * 当客户端浏览器地址栏中输入http://tomcat.apahce.org/apache-7.0-doc/index.html时，浏览器产生HTTP报文大致如下 。
 * GET /tomcat-7.0-doc/index.html HTTP/1.1
 * Host : tomcat.apche.org
 * Connection: keep-alive
 * Cache-Control:max-age = 0
 * Accept : text/html,application/xhtml+xml,application/xml;q=0.9,image/web, * / * ;q = 0.8
 * Upgrade-Insecure-Requests: 1
 * User-Agent : Mozilla/5.0 (Windows NT 10.0 ,WOW64 ) AppleWebKit/537.36(KHTMT like Gecko) Chome/45.0.2454.101 Safari/537.36
 * Accept-Encoding: gzip ,defalte ,sdch
 * Accept-Language : zh-CN , zh; q=0.8
 *
 * 注意加粗的报文，Host ,tomcat.apache.org 表明访问的主机是tomcat.apache.org 而/tomcat-7.0-doc/index.html 则表示请求的资源是
 * tomcat-7.0-doc Web 应用的index.html 页面，Tomcat 通过解析这些报文就可以知道请求对应的资源，因为Tomcat根据请求路径对处理进行了容器级别的
 * 分层，所以请求URL 与Tomcat内部组件的对应关系如图14.3 所示，tomcat.apche.org对应Host 容器，tomcat -7.0-doc对应的是 Context 容器，index.html
 * 对应的是Wrapper 容器。
 *
 * 对应上面的请求，该Web 项目对应的配置文件主要如下 ：
 *
 * <Host name="tomcat.apche.org" appBas e="webapps" autoDeploy="true">
 *     <Context path = "/tomcat-7.0-doc" doBase="/usr/tomcat/tomcat-7.0-doc"/>
 * </Host>
 *
 * 当  Tomcat 启动好后，首先http://tomcat.apche.org/tomcat-7.0-doc/index.html 请求就会被Tomcat 的路由器通过匹配算法路由到名为
 * tomcat.apache.org的Host容器上，然后在该容器中继续匹配名为tomcat-7.0-doc 的Context 容器的Web 应用 ，最后该Context 容器中匹配index.html
 * 资源，并返回给客户端 。
 *
 * 以上大致介绍了Web 请求从客户端到服务器tomcat的资源匹配过程 ，每个完整的请求都有如上的层次结构，Tomcat 的内部中有Host,Context，Wrapper
 * 层次与之对应，而具体的路由工作则由Mapper 组件负责，下面介绍Mapper的实现。
 *
 * Mapper的实现。
 * Mapper组件的核心功能是提供了请求路径的路由映射，根据某个请求路径，通过计算得到相应的Servlet(Wrapper)，下面介绍Mapper的实现细节，
 * 包括Host容器，Context 容器，Wrapper 容器等映射关系以及映射算法。
 *
 * 如果要将整个Tomcat 容器中所有的Web 项目以Servlet级别组织起来 ，需要一个多层级的类似Map结构的存储空间
 * Mapper 只要包含一个Host数组即可完成所有组件的关系映射，在Tomcat启动时将所有的Host 容器和它的名字组成Host映射模型添加到Mapper对象中。
 * 把每个Host下的Context 容器和它的名字组成Context映射模式添加到对应的Host下，把每个Context下的Wappre 容器和它的名字组成的Wapper
 * 添加到对应的Context下，Mapper 组件提供了对应的Host映射，Context 映射，Wrapper 映射的添加和移除方法，在Tomcat 容器中添加或移除相应的
 * 容器时，都要调用相应的方法维护这些映射关系，为了提高查找速度和效率，Mapper 组件使用二分搜索查找，所以在添加时应按照字典序把Host,Context
 * Wrapper等映射排序 。
 * 当Tomcat 启动稳定后，意味着这些映射都已经组织好，那么具体是如何查找对应的这容器的呢？
 * 关于Context的匹配，对上面的查找的Host 映射中的Context映射数组进行忽略大小写的二分搜索查找，这里有个比较特殊的情况就是请求地址可以直接以
 * Context名结束，例如 http://tomcat.apache.org/tomcat-7.0-doc ，另外一些则类似于http://tomcat.apche.org/tomcat-7.0-doc/index.html
 * ,另外映射中的name对应 Context容器的path属性。
 *  关于Wapper的匹配，涉及几个步骤，首先，尝试使用精确匹配法匹配精确类型Servlet 的路径，然后尝试使用前缀匹配通配符类型Servlet ，接着
 *  尝试使用扩展名匹配通配符类型Servlet ，最后匹配默认的Sevlet 。
 *  Tomcat 在处理请求时对请求的路由分发由Mapper组件负责，请求通过Mapper 找到最终的Servlet 资源 ，而在Tomcat 中会有两种类型的
 *  Mapper, 根据他们作用范围，分别称为全局路由Maper 和局部路由Mapper   。
 *
 *
 * 局部路由Mapper是指提供了Context 容器内部路由导航功能的组件，它只存在于Context容器中，用于记录访问资源与Wrapper之间的映射 。每个
 * Web 应用都存在自己的局部路由Mapper组件 。
 * 在做Web开发时，我们有时会用类似request.getRequestDispacher("/servlet/jump?action=do").forward(request,response)这样的代码 。
 * 这里其实就是使用了context容器内部的Mapper功能，用它匹配/servlet/jump?action=do 对应的Servlet，然后调用Servlet具体的处理逻辑 。
 * 从这点来看，它只能路由一部分的地址路径，而不能路径一个完整的请求地址 。
 * 所以局部路由Mapper只能在同一个Web 应用内进行转发路由，而不能实现跨Web 应用的路由，如果要实现跨web应用，需要用重定向的功能，让客户端
 * 重定向到其他的主机或其他的Web应用上，而对客户端到服务端的请求，则需要全局路由Mapper组件参与 。
 *
 * 全局路由Mapper。
 * 除了局部路由Mapper，另外一个一种Mapper就是全局路由Mapper,它是提供了完整的路由导航功能的组件，它位于Tomcat的Connector 组件中，通过它
 * 能对Host,Context,Wrapper 等路由，即对于一个完整的请求地址，它能定位到指定的Host容器，Context 容器以及Wrapper容器。
 *
 * 所以全局路由Mapper拥有Tomcat容器完整的路由映射，负责完整的请求地址路由功能 。
 * 围绕着NamingManager 的这些类和接口是JNDI 能正常运行的基础，所有的上下文都要实现Context接口，这个接口的主要方法是lookup ,bind , 分别用于查找对象的
 * 与绑定对象，我们熟知的InitalContext即是JNDI的入口，NamingManager包含很多的操作上下文的方法，其中，getStateToBind及getObjectInstace
 * 两个方法有必要提一下，它们将任意类型的对象转换成适合在命名空间存储的形式，并且将存储在命名空间中的信息转换成对象，两者是相反的过程。
 * 具体的转换策略可以在自定义的XXXFactory工厂类里面自定义，另外，还有几个接口用于约束在整个JNDI机制实现中特定的方法，为了更好的理解JNDI
 * 的运行机制，下面分步说明JNDI 的运行机制。
 * 1. 实例化InitialContext 作为入口 。
 * 2. 调用InitialContext的lookup或bind方法 。
 * 3. lookup，bind方法实际上是调用了getURLOrDefaultInitialCtx返回的上下文的lookup或bind方法 。
 *
 *
 */
public final class Mapper {


    private static final org.apache.juli.logging.Log log =
        org.apache.juli.logging.LogFactory.getLog(Mapper.class);

    static final StringManager sm =
        StringManager.getManager(Mapper.class.getPackage().getName());

    // ----------------------------------------------------- Instance Variables


    /**
     * Array containing the virtual hosts definitions.
     */
    Host[] hosts = new Host[0];


    /**
     * Default host name.
     */
    String defaultHostName = null;

    /**
     * ContextVersion associated with this Mapper, used for wrapper mapping.
     *
     * <p>
     * It is used only by Mapper in a Context. Is not used by Mapper in a
     * Connector.
     *
     * @see #setContext(String, String[], javax.naming.Context)
     */
    ContextVersion context = new ContextVersion();


    // --------------------------------------------------------- Public Methods


    /**
     * Set default host.
     *
     * @param defaultHostName Default host name
     */
    public void setDefaultHostName(String defaultHostName) {
        this.defaultHostName = defaultHostName;
    }

    /**
     * Add a new host to the mapper.
     *
     * @param name Virtual host name
     * @param aliases Alias names for the virtual host
     * @param host Host object
     */
    public synchronized void addHost(String name, String[] aliases,
                                     Object host) {
        Host[] newHosts = new Host[hosts.length + 1];
        Host newHost = new Host(name, host);
        if (insertMap(hosts, newHosts, newHost)) {
            hosts = newHosts;
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("mapper.addHost.success", name));
            }
        } else {
            Host duplicate = hosts[find(hosts, name)];
            if (duplicate.object == host) {
                // The host is already registered in the mapper.
                // E.g. it might have been added by addContextVersion()
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("mapper.addHost.sameHost", name));
                }
                newHost = duplicate;
            } else {
                log.error(sm.getString("mapper.duplicateHost", name,
                        duplicate.getRealHostName()));
                // Do not add aliases, as removeHost(hostName) won't be able to
                // remove them
                return;
            }
        }
        List<Host> newAliases = new ArrayList<Host>(aliases.length);
        for (String alias : aliases) {
            Host newAlias = new Host(alias, newHost);
            if (addHostAliasImpl(newAlias))  {
                newAliases.add(newAlias);
            }
        }
        newHost.addAliases(newAliases);
    }


    /**
     * Remove a host from the mapper.
     *
     * @param name Virtual host name
     */
    public synchronized void removeHost(String name) {
        // Find and remove the old host
        Host host = exactFind(hosts, name);
        if (host == null || host.isAlias()) {
            return;
        }
        Host[] newHosts = hosts.clone();
        // Remove real host and all its aliases
        int j = 0;
        for (int i = 0; i < newHosts.length; i++) {
            if (newHosts[i].getRealHost() != host) {
                newHosts[j++] = newHosts[i];
            }
        }
        hosts = Arrays.copyOf(newHosts, j);
    }

    /**
     * Add an alias to an existing host.
     * @param name  The name of the host
     * @param alias The alias to add
     */
    public synchronized void addHostAlias(String name, String alias) {
        Host realHost = exactFind(hosts, name);
        if (realHost == null) {
            // Should not be adding an alias for a host that doesn't exist but
            // just in case...
            return;
        }
        Host newAlias = new Host(alias, realHost);
        if (addHostAliasImpl(newAlias)) {
            realHost.addAlias(newAlias);
        }
    }

    private boolean addHostAliasImpl(Host newAlias) {
        Host[] newHosts = new Host[hosts.length + 1];
        if (insertMap(hosts, newHosts, newAlias)) {
            hosts = newHosts;
            if (log.isDebugEnabled()) {
                log.debug(sm.getString("mapper.addHostAlias.success",
                        newAlias.name, newAlias.getRealHostName()));
            }
            return true;
        } else {
            Host duplicate = hosts[find(hosts, newAlias.name)];
            if (duplicate.getRealHost() == newAlias.getRealHost()) {
                // A duplicate Alias for the same Host.
                // A harmless redundancy. E.g.
                // <Host name="localhost"><Alias>localhost</Alias></Host>
                if (log.isDebugEnabled()) {
                    log.debug(sm.getString("mapper.addHostAlias.sameHost",
                            newAlias.name, newAlias.getRealHostName()));
                }
                return false;
            }
            log.error(sm.getString("mapper.duplicateHostAlias", newAlias.name,
                    newAlias.getRealHostName(), duplicate.getRealHostName()));
            return false;
        }
    }

    /**
     * Remove a host alias
     * @param alias The alias to remove
     */
    public synchronized void removeHostAlias(String alias) {
        // Find and remove the alias
        Host host = exactFind(hosts, alias);
        if (host == null || !host.isAlias()) {
            return;
        }
        Host[] newHosts = new Host[hosts.length - 1];
        if (removeMap(hosts, newHosts, alias)) {
            hosts = newHosts;
            host.getRealHost().removeAlias(host);
        }

    }

    /**
     * Replace {@link Host#contextList} field in <code>realHost</code> and
     * all its aliases with a new value.
     */
    private void updateContextList(Host realHost, ContextList newContextList) {
        realHost.contextList = newContextList;
        for (Host alias : realHost.getAliases()) {
            alias.contextList = newContextList;
        }
    }

    /**
     * Set context, used for wrapper mapping (request dispatcher).
     *
     * @param welcomeResources Welcome files defined for this context
     * @param resources Static resources of the context
     */
    public void setContext(String path, String[] welcomeResources,
                           javax.naming.Context resources) {
        context.path = path;
        context.welcomeResources = welcomeResources;
        context.resources = resources;
    }


    /**
     * Add a new Context to an existing Host.
     *
     * @param hostName Virtual host name this context belongs to
     * @param host Host object
     * @param path Context path
     * @param version Context version
     * @param context Context object
     * @param welcomeResources Welcome files defined for this context
     * @param resources Static resources of the context
     * @deprecated Use {@link #addContextVersion(String, Object, String, String, Object, String[],
     *             javax.naming.Context, Collection, boolean, boolean)}
     */
    @Deprecated
    public void addContextVersion(String hostName, Object host, String path,
            String version, Object context, String[] welcomeResources,
            javax.naming.Context resources) {
        addContextVersion(hostName, host, path, version, context,
                welcomeResources, resources, null);
    }


    /**
     * Add a new Context to an existing Host.
     *
     * @param hostName Virtual host name this context belongs to
     * @param host Host object
     * @param path Context path
     * @param version Context version
     * @param context Context object
     * @param welcomeResources Welcome files defined for this context
     * @param resources Static resources of the context
     * @param wrappers Information on wrapper mappings
     * @deprecated Use {@link #addContextVersion(String, Object, String, String, Object, String[],
     *             javax.naming.Context, Collection, boolean, boolean)}
     */
    @Deprecated
    public void addContextVersion(String hostName, Object host, String path,
            String version, Object context, String[] welcomeResources,
            javax.naming.Context resources, Collection<WrapperMappingInfo> wrappers) {
        addContextVersion(hostName, host, path, version, context, welcomeResources, resources,
                wrappers, false, false);
    }


    /**
     * Add a new Context to an existing Host.
     *
     * @param hostName Virtual host name this context belongs to
     * @param host Host object
     * @param path Context path
     * @param version Context version
     * @param context Context object
     * @param welcomeResources Welcome files defined for this context
     * @param resources Static resources of the context
     * @param wrappers Information on wrapper mappings
     * @param mapperContextRootRedirectEnabled Mapper does context root redirects
     * @param mapperDirectoryRedirectEnabled Mapper does directory redirects
     */
    public void addContextVersion(String hostName, Object host, String path,
            String version, Object context, String[] welcomeResources,
            javax.naming.Context resources, Collection<WrapperMappingInfo> wrappers,
            boolean mapperContextRootRedirectEnabled, boolean mapperDirectoryRedirectEnabled) {

        Host mappedHost = exactFind(hosts, hostName);
        if (mappedHost == null) {
            addHost(hostName, new String[0], host);
            mappedHost = exactFind(hosts, hostName);
            if (mappedHost == null) {
                log.error("No host found: " + hostName);
                return;
            }
        }
        if (mappedHost.isAlias()) {
            log.error("No host found: " + hostName);
            return;
        }
        int slashCount = slashCount(path);
        synchronized (mappedHost) {
            // 整个每个Context的不同版本生成不同的ContextVersion对象，其实就是不同的应用，应为就算Context的Path相同，version不相同，那么还是表示不同的应用
            ContextVersion newContextVersion = new ContextVersion(version, context);
            newContextVersion.path = path;
            newContextVersion.slashCount = slashCount;
            newContextVersion.welcomeResources = welcomeResources;
            newContextVersion.resources = resources;
            newContextVersion.mapperContextRootRedirectEnabled = mapperContextRootRedirectEnabled;
            newContextVersion.mapperDirectoryRedirectEnabled = mapperDirectoryRedirectEnabled;

            if (wrappers != null) {
                // 针对每个应用将下层包括的Wrapper映射关系进行分类，并且添加到contextVersion中不同的Wrapperlist中去
                addWrappers(newContextVersion, wrappers);
            }

            ContextList contextList = mappedHost.contextList;
            Context mappedContext = exactFind(contextList.contexts, path);
            if (mappedContext == null) {
                mappedContext = new Context(path, newContextVersion);
                ContextList newContextList = contextList.addContext(
                        mappedContext, slashCount);
                if (newContextList != null) {
                    // 如果ContextList发生了改变，回头更新Host中的contextList
                    updateContextList(mappedHost, newContextList);
                }
            } else {
                ContextVersion[] contextVersions = mappedContext.versions;
                ContextVersion[] newContextVersions =
                    new ContextVersion[contextVersions.length + 1];
                if (insertMap(contextVersions, newContextVersions, newContextVersion)) {
                    mappedContext.versions = newContextVersions;
                } else {
                    // Re-registration after Context.reload()
                    // Replace ContextVersion with the new one
                    int pos = find(contextVersions, version);
                    if (pos >= 0 && contextVersions[pos].name.equals(version)) {
                        contextVersions[pos] = newContextVersion;
                    }
                }
            }
        }

    }


    /**
     * Remove a context from an existing host.
     *
     * @param hostName Virtual host name this context belongs to
     * @param path Context path
     * @param version Context version
     */
    public void removeContextVersion(String hostName, String path,
            String version) {
        Host host = exactFind(hosts, hostName);
        if (host == null || host.isAlias()) {
            return;
        }
        synchronized (host) {
            ContextList contextList = host.contextList;
            Context context = exactFind(contextList.contexts, path);
            if (context == null) {
                return;
            }

            ContextVersion[] contextVersions = context.versions;
            ContextVersion[] newContextVersions =
                new ContextVersion[contextVersions.length - 1];
            if (removeMap(contextVersions, newContextVersions, version)) {
                if (newContextVersions.length == 0) {
                    // Remove the context
                    ContextList newContextList = contextList.removeContext(path);
                    if (newContextList != null) {
                        updateContextList(host, newContextList);
                    }
                } else {
                    context.versions = newContextVersions;
                }
            }
        }
    }


    /**
     * Mark a context as being reloaded. Reversion of this state is performed
     * by calling <code>addContextVersion(...)</code> when context starts up.
     *
     * @param ctxt      The actual context
     * @param hostName  Virtual host name this context belongs to
     * @param contextPath Context path
     * @param version   Context version
     */
    public void pauseContextVersion(Object ctxt, String hostName,
            String contextPath, String version) {

        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, true);
        if (contextVersion == null || !ctxt.equals(contextVersion.object)) {
            return;
        }
        contextVersion.markPaused();
    }


    private ContextVersion findContextVersion(String hostName,
            String contextPath, String version, boolean silent) {
        Host host = exactFind(hosts, hostName);
        if (host == null || host.isAlias()) {
            if (!silent) {
                log.error("No host found: " + hostName);
            }
            return null;
        }
        Context context = exactFind(host.contextList.contexts, contextPath);
        if (context == null) {
            if (!silent) {
                log.error("No context found: " + contextPath);
            }
            return null;
        }
        ContextVersion contextVersion = exactFind(context.versions, version);
        if (contextVersion == null) {
            if (!silent) {
                log.error("No context version found: " + contextPath + " "
                        + version);
            }
            return null;
        }
        return contextVersion;
    }


    public void addWrapper(String hostName, String contextPath, String version,
                           String path, Object wrapper, boolean jspWildCard,
                           boolean resourceOnly) {
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        addWrapper(contextVersion, path, wrapper, jspWildCard, resourceOnly);
    }


    public void addWrapper(String path, Object wrapper, boolean jspWildCard,
            boolean resourceOnly) {
        addWrapper(context, path, wrapper, jspWildCard, resourceOnly);
    }

    public void addWrappers(String hostName, String contextPath,
            String version, Collection<WrapperMappingInfo> wrappers) {
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        addWrappers(contextVersion, wrappers);
    }

    /**
     * Adds wrappers to the given context.
     *
     * @param contextVersion The context to which to add the wrappers
     * @param wrappers Information on wrapper mappings
     */
    private void addWrappers(ContextVersion contextVersion,
            Collection<WrapperMappingInfo> wrappers) {
        for (WrapperMappingInfo wrapper : wrappers) {
            addWrapper(contextVersion, wrapper.getMapping(),
                    wrapper.getWrapper(), wrapper.isJspWildCard(),
                    wrapper.isResourceOnly());
        }
    }

    /**
     * Adds a wrapper to the given context.
     *
     * @param context The context to which to add the wrapper
     * @param path Wrapper mapping
     * @param wrapper The Wrapper object
     * @param jspWildCard true if the wrapper corresponds to the JspServlet
     *   and the mapping path contains a wildcard; false otherwise
     * @param resourceOnly true if this wrapper always expects a physical
     *                     resource to be present (such as a JSP)
     *
     */
    protected void addWrapper(ContextVersion context, String path,
            Object wrapper, boolean jspWildCard, boolean resourceOnly) {

        synchronized (context) {
            if (path.endsWith("/*")) {  // 比如/test/*
                // Wildcard wrapper 通配符Wrapper
                String name = path.substring(0, path.length() - 2);
                // 这里的两个Wrapper都表示Servlet包装器，不同的是，一个是只用来记录映射关系，一个是真正的StandardWrapper
                Wrapper newWrapper = new Wrapper(name, wrapper, jspWildCard,
                        resourceOnly);

                // 如果在老的wildcardWrappers中不存在相同name的，则把新的通配符wrapper插入到数组中
                Wrapper[] oldWrappers = context.wildcardWrappers;
                Wrapper[] newWrappers =
                    new Wrapper[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.wildcardWrappers = newWrappers;
                    int slashCount = slashCount(newWrapper.name);   // 一个url被"/"切分为几个部分
                    // 记录当前Context中url按"/"切分后长度最大的长度
                    if (slashCount > context.nesting) {
                        context.nesting = slashCount;
                    }
                }
            } else if (path.startsWith("*.")) { // 比如*.jsp
                // Extension wrapper  扩展匹配
                String name = path.substring(2);
                Wrapper newWrapper = new Wrapper(name, wrapper, jspWildCard,
                        resourceOnly);
                Wrapper[] oldWrappers = context.extensionWrappers;
                Wrapper[] newWrappers =
                    new Wrapper[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // Default wrapper
                Wrapper newWrapper = new Wrapper("", wrapper, jspWildCard,
                        resourceOnly);
                context.defaultWrapper = newWrapper;
            } else {
                // Exact wrapper   精确匹配
                final String name;
                if (path.length() == 0) {
                    // Special case for the Context Root mapping which is
                    // treated as an exact match
                    // 我们可以在web.xml中配置一个mapping关系是，url-pattern设置为空，那么就表示可以通过应用跟路径来访问
                    name = "/";
                } else {
                    name = path;
                }
                Wrapper newWrapper = new Wrapper(name, wrapper, jspWildCard,
                        resourceOnly);
                Wrapper[] oldWrappers = context.exactWrappers;
                Wrapper[] newWrappers =
                    new Wrapper[oldWrappers.length + 1];
                if (insertMap(oldWrappers, newWrappers, newWrapper)) {
                    context.exactWrappers = newWrappers;
                }
            }
        }
    }


    /**
     * Remove a wrapper from the context associated with this wrapper.
     *
     * @param path Wrapper mapping
     */
    public void removeWrapper(String path) {
        removeWrapper(context, path);
    }


    /**
     * Remove a wrapper from an existing context.
     *
     * @param hostName Virtual host name this wrapper belongs to
     * @param contextPath Context path this wrapper belongs to
     * @param path Wrapper mapping
     */
    public void removeWrapper(String hostName, String contextPath,
            String version, String path) {
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, true);
        if (contextVersion == null || contextVersion.isPaused()) {
            return;
        }
        removeWrapper(contextVersion, path);
    }

    protected void removeWrapper(ContextVersion context, String path) {

        if (log.isDebugEnabled()) {
            log.debug(sm.getString("mapper.removeWrapper", context.name, path));
        }

        synchronized (context) {
            if (path.endsWith("/*")) {
                // Wildcard wrapper
                String name = path.substring(0, path.length() - 2);
                Wrapper[] oldWrappers = context.wildcardWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                Wrapper[] newWrappers =
                    new Wrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    // Recalculate nesting
                    context.nesting = 0;
                    for (int i = 0; i < newWrappers.length; i++) {
                        int slashCount = slashCount(newWrappers[i].name);
                        if (slashCount > context.nesting) {
                            context.nesting = slashCount;
                        }
                    }
                    context.wildcardWrappers = newWrappers;
                }
            } else if (path.startsWith("*.")) {
                // Extension wrapper
                String name = path.substring(2);
                Wrapper[] oldWrappers = context.extensionWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                Wrapper[] newWrappers =
                    new Wrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.extensionWrappers = newWrappers;
                }
            } else if (path.equals("/")) {
                // Default wrapper
                context.defaultWrapper = null;
            } else {
                // Exact wrapper
                String name;
                if (path.length() == 0) {
                    // Special case for the Context Root mapping which is
                    // treated as an exact match
                    name = "/";
                } else {
                    name = path;
                }
                Wrapper[] oldWrappers = context.exactWrappers;
                if (oldWrappers.length == 0) {
                    return;
                }
                Wrapper[] newWrappers =
                    new Wrapper[oldWrappers.length - 1];
                if (removeMap(oldWrappers, newWrappers, name)) {
                    context.exactWrappers = newWrappers;
                }
            }
        }
    }


    /**
     * Add a welcome file to the given context.
     *
     * @param hostName
     * @param contextPath
     * @param welcomeFile
     */
    public void addWelcomeFile(String hostName, String contextPath,
            String version, String welcomeFile) {
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        int len = contextVersion.welcomeResources.length + 1;
        String[] newWelcomeResources = new String[len];
        System.arraycopy(contextVersion.welcomeResources, 0,
                newWelcomeResources, 0, len - 1);
        newWelcomeResources[len - 1] = welcomeFile;
        contextVersion.welcomeResources = newWelcomeResources;
    }

    /**
     * Remove a welcome file from the given context.
     *
     * @param hostName
     * @param contextPath
     * @param welcomeFile
     */
    public void removeWelcomeFile(String hostName, String contextPath,
            String version, String welcomeFile) {
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, false);
        if (contextVersion == null || contextVersion.isPaused()) {
            return;
        }
        int match = -1;
        for (int i = 0; i < contextVersion.welcomeResources.length; i++) {
            if (welcomeFile.equals(contextVersion.welcomeResources[i])) {
                match = i;
                break;
            }
        }
        if (match > -1) {
            int len = contextVersion.welcomeResources.length - 1;
            String[] newWelcomeResources = new String[len];
            System.arraycopy(contextVersion.welcomeResources, 0,
                    newWelcomeResources, 0, match);
            if (match < len) {
                System.arraycopy(contextVersion.welcomeResources, match + 1,
                        newWelcomeResources, match, len - match);
            }
            contextVersion.welcomeResources = newWelcomeResources;
        }
    }

    /**
     * Clear the welcome files for the given context.
     *
     * @param hostName
     * @param contextPath
     */
    public void clearWelcomeFiles(String hostName, String contextPath,
            String version) {
        ContextVersion contextVersion = findContextVersion(hostName,
                contextPath, version, false);
        if (contextVersion == null) {
            return;
        }
        contextVersion.welcomeResources = new String[0];
    }


    /**
     * Map the specified host name and URI, mutating the given mapping data.
     *
     * @param host Virtual host name
     * @param uri URI
     * @param mappingData This structure will contain the result of the mapping
     *                    operation
     * 在讲解算法之前，有必要先了解一下Mapper的静态结构，这有助于我们加深对算法的理解，Mapper静态结构如图 3 -6 所示 。
     * 第一：Mapper 对Host ，Context , Wrapper 均提供了对应的封装类，因此描述算法时， 我们用MappedHost，MappedContext ，MappedWrapper
     *表示其封装对象，用Host,Context,Wapper 表示Catalina组件 。
     *
     */
    public void map(MessageBytes host, MessageBytes uri, String version,
                    MappingData mappingData)
        throws Exception {

        if (host.isNull()) {
            host.getCharChunk().append(defaultHostName);
        }
        host.toChars();
        uri.toChars();
        internalMap(host.getCharChunk(), uri.getCharChunk(), version,
                mappingData);

    }


    /**
     * Map the specified URI relative to the context,
     * mutating the given mapping data.
     *
     * @param uri URI
     * @param mappingData This structure will contain the result of the mapping
     *                    operation
     */
    public void map(MessageBytes uri, MappingData mappingData)
        throws Exception {

        uri.toChars();
        CharChunk uricc = uri.getCharChunk();
        uricc.setLimit(-1);
        internalMapWrapper(context, uricc, mappingData);

    }


    // -------------------------------------------------------- Private Methods


    /**
     * Map the specified URI.
     */
    private final void internalMap(CharChunk host, CharChunk uri,
            String version, MappingData mappingData) throws Exception {

        if (mappingData.host != null) {
            // The legacy code (dating down at least to Tomcat 4.1) just
            // skipped all mapping work in this case. That behaviour has a risk
            // of returning an inconsistent result.
            // I do not see a valid use case for it.
            throw new AssertionError();
        }

        uri.setLimit(-1);

        // Virtual host mapping
        // 从当前Engine中包含的虚拟主机中进行筛选
        Host[] hosts = this.hosts;
        // 1. 一般情况下，需要查找Host名称为请求的serverName,但是，如果没有指定Host名称，那么将使用默认的Host名称
        // 【注意】：默认的Host名称通过按照Engine的defaultHost属性查找其Host子节点获取，查找规则：Host名称与defaultHost相等
        // 或Host缩写名与defaultHost相等（忽略大小写），此处需要注意一个问题，由于Container在维护子节点时，使用的是HashMap 。
        // 因此得到其子节点列表时 ，顺序与名称的哈希码相关，例如 ，如果Engine 中配置的defaultHost为"Server001",而Tomcat 中配置了
        // "SERVER001" 和 "Server001" 两个,两个Host ，此时默认Host名称为"SERVER001"，而如果我们将"Sever001"换成了"server001", 则
        // 结果就变成了"server001",当然，实际配置过程中，应彻底避免这种命名
        // 2. 按照Host名称查找Mapper.Host(忽略大小写)，如果没有找到匹配结果，且默认的Host名称不为空，则按默认的Host名称精确查找
        // ,如果存在匹配结果，将其保存到MappingData的Host属性
        // 【注意】此处有时候会让人产生疑惑（第1步在没有指定host名称时），已经将host名称设置为默认的Host名称，为什么第2步仍然压根按照
        // 默认的Host名称查找，这主要满足如下场景，当host不为空，且为无效名称时 ， Tomcat将会尝试返回默认的Host ，而非空值 。
        Host mappedHost = exactFindIgnoreCase(hosts, host);
        if (mappedHost == null) {
            if (defaultHostName == null) {
                return;
            }
            mappedHost = exactFind(hosts, defaultHostName);
            if (mappedHost == null) {
                return;
            }
        }
        mappingData.host = mappedHost.object;       // 找到了对应的Standerhost

        // Context mapping
        ContextList contextList = mappedHost.contextList;
        Context[] contexts = contextList.contexts;  // 找到的host中对应的context
        int nesting = contextList.nesting;
        // 3. 按照url查找MapperdContext最大可能匹配的位置pos(只限于第2步查找的MappedHost下的MappedContext),之所以如此描述。
        // 与Tomcat的查找算法相关
        // 【注意】：在Mapperd中所有的Container是有序的，按照名称的ASCII正序排列，因此Tomcat采用十分法进行查找，其返回的结果存在如下两种情况
        // 3.1  -1:表明url比当前的MappedHost下所有的MappedContext的名称都小，也就是说，没有匹配到MappedContext
        // 3.2 >=0 可能是精确匹配的位置，也可能是表中比url小的最大值位置，即使没有精确匹配，也不代表最终没有匹配项，这需要进一步的处理。
        // 如果比较难以理解，我们下面试举一个例子，例如我们配置了两个Context,路径分别为/myapp/和/myapp/app1 ，在Tomcat中，这两个是
        // 允许同时存在的，然后我们尝试输入请求路径http://127.0.0.1:8080/myapp/app1/index.jsp, 此时url为/myapp/app1/index.jsp
        // 很显然，url 可能和Context路径精确匹配，此时返回比其最小的最大值位置（即/myapp/app1）,当Tomcat发现其非精确匹配时，会将url
        // 进行截取（截取为/myapp/app1）,再进行匹配，此时将会精确匹配到/myapp/app1, 当然，如果我们输入的是http://127.0.0.1:8080/myapp/app2/index.jsp
        // Tomcat将会继续截取，直到匹配到/myapp
        // 由此可见，Tomcat 总是试图查找一个最精确的MappedContext（如上例使用/myapp/app1）,而非/myapp， 尽管这两个都是可以匹配的。
        int pos = find(contexts, uri); //折半查找法
        if (pos == -1) {
            return;
        }

        int lastSlash = -1;
        int uriEnd = uri.getEnd();
        int length = -1;
        boolean found = false;
        Context context = null;
        // 4. 当第3步查找的pos>=0 ,得到对应的MappedContext，如果url与MappedContext路径相等或者url以MappedContext路径+"/"开头
        // 均视为找到了匹配的MappedContext,否则，循环执行第4步，逐渐 降低精确度以查找合适的MappedContext（具体可以参见第3步的例子）
        // 【注意】对于第3步的例子，如果请求地址为http://127.0.0.1:8080/myapp/app1 ,那么最终的匹配条件是url与MappedContext路径相等
        // 如果请求地址为 http://127.0.0.1:8080/myapp/app1/index.jsp ,那么最终的匹配条件应该是url以MappedContext路径 + "/"开头。
        while (pos >= 0) {
            context = contexts[pos];
            if (uri.startsWith(context.name)) {
                length = context.name.length();
                if (uri.getLength() == length) {
                    found = true;
                    break;
                } else if (uri.startsWithIgnoreCase("/", length)) {
                    // 这个方法判断在length位置是不是"/",所以如果uri中是ServletDemo123,Context是ServletDemo那么将不会匹配
                    found = true;
                    break;
                }
            }
            if (lastSlash == -1) {
                lastSlash = nthSlash(uri, nesting + 1);
            } else {
                lastSlash = lastSlash(uri);
            }
            uri.setEnd(lastSlash);
            pos = find(contexts, uri);
        }
        uri.setEnd(uriEnd);
        // 5. 如果循环结束后仍然未找到合适的MappedContext ，那么会判断第0个MappedContext的名称是否为空字符串，如果是，则将其作为匹配
        // 结果（即使用默认的MappedContext）
        if (!found) {
            // 就算没有找到，那么也将当前这个请求交给context[0]来进行处理,就是ROOT应用
            if (contexts[0].name.equals("")) {
                context = contexts[0];
            } else {
                context = null;
            }
        }
        if (context == null) {
            return;
        }

        mappingData.contextPath.setString(context.name); // 设置最终映射到的contextPath

        ContextVersion contextVersion = null;
        // 6. 前面曾讲到MappedContext存放了路径相同的所有版本的Context(ContextVersion) ，因此第5步结束后，还需要对MappedContext版本进行
        // 处理，如果指定了版本号，则返回版本号相等的ContextVersion ,否则返回版本号最大的，最后，将ContextVersion中维护的Context保存到
        // MappingData中
        ContextVersion[] contextVersions = context.versions;
        final int versionCount = contextVersions.length;
        if (versionCount > 1) { // 如果context有多个版本
            Object[] contextObjects = new Object[contextVersions.length];
            for (int i = 0; i < contextObjects.length; i++) {
                contextObjects[i] = contextVersions[i].object;
            }
            mappingData.contexts = contextObjects; // 匹配的所有版本
            if (version != null) {
                contextVersion = exactFind(contextVersions, version); // 找出对应版本
            }
        }
        if (contextVersion == null) {
            // Return the latest version
            // The versions array is known to contain at least one element
            contextVersion = contextVersions[versionCount - 1]; // 如果没找到对应版本，则返回最新版本
        }

        mappingData.context = contextVersion.object;
        mappingData.contextSlashCount = contextVersion.slashCount;

        // Wrapper mapping
        // 7. 如果Context当前状态为有效（如图3-6可知，当Context处于暂停状态时，将会重新按照url映射，此时MappedWrapper的映射没有意义），
        // 则映射对应的MappedWrapper
        if (!contextVersion.isPaused()) {
            // 根据uri寻找wrapper
            internalMapWrapper(contextVersion, uri, mappingData);
        }

    }


    /**
     * Wrapper mapping.
     * MapperWrapper映射
     *      我们知道ContextVersion中将MappedWrapper分为：默认Wrapper(defaultWrapper)，精确Wrapper(exactWrappers) ,前缀加通配符匹配
     * Wrapper(wildcardWrappers)和扩展名匹配Wrapper(extensionWrappers), 之所以分为这几类是因为他们之间存在匹配的优先级。
     *      此外，在ContextVersion中，并非每一个Wrapper对应一个MappedWrapper对象，而是每一个url-pattern对应一个，如果web.xml中的
     * servlet-mapping配置如下 ：
     *      <servlet-mapping>
     *          <servlet-name>example</servlet-name>
     *          <url-pattern>*.do</url-pattern>
     *          <url-pattern>*.action</url-pattern>
     *      </servlet-mapping>
     * 那么，在ContextVersion中将存在两个MappedWrapper封装对象，分别指向同一个Wrapper实例。
     * Mapper按照如下规则将Wrapper添加到ContextVersion对应的MappedWrapper分类中去。。。。
     * 1. 如果url-pattern以/* 结尾，则为wildcardWrappers，此时MappedWrapper的名称为url-pattern去除结尾的"/*"
     * 2. 如果url-pattern 以 *. 结尾，则为extensionWrappers，此时,MappedWrapper的名称为url-pattern去除开头的 "*."
     * 3. 如果url-pattern 以 "/" 结尾，则为defaultWrapper，此时MappedWrapper的名称为空字符串
     * 4. 其他情况均为exactWrappers , 如果url-pattern为空字符串，MappedWrapper的名称为"/" ，否则为url-pattern的值 。
     *
     */
    private final void internalMapWrapper(ContextVersion contextVersion,
                                          CharChunk path,
                                          MappingData mappingData)
        throws Exception {

        int pathOffset = path.getOffset();
        int pathEnd = path.getEnd();
        boolean noServletPath = false;

        int length = contextVersion.path.length();
        if (length == (pathEnd - pathOffset)) {
            noServletPath = true;
        }
        int servletPath = pathOffset + length;
        path.setOffset(servletPath);
        // 接下来看一下MappedWrapper的详细匹配过程
        // 1. 依据url和Context路径来计算 MappedWrapper匹配路径，例如，如果Context路径为"/myapp",url为"/myapp/app1/index.jsp"
        // 那么MappedWrapper的匹配路径为"/app1/index.jsp", 如果url 为"/myapp",那么MappedWrapper的匹配路径为"/"
        // 2. 先精确查找exactWrappers 。
        // Rule 1 -- Exact Match 精准匹配
        Wrapper[] exactWrappers = contextVersion.exactWrappers;
        internalMapExactWrapper(exactWrappers, path, mappingData);

        // Rule 2 -- Prefix Match 前缀匹配 *.jar
        // 如果未找到，然后再按照前缀查找wildcardWrappers ，算法与MappedContext查找类似，逐步降低精度
        boolean checkJspWelcomeFiles = false;
        Wrapper[] wildcardWrappers = contextVersion.wildcardWrappers;
        if (mappingData.wrapper == null) {
            internalMapWildcardWrapper(wildcardWrappers, contextVersion.nesting,
                                       path, mappingData);
            if (mappingData.wrapper != null && mappingData.jspWildCard) {
                char[] buf = path.getBuffer();
                if (buf[pathEnd - 1] == '/') {
                    /*
                     * Path ending in '/' was mapped to JSP servlet based on
                     * wildcard match (e.g., as specified in url-pattern of a
                     * jsp-property-group.
                     * Force the context's welcome files, which are interpreted
                     * as JSP files (since they match the url-pattern), to be
                     * considered. See Bugzilla 27664.
                     */
                    mappingData.wrapper = null;
                    checkJspWelcomeFiles = true;
                } else {
                    // See Bugzilla 27704
                    mappingData.wrapperPath.setChars(buf, path.getStart(),
                                                     path.getLength());
                    mappingData.pathInfo.recycle();
                }
            }
        }

        if(mappingData.wrapper == null && noServletPath &&
                contextVersion.mapperContextRootRedirectEnabled) {
            // The path is empty, redirect to "/"
            path.append('/');
            pathEnd = path.getEnd();
            mappingData.redirectPath.setChars
                (path.getBuffer(), pathOffset, pathEnd - pathOffset);
            path.setEnd(pathEnd - 1);
            return;
        }

        // Rule 3 -- Extension Match /123123/*
        // 如果未找到，然后按照扩展名查找extensionWrappers 。
        Wrapper[] extensionWrappers = contextVersion.extensionWrappers;
        if (mappingData.wrapper == null && !checkJspWelcomeFiles) {
            internalMapExtensionWrapper(extensionWrappers, path, mappingData,
                    true);
        }

        // Rule 4 -- Welcome resources processing for servlets
        // 如果未找到，则尝试匹配欢迎文件列表（web.xml的welcome-file-list配置），主要用于我们输入的请求路径是一个目录而非文件的情况
        // 如：http://127.0.0.1:8080/myapp/app1 ，此时使用匹配路径为"原匹配路径+welcome-file-list中的文件名称" ，欢迎文件匹配分为如下两步
        // 4.1 对于每个欢迎文件生成的新的匹配路径，先查找exactWrappers，再查找wildcardWrappers，如果该文件的物理路径不存在 ，则查找
        // extensionWrappers，如果extensionWrappers未找到，则使用defaultWrapper
        // 4.2 对于每个欢迎文件生成的新的匹配路径，查找extensionWrappers
        // 【注意】在第1步中，只有当存在物理路径时，才会查找extensionWrappers，并在找不到时使用defaultWrapper，而在第2步则不判断物理路径
        // 直到通过extensionWrappers查找，按照这种方式处理，如果我们配置如下 。
        // 4.2.1 url-pattern 配置为"*.do"
        // 4.2.2 welcome-file-list 包括index.do ,index.html
        // 当我们输入的请求路径为http://127.0.0.1:8080/myapp/app1/ ,  且在app1目录下存在index.html文件时，打开的是index.html，而
        // 非index.do ，即便它位于前面（因为它不是个具体文件，而是由Web 应用动态生成 ）
        if (mappingData.wrapper == null) {
            boolean checkWelcomeFiles = checkJspWelcomeFiles;
            if (!checkWelcomeFiles) {
                char[] buf = path.getBuffer();
                checkWelcomeFiles = (buf[pathEnd - 1] == '/');
            }
            if (checkWelcomeFiles) {
                for (int i = 0; (i < contextVersion.welcomeResources.length)
                         && (mappingData.wrapper == null); i++) {
                    path.setOffset(pathOffset);
                    path.setEnd(pathEnd);
                    path.append(contextVersion.welcomeResources[i], 0,
                            contextVersion.welcomeResources[i].length());
                    path.setOffset(servletPath);

                    // Rule 4a -- Welcome resources processing for exact macth
                    internalMapExactWrapper(exactWrappers, path, mappingData);

                    // Rule 4b -- Welcome resources processing for prefix match
                    if (mappingData.wrapper == null) {
                        internalMapWildcardWrapper
                            (wildcardWrappers, contextVersion.nesting,
                             path, mappingData);
                    }

                    // Rule 4c -- Welcome resources processing
                    //            for physical folder
                    if (mappingData.wrapper == null
                        && contextVersion.resources != null) {
                        Object file = null;
                        String pathStr = path.toString();
                        try {
                            file = contextVersion.resources.lookup(pathStr);
                        } catch(NamingException nex) {
                            // Swallow not found, since this is normal
                        }
                        if (file != null && !(file instanceof DirContext) ) {
                            internalMapExtensionWrapper(extensionWrappers, path,
                                                        mappingData, true);
                            if (mappingData.wrapper == null
                                && contextVersion.defaultWrapper != null) {
                                mappingData.wrapper =
                                    contextVersion.defaultWrapper.object;
                                mappingData.requestPath.setChars
                                    (path.getBuffer(), path.getStart(),
                                     path.getLength());
                                mappingData.wrapperPath.setChars
                                    (path.getBuffer(), path.getStart(),
                                     path.getLength());
                                mappingData.requestPath.setString(pathStr);
                                mappingData.wrapperPath.setString(pathStr);
                            }
                        }
                    }
                }

                path.setOffset(servletPath);
                path.setEnd(pathEnd);
            }

        }

        /* welcome file processing - take 2
         * Now that we have looked for welcome files with a physical
         * backing, now look for an extension mapping listed
         * but may not have a physical backing to it. This is for
         * the case of index.jsf, index.do, etc.
         * A watered down version of rule 4
         *  如果未找到，则使用默认的MappedWrapper（通过conf/web.xml,即使Web应用不显式的进行配置，也一定会存在一个默认的Wrapper）
         * 因此，无论请求链接是什么，只要匹配到合适的Context,那么肯定会存在一个匹配的Wrapper
         */
        if (mappingData.wrapper == null) {
            boolean checkWelcomeFiles = checkJspWelcomeFiles;
            if (!checkWelcomeFiles) {
                char[] buf = path.getBuffer();
                checkWelcomeFiles = (buf[pathEnd - 1] == '/');
            }
            if (checkWelcomeFiles) {
                for (int i = 0; (i < contextVersion.welcomeResources.length)
                         && (mappingData.wrapper == null); i++) {
                    path.setOffset(pathOffset);
                    path.setEnd(pathEnd);
                    path.append(contextVersion.welcomeResources[i], 0,
                                contextVersion.welcomeResources[i].length());
                    path.setOffset(servletPath);
                    internalMapExtensionWrapper(extensionWrappers, path,
                                                mappingData, false);
                }

                path.setOffset(servletPath);
                path.setEnd(pathEnd);
            }
        }


        // Rule 7 -- Default servlet
        if (mappingData.wrapper == null && !checkJspWelcomeFiles) {
            if (contextVersion.defaultWrapper != null) {
                mappingData.wrapper = contextVersion.defaultWrapper.object;
                mappingData.requestPath.setChars
                    (path.getBuffer(), path.getStart(), path.getLength());
                mappingData.wrapperPath.setChars
                    (path.getBuffer(), path.getStart(), path.getLength());
            }
            // Redirection to a folder
            char[] buf = path.getBuffer();
            if (contextVersion.resources != null && buf[pathEnd -1 ] != '/') {
                Object file = null;
                String pathStr = path.toString();
                try {
                    if (pathStr.length() == 0) {
                        file = contextVersion.resources.lookup("/");
                    } else {
                        file = contextVersion.resources.lookup(pathStr);
                    }
                } catch(NamingException nex) {
                    // Swallow, since someone else handles the 404
                }
                if (file != null && file instanceof DirContext &&
                        contextVersion.mapperDirectoryRedirectEnabled) {
                    // Note: this mutates the path: do not do any processing
                    // after this (since we set the redirectPath, there
                    // shouldn't be any)
                    path.setOffset(pathOffset);
                    path.append('/');
                    mappingData.redirectPath.setChars
                        (path.getBuffer(), path.getStart(), path.getLength());
                } else {
                    mappingData.requestPath.setString(pathStr);
                    mappingData.wrapperPath.setString(pathStr);
                }
            }
        }

        path.setOffset(pathOffset);
        path.setEnd(pathEnd);
    }


    /**
     * Exact mapping.
     */
    private final void internalMapExactWrapper
        (Wrapper[] wrappers, CharChunk path, MappingData mappingData) {
        Wrapper wrapper = exactFind(wrappers, path);
        if (wrapper != null) {
            mappingData.requestPath.setString(wrapper.name);
            mappingData.wrapper = wrapper.object;
            if (path.equals("/")) {
                // Special handling for Context Root mapped servlet
                mappingData.pathInfo.setString("/");
                mappingData.wrapperPath.setString("");
                // This seems wrong but it is what the spec says...
                mappingData.contextPath.setString("");
            } else {
                mappingData.wrapperPath.setString(wrapper.name);
            }
        }
    }


    /**
     * Wildcard mapping.
     */
    private final void internalMapWildcardWrapper
        (Wrapper[] wrappers, int nesting, CharChunk path,
         MappingData mappingData) {

        int pathEnd = path.getEnd();

        int lastSlash = -1;
        int length = -1;
        int pos = find(wrappers, path);
        if (pos != -1) {
            boolean found = false;
            while (pos >= 0) {
                if (path.startsWith(wrappers[pos].name)) {
                    length = wrappers[pos].name.length();
                    if (path.getLength() == length) {
                        found = true;
                        break;
                    } else if (path.startsWithIgnoreCase("/", length)) {
                        found = true;
                        break;
                    }
                }
                if (lastSlash == -1) {
                    lastSlash = nthSlash(path, nesting + 1);
                } else {
                    lastSlash = lastSlash(path);
                }
                path.setEnd(lastSlash);
                pos = find(wrappers, path);
            }
            path.setEnd(pathEnd);
            if (found) {
                mappingData.wrapperPath.setString(wrappers[pos].name);
                if (path.getLength() > length) {
                    mappingData.pathInfo.setChars
                        (path.getBuffer(),
                         path.getOffset() + length,
                         path.getLength() - length);
                }
                mappingData.requestPath.setChars
                    (path.getBuffer(), path.getOffset(), path.getLength());
                mappingData.wrapper = wrappers[pos].object;
                mappingData.jspWildCard = wrappers[pos].jspWildCard;
            }
        }
    }


    /**
     * Extension mappings.
     *
     * @param wrappers          Set of wrappers to check for matches
     * @param path              Path to map
     * @param mappingData       Mapping data for result
     * @param resourceExpected  Is this mapping expecting to find a resource
     */
    private final void internalMapExtensionWrapper(Wrapper[] wrappers,
            CharChunk path, MappingData mappingData, boolean resourceExpected) {
        char[] buf = path.getBuffer();
        int pathEnd = path.getEnd();
        int servletPath = path.getOffset();
        int slash = -1;
        for (int i = pathEnd - 1; i >= servletPath; i--) {
            if (buf[i] == '/') {
                slash = i;
                break;
            }
        }
        if (slash >= 0) {
            int period = -1;
            for (int i = pathEnd - 1; i > slash; i--) {
                if (buf[i] == '.') {
                    period = i;
                    break;
                }
            }
            if (period >= 0) {
                path.setOffset(period + 1);
                path.setEnd(pathEnd);
                Wrapper wrapper = exactFind(wrappers, path);
                if (wrapper != null
                        && (resourceExpected || !wrapper.resourceOnly)) {
                    mappingData.wrapperPath.setChars(buf, servletPath, pathEnd
                            - servletPath);
                    mappingData.requestPath.setChars(buf, servletPath, pathEnd
                            - servletPath);
                    mappingData.wrapper = wrapper.object;
                }
                path.setOffset(servletPath);
                path.setEnd(pathEnd);
            }
        }
    }


    /**
     * Find a map element given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final int find(MapElement[] map, CharChunk name) {
        return find(map, name, name.getStart(), name.getEnd());
    }


    /**
     * Find a map element given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final int find(MapElement[] map, CharChunk name,
                                  int start, int end) {

        int a = 0;     // 开始位置
        int b = map.length - 1; // 结束位置

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        // 因为map是一个排好序了的数组，所以先比较name是不是小于map[0].name，如果小于那么肯定在map中不存在name了
        if (compare(name, start, end, map[0].name) < 0 ) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2; // 折半
            int result = compare(name, start, end, map[i].name);
            if (result == 1) { // 如果那么大于map[i].name，则表示name应该在右侧，将a变大为i
                a = i;
            } else if (result == 0) { // 相等
                return i;
            } else {
                b = i; // 将b缩小为i
            }
            if ((b - a) == 1) { // 表示缩小到两个元素了，那么取b进行比较
                int result2 = compare(name, start, end, map[b].name);
                if (result2 < 0) { // name小于b,则返回a
                    return a;
                } else {
                    return b;  // 否则返回b
                }
            }
        }

    }

    /**
     * Find a map element given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final int findIgnoreCase(MapElement[] map, CharChunk name) {
        return findIgnoreCase(map, name, name.getStart(), name.getEnd());
    }


    /**
     * Find a map element given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     */
    private static final int findIgnoreCase(MapElement[] map, CharChunk name,
                                  int start, int end) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }
        if (compareIgnoreCase(name, start, end, map[0].name) < 0 ) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = compareIgnoreCase(name, start, end, map[i].name);
            if (result == 1) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = compareIgnoreCase(name, start, end, map[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    /**
     * Find a map element given its name in a sorted array of map elements.
     * This will return the index for the closest inferior or equal item in the
     * given array.
     * @see #exactFind(MapElement[], String)
     */
    private static final int find(MapElement[] map, String name) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        if (name.compareTo(map[0].name) < 0) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2;
            int result = name.compareTo(map[i].name);
            if (result > 0) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = name.compareTo(map[b].name);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


    /**
     * Find a map element given its name in a sorted array of map elements. This
     * will return the element that you were searching for. Otherwise it will
     * return <code>null</code>.
     * @see #find(MapElement[], String)
     */
    private static final <E extends MapElement> E exactFind(E[] map,
            String name) {
        int pos = find(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equals(result.name)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Find a map element given its name in a sorted array of map elements. This
     * will return the element that you were searching for. Otherwise it will
     * return <code>null</code>.
     */
    private static final <E extends MapElement> E exactFind(E[] map,
            CharChunk name) {
        int pos = find(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equals(result.name)) {
                return result;
            }
        }
        return null;
    }

    /**
     * Find a map element given its name in a sorted array of map elements. This
     * will return the element that you were searching for. Otherwise it will
     * return <code>null</code>.
     * @see #findIgnoreCase(MapElement[], CharChunk)
     */
    private static final <E extends MapElement> E exactFindIgnoreCase(E[] map,
            CharChunk name) {
        int pos = findIgnoreCase(map, name);
        if (pos >= 0) {
            E result = map[pos];
            if (name.equalsIgnoreCase(result.name)) {
                return result;
            }
        }
        return null;
    }


    /**
     * Compare given char chunk with String.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    private static final int compare(CharChunk name, int start, int end,
                                     String compareTo) {
        int result = 0;
        char[] c = name.getBuffer();
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (c[i + start] > compareTo.charAt(i)) {
                result = 1;
            } else if (c[i + start] < compareTo.charAt(i)) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * Compare given char chunk with String ignoring case.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    private static final int compareIgnoreCase(CharChunk name, int start, int end,
                                     String compareTo) {
        int result = 0;
        char[] c = name.getBuffer();
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (Ascii.toLower(c[i + start]) > Ascii.toLower(compareTo.charAt(i))) {
                result = 1;
            } else if (Ascii.toLower(c[i + start]) < Ascii.toLower(compareTo.charAt(i))) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


    /**
     * Find the position of the last slash in the given char chunk.
     */
    private static final int lastSlash(CharChunk name) {

        char[] c = name.getBuffer();
        int end = name.getEnd();
        int start = name.getStart();
        int pos = end;

        while (pos > start) {
            if (c[--pos] == '/') {
                break;
            }
        }

        return (pos);

    }


    /**
     * Find the position of the nth slash, in the given char chunk.
     */
    private static final int nthSlash(CharChunk name, int n) {

        char[] c = name.getBuffer();
        int end = name.getEnd();
        int start = name.getStart();
        int pos = start;
        int count = 0;

        while (pos < end) {
            if ((c[pos++] == '/') && ((++count) == n)) {
                pos--;
                break;
            }
        }

        return (pos);

    }


    /**
     * Return the slash count in a given string.
     */
    private static final int slashCount(String name) {
        int pos = -1;
        int count = 0;
        while ((pos = name.indexOf('/', pos + 1)) != -1) {
            count++;
        }
        return count;
    }


    /**
     * Insert into the right place in a sorted MapElement array, and prevent
     * duplicates.
     */
    private static final boolean insertMap
        (MapElement[] oldMap, MapElement[] newMap, MapElement newElement) {
        int pos = find(oldMap, newElement.name);    // 先在oldMap中找是否存在newElement
        if ((pos != -1) && (newElement.name.equals(oldMap[pos].name))) {
            // 如果存在则不插入了
            return false;
        }
        // 从oldMap的第0个位置开始，复制pos + 1个元素到newMap中，newMap中从第0个位置开始
        System.arraycopy(oldMap, 0, newMap, 0, pos + 1);
        newMap[pos + 1] = newElement; // 修改newMap中的pos+1位置的元素为新元素
        // 从oldMap的第pos+1个位置开始，复制oldMap剩下的元素到newMap中
        System.arraycopy
            (oldMap, pos + 1, newMap, pos + 2, oldMap.length - pos - 1);
        return true;
    }


    /**
     * Insert into the right place in a sorted MapElement array.
     */
    private static final boolean removeMap
        (MapElement[] oldMap, MapElement[] newMap, String name) {
        int pos = find(oldMap, name);
        if ((pos != -1) && (name.equals(oldMap[pos].name))) {
            System.arraycopy(oldMap, 0, newMap, 0, pos);
            System.arraycopy(oldMap, pos + 1, newMap, pos,
                             oldMap.length - pos - 1);
            return true;
        }
        return false;
    }


    // ------------------------------------------------- MapElement Inner Class


    protected abstract static class MapElement {

        public final String name;
        public final Object object;

        public MapElement(String name, Object object) {
            this.name = name;
            this.object = object;
        }
    }


    // ------------------------------------------------------- Host Inner Class


    protected static final class Host extends MapElement {

        public volatile ContextList contextList;

        /**
         * Link to the "real" Host, shared by all aliases.
         */
        private final Host realHost;

        /**
         * Links to all registered aliases, for easy enumeration. This field
         * is available only in the "real" Host. In an alias this field
         * is <code>null</code>.
         */
        private final List<Host> aliases;

        /**
         * Creates an object for primary Host
         */
        public Host(String name, Object host) {
            super(name, host);
            this.realHost = this;
            this.contextList = new ContextList();
            this.aliases = new CopyOnWriteArrayList<Host>();
        }

        /**
         * Creates an object for an Alias
         */
        public Host(String alias, Host realHost) {
            super(alias, realHost.object);
            this.realHost = realHost;
            this.contextList = realHost.contextList;
            this.aliases = null;
        }

        public boolean isAlias() {
            return realHost != this;
        }

        public Host getRealHost() {
            return realHost;
        }

        public String getRealHostName() {
            return realHost.name;
        }

        public Collection<Host> getAliases() {
            return aliases;
        }

        public void addAlias(Host alias) {
            aliases.add(alias);
        }

        public void addAliases(Collection<? extends Host> c) {
            aliases.addAll(c);
        }

        public void removeAlias(Host alias) {
            aliases.remove(alias);
        }
    }


    // ------------------------------------------------ ContextList Inner Class


    protected static final class ContextList {

        public final Context[] contexts;
        public final int nesting;

        public ContextList() {
            this(new Context[0], 0);
        }

        private ContextList(Context[] contexts, int nesting) {
            this.contexts = contexts;
            this.nesting = nesting;
        }

        public ContextList addContext(Context mappedContext, int slashCount) {
            Context[] newContexts = new Context[contexts.length + 1];
            // 根据name来进行比较，如果mappedContext已经在contexts中存在，那么则不会进行插入，返回空
            // 如果不存在，则将mappedContext插入到newContexts中，并返回一个新的ContextList对象
            if (insertMap(contexts, newContexts, mappedContext)) {
                return new ContextList(newContexts, Math.max(nesting,
                        slashCount));
            }
            return null;
        }

        public ContextList removeContext(String path) {
            Context[] newContexts = new Context[contexts.length - 1];
            if (removeMap(contexts, newContexts, path)) {
                int newNesting = 0;
                for (Context context : newContexts) {
                    newNesting = Math.max(newNesting, slashCount(context.name));
                }
                return new ContextList(newContexts, newNesting);
            }
            return null;
        }
    }


    // ---------------------------------------------------- Context Inner Class


    protected static final class Context extends MapElement {
        public volatile ContextVersion[] versions;

        public Context(String name, ContextVersion firstVersion) {
            super(name, null);
            versions = new ContextVersion[] { firstVersion };
        }
    }


    protected static final class ContextVersion extends MapElement {
        public String path = null;
        public int slashCount;
        public String[] welcomeResources = new String[0];
        public javax.naming.Context resources = null;
        public Wrapper defaultWrapper = null;               // urlPattern等于("/")，如果一个请求没有匹配其他映射关系，那么就会走这个
        public Wrapper[] exactWrappers = new Wrapper[0];    // 精确匹配，urlPattern不符合其他情况
        public Wrapper[] wildcardWrappers = new Wrapper[0];  // urlPattern是以("/*")结尾的
        public Wrapper[] extensionWrappers = new Wrapper[0]; // urlPattern是以("*.")开始的
        public int nesting = 0;
        public boolean mapperContextRootRedirectEnabled = false;
        public boolean mapperDirectoryRedirectEnabled = false;
        private volatile boolean paused;

        public ContextVersion() {
            super(null, null);
        }

        public ContextVersion(String version, Object context) {
            super(version, context);
        }

        public boolean isPaused() {
            return paused;
        }

        public void markPaused() {
            paused = true;
        }
    }


    // ---------------------------------------------------- Wrapper Inner Class


    protected static class Wrapper extends MapElement {

        public final boolean jspWildCard;
        public final boolean resourceOnly;

        public Wrapper(String name, /* Wrapper */Object wrapper,
                boolean jspWildCard, boolean resourceOnly) {
            super(name, wrapper);
            this.jspWildCard = jspWildCard;
            this.resourceOnly = resourceOnly;
        }
    }
}
