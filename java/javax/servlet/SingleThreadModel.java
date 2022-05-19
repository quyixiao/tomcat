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

/**
 * Ensures that servlets handle only one request at a time. This interface has
 * no methods.
 * <p>
 * If a servlet implements this interface, you are <i>guaranteed</i> that no two
 * threads will execute concurrently in the servlet's <code>service</code>
 * method. The servlet container can make this guarantee by synchronizing access
 * to a single instance of the servlet, or by maintaining a pool of servlet
 * instances and dispatching each new request to a free servlet.
 * <p>
 * Note that SingleThreadModel does not solve all thread safety issues. For
 * example, session attributes and static variables can still be accessed by
 * multiple requests on multiple threads at the same time, even when
 * SingleThreadModel servlets are used. It is recommended that a developer take
 * other means to resolve those issues instead of implementing this interface,
 * such as avoiding the usage of an instance variable or synchronizing the block
 * of the code accessing those resources. This interface is deprecated in
 * Servlet API version 2.4.
 *
 * @author Various
 * @deprecated As of Java Servlet API 2.4, with no direct replacement.
 */
@SuppressWarnings("dep-ann")
// Spec API does not use @Deprecated
// 一个servlet 可以实现javax.servlet.SingleThreadModel 接口，实现此接口的一个servlet 通俗称为SingleThreadModel (STM)的程序组件 。
// 根据Servlet规范，实现此接口的目的是保证servlet 一次只能有一个请求，Servlet 2.4 规范的第SRV.14.2.24 节 (Servlet 2.3 的有
// SingleThreadModel 接口上的类似说明 )
// 如果一个servlet实现了此接口，将保证不会有两个线程同使用servlet 的service方法，servlet 容器可以保证同步进入一个servlet 的一个实例。
// Servlet实例的处理每个新请求，该接口并不能避免同步而产生的问题，如访问静态类变量或该servlet 以外的类或变量 。
// 很多的程序员并没有仔细阅读它，只是认为实现了SingleThreadModel 就能保证它们的servlet 是线程安全的，显然并非如此，重新阅读上面的引文
// 一个servlet 实现SignleThreadModel 之后确保能保证它的service 方法不会被两个线程同时使用，为了提高servlet 容器的性能，可以创建STM
// servlet 多个实例。
//
public interface SingleThreadModel {
    // No methods
}
