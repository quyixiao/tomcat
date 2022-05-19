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


package org.apache.catalina.core;


import java.util.Enumeration;

import javax.servlet.ServletConfig;
import javax.servlet.ServletContext;


/**
 * Facade for the <b>StandardWrapper</b> object.
 *
 * @author Remy Maucherat
 * StandardWrapper 调用它价值的 Servlet 的 init 方法。该方法需要一个 javax.servlet.ServletConfig 的参数，而 StandardWrapper
 * 类自己就实现了 ServletConfig 接口。所以，理论上 StandardWrapper 可以将它自己作为参数传 递给 init 方法。但是 StandardWrapper
 * 需要对 Servlet 隐藏他的大多数 public 方法。为了实现这一点，StandardWraper 将它自己包装的一个 StandardWrapperFacade 实例中
 */
public final class StandardWrapperFacade
    implements ServletConfig {


    // ----------------------------------------------------------- Constructors


    /**
     * Create a new facade around a StandardWrapper.
     */
    public StandardWrapperFacade(StandardWrapper config) {

        super();
        this.config = config;

    }


    // ----------------------------------------------------- Instance Variables


    /**
     * Wrapped config.
     */
    private ServletConfig config = null;


    /**
     * Wrapped context (facade).
     */
    private ServletContext context = null;


    // -------------------------------------------------- ServletConfig Methods


    @Override
    public String getServletName() {
        return config.getServletName();
    }


    @Override
    // Gauge 方法调用 StandardWrapper 类的 getServletContext 方法，但是它返回一 个 ServletContext 的外观对象，而不是 ServletContext 对象自己。
    public ServletContext getServletContext() {
        if (context == null) {
            context = config.getServletContext();
            if ((context != null) && (context instanceof ApplicationContext))
                context = ((ApplicationContext) context).getFacade();
        }
        return (context);
    }


    @Override
    public String getInitParameter(String name) {
        return config.getInitParameter(name);
    }


    @Override
    public Enumeration<String> getInitParameterNames() {
        return config.getInitParameterNames();
    }


}
