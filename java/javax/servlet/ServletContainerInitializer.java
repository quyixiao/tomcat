/*
* Licensed to the Apache Software Foundation (ASF) under one or more
* contributor license agreements.  See the NOTICE file distributed with
* this work for additional information regarding copyright ownership.
* The ASF licenses this file to You under the Apache License, Version 2.0
* (the "License"); you may not use this file except in compliance with
* the License.  You may obtain a copy of the License at
*
*     http://www.apache.org/licenses/LICENSE-2.0
*
* Unless required by applicable law or agreed to in writing, software
* distributed under the License is distributed on an "AS IS" BASIS,
* WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
* See the License for the specific language governing permissions and
* limitations under the License.
*/
package javax.servlet;

import java.util.Set;

/**
 * ServletContainerInitializers (SCIs) are registered via an entry in the
 * file META-INF/services/javax.servlet.ServletContainerInitializer that must be
 * included in the JAR file that contains the SCI implementation.
 * <p>
 * SCI processing is performed regardless of the setting of metadata-complete.
 * SCI processing can be controlled per JAR file via fragment ordering. If
 * absolute ordering is defined, then only the JARs included in the ordering
 * will be processed for SCIs. To disable SCI processing completely, an empty
 * absolute ordering may be defined.
 * <p>
 * SCIs register an interest in annotations (class, method or field) and/or
 * types via the {@link javax.servlet.annotation.HandlesTypes} annotation which
 * is added to the class.
 *
 * @since Servlet 3.0
 *  在Web 容器启动时为了让第三方组件机做一些初始化工作，例如 注册Servlet 或者 Filters 等，Servlet规范中通过 ServletContainerInitializer
 *  实现此功能，每个框架要使用ServletContainerInitializer 就必须在对应的jar 包的META-INF/services 目录下创建一个名为javax.servlet.ServletContainerInitializer
 *  文件，文件的内容指定具体的ServiceContainerInitializer的实现类 ，于是 ，当Web 容器启动时，就会运行这个初始化器做一些组件内的初始化工作 。
 *
 */
public interface ServletContainerInitializer {

    /**
     * Receives notification during startup of a web application of the classes
     * within the web application that matched the criteria defined via the
     * {@link javax.servlet.annotation.HandlesTypes} annotation.
     *
     * @param c     The (possibly null) set of classes that met the specified
     *              criteria
     * @param ctx   The ServletContext of the web application in which the
     *              classes were discovered
     *
     * @throws ServletException If an error occurs
     */
    void onStartup(Set<Class<?>> c, ServletContext ctx) throws ServletException;
}
