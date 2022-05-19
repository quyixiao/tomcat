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
package org.apache.catalina;

import java.io.IOException;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServletResponse;

import org.apache.catalina.connector.Request;
import org.apache.catalina.deploy.LoginConfig;


/**
 * An <b>Authenticator</b> is a component (usually a Valve or Container) that
 * provides some sort of authentication service.
 *
 * @author Craig R. McClanahan
 * org.apache.catalina.Authenticator 接口用来表示一个验证器，该方法的接口并没有方法，只是一个组件的标识器，这样就能使用instanceof
 * 来检查一个组件是否为验证器。
 * Catalina 提供了Authenticator接口的基本实现。org.apache.catalina.authenticator.AuthenticatorBase类，除了实现Authenticator接口外。
 * AuthenticatorBase还继承了org.apache.catalina.values.ValueBase类，这就是说authenticatorBase也是一个阀门，可以在org.apache.catalina.authenticator
 * 包中找到该接口的几个类。BasicAuthenticator用于基本的验证，FormAuthenticator用于基本的静音验证，DigestAuthentication用于摘要（digest）验证。
 * SSLauthenticator用于SSL验证，NonLoginAuthenticator用于Tomcat 没有指定验证元素的时候，NonLoginAuthenticator类表示只是检查安全限制的
 * 验证器，但是不进行用户验证。
 * org.apache.catalina.authenticator
 * 一个验证器的主要工作就是验证用户，因此，AuthenticatorBase 类的invoke方法调用了抽象方法authenticate ，这个方法的具体实现由子类来完成 。
 * 在BasicAuthenticator中，它authenticate使用基本验证器来验证用户 。
 *
 */
public interface Authenticator {

    /**
     * Authenticate the user making this request, based on the login
     * configuration of the {@link Context} with which this Authenticator is
     * associated.
     *
     * @param request Request we are processing
     * @param response Response we are populating
     *
     * @return <code>true</code> if any specified constraints have been
     *         satisfied, or <code>false</code> if one more constraints were not
     *         satisfied (in which case an authentication challenge will have
     *         been written to the response).
     *
     * @exception IOException if an input/output error occurs
     */
    public boolean authenticate(Request request, HttpServletResponse response)
            throws IOException;

    /**
     * Authenticate the user making this request, based on the specified
     * login configuration.
     *
     * @param request Request we are processing
     * @param response Response we are populating
     * @param config    Login configuration describing how authentication
     *              should be performed
     *
     * @return <code>true</code> if any specified constraints have been
     *         satisfied, or <code>false</code> if one more constraints were not
     *         satisfied (in which case an authentication challenge will have
     *         been written to the response).
     *
     * @exception IOException if an input/output error occurs
     *
     * @deprecated  Use {@link #authenticate(Request, HttpServletResponse)}.
     *              This will be removed / have reduced visibility in Tomcat
     *              8.0.x
     */
    @Deprecated
    public boolean authenticate(Request request, HttpServletResponse response,
            LoginConfig config) throws IOException;

    public void login(String userName, String password, Request request)
            throws ServletException;

    public void logout(Request request) throws ServletException;
}
