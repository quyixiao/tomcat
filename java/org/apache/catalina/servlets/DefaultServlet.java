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
package org.apache.catalina.servlets;

import java.io.BufferedInputStream;
import java.io.ByteArrayInputStream;
import java.io.ByteArrayOutputStream;
import java.io.File;
import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.IOException;
import java.io.InputStream;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.io.RandomAccessFile;
import java.io.Reader;
import java.io.StringReader;
import java.io.StringWriter;
import java.security.AccessController;
import java.util.ArrayList;
import java.util.Iterator;
import java.util.Locale;
import java.util.StringTokenizer;

import javax.naming.InitialContext;
import javax.naming.NameClassPair;
import javax.naming.NamingEnumeration;
import javax.naming.NamingException;
import javax.naming.directory.DirContext;
import javax.servlet.DispatcherType;
import javax.servlet.RequestDispatcher;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.ServletOutputStream;
import javax.servlet.ServletResponse;
import javax.servlet.ServletResponseWrapper;
import javax.servlet.UnavailableException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.xml.parsers.DocumentBuilder;
import javax.xml.parsers.DocumentBuilderFactory;
import javax.xml.parsers.ParserConfigurationException;
import javax.xml.transform.Source;
import javax.xml.transform.Transformer;
import javax.xml.transform.TransformerException;
import javax.xml.transform.TransformerFactory;
import javax.xml.transform.dom.DOMSource;
import javax.xml.transform.stream.StreamResult;
import javax.xml.transform.stream.StreamSource;

import org.apache.catalina.Globals;
import org.apache.catalina.connector.RequestFacade;
import org.apache.catalina.connector.ResponseFacade;
import org.apache.catalina.util.IOTools;
import org.apache.catalina.util.RequestUtil;
import org.apache.catalina.util.ServerInfo;
import org.apache.catalina.util.URLEncoder;
import org.apache.naming.resources.CacheEntry;
import org.apache.naming.resources.ProxyDirContext;
import org.apache.naming.resources.Resource;
import org.apache.naming.resources.ResourceAttributes;
import org.apache.tomcat.util.res.StringManager;
import org.apache.tomcat.util.security.PrivilegedGetTccl;
import org.apache.tomcat.util.security.PrivilegedSetTccl;
import org.w3c.dom.Document;
import org.xml.sax.InputSource;
import org.xml.sax.SAXException;
import org.xml.sax.ext.EntityResolver2;


/**
 * <p>The default resource-serving servlet for most web applications,
 * used to serve static resources such as HTML pages and images.
 * </p>
 * <p>
 * This servlet is intended to be mapped to <em>/</em> e.g.:
 * </p>
 * <pre>
 *   &lt;servlet-mapping&gt;
 *       &lt;servlet-name&gt;default&lt;/servlet-name&gt;
 *       &lt;url-pattern&gt;/&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 * <p>It can be mapped to sub-paths, however in all cases resources are served
 * from the web application resource root using the full path from the root
 * of the web application context.
 * <br>e.g. given a web application structure:
 *</p>
 * <pre>
 * /context
 *   /images
 *     tomcat2.jpg
 *   /static
 *     /images
 *       tomcat.jpg
 * </pre>
 * <p>
 * ... and a servlet mapping that maps only <code>/static/*</code> to the default servlet:
 * </p>
 * <pre>
 *   &lt;servlet-mapping&gt;
 *       &lt;servlet-name&gt;default&lt;/servlet-name&gt;
 *       &lt;url-pattern&gt;/static/*&lt;/url-pattern&gt;
 *   &lt;/servlet-mapping&gt;
 * </pre>
 * <p>
 * Then a request to <code>/context/static/images/tomcat.jpg</code> will succeed
 * while a request to <code>/context/images/tomcat2.jpg</code> will fail.
 * </p>
 * @author Craig R. McClanahan
 * @author Remy Maucherat
 *
 *
 * 同样是Tomcat 内部使用Servlet ，DefaultSevlet是Tomcat专门用于处理静态资源的Servlet ,它同样被部署到Tomcat安装目录下的Config 的web.xml
 * 文件中，DefaultServlet 的配置如下 。
 * <servlet>
 *     <servlet-name>default</servlet-name>
 *     <servlet-class>org.apache.catalina.servlets.DefaultServlet</servlet-class>
 *     <init-param>
 *         <param-name>debug</param-name>
 *         <param-value>0</param-value>
 *     </init-param>
 *     <init-param>
 *         <param-name>listings</param-name>
 *         <param-value>false</param-value>
 *     </init-param>
 *     <load-on-startup>1</load-on-startup>
 * </servlet>
 * <servlet-mapping>
 *     <servlet-name>default</servlet-name>
 *     <url-pattern>/</url-pattern>
 * </servlet-mapping>
 *
 * 可以看到，所有的URL 请求都会被它匹配，但由于Mapper组件匹配Servlet时将DefaultServlet 放到最后才匹配，所以它并不会把所有的请求都拦截下来  。
 * 只有那些经过精确匹配，前缀匹配，扩展名匹配等还没有匹配上的，才会留给DefaultServlet ，DefaultServlet 通过JNDI根据URI 在Tomcat 内部查找资源 。
 * 然后该资源响应客户端。
 *
 *      由前面的配置可知，Default的url-pattern为"/"，因此，它将作为默认的Servlet，当客户端请求不能匹配到他的Servlet时，将由该Servlet处理
 *      DefaultServlet主要用于处理静态资源，如HTML,图片，CSS,JS文件等，而且为了提升服务器的性能，Tomcat对访问文件进行了缓存，按照默认的配置。
 * ，客户端请求路径与资源的物理路径是一致的， 即当我们输入的链接为http://127.0.0.1:8080/myapp/static/sample.png时， 加载的图片物理路径为
 * $CATALINA_BASE/webapps/myapp/static/sample.png
 *      当然，如果我们希望DefaultServlet只加载static目录下的资源，只需要将url-pattern改为"/static/*" 即可，此时DefaultServlet将不再
 * 是默认的Servlet , 但是，这不会改变我们的请求路径，也就是说资源指向的仍旧是物理路径，这是因为DefaultServlet根据完整的请求地址获取文件而非
 * 基于Servlet相对路径 。
 *    如果我们希望Web应用覆盖Tomcat的DefaultServlet配置，只需要将"/"添加到自定义的Servlet的url-pattern中即可（此时，自定义的Servlet将
 *成为默认的Servlet）。
 * 【注意】覆盖DefaultServlet配置时要慎重，因为这可能导致无法加载静态文件，除非在覆盖的情况下，自定义Servlet可以兼容DefaultServlet
 * 的功能以及请求地址的处理（大多数Servlet基于相对路径来分发请求， 而非完整路径，此时如果直接覆盖，将导致客户端请求无效）
 * 当然，我们应该尽量避免使用不同的Servlet之间产生覆盖，因为覆盖结果会与具体的加载顺序(web.xml,web-fragment.xml以及注解顺序)相关
 * 当系统复杂度上升时，可维护性势必会降低，建议通过划分Servlet请求目录（如：/static/,/html/,/js/等）指定请求扩展名（如：*.do ,*.action）
 * 来合理的请求路径命名。
 *      DefaultServlet除了支持访问静态资源，还支持查找目录列表，只需要名为"listings"的<init-param>设置为true，此时如果我们输入
 *  http://127.0.0.1:8080/myapp/static/，且该目录下没有任何欢迎文件（welcome-file-list配置），Tomcat将返回对物理目录下的文件目录列表 。
 *  【注意】需要确保welcome-file-list不包含虚拟的文件名，如index.do ,否则此时仍然会由index.do匹配的Servlet处理。
 *      默认情况下，Tomcat 以HTML形式输出文件目录列表 （包括文件名，大小，最后修改时间），此外，可以通过参数localXsltFile ，contextXsltFile
 * 或globalXsltFile指定一个XSL 或XSLT 文件，此时，Tomcat将XML形式输出文件目录，并使用指定的XSL或XSLT文件将其转换为响应输出，通过这种
 * 方式，我们可以根据需要定制文件目录输出界面 。
 * Tomcat输出的XML内容格式如下：
 * <?xml version='1.0'?>
 *     <listing contextPath='Web应用根目录' director='当前查看目录' hasParent='true'>
 *         <entries>
 *             <entry type='file' urlPath='文件路径' size='文件大小' date='最后修改时间'></entry>
 *             <entry type='dir' urlPath='子目录路径' date='最后修改时间'>目录名</entry>
 *         </entries>
 *     </listing>
 * </?xml>
 *
 *      DefaultServlet支持初始化参数如表3-5所示，我们可以根据实际需要进行配置。
 * 参数                  描述
 * debug                Debug日志级别，主要用于开发环境，当前版本只有0，1-10，>= 11 这3个级别，而且同一级别内的值无论配置为何都是等价的。
 * listings             如果配置为true,当请求目录下没有欢迎文件时，将显示文件目录列表，默认为false,如果目录下包含过多的子目录和文件，将
 *                      操作非常的耗费性能，在请求访问最大的情况下占用大量的系统资源 。
 * readmeFile           ReadMe文件名，当DefaultServlet允许显示文件目录，且当前文件目录下存在readmeFile指定的文件时，该文件将会被插入
 *                      到最终的请求响应，以显示当前目录的ReadMe信息
 * globalXsltFile       Tomcat支持通过XSL定制文件目录显示列表，该参数用于指定XSL文件（基于$CATALINA_BASE/conf/或$CATALINA_HOME/conf/的相对路径 ）
 *                      因此XSL文件可以在所有Web应用中共享 。
 * localXsltFile        使用与globalXsltFile相同，但是文件路径相对当前请求目录，因此该参数指定的文件只适用于当前请求目录，Tomcat优先
 *                      使用该参数，其次为contextXsltFile，globalXsltFile的优先级最低。
 * input                读资源文件时，输出缓冲大小，单位为字节，默认为2048
 * output               写资源文件时，输出缓冲大小，单位为字节，默认为2048
 * readonly             如果配置为true ,Tomcat将会拒绝由DefaultServlet处理PUT和DELETE请求。
 * fileEncoding         读取资源文件时使用的文件编码 。
 * sendfileSize         如果当前连接器支持sendFile, 该参数用于配置使用sendFile的最小文件大小，单位为KB,如果为负数，表示禁用sendfile
 * useAcceptRanges      如果为true, 将添加Accept-Ranges 响应头。
 * showServerInfo       如果为true , 将会在文件目录列表中输出服务器信息， 当前版本只适用于默认的格式输出的情况 。
 */
