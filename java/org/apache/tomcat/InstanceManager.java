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
package org.apache.tomcat;

import java.lang.reflect.InvocationTargetException;

import javax.naming.NamingException;


/***
 * Context 容器中包含了一个实例管理器，它主要的作用就是实现对Context 容器的中监听器，过滤器以及Servlet等实例管理，其中包括根据监听器
 * Class对其进行实例化，对它们的Class 的注解进行解析并处理，对它们的Class 实例化的访问权限的限制，销毁前统一调用preDestory等方法 。
 *
 * 实例管理器的实现其实很简单，其中就是用一些反射机制实例化对象，但这里需要注意的地方是，InstanceManager 包含了两个类加载器。一个是属于
 * Tomcat 容器内部类加载器，另外一个是Web 应用的类加载器，Tomcat 容器类加载器是Web 应用类加载器的父类加载器，且Tomcat 容器类加载器
 * 在Tomcat整个生命周期中都存在，而Web 应用类加载器则不同，它可能在重启后则被丢弃，最终被GC 回收掉。
 *
 *
 *
 * 所以，由不同的类回城akkd的类也会有不同的生命周期，于是，实例管理器中的loadClass 方法中会有类似的如下判断 。
 *
 * protected Class <?> loadClass(String className,ClassLoader classLoader) throws ClassNotFoundException{
 *      if(className.startsWith("org.apache.catalina")){
 *          return containerClassLoader.loadClass(className);
 *      }else{
 *         return webClassLoader.loadClass(className);
 *      }
 * }
 * 判断需要实例化的Class 是否属于org.apache.catalina 包下的类，如果属于则使用Tomcat 容器的类加载器加载，这个类会在Tomcaat整个生命周期
 * 中存在内存中，否则会使用Web为加载器加载 。
 *
 */
public interface InstanceManager {

    Object newInstance(Class<?> clazz) throws IllegalAccessException, InvocationTargetException,
            NamingException, InstantiationException, IllegalArgumentException,
            NoSuchMethodException, SecurityException;

    Object newInstance(String className) throws IllegalAccessException, InvocationTargetException,
            NamingException, InstantiationException, ClassNotFoundException,
            IllegalArgumentException, NoSuchMethodException, SecurityException;

    Object newInstance(String fqcn, ClassLoader classLoader) throws IllegalAccessException,
            InvocationTargetException, NamingException, InstantiationException,
            ClassNotFoundException, IllegalArgumentException, NoSuchMethodException,
            SecurityException;

    void newInstance(Object o)
            throws IllegalAccessException, InvocationTargetException, NamingException;

    void destroyInstance(Object o) throws IllegalAccessException, InvocationTargetException;
}
