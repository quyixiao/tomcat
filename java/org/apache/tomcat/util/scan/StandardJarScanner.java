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

package org.apache.tomcat.util.scan;

import java.io.File;
import java.io.IOException;
import java.net.JarURLConnection;
import java.net.MalformedURLException;
import java.net.URL;
import java.net.URLClassLoader;
import java.net.URLConnection;
import java.util.ArrayList;
import java.util.Collections;
import java.util.Deque;
import java.util.HashSet;
import java.util.LinkedList;
import java.util.List;
import java.util.Set;
import java.util.StringTokenizer;

import javax.servlet.ServletContext;

import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.JarScanner;
import org.apache.tomcat.JarScannerCallback;
import org.apache.tomcat.util.ExceptionUtils;
import org.apache.tomcat.util.buf.UriUtil;
import org.apache.tomcat.util.compat.JreCompat;
import org.apache.tomcat.util.file.Matcher;
import org.apache.tomcat.util.res.StringManager;

/**
 * The default {@link JarScanner} implementation scans the WEB-INF/lib directory
 * followed by the provided classloader and then works up the classloader
 * hierarchy. This implementation is sufficient to meet the requirements of the
 * Servlet 3.0 specification as well as to provide a number of Tomcat specific
 * extensions. The extensions are:
 * <ul>
 *   <li>Scanning the classloader hierarchy (enabled by default)</li>
 *   <li>Testing all files to see if they are JARs (disabled by default)</li>
 *   <li>Testing all directories to see if they are exploded JARs
 *       (disabled by default)</li>
 * </ul>
 * All of the extensions may be controlled via configuration.
 *
 * 从JarScanner的名字上已经知道它的作用，它一般包含在Context容器中，专门用于扫描Context的对应Web 应用的Jar 包，每个Web 应用初始化时
 * 在TLD 文件和Web-fragment.xml文件处理时都需要对该Web 应用下的Jar 包进行扫描，因为JAR 包可能包含这些配置文件，Web 容器需要对它们进行处理
 * 因为jar包可能包含这些配置文件，Web 容器需要对他们进行处理。
 *
 * Tomcat 中的JarScanner 的标准实现为StandardJarScanner ，它将对Web 应用的WEB-INF/lib目录的Jar 包进行扫描，它支持声明忽略某些Jar包
 * 同时它还支持classpath 下的jar 包进行扫描，然而，如果classpath 下的jar 包与WEB-INF/lib 目录下的JAR包相同 。则会被忽略掉。
 *
 * JarScanner 在设计上采用了回调机制，每扫描到一个Jar包时都会调用回调对象进行处理，回调对象需要实现JarScannerCallBack 接口，此接口包含了
 * scan(JarURLConnection urlConn) 和scan(File file) 两个方法，我们只需要将对Jar包处理的逻辑写入到这两个方法即可，JarScanner 在扫描
 * 到每个Jar 包后都会调用一次此方法，执行对该Jar 包的逻辑处理。
 *
 * Jar 包扫描器为Context 容器的启动过程提供了方便的扫描Jar包的功能，它让开发过程中不必关注Web 应用Jar 包的搜索，而是专注于编写对Jar包
 * 中的TLD 文件和web-fragment.xml 文件的处理逻辑 。
 *
 *
 *
 *
 */
public class StandardJarScanner implements JarScanner {

    private final Log log = LogFactory.getLog(StandardJarScanner.class); // must not be static

    private static final Set<String> defaultJarsToSkip = new HashSet<String>();

    /**
     * The string resources for this package.
     */
    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    static {
        String jarList = System.getProperty(Constants.SKIP_JARS_PROPERTY);
        if (jarList != null) {
            StringTokenizer tokenizer = new StringTokenizer(jarList, ",");
            while (tokenizer.hasMoreElements()) {
                String token = tokenizer.nextToken().trim();
                if (token.length() > 0) {
                    defaultJarsToSkip.add(token);
                }
            }
        }
    }

    /**
     * Controls the classpath scanning extension.
     */
    private boolean scanClassPath = true;
    public boolean isScanClassPath() {
        return scanClassPath;
    }
    public void setScanClassPath(boolean scanClassPath) {
        this.scanClassPath = scanClassPath;
    }