public class DefaultServlet extends HttpServlet {

    private static final long serialVersionUID = 1L;

    /**
     * The string manager for this package.
     */
    protected static final StringManager sm = StringManager.getManager(Constants.Package);

    private static final DocumentBuilderFactory factory;

    private static final SecureEntityResolver secureEntityResolver;

    /**
     * Full range marker.
     */
    protected static final ArrayList<Range> FULL = new ArrayList<Range>();

    /**
     * MIME multipart separation string
     */
    protected static final String mimeSeparation = "CATALINA_MIME_BOUNDARY";

    /**
     * JNDI resources name.
     */
    protected static final String RESOURCES_JNDI_NAME = "java:/comp/Resources";


    /**
     * Size of file transfer buffer in bytes.
     */
    protected static final int BUFFER_SIZE = 4096;


    /**
     * Array containing the safe characters set.
     */
    protected static final URLEncoder urlEncoder;


    // ----------------------------------------------------- Static Initializer

    static {
        urlEncoder = new URLEncoder();
        urlEncoder.addSafeCharacter('-');
        urlEncoder.addSafeCharacter('_');
        urlEncoder.addSafeCharacter('.');
        urlEncoder.addSafeCharacter('*');
        urlEncoder.addSafeCharacter('/');

        if (Globals.IS_SECURITY_ENABLED) {
            factory = DocumentBuilderFactory.newInstance();
            factory.setNamespaceAware(true);
            factory.setValidating(false);
            secureEntityResolver = new SecureEntityResolver();
        } else {
            factory = null;
            secureEntityResolver = null;
        }
    }


    // ----------------------------------------------------- Instance Variables

    /**
     * The debugging detail level for this servlet.
     */
    protected int debug = 0;

    /**
     * The input buffer size to use when serving resources.
     */
    protected int input = 2048;

    /**
     * Should we generate directory listings?
     */
    protected boolean listings = false;

    /**
     * Read only flag. By default, it's set to true.
     */
    protected boolean readOnly = true;


    /**
     * The output buffer size to use when serving resources.
     */
    protected int output = 2048;

    /**
     * Allow customized directory listing per directory.
     */
    protected String localXsltFile = null;

    /**
     * Allow customized directory listing per context.
     */
    protected String contextXsltFile = null;

    /**
     * Allow customized directory listing per instance.
     */
    protected String globalXsltFile = null;

    /**
     * Allow a readme file to be included.
     */
    protected String readmeFile = null;

    /**
     * Proxy directory context.
     */
    protected transient ProxyDirContext resources = null;


    /**
     * File encoding to be used when reading static files. If none is specified
     * the platform default is used.
     */
    protected String fileEncoding = null;


    /**
     * Minimum size for sendfile usage in bytes.
     */
    protected int sendfileSize = 48 * 1024;

    /**
     * Should the Accept-Ranges: bytes header be send with static resources?
     */
    protected boolean useAcceptRanges = true;

    /**
     * Flag to determine if server information is presented.
     */
    protected boolean showServerInfo = true;


    // --------------------------------------------------------- Public Methods

    /**
     * Finalize this servlet.
     */
    @Override
    public void destroy() {
        // NOOP
    }


    /**
     * Initialize this servlet.
     */
    @Override
    public void init() throws ServletException {

        if (getServletConfig().getInitParameter("debug") != null)
            debug = Integer.parseInt(getServletConfig().getInitParameter("debug"));

        if (getServletConfig().getInitParameter("input") != null)
            input = Integer.parseInt(getServletConfig().getInitParameter("input"));

        if (getServletConfig().getInitParameter("output") != null)
            output = Integer.parseInt(getServletConfig().getInitParameter("output"));

        listings = Boolean.parseBoolean(getServletConfig().getInitParameter("listings"));

        if (getServletConfig().getInitParameter("readonly") != null)
            readOnly = Boolean.parseBoolean(getServletConfig().getInitParameter("readonly"));

        if (getServletConfig().getInitParameter("sendfileSize") != null)
            sendfileSize =
                Integer.parseInt(getServletConfig().getInitParameter("sendfileSize")) * 1024;

        fileEncoding = getServletConfig().getInitParameter("fileEncoding");

        globalXsltFile = getServletConfig().getInitParameter("globalXsltFile");
        contextXsltFile = getServletConfig().getInitParameter("contextXsltFile");
        localXsltFile = getServletConfig().getInitParameter("localXsltFile");
        readmeFile = getServletConfig().getInitParameter("readmeFile");

        if (getServletConfig().getInitParameter("useAcceptRanges") != null)
            useAcceptRanges = Boolean.parseBoolean(getServletConfig().getInitParameter("useAcceptRanges"));

        // Sanity check on the specified buffer sizes
        if (input < 256)
            input = 256;
        if (output < 256)
            output = 256;

        if (debug > 0) {
            log("DefaultServlet.init:  input buffer size=" + input +
                ", output buffer size=" + output);
        }

        // Load the proxy dir context.
        resources = (ProxyDirContext) getServletContext()
            .getAttribute(Globals.RESOURCES_ATTR);
        if (resources == null) {
            try {
                resources =
                    (ProxyDirContext) new InitialContext()
                    .lookup(RESOURCES_JNDI_NAME);
            } catch (NamingException e) {
                // Failed
                throw new ServletException("No resources", e);
            }
        }

        if (resources == null) {
            throw new UnavailableException(sm.getString("defaultServlet.noResources"));
        }

        if (getServletConfig().getInitParameter("showServerInfo") != null) {
            showServerInfo = Boolean.parseBoolean(getServletConfig().getInitParameter("showServerInfo"));
        }
    }


    // ------------------------------------------------------ Protected Methods


    /**
     * Return the relative path associated with this servlet.
     *
     * @param request The servlet request we are processing
     * @return the relative path
     */
    protected String getRelativePath(HttpServletRequest request) {
        return getRelativePath(request, false);
    }

    protected String getRelativePath(HttpServletRequest request, boolean allowEmptyPath) {
        // IMPORTANT: DefaultServlet can be mapped to '/' or '/path/*' but always
        // serves resources from the web app root with context rooted paths.
        // i.e. it cannot be used to mount the web app root under a sub-path
        // This method must construct a complete context rooted path, although
        // subclasses can change this behaviour.

        String servletPath;
        String pathInfo;

        if (request.getAttribute(RequestDispatcher.INCLUDE_REQUEST_URI) != null) {
            // For includes, get the info from the attributes
            pathInfo = (String) request.getAttribute(RequestDispatcher.INCLUDE_PATH_INFO);
            servletPath = (String) request.getAttribute(RequestDispatcher.INCLUDE_SERVLET_PATH);
        } else {
            pathInfo = request.getPathInfo();
            servletPath = request.getServletPath();
        }

        StringBuilder result = new StringBuilder();
        if (servletPath.length() > 0) {
            result.append(servletPath);
        }
        if (pathInfo != null) {
            result.append(pathInfo);
        }
        if (result.length() == 0 && !allowEmptyPath) {
            result.append('/');
        }

        return result.toString();
    }


