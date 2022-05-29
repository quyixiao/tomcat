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


package org.apache.catalina.deploy;


import java.io.Serializable;
import java.util.HashMap;
import java.util.Map;

import javax.servlet.Filter;

import org.apache.tomcat.util.res.StringManager;


/**
 * Representation of a filter definition for a web application, as represented
 * in a <code>&lt;filter&gt;</code> element in the deployment descriptor.
 *
 * @author Craig R. McClanahan
 * org.apache.catalina.deploy.FilterDef 表示一个过滤器定义，就像是在部署 文件中定义一个过滤器元素那样
 * FilterDef 类中的每一个属性都代表一个可以在过滤器中出现的子元素。该类包 括一个 Map 类型的变量表示一个包含所有初始参数的 Map。
 * 方法 addInitParameer 添加一个 name/value 对到该 Map。
 *
 * 过滤器提供了为某个Web 应用的所有请求和响应做统一的逻辑处理的功能，如图9.4 所示 。客户端发起请求后，服务器将请求转到对应的Web应用的web1上
 * ，过滤器filter1 和filter2 对应的请求和响应进行处理后返回响应给客户端
 * Servlet 规范中规定需要提供过滤器的功能，允许Web 容器对请求和响应做统一的处理，因为每个Context 容器对应一个Web应用，所以Tomcat
 * 中的过滤器及其相关配置保存在Context 容器中最适合的，也就是说，每个Context 可能包含若干个过滤器，一个简单的典型的Filter配置如下 。
 * <filter>
 *     <filter-name>EcodingFilter</filter-name>
 *     <filter-class> com.test.EncodeFilter</filter-class>
 *     <init-param>
 *         <param-name>EncodeCoding</param-name>
 *         <param-value>UTF-8</param-value>
 *     </init-param>
 * </filter>
 * <filter-mapping>
 *     <filter-name>EcodingFilter</filter-name>
 *     <url-pattern>*</url-pattern>
 * </filter-mapping>
 *
 * 配置主要是配置过滤器的名称，过滤器的类，初始化参数以及过滤器的映射路径，下面介绍Context 容器如何实现过滤器的功能 。
 *
 * FilterDef
 * FilterDef 用于描述过滤器的定义，它其实对应的是Web部署描述符配置的Filter 元素，如FilterDef对象包含FilterClass ，FilterName,paramters
 * 等属性，它们的值对应的是web.xml文件 中的Filter元素的<filter-name>,<filter-class>,<init-param>子元素的值，Web 应用启动解析web.xml
 * 时，将元素Filter 元素转换成FilterDef实例对象  。
 *
 *
 */
public class FilterDef implements Serializable {

    private static final long serialVersionUID = 1L;

    private static final StringManager sm =
        StringManager.getManager(Constants.Package);

    // ------------------------------------------------------------- Properties


    /**
     * The description of this filter.
     */
    private String description = null;

    public String getDescription() {
        return (this.description);
    }

    public void setDescription(String description) {
        this.description = description;
    }


    /**
     * The display name of this filter.
     */
    private String displayName = null;

    public String getDisplayName() {
        return (this.displayName);
    }

    public void setDisplayName(String displayName) {
        this.displayName = displayName;
    }


    /**
     * The filter instance associated with this definition
     */
    private transient Filter filter = null;

    public Filter getFilter() {
        return filter;
    }

    public void setFilter(Filter filter) {
        this.filter = filter;
    }


    /**
     * The fully qualified name of the Java class that implements this filter.
     */
    private String filterClass = null;

    public String getFilterClass() {
        return (this.filterClass);
    }

    public void setFilterClass(String filterClass) {
        this.filterClass = filterClass;
    }


    /**
     * The name of this filter, which must be unique among the filters
     * defined for a particular web application.
     */
    private String filterName = null;

    public String getFilterName() {
        return (this.filterName);
    }

    public void setFilterName(String filterName) {
        if (filterName == null || filterName.equals("")) {
            throw new IllegalArgumentException(
                    sm.getString("filterDef.invalidFilterName", filterName));
        }
        this.filterName = filterName;
    }


    /**
     * The large icon associated with this filter.
     */
    private String largeIcon = null;

    public String getLargeIcon() {
        return (this.largeIcon);
    }

    public void setLargeIcon(String largeIcon) {
        this.largeIcon = largeIcon;
    }


    /**
     * The set of initialization parameters for this filter, keyed by
     * parameter name.
     */
    private Map<String, String> parameters = new HashMap<String, String>();

    public Map<String, String> getParameterMap() {

        return (this.parameters);

    }


    /**
     * The small icon associated with this filter.
     */
    private String smallIcon = null;

    public String getSmallIcon() {
        return (this.smallIcon);
    }

    public void setSmallIcon(String smallIcon) {
        this.smallIcon = smallIcon;
    }

    private String asyncSupported = null;

    public String getAsyncSupported() {
        return asyncSupported;
    }

    public void setAsyncSupported(String asyncSupported) {
        this.asyncSupported = asyncSupported;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Add an initialization parameter to the set of parameters associated
     * with this filter.
     *
     * @param name The initialization parameter name
     * @param value The initialization parameter value
     */
    public void addInitParameter(String name, String value) {

        if (parameters.containsKey(name)) {
            // The spec does not define this but the TCK expects the first
            // definition to take precedence
            return;
        }
        parameters.put(name, value);

    }


    /**
     * Render a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("FilterDef[");
        sb.append("filterName=");
        sb.append(this.filterName);
        sb.append(", filterClass=");
        sb.append(this.filterClass);
        sb.append("]");
        return (sb.toString());

    }


}