    /**
     * Controls the testing all files to see of they are JAR files extension.
     */
    private boolean scanAllFiles = false;
    public boolean isScanAllFiles() {
        return scanAllFiles;
    }
    public void setScanAllFiles(boolean scanAllFiles) {
        this.scanAllFiles = scanAllFiles;
    }

    /**
     * Controls the testing all directories to see of they are exploded JAR
     * files extension.
     */
    private boolean scanAllDirectories = false;
    public boolean isScanAllDirectories() {
        return scanAllDirectories;
    }
    public void setScanAllDirectories(boolean scanAllDirectories) {
        this.scanAllDirectories = scanAllDirectories;
    }

    /**
     * Controls the testing of the bootstrap classpath which consists of the
     * runtime classes provided by the JVM and any installed system extensions.
     */
    private boolean scanBootstrapClassPath = false;
    public boolean isScanBootstrapClassPath() {
        return scanBootstrapClassPath;
    }
    public void setScanBootstrapClassPath(boolean scanBootstrapClassPath) {
        this.scanBootstrapClassPath = scanBootstrapClassPath;
    }

    /**
     * Scan the provided ServletContext and classloader for JAR files. Each JAR
     * file found will be passed to the callback handler to be processed.
     *
     * @param context       The ServletContext - used to locate and access
     *                      WEB-INF/lib
     * @param classloader   The classloader - used to access JARs not in
     *                      WEB-INF/lib
     * @param callback      The handler to process any JARs found
     * @param jarsToSkip    List of JARs to ignore. If this list is null, a
     *                      default list will be read from the system property
     *                      defined by {@link Constants#SKIP_JARS_PROPERTY}
     */
    @Override
    public void scan(ServletContext context, ClassLoader classloader,
            JarScannerCallback callback, Set<String> jarsToSkip) {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("jarScan.webinflibStart"));
        }

        final Set<String> ignoredJars;
        if (jarsToSkip == null) {
            ignoredJars = defaultJarsToSkip;
        } else {
            ignoredJars = jarsToSkip;
        }

        // Scan WEB-INF/lib
        Set<String> dirList = context.getResourcePaths(Constants.WEB_INF_LIB);
        if (dirList != null) {
            for (String path : dirList) {
                if (path.endsWith(Constants.JAR_EXT) &&
                    !Matcher.matchName(ignoredJars,
                        path.substring(path.lastIndexOf('/')+1))) {
                    // Need to scan this JAR
                    if (log.isDebugEnabled()) {
                        log.debug(sm.getString("jarScan.webinflibJarScan", path));
                    }
                    URL url = null;
                    try {
                        // File URLs are always faster to work with so use them
                        // if available.
                        String realPath = context.getRealPath(path);
                        if (realPath == null) {
                            url = context.getResource(path);
                        } else {
                            url = (new File(realPath)).toURI().toURL();
                        }
                        process(callback, url);
                    } catch (IOException e) {
                        log.warn(sm.getString("jarScan.webinflibFail", url), e);
                    }
                } else {
                    if (log.isTraceEnabled()) {
                        log.trace(sm.getString("jarScan.webinflibJarNoScan", path));
                    }
                }
            }
        }

        // Scan the classpath
        if (scanClassPath && classloader != null) {
            doScanClassPath(classloader, callback, ignoredJars);
        }
    }


    protected void doScanClassPath(ClassLoader classloader, JarScannerCallback callback,
            Set<String> ignoredJars) {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("jarScan.classloaderStart"));
        }

        ClassLoader loader = classloader;

        ClassLoader stopLoader = null;
        if (!scanBootstrapClassPath) {
            // Stop when we reach the bootstrap class loader
            stopLoader = ClassLoader.getSystemClassLoader().getParent();
        }

        Set<URL> processedURLs = new HashSet<URL>();

        while (loader != null && loader != stopLoader) {
            if (loader instanceof URLClassLoader) {
                URL[] urls = ((URLClassLoader) loader).getURLs();
                for (int i=0; i<urls.length; i++) {
                    // Extract the jarName if there is one to be found
                    String jarName = getJarName(urls[i]);

                    // Skip JARs known not to be interesting and JARs
                    // in WEB-INF/lib we have already scanned
                    if (jarName != null &&
                        !(Matcher.matchName(ignoredJars, jarName) ||
                            urls[i].toString().contains(
                                    Constants.WEB_INF_LIB + jarName))) {
                        if (log.isDebugEnabled()) {
                            log.debug(sm.getString("jarScan.classloaderJarScan", urls[i]));
                        }
                        try {
                            process(callback, urls[i]);
                            processedURLs.add(urls[i]);
                        } catch (IOException ioe) {
                            log.warn(sm.getString("jarScan.classloaderFail",urls[i]), ioe);
                        }
                    } else {
                        if (log.isTraceEnabled()) {
                            log.trace(sm.getString("jarScan.classloaderJarNoScan", urls[i]));
                        }
                    }
                }
            }
            loader = loader.getParent();
        }

        if (JreCompat.isJre9Available()) {
            // The application and platform class loaders are not
            // instances of URLClassLoader. Use the class path in this
            // case.
            List<URL> urls = getClassPath();
            // Also add any modules
            Deque<URL> modulePathUrls = new LinkedList<URL>();
            JreCompat.getInstance().addBootModulePath(modulePathUrls);
            urls.addAll(modulePathUrls);
            // Process URLs
            for (URL url : urls) {
                if (!processedURLs.contains(url)) {
                    // Avoid duplicates
                    processedURLs.add(url);

                    // Extract the jarName if there is one to be found
                    String jarName = getJarName(url);
                    if (jarName != null && Matcher.matchName(ignoredJars, jarName)) {
                        continue;
                    }

                    try {
                        process(callback, url);
                    } catch (IOException ioe) {
                        log.warn(sm.getString("jarScan.classloaderFail",url), ioe);
                    }
                }
            }
        }

    }


    protected List<URL> getClassPath() {
        String classPath = System.getProperty("java.class.path");

        if (classPath == null || classPath.length() == 0) {
            return Collections.emptyList();
        }

        String[] classPathEntries = classPath.split(File.pathSeparator);
        List<URL> urls = new ArrayList<URL>(classPathEntries.length);
        for (String classPathEntry : classPathEntries) {
            File f = new File(classPathEntry);
            try {
                urls.add(f.toURI().toURL());
            } catch (MalformedURLException e) {
                log.warn(sm.getString("jarScan.classPath.badEntry", classPathEntry), e);
            }
        }

        return urls;
    }


    /*
     * Scan a URL for JARs with the optional extensions to look at all files
     * and all directories.
     */
    protected void process(JarScannerCallback callback, URL url)
            throws IOException {

        if (log.isTraceEnabled()) {
            log.trace(sm.getString("jarScan.jarUrlStart", url));
        }

        URLConnection conn = url.openConnection();
        if (conn instanceof JarURLConnection) {
            callback.scan((JarURLConnection) conn);
        } else {
            String urlStr = url.toString();
            if (urlStr.startsWith("file:") || urlStr.startsWith("jndi:") ||
                    urlStr.startsWith("http:") || urlStr.startsWith("https:")) {
                if (urlStr.endsWith(Constants.JAR_EXT)) {
                    URL jarURL = UriUtil.buildJarUrl(urlStr);
                    callback.scan((JarURLConnection) jarURL.openConnection());
                } else {
                    File f;
                    try {
                        f = new File(url.toURI());
                        if (f.isFile() && scanAllFiles) {
                            // Treat this file as a JAR
                            URL jarURL = UriUtil.buildJarUrl(f);
                            callback.scan((JarURLConnection) jarURL.openConnection());
                        } else if (f.isDirectory() && scanAllDirectories) {
                            File metainf = new File(f.getAbsoluteFile() +
                                    File.separator + "META-INF");
                            if (metainf.isDirectory()) {
                                callback.scan(f);
                            }
                        }
                    } catch (Throwable t) {
                        ExceptionUtils.handleThrowable(t);
                        // Wrap the exception and re-throw
                        IOException ioe = new IOException();
                        ioe.initCause(t);
                        throw ioe;
                    }
                }
            }
        }

    }

    /*
     * Extract the JAR name, if present, from a URL
     */
    private String getJarName(URL url) {

        String name = null;

        String path = url.getPath();
        int end = path.indexOf(Constants.JAR_EXT);
        if (end != -1) {
            int start = path.lastIndexOf('/', end);
            name = path.substring(start + 1, end + 4);
        } else if (isScanAllDirectories()){
            int start = path.lastIndexOf('/');
            name = path.substring(start + 1);
        }

        return name;
    }

}