    /**
     * Determines the appropriate path to prepend resources with
     * when generating directory listings. Depending on the behaviour of
     * {@link #getRelativePath(HttpServletRequest)} this will change.
     * @param request the request to determine the path for
     * @return the prefix to apply to all resources in the listing.
     */
    protected String getPathPrefix(final HttpServletRequest request) {
        return request.getContextPath();
    }


    @Override
    protected void service(HttpServletRequest req, HttpServletResponse resp)
            throws ServletException, IOException {

        if (req.getDispatcherType() == DispatcherType.ERROR) {
            doGet(req, resp);
        } else {
            super.service(req, resp);
        }
    }


    /**
     * Process a GET request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doGet(HttpServletRequest request,
                         HttpServletResponse response)
        throws IOException, ServletException {

        // Serve the requested resource, including the data content
        serveResource(request, response, true);

    }


    /**
     * Process a HEAD request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doHead(HttpServletRequest request, HttpServletResponse response)
            throws IOException, ServletException {
        // Serve the requested resource, without the data content unless we are
        // being included since in that case the content needs to be provided so
        // the correct content length is reported for the including resource
        boolean serveContent = DispatcherType.INCLUDE.equals(request.getDispatcherType());
        serveResource(request, response, serveContent);
    }


    /**
     * Override default implementation to ensure that TRACE is correctly
     * handled.
     *
     * @param req   the {@link HttpServletRequest} object that
     *                  contains the request the client made of
     *                  the servlet
     *
     * @param resp  the {@link HttpServletResponse} object that
     *                  contains the response the servlet returns
     *                  to the client
     *
     * @exception IOException   if an input or output error occurs
     *                              while the servlet is handling the
     *                              OPTIONS request
     *
     * @exception ServletException  if the request for the
     *                                  OPTIONS cannot be handled
     */
    @Override
    protected void doOptions(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        resp.setHeader("Allow", determineMethodsAllowed(req));
    }


    protected String determineMethodsAllowed(HttpServletRequest req) {
        StringBuilder allow = new StringBuilder();

        // Start with methods that are always allowed
        allow.append("OPTIONS, GET, HEAD, POST");

        // PUT and DELETE depend on readonly
        if (!readOnly) {
            allow.append(", PUT, DELETE");
        }

        // Trace - assume disabled unless we can prove otherwise
        if (req instanceof RequestFacade &&
                ((RequestFacade) req).getAllowTrace()) {
            allow.append(", TRACE");
        }

        return allow.toString();
    }


    protected void sendNotAllowed(HttpServletRequest req, HttpServletResponse resp)
            throws IOException {
        resp.addHeader("Allow", determineMethodsAllowed(req));
        resp.sendError(WebdavStatus.SC_METHOD_NOT_ALLOWED);
    }


    /**
     * Process a POST request for the specified resource.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doPost(HttpServletRequest request,
                          HttpServletResponse response)
        throws IOException, ServletException {
        doGet(request, response);
    }


    /**
     * Process a PUT request for the specified resource.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doPut(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            sendNotAllowed(req, resp);
            return;
        }

        String path = getRelativePath(req);

        boolean exists = true;
        try {
            resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        boolean result = true;

        Range range = parseContentRange(req, resp);

        InputStream resourceInputStream = null;

        try {
            // Append data specified in ranges to existing content for this
            // resource - create a temp. file on the local filesystem to
            // perform this operation
            // Assume just one range is specified for now
            if (range != null) {
                File contentFile = executePartialPut(req, range, path);
                resourceInputStream = new FileInputStream(contentFile);
            } else {
                resourceInputStream = req.getInputStream();
            }

            Resource newResource = new Resource(resourceInputStream);
            // FIXME: Add attributes
            if (exists) {
                resources.rebind(path, newResource);
            } else {
                resources.bind(path, newResource);
            }
        } catch(NamingException e) {
            result = false;
        }

        if (result) {
            if (exists) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.setStatus(HttpServletResponse.SC_CREATED);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_CONFLICT);
        }
    }


    /**
     * Handle a partial PUT.  New content specified in request is appended to
     * existing content in oldRevisionContent (if present). This code does
     * not support simultaneous partial updates to the same resource.
     * @param req The Servlet request
     * @param range The range that will be written
     * @param path The path
     * @return the associated file object
     * @throws IOException an IO error occurred
     */
    protected File executePartialPut(HttpServletRequest req, Range range,
                                     String path)
        throws IOException {

        // Append data specified in ranges to existing content for this
        // resource - create a temp. file on the local filesystem to
        // perform this operation
        File tempDir = (File) getServletContext().getAttribute
            (ServletContext.TEMPDIR);
        // Convert all '/' characters to '.' in resourcePath
        String convertedResourcePath = path.replace('/', '.');
        File contentFile = new File(tempDir, convertedResourcePath);
        if (contentFile.createNewFile()) {
            // Clean up contentFile when Tomcat is terminated
            contentFile.deleteOnExit();
        }

        RandomAccessFile randAccessContentFile = null;
        try {
            randAccessContentFile =
                    new RandomAccessFile(contentFile, "rw");
            Resource oldResource = null;
            try {
                Object obj = resources.lookup(path);
                if (obj instanceof Resource)
                    oldResource = (Resource) obj;
            } catch (NamingException e) {
                // Ignore
            }

            // Copy data in oldRevisionContent to contentFile
            if (oldResource != null) {
                BufferedInputStream bufOldRevStream = null;
                try {
                    bufOldRevStream =
                            new BufferedInputStream(oldResource.streamContent(),
                                                    BUFFER_SIZE);
                    int numBytesRead;
                    byte[] copyBuffer = new byte[BUFFER_SIZE];
                    while ((numBytesRead = bufOldRevStream.read(copyBuffer)) != -1) {
                        randAccessContentFile.write(copyBuffer, 0, numBytesRead);
                    }
                } finally {
                    if (bufOldRevStream != null) {
                        try {
                            bufOldRevStream.close();
                        } catch (IOException e) {
                        }
                    }
                }
            }

            randAccessContentFile.setLength(range.length);

            // Append data in request input stream to contentFile
            randAccessContentFile.seek(range.start);
            int numBytesRead;
            byte[] transferBuffer = new byte[BUFFER_SIZE];
            BufferedInputStream requestBufInStream = null;
            try {
                requestBufInStream = new BufferedInputStream(req.getInputStream(), BUFFER_SIZE);
                while ((numBytesRead = requestBufInStream.read(transferBuffer)) != -1) {
                    randAccessContentFile.write(transferBuffer, 0, numBytesRead);
                }
            } finally {
                if (requestBufInStream != null) {
                    try {
                        requestBufInStream.close();
                    } catch (IOException e) {
                    }
                }
            }
        } finally {
            if (randAccessContentFile != null) {
                try {
                    randAccessContentFile.close();
                } catch (IOException e) {
                }
            }
        }

        return contentFile;
    }


    /**
     * Process a DELETE request for the specified resource.
     *
     * @param req The servlet request we are processing
     * @param resp The servlet response we are creating
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    @Override
    protected void doDelete(HttpServletRequest req, HttpServletResponse resp)
        throws ServletException, IOException {

        if (readOnly) {
            sendNotAllowed(req, resp);
            return;
        }

        String path = getRelativePath(req);

        boolean exists = true;
        try {
            resources.lookup(path);
        } catch (NamingException e) {
            exists = false;
        }

        if (exists) {
            boolean result = true;
            try {
                resources.unbind(path);
            } catch (NamingException e) {
                result = false;
            }
            if (result) {
                resp.setStatus(HttpServletResponse.SC_NO_CONTENT);
            } else {
                resp.sendError(HttpServletResponse.SC_METHOD_NOT_ALLOWED);
            }
        } else {
            resp.sendError(HttpServletResponse.SC_NOT_FOUND);
        }

    }


    /**
     * Check if the conditions specified in the optional If headers are
     * satisfied.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resourceAttributes The resource information
     * @return <code>true</code> if the resource meets all the specified
     *  conditions, and <code>false</code> if any of the conditions is not
     *  satisfied, in which case request processing is stopped
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfHeaders(HttpServletRequest request,
                                     HttpServletResponse response,
                                     ResourceAttributes resourceAttributes)
        throws IOException {

        return checkIfMatch(request, response, resourceAttributes)
            && checkIfModifiedSince(request, response, resourceAttributes)
            && checkIfNoneMatch(request, response, resourceAttributes)
            && checkIfUnmodifiedSince(request, response, resourceAttributes);

    }


    /**
     * URL rewriter.
     *
     * @param path Path which has to be rewritten
     * @return the rewritten path
     */
    protected String rewriteUrl(String path) {
        return urlEncoder.encode(path, "UTF-8");
    }


