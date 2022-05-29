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

import org.apache.catalina.util.RequestUtil;


/**
 * Representation of an error page element for a web application,
 * as represented in a <code>&lt;error-page&gt;</code> element in the
 * deployment descriptor.
 *
 * @author Craig R. McClanahan
 * <error-page>
 *     <error-code>404</error-code>
 *     <location>/WEB-INF/404.html</location>
 * </error-page>
 * 或
 * <error-page>
 *     <excetion-type>java.lang.NullPointException</excetion-type>
 *     <location>/WEB-INF/nullPointException.html</location>
 * </error-page>
 *
 * 第一个配置表示，Web 容器处理过程中，当错误编码为404时，向客户端展示/WEB-INF/404.html 页面，第二个配置表示，处理过程中，当发生
 * NullPointException 异常时，向客户端展示 /WEB-INF/nullPointException.html页面 。
 *
 * 在Web 应用启动过程中，会将web.xml中配置的error-page元素读取到Context 容器中，并以ErrorPage 对象的形式存在，ErrorPage 类包含三个
 * 属性，errorCode ,ExceptionType 和location ，刚好对应web.xml 中的error-page元素 。
 *
 * 实际上Tomcat 对整个请求处理过程都在不同级别的管道中流转，而对错误页面的处理其实是在StandardHostValue 阀门中，它调用对应的Context
 * 容器对请求处理后，根据请求对象的响应码，判断是否需要返回对应的错误页面，同时还根据处理过程中发生的异常寻找对应的错误页面，这样就实现了
 * Servlet 规范中的错误页面的功能 。
 *
 */
public class ErrorPage implements Serializable {

    private static final long serialVersionUID = 1L;

    // ----------------------------------------------------- Instance Variables


    /**
     * The error (status) code for which this error page is active. Note that
     * status code 0 is used for the default error page.
     */
    private int errorCode = 0;


    /**
     * The exception type for which this error page is active.
     */
    private String exceptionType = null;


    /**
     * The context-relative location to handle this error or exception.
     */
    private String location = null;


    // ------------------------------------------------------------- Properties


    /**
     * Return the error code.
     */
    public int getErrorCode() {

        return (this.errorCode);

    }


    /**
     * Set the error code.
     *
     * @param errorCode The new error code
     */
    public void setErrorCode(int errorCode) {

        this.errorCode = errorCode;

    }


    /**
     * Set the error code (hack for default XmlMapper data type).
     *
     * @param errorCode The new error code
     */
    public void setErrorCode(String errorCode) {

        try {
            this.errorCode = Integer.parseInt(errorCode);
        } catch (NumberFormatException nfe) {
            throw new IllegalArgumentException(nfe);
        }
    }


    /**
     * Return the exception type.
     */
    public String getExceptionType() {

        return (this.exceptionType);

    }


    /**
     * Set the exception type.
     *
     * @param exceptionType The new exception type
     */
    public void setExceptionType(String exceptionType) {

        this.exceptionType = exceptionType;

    }


    /**
     * Return the location.
     */
    public String getLocation() {

        return (this.location);

    }


    /**
     * Set the location.
     *
     * @param location The new location
     */
    public void setLocation(String location) {

        //        if ((location == null) || !location.startsWith("/"))
        //            throw new IllegalArgumentException
        //                ("Error Page Location must start with a '/'");
        this.location = RequestUtil.URLDecode(location);

    }


    // --------------------------------------------------------- Public Methods


    /**
     * Render a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("ErrorPage[");
        if (exceptionType == null) {
            sb.append("errorCode=");
            sb.append(errorCode);
        } else {
            sb.append("exceptionType=");
            sb.append(exceptionType);
        }
        sb.append(", location=");
        sb.append(location);
        sb.append("]");
        return (sb.toString());

    }

    public String getName() {
        if (exceptionType == null) {
            return Integer.toString(errorCode);
        } else {
            return exceptionType;
        }
    }

}
