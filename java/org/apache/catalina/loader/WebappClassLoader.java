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
package org.apache.catalina.loader;

import org.apache.catalina.LifecycleException;


/***
 * WebappClassLoader 类加载器是如何 达到互相隔离和实现重新加载的呢？
 * WebappClassLoader 并没有遵循双亲委派机制，而是按自己的策略顺序加载类，根据委托标识，加载分为两种。
 *
 * 1. 当委托标识delegate 为false时，WebappClassLoader 类加载器首先尝试从本地缓存中查找加载该类，然后用System类加载器尝试加载类。
 * 接着由自己的尝试加载类，最后才是由父类加载器（Common）器尝试加载，所以此时它搜索的目录顺序是<JAVA_HOME>/jre/lib -> <JAVA_HOME/jre/lib/ext
 * -> CLASSPATH->/WEB-INF/classes -> /WEB-INF/lib -> $CATALINA_BASE/lib 和 CATALINA_HOME/lib
 * 2. 当委托标识delegate为true时，WebappClassLoader 类加载器首先尝试从本地缓存中查找加载该类，然后用System类加载器尝试加载类，接着由父类 加载器
 * （Common）尝试加载类，最后才由自己的尝试加载，所以此时它的搜索目录顺序为<JAVA_HOME>/jre/lib-><JAVA_HOME>/jre/lib/ext -> CLASSPATH
 * ->$CATALINA_BASE/lib 和$CATALINA_HOME/lib -> /WEB-INF/classes->/WEB-INF/lib
 *
 * 3. WebappClassLoader 和其他类加载器的关系结构图，可以看出，对于公共资源可以共享，而属于Web 应用的资源则通过类加载器进行隔离，对于重新加载的实现
 * 也比较清晰，只需要重新实例化一个WebappClassLoader 对象并把原来的WebappLoader中旧的转换掉即可完成重新加载的功能，转换掉被GC回收。
 *
 */
public class WebappClassLoader extends WebappClassLoaderBase {

    public WebappClassLoader() {
        super();
    }


    public WebappClassLoader(ClassLoader parent) {
        super(parent);
    }


    /**
     * Returns a copy of this class loader without any class file
     * transformers. This is a tool often used by Java Persistence API
     * providers to inspect entity classes in the absence of any
     * instrumentation, something that can't be guaranteed within the
     * context of a {@link java.lang.instrument.ClassFileTransformer}'s
     * {@link java.lang.instrument.ClassFileTransformer#transform(ClassLoader,
     * String, Class, java.security.ProtectionDomain, byte[]) transform} method.
     * <p>
     * The returned class loader's resource cache will have been cleared
     * so that classes already instrumented will not be retained or
     * returned.
     *
     * @return the transformer-free copy of this class loader.
     */
    @Override
    public WebappClassLoader copyWithoutTransformers() {

        WebappClassLoader result = new WebappClassLoader(getParent());

        super.copyStateWithoutTransformers(result);

        try {
            result.start();
        } catch (LifecycleException e) {
            throw new IllegalStateException(e);
        }

        return result;
    }
}
