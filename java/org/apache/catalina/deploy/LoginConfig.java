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
 * Representation of a login configuration element for a web application,
 * as represented in a <code>&lt;login-config&gt;</code> element in the
 * deployment descriptor.
 *
 * @author Craig R. McClanahan
 * 一个login configuration 包括一个域名 ，用org.apache.catalina.deploy.LoginConfig 类表示 ， LoginConfig 的实例封装了域名和验证要
 * 用到的方法，可以使用LoginConfig实例的getRealmName方法来获得域名 ， 可以使用getAuthName方法来验证用户 一个验证（authentication）的名字必须是
 * 下面的之一，BASIC ，DIGEST，FROM ，o 或者 CLIENT-CERT ，如果用到的是基于表单（form）的验证，该LoginConfig对象还包括登录或者错误的页面
 * 像对应的URL。
 * Tomcat 一个部署启动的时候，先读取web.xml ，如果web.xml包括一个login-config元素，Tomcat 创建一个LoginConfig对象并相应的设置它的属性。
 * 验证阀门调用LoginConfig的getRealmName方法并将域名发送给浏览器显示登录表单，如果getRealName名字返回的值为null,则发送给浏览器服务器的名字 。
 * 和端口名 。
 *
 * 安装Authenticator阀门
 * 在部署文件中，只能出现一个login-config 元素，login-config 元素包括了auth-method 元素用于定义验证方法，也就是说一个上下文容器只能有一个
 * LoginConfig 对象来使用authentication 的实现类  。
 * AuthenticatorBase的子类在上下文中被作为验证阀门，这个依赖于部署文件中的auth-method 元素的值，表10.1 为auth-method 元素的值，可以用于
 * 确定验证器。
 *
 */
public class LoginConfig implements Serializable {


    private static final long serialVersionUID = 1L;


    // ----------------------------------------------------------- Constructors


    /**
     * Construct a new LoginConfig with default properties.
     */
    public LoginConfig() {

        super();

    }


    /**
     * Construct a new LoginConfig with the specified properties.
     *
     * @param authMethod The authentication method
     * @param realmName The realm name
     * @param loginPage The login page URI
     * @param errorPage The error page URI
     */
    public LoginConfig(String authMethod, String realmName,
                       String loginPage, String errorPage) {

        super();
        setAuthMethod(authMethod);
        setRealmName(realmName);
        setLoginPage(loginPage);
        setErrorPage(errorPage);

    }


    // ------------------------------------------------------------- Properties


    /**
     * The authentication method to use for application login.  Must be
     * BASIC, DIGEST, FORM, or CLIENT-CERT.
     */
    private String authMethod = null;

    public String getAuthMethod() {
        return (this.authMethod);
    }

    public void setAuthMethod(String authMethod) {
        this.authMethod = authMethod;
    }


    /**
     * The context-relative URI of the error page for form login.
     */
    private String errorPage = null;

    public String getErrorPage() {
        return (this.errorPage);
    }

    public void setErrorPage(String errorPage) {
        //        if ((errorPage == null) || !errorPage.startsWith("/"))
        //            throw new IllegalArgumentException
        //                ("Error Page resource path must start with a '/'");
        this.errorPage = RequestUtil.URLDecode(errorPage);
    }


    /**
     * The context-relative URI of the login page for form login.
     */
    private String loginPage = null;

    public String getLoginPage() {
        return (this.loginPage);
    }

    public void setLoginPage(String loginPage) {
        //        if ((loginPage == null) || !loginPage.startsWith("/"))
        //            throw new IllegalArgumentException
        //                ("Login Page resource path must start with a '/'");
        this.loginPage = RequestUtil.URLDecode(loginPage);
    }


    /**
     * The realm name used when challenging the user for authentication
     * credentials.
     */
    private String realmName = null;

    public String getRealmName() {
        return (this.realmName);
    }

    public void setRealmName(String realmName) {
        this.realmName = realmName;
    }


    // --------------------------------------------------------- Public Methods


    /**
     * Return a String representation of this object.
     */
    @Override
    public String toString() {

        StringBuilder sb = new StringBuilder("LoginConfig[");
        sb.append("authMethod=");
        sb.append(authMethod);
        if (realmName != null) {
            sb.append(", realmName=");
            sb.append(realmName);
        }
        if (loginPage != null) {
            sb.append(", loginPage=");
            sb.append(loginPage);
        }
        if (errorPage != null) {
            sb.append(", errorPage=");
            sb.append(errorPage);
        }
        sb.append("]");
        return (sb.toString());

    }


    /* (non-Javadoc)
     * @see java.lang.Object#hashCode()
     */
    @Override
    public int hashCode() {
        final int prime = 31;
        int result = 1;
        result = prime * result
                + ((authMethod == null) ? 0 : authMethod.hashCode());
        result = prime * result
                + ((errorPage == null) ? 0 : errorPage.hashCode());
        result = prime * result
                + ((loginPage == null) ? 0 : loginPage.hashCode());
        result = prime * result
                + ((realmName == null) ? 0 : realmName.hashCode());
        return result;
    }


    /* (non-Javadoc)
     * @see java.lang.Object#equals(java.lang.Object)
     */
    @Override
    public boolean equals(Object obj) {
        if (this == obj)
            return true;
        if (!(obj instanceof LoginConfig))
            return false;
        LoginConfig other = (LoginConfig) obj;
        if (authMethod == null) {
            if (other.authMethod != null)
                return false;
        } else if (!authMethod.equals(other.authMethod))
            return false;
        if (errorPage == null) {
            if (other.errorPage != null)
                return false;
        } else if (!errorPage.equals(other.errorPage))
            return false;
        if (loginPage == null) {
            if (other.loginPage != null)
                return false;
        } else if (!loginPage.equals(other.loginPage))
            return false;
        if (realmName == null) {
            if (other.realmName != null)
                return false;
        } else if (!realmName.equals(other.realmName))
            return false;
        return true;
    }


}
