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

import com.luban.transformlet.JavassistTransformlet;
import com.luban.transformlet.TtlTransformer;
import com.luban.transformlet.TtlVariableTransformlet;
import org.apache.catalina.LifecycleException;

import java.io.File;
import java.lang.instrument.ClassFileTransformer;
import java.util.*;

/**
 * A WebappLoader that allows a customized classpath to be added
 * through configuration in context xml. Any additional classpath entry will be
 * added to the default webapp classpath, making easy to emulate a standard
 * webapp without the need for assembly all the webapp dependencies as jars in
 * WEB-INF/lib.
 *
 * <pre>
 * &lt;Context docBase="\webapps\mydocbase"&gt;
 *   &lt;Loader className="org.apache.catalina.loader.VirtualWebappLoader"
 *              virtualClasspath="/dir/classes;/somedir/somejar.jar;
 *                /somedir/*.jar"/&gt;
 * &lt;/Context&gt;
 * </pre>
 *
 * <p>The <code>*.jar</code> suffix can be used to include all JAR files in a
 * certain directory. If a file or a directory does not exist, it will be
 * skipped.
 * </p>
 *
 *
 * @author Fabrizio Giustina
 */
public class MyVirtualWebappLoader extends WebappLoader {

    @Override
    protected void startInternal() throws LifecycleException {

        super.startInternal();


        try {
            final List<Class<? extends JavassistTransformlet>> transformletList = new ArrayList<Class<? extends JavassistTransformlet>>();
            //添加 my Transformlet
            transformletList.add(TtlVariableTransformlet.class);
            final ClassFileTransformer transformer = new TtlTransformer(transformletList);
            WebappClassLoader webappClassLoader = (WebappClassLoader)getClassLoader();
            webappClassLoader.addTransformer(transformer);
        } catch (Exception e) {
            e.printStackTrace();
        }
    }

}