    /**
     * Display the size of a file.
     * @deprecated Will be removed in Tomcat 8.0.x. Replaced by
     *             {@link #renderSize(long)}
     */
    @Deprecated
    protected void displaySize(StringBuilder buf, int filesize) {

        int leftside = filesize / 1024;
        int rightside = (filesize % 1024) / 103;  // makes 1 digit
        // To avoid 0.0 for non-zero file, we bump to 0.1
        if (leftside == 0 && rightside == 0 && filesize != 0)
            rightside = 1;
        buf.append(leftside).append(".").append(rightside);
        buf.append(" KB");

    }


    /**
     * Serve the specified resource, optionally including the data content.
     *
     * @param request       The servlet request we are processing
     * @param response      The servlet response we are creating
     * @param content       Should the content be included?
     *
     * @exception IOException if an input/output error occurs
     * @exception ServletException if a servlet-specified error occurs
     */
    protected void serveResource(HttpServletRequest request,
                                 HttpServletResponse response,
                                 boolean content)
        throws IOException, ServletException {

        boolean serveContent = content;

        // Identify the requested resource path
        String path = getRelativePath(request, true);

        if (debug > 0) {
            if (serveContent)
                log("DefaultServlet.serveResource:  Serving resource '" +
                    path + "' headers and data");
            else
                log("DefaultServlet.serveResource:  Serving resource '" +
                    path + "' headers only");
        }

        if (path.length() == 0) {
            // Context root redirect
            doDirectoryRedirect(request, response);
            return;
        }

        CacheEntry cacheEntry = resources.lookupCache(path);
        boolean isError = DispatcherType.ERROR == request.getDispatcherType();

        if (!cacheEntry.exists) {
            // Check if we're included so we can return the appropriate
            // missing resource name in the error
            String requestUri = (String) request.getAttribute(
                    RequestDispatcher.INCLUDE_REQUEST_URI);
            if (requestUri == null) {
                requestUri = request.getRequestURI();
            } else {
                // We're included
                // SRV.9.3 says we must throw a FNFE
                throw new FileNotFoundException(sm.getString(
                        "defaultServlet.missingResource", requestUri));
            }

            if (isError) {
                response.sendError(((Integer) request.getAttribute(
                        RequestDispatcher.ERROR_STATUS_CODE)).intValue());
            } else {
                response.sendError(HttpServletResponse.SC_NOT_FOUND, requestUri);
            }
            return;
        }

        // Check if the conditions specified in the optional If headers are
        // satisfied.
        if (cacheEntry.context == null) {
            // Checking If headers
            boolean included = (request.getAttribute(
                    RequestDispatcher.INCLUDE_CONTEXT_PATH) != null);
            if (!included && !isError &&
                    !checkIfHeaders(request, response, cacheEntry.attributes)) {
                return;
            }
        }

        // Find content type.
        String contentType = cacheEntry.attributes.getMimeType();
        if (contentType == null) {
            contentType = getServletContext().getMimeType(cacheEntry.name);
            cacheEntry.attributes.setMimeType(contentType);
        }

        ArrayList<Range> ranges = null;
        long contentLength = -1L;

        if (cacheEntry.context != null) {
            if (!path.endsWith("/")) {
                doDirectoryRedirect(request, response);
                return;
            }

            // Skip directory listings if we have been configured to
            // suppress them
            if (!listings) {
                response.sendError(HttpServletResponse.SC_NOT_FOUND,
                                   request.getRequestURI());
                return;
            }
            contentType = "text/html;charset=UTF-8";
        } else {
            if (!isError) {
                if (useAcceptRanges) {
                    // Accept ranges header
                    response.setHeader("Accept-Ranges", "bytes");
                }

                // Parse range specifier
                ranges = parseRange(request, response, cacheEntry.attributes);

                // ETag header
                response.setHeader("ETag", cacheEntry.attributes.getETag());

                // Last-Modified header
                response.setHeader("Last-Modified",
                        cacheEntry.attributes.getLastModifiedHttp());
            }

            // Get content length
            contentLength = cacheEntry.attributes.getContentLength();
            // Special case for zero length files, which would cause a
            // (silent) ISE when setting the output buffer size
            if (contentLength == 0L) {
                serveContent = false;
            }
        }

        ServletOutputStream ostream = null;
        PrintWriter writer = null;

        if (serveContent) {
            // Trying to retrieve the servlet output stream
            try {
                ostream = response.getOutputStream();
            } catch (IllegalStateException e) {
                // If it fails, we try to get a Writer instead if we're
                // trying to serve a text file
                if ( (contentType == null)
                        || (contentType.startsWith("text"))
                        || (contentType.endsWith("xml"))
                        || (contentType.contains("/javascript")) ) {
                    writer = response.getWriter();
                    // Cannot reliably serve partial content with a Writer
                    ranges = FULL;
                } else {
                    throw e;
                }
            }
        }

        // Check to see if a Filter, Valve or wrapper has written some content.
        // If it has, disable range requests and setting of a content length
        // since neither can be done reliably.
        ServletResponse r = response;
        long contentWritten = 0;
        while (r instanceof ServletResponseWrapper) {
            r = ((ServletResponseWrapper) r).getResponse();
        }
        if (r instanceof ResponseFacade) {
            contentWritten = ((ResponseFacade) r).getContentWritten();
        }
        if (contentWritten > 0) {
            ranges = FULL;
        }

        if ( (cacheEntry.context != null)
                || isError
                || ( ((ranges == null) || (ranges.isEmpty()))
                        && (request.getHeader("Range") == null) )
                || (ranges == FULL) ) {

            // Set the appropriate output headers
            if (contentType != null) {
                if (debug > 0)
                    log("DefaultServlet.serveFile:  contentType='" +
                        contentType + "'");
                response.setContentType(contentType);
            }
            if ((cacheEntry.resource != null) && (contentLength >= 0)
                    && (!serveContent || ostream != null)) {
                if (debug > 0)
                    log("DefaultServlet.serveFile:  contentLength=" +
                        contentLength);
                // Don't set a content length if something else has already
                // written to the response.
                if (contentWritten == 0) {
                    if (contentLength < Integer.MAX_VALUE) {
                        response.setContentLength((int) contentLength);
                    } else {
                        // Set the content-length as String to be able to use a
                        // long
                        response.setHeader("content-length",
                                "" + contentLength);
                    }
                }
            }

            InputStream renderResult = null;
            if (cacheEntry.context != null) {

                if (serveContent) {
                    // Serve the directory browser
                    renderResult = render(getPathPrefix(request), cacheEntry);
                }

            }

            // Copy the input stream to our output stream (if requested)
            if (serveContent) {
                try {
                    response.setBufferSize(output);
                } catch (IllegalStateException e) {
                    // Silent catch
                }
                if (ostream != null) {
                    if (!checkSendfile(request, response, cacheEntry, contentLength, null))
                        copy(cacheEntry, renderResult, ostream);
                } else {
                    copy(cacheEntry, renderResult, writer);
                }
            }

        } else {

            if ((ranges == null) || (ranges.isEmpty()))
                return;

            // Partial content response.

            response.setStatus(HttpServletResponse.SC_PARTIAL_CONTENT);

            if (ranges.size() == 1) {

                Range range = ranges.get(0);
                response.addHeader("Content-Range", "bytes "
                                   + range.start
                                   + "-" + range.end + "/"
                                   + range.length);
                long length = range.end - range.start + 1;
                if (length < Integer.MAX_VALUE) {
                    response.setContentLength((int) length);
                } else {
                    // Set the content-length as String to be able to use a long
                    response.setHeader("content-length", "" + length);
                }

                if (contentType != null) {
                    if (debug > 0)
                        log("DefaultServlet.serveFile:  contentType='" +
                            contentType + "'");
                    response.setContentType(contentType);
                }

                if (serveContent) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        if (!checkSendfile(request, response, cacheEntry, range.end - range.start + 1, range))
                            copy(cacheEntry, ostream, range);
                    } else {
                        // we should not get here
                        throw new IllegalStateException();
                    }
                }
            } else {
                response.setContentType("multipart/byteranges; boundary="
                                        + mimeSeparation);
                if (serveContent) {
                    try {
                        response.setBufferSize(output);
                    } catch (IllegalStateException e) {
                        // Silent catch
                    }
                    if (ostream != null) {
                        copy(cacheEntry, ostream, ranges.iterator(),
                             contentType);
                    } else {
                        // we should not get here
                        throw new IllegalStateException();
                    }
                }
            }
        }
    }

    private void doDirectoryRedirect(HttpServletRequest request, HttpServletResponse response)
            throws IOException {
        StringBuilder location = new StringBuilder(request.getRequestURI());
        location.append('/');
        if (request.getQueryString() != null) {
            location.append('?');
            location.append(request.getQueryString());
        }
        // Avoid protocol relative redirects
        while (location.length() > 1 && location.charAt(1) == '/') {
            location.deleteCharAt(0);
        }
        response.sendRedirect(response.encodeRedirectURL(location.toString()));
    }

    /**
     * Parse the content-range header.
     *
     * @param request The servlet request we are processing
     * @param response The servlet response we are creating
     * @return the range object
     * @throws IOException an IO error occurred
     */
    protected Range parseContentRange(HttpServletRequest request,
                                      HttpServletResponse response)
        throws IOException {

        // Retrieving the content-range header (if any is specified
        String rangeHeader = request.getHeader("Content-Range");

        if (rangeHeader == null)
            return null;

        // bytes is the only range unit supported
        if (!rangeHeader.startsWith("bytes")) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        rangeHeader = rangeHeader.substring(6).trim();

        int dashPos = rangeHeader.indexOf('-');
        int slashPos = rangeHeader.indexOf('/');

        if (dashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (slashPos == -1) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        Range range = new Range();

        try {
            range.start = Long.parseLong(rangeHeader.substring(0, dashPos));
            range.end =
                Long.parseLong(rangeHeader.substring(dashPos + 1, slashPos));
            range.length = Long.parseLong
                (rangeHeader.substring(slashPos + 1, rangeHeader.length()));
        } catch (NumberFormatException e) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        if (!range.validate()) {
            response.sendError(HttpServletResponse.SC_BAD_REQUEST);
            return null;
        }

        return range;

    }


    /**
     * Parse the range header.
     *
     * @param request             The servlet request we are processing
     * @param response            The servlet response we are creating
     * @param resourceAttributes  The resource information
     * @return a list of ranges
     * @throws IOException an IO error occurred
     */
    protected ArrayList<Range> parseRange(HttpServletRequest request,
            HttpServletResponse response,
            ResourceAttributes resourceAttributes) throws IOException {

        // Checking If-Range
        String headerValue = request.getHeader("If-Range");

        if (headerValue != null) {

            long headerValueTime = (-1L);
            try {
                headerValueTime = request.getDateHeader("If-Range");
            } catch (IllegalArgumentException e) {
                // Ignore
            }

            String eTag = resourceAttributes.getETag();
            long lastModified = resourceAttributes.getLastModified();

            if (headerValueTime == (-1L)) {

                // If the ETag the client gave does not match the entity
                // etag, then the entire entity is returned.
                if (!eTag.equals(headerValue.trim()))
                    return FULL;

            } else {

                // If the timestamp of the entity the client got is older than
                // the last modification date of the entity, the entire entity
                // is returned.
                if (lastModified > (headerValueTime + 1000))
                    return FULL;

            }

        }

        long fileLength = resourceAttributes.getContentLength();

        if (fileLength == 0) {
            return null;
        }

        // Retrieving the range header (if any is specified
        String rangeHeader = request.getHeader("Range");

        if (rangeHeader == null) {
            return null;
        }

        // bytes is the only range unit supported (and I don't see the point
        // of adding new ones).
        if (!rangeHeader.startsWith("bytes")) {
            response.addHeader("Content-Range", "bytes */" + fileLength);
            response.sendError
                (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
            return null;
        }

        rangeHeader = rangeHeader.substring(6);

        // Collection which will contain all the ranges which are successfully
        // parsed.
        ArrayList<Range> result = new ArrayList<Range>();
        StringTokenizer commaTokenizer = new StringTokenizer(rangeHeader, ",");

        // Parsing the range list
        while (commaTokenizer.hasMoreTokens()) {
            String rangeDefinition = commaTokenizer.nextToken().trim();

            Range currentRange = new Range();
            currentRange.length = fileLength;

            int dashPos = rangeDefinition.indexOf('-');

            if (dashPos == -1) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError
                    (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }

            if (dashPos == 0) {

                try {
                    long offset = Long.parseLong(rangeDefinition);
                    currentRange.start = fileLength + offset;
                    currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    response.addHeader("Content-Range",
                                       "bytes */" + fileLength);
                    response.sendError
                        (HttpServletResponse
                         .SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return null;
                }

            } else {

                try {
                    currentRange.start = Long.parseLong
                        (rangeDefinition.substring(0, dashPos));
                    if (dashPos < rangeDefinition.length() - 1)
                        currentRange.end = Long.parseLong
                            (rangeDefinition.substring
                             (dashPos + 1, rangeDefinition.length()));
                    else
                        currentRange.end = fileLength - 1;
                } catch (NumberFormatException e) {
                    response.addHeader("Content-Range",
                                       "bytes */" + fileLength);
                    response.sendError
                        (HttpServletResponse
                         .SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                    return null;
                }

            }

            if (!currentRange.validate()) {
                response.addHeader("Content-Range", "bytes */" + fileLength);
                response.sendError
                    (HttpServletResponse.SC_REQUESTED_RANGE_NOT_SATISFIABLE);
                return null;
            }

            result.add(currentRange);
        }

        return result;
    }


    /**
     * Decide which way to render. HTML or XML.
     *
     * @param contextPath The path
     * @param cacheEntry  The resource
     *
     * @return the input stream with the rendered output
     *
     * @throws IOException an IO error occurred
     * @throws ServletException rendering error
     */
    protected InputStream render(String contextPath, CacheEntry cacheEntry)
        throws IOException, ServletException {

        Source xsltSource = findXsltInputStream(cacheEntry.context);

        if (xsltSource == null) {
            return renderHtml(contextPath, cacheEntry);
        }
        return renderXml(contextPath, cacheEntry, xsltSource);

    }


    /**
     * Return an InputStream to an XML representation of the contents this
     * directory.
     *
     * @param contextPath Context path to which our internal paths are
     *  relative
     */
    protected InputStream renderXml(String contextPath,
                                    CacheEntry cacheEntry,
            Source xsltSource)
        throws IOException, ServletException {

        StringBuilder sb = new StringBuilder();

        sb.append("<?xml version=\"1.0\"?>");
        sb.append("<listing ");
        sb.append(" contextPath='");
        sb.append(contextPath);
        sb.append("'");
        sb.append(" directory='");
        sb.append(cacheEntry.name);
        sb.append("' ");
        sb.append(" hasParent='").append(!cacheEntry.name.equals("/"));
        sb.append("'>");

        sb.append("<entries>");

        try {

            // Render the directory entries within this directory
            NamingEnumeration<NameClassPair> enumeration =
                resources.list(cacheEntry.name);

            // rewriteUrl(contextPath) is expensive. cache result for later reuse
            String rewrittenContextPath =  rewriteUrl(contextPath);

            while (enumeration.hasMoreElements()) {

                NameClassPair ncPair = enumeration.nextElement();
                String resourceName = ncPair.getName();
                String trimmed = resourceName/*.substring(trim)*/;
                if (trimmed.equalsIgnoreCase("WEB-INF") ||
                    trimmed.equalsIgnoreCase("META-INF") ||
                    trimmed.equalsIgnoreCase(localXsltFile))
                    continue;

                if ((cacheEntry.name + trimmed).equals(contextXsltFile))
                    continue;

                CacheEntry childCacheEntry =
                    resources.lookupCache(cacheEntry.name + resourceName);
                if (!childCacheEntry.exists) {
                    continue;
                }

                sb.append("<entry");
                sb.append(" type='")
                  .append((childCacheEntry.context != null)?"dir":"file")
                  .append("'");
                sb.append(" urlPath='")
                  .append(rewrittenContextPath)
                  .append(rewriteUrl(cacheEntry.name + resourceName))
                  .append((childCacheEntry.context != null)?"/":"")
                  .append("'");
                if (childCacheEntry.resource != null) {
                    sb.append(" size='")
                      .append(renderSize(childCacheEntry.attributes.getContentLength()))
                      .append("'");
                }
                sb.append(" date='")
                  .append(childCacheEntry.attributes.getLastModifiedHttp())
                  .append("'");

                sb.append(">");
                sb.append(RequestUtil.filter(trimmed));
                if (childCacheEntry.context != null)
                    sb.append("/");
                sb.append("</entry>");

            }

        } catch (NamingException e) {
            // Something went wrong
            throw new ServletException("Error accessing resource", e);
        }
        sb.append("</entries>");

        String readme = getReadme(cacheEntry.context);

        if (readme!=null) {
            sb.append("<readme><![CDATA[");
            sb.append(readme);
            sb.append("]]></readme>");
        }

        sb.append("</listing>");

        // Prevent possible memory leak. Ensure Transformer and
        // TransformerFactory are not loaded from the web application.
        ClassLoader original;
        if (Globals.IS_SECURITY_ENABLED) {
            PrivilegedGetTccl pa = new PrivilegedGetTccl();
            original = AccessController.doPrivileged(pa);
        } else {
            original = Thread.currentThread().getContextClassLoader();
        }
        try {
            if (Globals.IS_SECURITY_ENABLED) {
                PrivilegedSetTccl pa =
                        new PrivilegedSetTccl(DefaultServlet.class.getClassLoader());
                AccessController.doPrivileged(pa);
            } else {
                Thread.currentThread().setContextClassLoader(
                        DefaultServlet.class.getClassLoader());
            }

            TransformerFactory tFactory = TransformerFactory.newInstance();
            Source xmlSource = new StreamSource(new StringReader(sb.toString()));
            Transformer transformer = tFactory.newTransformer(xsltSource);

            ByteArrayOutputStream stream = new ByteArrayOutputStream();
            OutputStreamWriter osWriter = new OutputStreamWriter(stream, "UTF-8");
            StreamResult out = new StreamResult(osWriter);
            transformer.transform(xmlSource, out);
            osWriter.flush();
            return new ByteArrayInputStream(stream.toByteArray());
        } catch (TransformerException e) {
            throw new ServletException(sm.getString("defaultServlet.xslError"), e);
        } finally {
            if (Globals.IS_SECURITY_ENABLED) {
                PrivilegedSetTccl pa = new PrivilegedSetTccl(original);
                AccessController.doPrivileged(pa);
            } else {
                Thread.currentThread().setContextClassLoader(original);
            }
        }
    }

    /**
     * Return an InputStream to an HTML representation of the contents of this
     * directory.
     *
     * @param contextPath Context path to which our internal paths are relative
     */
    protected InputStream renderHtml(String contextPath, CacheEntry cacheEntry)
        throws IOException, ServletException {

        String name = cacheEntry.name;

        // Prepare a writer to a buffered area
        ByteArrayOutputStream stream = new ByteArrayOutputStream();
        OutputStreamWriter osWriter = new OutputStreamWriter(stream, "UTF-8");
        PrintWriter writer = new PrintWriter(osWriter);

        StringBuilder sb = new StringBuilder();

        // rewriteUrl(contextPath) is expensive. cache result for later reuse
        String rewrittenContextPath =  rewriteUrl(contextPath);

        // Render the page header
        sb.append("<html>\r\n");
        sb.append("<head>\r\n");
        sb.append("<title>");
        sb.append(sm.getString("directory.title", name));
        sb.append("</title>\r\n");
        sb.append("<STYLE><!--");
        sb.append(org.apache.catalina.util.TomcatCSS.TOMCAT_CSS);
        sb.append("--></STYLE> ");
        sb.append("</head>\r\n");
        sb.append("<body>");
        sb.append("<h1>");
        sb.append(sm.getString("directory.title", name));

        // Render the link to our parent (if required)
        String parentDirectory = name;
        if (parentDirectory.endsWith("/")) {
            parentDirectory =
                parentDirectory.substring(0, parentDirectory.length() - 1);
        }
        int slash = parentDirectory.lastIndexOf('/');
        if (slash >= 0) {
            String parent = name.substring(0, slash);
            sb.append(" - <a href=\"");
            sb.append(rewrittenContextPath);
            if (parent.equals(""))
                parent = "/";
            sb.append(rewriteUrl(parent));
            if (!parent.endsWith("/"))
                sb.append("/");
            sb.append("\">");
            sb.append("<b>");
            sb.append(sm.getString("directory.parent", parent));
            sb.append("</b>");
            sb.append("</a>");
        }

        sb.append("</h1>");
        sb.append("<HR size=\"1\" noshade=\"noshade\">");

        sb.append("<table width=\"100%\" cellspacing=\"0\"" +
                     " cellpadding=\"5\" align=\"center\">\r\n");

        // Render the column headings
        sb.append("<tr>\r\n");
        sb.append("<td align=\"left\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.filename"));
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"center\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.size"));
        sb.append("</strong></font></td>\r\n");
        sb.append("<td align=\"right\"><font size=\"+1\"><strong>");
        sb.append(sm.getString("directory.lastModified"));
        sb.append("</strong></font></td>\r\n");
        sb.append("</tr>");

        try {

            // Render the directory entries within this directory
            NamingEnumeration<NameClassPair> enumeration =
                resources.list(cacheEntry.name);
            boolean shade = false;
            while (enumeration.hasMoreElements()) {

                NameClassPair ncPair = enumeration.nextElement();
                String resourceName = ncPair.getName();
                String trimmed = resourceName/*.substring(trim)*/;
                if (trimmed.equalsIgnoreCase("WEB-INF") ||
                    trimmed.equalsIgnoreCase("META-INF"))
                    continue;

                CacheEntry childCacheEntry =
                    resources.lookupCache(cacheEntry.name + resourceName);
                if (!childCacheEntry.exists) {
                    continue;
                }

                sb.append("<tr");
                if (shade)
                    sb.append(" bgcolor=\"#eeeeee\"");
                sb.append(">\r\n");
                shade = !shade;

                sb.append("<td align=\"left\">&nbsp;&nbsp;\r\n");
                sb.append("<a href=\"");
                sb.append(rewrittenContextPath);
                resourceName = rewriteUrl(name + resourceName);
                sb.append(resourceName);
                if (childCacheEntry.context != null)
                    sb.append("/");
                sb.append("\"><tt>");
                sb.append(RequestUtil.filter(trimmed));
                if (childCacheEntry.context != null)
                    sb.append("/");
                sb.append("</tt></a></td>\r\n");

                sb.append("<td align=\"right\"><tt>");
                if (childCacheEntry.context != null)
                    sb.append("&nbsp;");
                else
                    sb.append(renderSize(childCacheEntry.attributes.getContentLength()));
                sb.append("</tt></td>\r\n");

                sb.append("<td align=\"right\"><tt>");
                sb.append(childCacheEntry.attributes.getLastModifiedHttp());
                sb.append("</tt></td>\r\n");

                sb.append("</tr>\r\n");
            }

        } catch (NamingException e) {
            // Something went wrong
            throw new ServletException("Error accessing resource", e);
        }

        // Render the page footer
        sb.append("</table>\r\n");

        sb.append("<HR size=\"1\" noshade=\"noshade\">");

        String readme = getReadme(cacheEntry.context);
        if (readme!=null) {
            sb.append(readme);
            sb.append("<HR size=\"1\" noshade=\"noshade\">");
        }

        if (showServerInfo) {
            sb.append("<h3>").append(ServerInfo.getServerInfo()).append("</h3>");
        }
        sb.append("</body>\r\n");
        sb.append("</html>\r\n");

        // Return an input stream to the underlying bytes
        writer.write(sb.toString());
        writer.flush();
        return new ByteArrayInputStream(stream.toByteArray());

    }


    /**
     * Render the specified file size (in bytes).
     *
     * @param size File size (in bytes)
     * @return the formatted size
     */
    protected String renderSize(long size) {

        long leftSide = size / 1024;
        long rightSide = (size % 1024) / 103;   // Makes 1 digit
        if ((leftSide == 0) && (rightSide == 0) && (size > 0))
            rightSide = 1;

        return ("" + leftSide + "." + rightSide + " kb");

    }


    /**
     * Get the readme file as a string.
     * @param directory The directory to search
     */
    protected String getReadme(DirContext directory)
        throws IOException {

        if (readmeFile != null) {
            try {
                Object obj = directory.lookup(readmeFile);
                if ((obj != null) && (obj instanceof Resource)) {
                    StringWriter buffer = new StringWriter();
                    InputStream is = null;
                    Reader reader = null;
                    try {
                        is = ((Resource) obj).streamContent();
                        if (fileEncoding != null) {
                            reader = new InputStreamReader(is, fileEncoding);
                        } else {
                            reader = new InputStreamReader(is);
                        }
                        copyRange(reader,
                                new PrintWriter(buffer));
                    } finally {
                        if (reader != null) {
                            try {
                                reader.close();
                            } catch (IOException e) {
                                log("Could not close reader", e);
                            }
                        }
                        if (is != null) {
                            try {
                                is.close();
                            } catch (IOException e) {
                                log("Could not close is", e);
                            }
                        }
                    }
                    return buffer.toString();
                }
            } catch (NamingException e) {
                if (debug > 10) {
                    log("readme '" + readmeFile + "' not found", e);
                }
                return null;
            }
        }

        return null;
    }


    /**
     * Return a Source for the xsl template (if possible).
     * @param directory The directory to search
     * @return the source for the specified directory
     * @throws IOException an IO error occurred
     */
    protected Source findXsltInputStream(DirContext directory)
        throws IOException {

        if (localXsltFile != null) {
            try {
                Object obj = directory.lookup(localXsltFile);
                if ((obj != null) && (obj instanceof Resource)) {
                    InputStream is = ((Resource) obj).streamContent();
                    if (is != null) {
                        if (Globals.IS_SECURITY_ENABLED) {
                            return secureXslt(is);
                        } else {
                            return new StreamSource(is);
                        }
                    }
                }
            } catch (NamingException e) {
                if (debug > 10)
                    log("localXsltFile '" + localXsltFile + "' not found", e);
            }
        }

        if (contextXsltFile != null) {
            InputStream is =
                getServletContext().getResourceAsStream(contextXsltFile);
            if (is != null) {
                if (Globals.IS_SECURITY_ENABLED) {
                    return secureXslt(is);
                } else {
                    return new StreamSource(is);
                }
            }

            if (debug > 10)
                log("contextXsltFile '" + contextXsltFile + "' not found");
        }

        /*  Open and read in file in one fell swoop to reduce chance
         *  chance of leaving handle open.
         */
        if (globalXsltFile != null) {
            File f = validateGlobalXsltFile();
            if (f != null) {
                long globalXsltFileSize = f.length();
                if (globalXsltFileSize > Integer.MAX_VALUE) {
                    log("globalXsltFile [" + f.getAbsolutePath() + "] is too big to buffer");
                } else {
                    FileInputStream fis = null;
                    try {
                        fis = new FileInputStream(f);
                        byte b[] = new byte[(int)f.length()];
                        IOTools.readFully(fis, b);
                        return new StreamSource(new ByteArrayInputStream(b));
                    } finally {
                        if (fis != null) {
                            try {
                                fis.close();
                            } catch (IOException ioe) {
                                // Ignore
                            }
                        }
                    }
                }
            }
        }

        return null;
    }


    private File validateGlobalXsltFile() {

        File result = null;
        String base = System.getProperty(Globals.CATALINA_BASE_PROP);

        if (base != null) {
            File baseConf = new File(base, "conf");
            result = validateGlobalXsltFile(baseConf);
        }

        if (result == null) {
            String home = System.getProperty(Globals.CATALINA_HOME_PROP);
            if (home != null && !home.equals(base)) {
                File homeConf = new File(home, "conf");
                result = validateGlobalXsltFile(homeConf);
            }
        }

        return result;
    }


    private File validateGlobalXsltFile(File base) {
        File candidate = new File(globalXsltFile);
        if (!candidate.isAbsolute()) {
            candidate = new File(base, globalXsltFile);
        }

        if (!candidate.isFile()) {
            return null;
        }

        // First check that the resulting path is under the provided base
        try {
            if (!candidate.getCanonicalPath().startsWith(base.getCanonicalPath())) {
                return null;
            }
        } catch (IOException ioe) {
            return null;
        }

        // Next check that an .xsl or .xslt file has been specified
        String nameLower = candidate.getName().toLowerCase(Locale.ENGLISH);
        if (!nameLower.endsWith(".xslt") && !nameLower.endsWith(".xsl")) {
            return null;
        }

        return candidate;
    }


    private Source secureXslt(InputStream is) {
        // Need to filter out any external entities
        Source result = null;
        try {
            DocumentBuilder builder = factory.newDocumentBuilder();
            builder.setEntityResolver(secureEntityResolver);
            Document document = builder.parse(is);
            result = new DOMSource(document);
        } catch (ParserConfigurationException e) {
            if (debug > 0) {
                log(e.getMessage(), e);
            }
        } catch (SAXException e) {
            if (debug > 0) {
                log(e.getMessage(), e);
            }
        } catch (IOException e) {
            if (debug > 0) {
                log(e.getMessage(), e);
            }
        } finally {
            if (is != null) {
                try {
                    is.close();
                } catch (IOException e) {
                    // Ignore
                }
            }
        }
        return result;
    }


    // -------------------------------------------------------- protected Methods

    /**
     * Check if sendfile can be used.
     * @param request The Servlet request
     * @param response The Servlet response
     * @param entry The resource
     * @param length The length which will be written (will be used only if
     *  range is null)
     * @param range The range that will be written
     * @return <code>true</code> if sendfile should be used (writing is then
     *  delegated to the endpoint)
     */
    protected boolean checkSendfile(HttpServletRequest request,
                                  HttpServletResponse response,
                                  CacheEntry entry,
                                  long length, Range range) {
        if ((sendfileSize > 0)
            && (entry.resource != null)
            && ((length > sendfileSize) || (entry.resource.getContent() == null))
            && (entry.attributes.getCanonicalPath() != null)
            && (Boolean.TRUE.equals(request.getAttribute(Globals.SENDFILE_SUPPORTED_ATTR)))
            && (request.getClass().getName().equals("org.apache.catalina.connector.RequestFacade"))
            && (response.getClass().getName().equals("org.apache.catalina.connector.ResponseFacade"))) {
            request.setAttribute(Globals.SENDFILE_FILENAME_ATTR, entry.attributes.getCanonicalPath());
            if (range == null) {
                request.setAttribute(Globals.SENDFILE_FILE_START_ATTR, Long.valueOf(0L));
                request.setAttribute(Globals.SENDFILE_FILE_END_ATTR, Long.valueOf(length));
            } else {
                request.setAttribute(Globals.SENDFILE_FILE_START_ATTR, Long.valueOf(range.start));
                request.setAttribute(Globals.SENDFILE_FILE_END_ATTR, Long.valueOf(range.end + 1));
            }
            return true;
        }
        return false;
    }


    /**
     * Check if the if-match condition is satisfied.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resourceAttributes File object
     * @return <code>true</code> if the resource meets the specified condition,
     *  and <code>false</code> if the condition is not satisfied, in which case
     *  request processing is stopped
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfMatch(HttpServletRequest request,
            HttpServletResponse response, ResourceAttributes resourceAttributes)
            throws IOException {

        String eTag = resourceAttributes.getETag();
        String headerValue = request.getHeader("If-Match");
        if (headerValue != null) {
            if (headerValue.indexOf('*') == -1) {

                StringTokenizer commaTokenizer = new StringTokenizer
                    (headerValue, ",");
                boolean conditionSatisfied = false;

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

                // If none of the given ETags match, 412 Precondition failed is
                // sent back
                if (!conditionSatisfied) {
                    response.sendError
                        (HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }

            }
        }
        return true;
    }


    /**
     * Check if the if-modified-since condition is satisfied.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resourceAttributes File object
     * @return <code>true</code> if the resource meets the specified condition,
     *  and <code>false</code> if the condition is not satisfied, in which case
     *  request processing is stopped
     */
    protected boolean checkIfModifiedSince(HttpServletRequest request,
            HttpServletResponse response, ResourceAttributes resourceAttributes) {
        try {
            long headerValue = request.getDateHeader("If-Modified-Since");
            long lastModified = resourceAttributes.getLastModified();
            if (headerValue != -1) {

                // If an If-None-Match header has been specified, if modified since
                // is ignored.
                if ((request.getHeader("If-None-Match") == null)
                    && (lastModified < headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", resourceAttributes.getETag());

                    return false;
                }
            }
        } catch (IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;
    }


    /**
     * Check if the if-none-match condition is satisfied.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resourceAttributes File object
     * @return <code>true</code> if the resource meets the specified condition,
     *  and <code>false</code> if the condition is not satisfied, in which case
     *  request processing is stopped
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfNoneMatch(HttpServletRequest request,
            HttpServletResponse response, ResourceAttributes resourceAttributes)
            throws IOException {

        String eTag = resourceAttributes.getETag();
        String headerValue = request.getHeader("If-None-Match");
        if (headerValue != null) {

            boolean conditionSatisfied = false;

            if (!headerValue.equals("*")) {

                StringTokenizer commaTokenizer =
                    new StringTokenizer(headerValue, ",");

                while (!conditionSatisfied && commaTokenizer.hasMoreTokens()) {
                    String currentToken = commaTokenizer.nextToken();
                    if (currentToken.trim().equals(eTag))
                        conditionSatisfied = true;
                }

            } else {
                conditionSatisfied = true;
            }

            if (conditionSatisfied) {

                // For GET and HEAD, we should respond with
                // 304 Not Modified.
                // For every other method, 412 Precondition Failed is sent
                // back.
                if ( ("GET".equals(request.getMethod()))
                     || ("HEAD".equals(request.getMethod())) ) {
                    response.setStatus(HttpServletResponse.SC_NOT_MODIFIED);
                    response.setHeader("ETag", eTag);

                    return false;
                }
                response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                return false;
            }
        }
        return true;
    }

    /**
     * Check if the if-unmodified-since condition is satisfied.
     *
     * @param request   The servlet request we are processing
     * @param response  The servlet response we are creating
     * @param resourceAttributes File object
     * @return <code>true</code> if the resource meets the specified condition,
     *  and <code>false</code> if the condition is not satisfied, in which case
     *  request processing is stopped
     * @throws IOException an IO error occurred
     */
    protected boolean checkIfUnmodifiedSince(HttpServletRequest request,
            HttpServletResponse response, ResourceAttributes resourceAttributes)
            throws IOException {
        try {
            long lastModified = resourceAttributes.getLastModified();
            long headerValue = request.getDateHeader("If-Unmodified-Since");
            if (headerValue != -1) {
                if ( lastModified >= (headerValue + 1000)) {
                    // The entity has not been modified since the date
                    // specified by the client. This is not an error case.
                    response.sendError(HttpServletResponse.SC_PRECONDITION_FAILED);
                    return false;
                }
            }
        } catch(IllegalArgumentException illegalArgument) {
            return true;
        }
        return true;
    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param cacheEntry The cache entry for the source resource
     * @param is         The input stream to read the source resource from
     * @param ostream    The output stream to write to
     *
     * @exception IOException if an input/output error occurs
     */
    protected void copy(CacheEntry cacheEntry, InputStream is,
                      ServletOutputStream ostream)
        throws IOException {

        IOException exception = null;
        InputStream resourceInputStream = null;

        // Optimization: If the binary content has already been loaded, send
        // it directly
        if (cacheEntry.resource != null) {
            byte buffer[] = cacheEntry.resource.getContent();
            if (buffer != null) {
                ostream.write(buffer, 0, buffer.length);
                return;
            }
            resourceInputStream = cacheEntry.resource.streamContent();
        } else {
            resourceInputStream = is;
        }

        InputStream istream = new BufferedInputStream
            (resourceInputStream, input);

        // Copy the input stream to the output stream
        exception = copyRange(istream, ostream);

        // Clean up the input stream
        istream.close();

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;
    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param cacheEntry The cache entry for the source resource
     * @param is The input stream to read the source resource from
     * @param writer The writer to write to
     *
     * @exception IOException if an input/output error occurs
     */
    protected void copy(CacheEntry cacheEntry, InputStream is, PrintWriter writer)
        throws IOException {

        IOException exception = null;

        InputStream resourceInputStream = null;
        if (cacheEntry.resource != null) {
            resourceInputStream = cacheEntry.resource.streamContent();
        } else {
            resourceInputStream = is;
        }

        Reader reader;
        if (fileEncoding == null) {
            reader = new InputStreamReader(resourceInputStream);
        } else {
            reader = new InputStreamReader(resourceInputStream,
                                           fileEncoding);
        }

        // Copy the input stream to the output stream
        exception = copyRange(reader, writer);

        // Clean up the reader
        reader.close();

        // Rethrow any exception that has occurred
        if (exception != null) {
            throw exception;
        }
    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param cacheEntry The cache entry for the source resource
     * @param ostream   The output stream to write to
     * @param range     Range the client wanted to retrieve
     * @exception IOException if an input/output error occurs
     */
    protected void copy(CacheEntry cacheEntry, ServletOutputStream ostream,
                      Range range)
        throws IOException {

        IOException exception = null;

        InputStream resourceInputStream = cacheEntry.resource.streamContent();
        InputStream istream =
            new BufferedInputStream(resourceInputStream, input);
        exception = copyRange(istream, ostream, range.start, range.end);

        // Clean up the input stream
        istream.close();

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param cacheEntry    The cache entry for the source resource
     * @param ostream       The output stream to write to
     * @param ranges        Enumeration of the ranges the client wanted to
     *                          retrieve
     * @param contentType   Content type of the resource
     * @exception IOException if an input/output error occurs
     */
    protected void copy(CacheEntry cacheEntry, ServletOutputStream ostream,
                      Iterator<Range> ranges, String contentType)
        throws IOException {

        IOException exception = null;

        while ( (exception == null) && (ranges.hasNext()) ) {

            InputStream resourceInputStream = cacheEntry.resource.streamContent();
            InputStream istream = null;
            try {
                istream = new BufferedInputStream(resourceInputStream, input);
                Range currentRange = ranges.next();

                // Writing MIME header.
                ostream.println();
                ostream.println("--" + mimeSeparation);
                if (contentType != null)
                    ostream.println("Content-Type: " + contentType);
                ostream.println("Content-Range: bytes " + currentRange.start
                               + "-" + currentRange.end + "/"
                               + currentRange.length);
                ostream.println();

                // Printing content
                exception = copyRange(istream, ostream, currentRange.start,
                                      currentRange.end);
            } finally {
                if (istream != null) {
                    try {
                        istream.close();
                    } catch (IOException e) {
                    }
                }
            }
        }

        ostream.println();
        ostream.print("--" + mimeSeparation + "--");

        // Rethrow any exception that has occurred
        if (exception != null)
            throw exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param istream The input stream to read from
     * @param ostream The output stream to write to
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(InputStream istream,
                                  ServletOutputStream ostream) {

        // Copy the input stream to the output stream
        IOException exception = null;
        byte buffer[] = new byte[input];
        int len = buffer.length;
        while (true) {
            try {
                len = istream.read(buffer);
                if (len == -1)
                    break;
                ostream.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param reader The reader to read from
     * @param writer The writer to write to
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(Reader reader, PrintWriter writer) {

        // Copy the input stream to the output stream
        IOException exception = null;
        char buffer[] = new char[input];
        int len = buffer.length;
        while (true) {
            try {
                len = reader.read(buffer);
                if (len == -1)
                    break;
                writer.write(buffer, 0, len);
            } catch (IOException e) {
                exception = e;
                len = -1;
                break;
            }
        }
        return exception;

    }


    /**
     * Copy the contents of the specified input stream to the specified
     * output stream, and ensure that both streams are closed before returning
     * (even in the face of an exception).
     *
     * @param istream The input stream to read from
     * @param ostream The output stream to write to
     * @param start Start of the range which will be copied
     * @param end End of the range which will be copied
     * @return Exception which occurred during processing
     */
    protected IOException copyRange(InputStream istream,
                                  ServletOutputStream ostream,
                                  long start, long end) {

        if (debug > 10)
            log("Serving bytes:" + start + "-" + end);

        long skipped = 0;
        try {
            skipped = istream.skip(start);
        } catch (IOException e) {
            return e;
        }
        if (skipped < start) {
            return new IOException(sm.getString("defaultServlet.skipfail",
                    Long.valueOf(skipped), Long.valueOf(start)));
        }

        IOException exception = null;
        long bytesToRead = end - start + 1;

        byte buffer[] = new byte[input];
        int len = buffer.length;
        while ( (bytesToRead > 0) && (len >= buffer.length)) {
            try {
                len = istream.read(buffer);
                if (bytesToRead >= len) {
                    ostream.write(buffer, 0, len);
                    bytesToRead -= len;
                } else {
                    ostream.write(buffer, 0, (int) bytesToRead);
                    bytesToRead = 0;
                }
            } catch (IOException e) {
                exception = e;
                len = -1;
            }
            if (len < buffer.length)
                break;
        }

        return exception;

    }


    protected static class Range {

        public long start;
        public long end;
        public long length;

        /**
         * Validate range.
         *
         * @return true if the range is valid, otherwise false
         */
        public boolean validate() {
            if (end >= length)
                end = length - 1;
            return (start >= 0) && (end >= 0) && (start <= end) && (length > 0);
        }
    }


    /**
     * This is secure in the sense that any attempt to use an external entity
     * will trigger an exception.
     */
    private static class SecureEntityResolver implements EntityResolver2  {

        @Override
        public InputSource resolveEntity(String publicId, String systemId)
                throws SAXException, IOException {
            throw new SAXException(sm.getString("defaultServlet.blockExternalEntity",
                    publicId, systemId));
        }

        @Override
        public InputSource getExternalSubset(String name, String baseURI)
                throws SAXException, IOException {
            throw new SAXException(sm.getString("defaultServlet.blockExternalSubset",
                    name, baseURI));
        }

        @Override
        public InputSource resolveEntity(String name, String publicId,
                String baseURI, String systemId) throws SAXException,
                IOException {
            throw new SAXException(sm.getString("defaultServlet.blockExternalEntity2",
                    name, publicId, baseURI, systemId));
        }
    }
}
