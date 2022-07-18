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

package org.apache.tomcat.util.http.mapper;

import org.apache.tomcat.util.buf.MessageBytes;

/**
 * Mapping data.
 *
 * @author Remy Maucherat
 */
public class MappingData {

    public Object host = null;          // 匹配Host
    public Object context = null;   // 匹配的Context
    public int contextSlashCount = 0;   // Context 路径中的"/"数量
    // 对于contexts 属性，主要使用于多版本Web应用同时部署的情况，此时，可以匹配请求路径的Context存在多个，需要进一步的处理，而
    // Context属性始终存放的匹配请求路径的最新版本（注意），匹配请求的最新版本并不代表的是最后匹配结果，具体参见算法讲解
    public Object[] contexts = null;       // 匹配的Context列表，只用于匹配过程，并非最终使用结果 ，
    public Object wrapper = null;       // 匹配的wrapper
    public boolean jspWildCard = false; // 对于JspServlet，其对应的匹配pattern是否包含通配符

    public MessageBytes contextPath = MessageBytes.newInstance();       //Context 路径
    public MessageBytes requestPath = MessageBytes.newInstance();   // 相对于Context 的请求路径
    public MessageBytes wrapperPath = MessageBytes.newInstance();   // Servlet路径
    public MessageBytes pathInfo = MessageBytes.newInstance();  // 相对于Servlet 的请求路径
    public MessageBytes redirectPath = MessageBytes.newInstance();      // 重定向路径

    public void recycle() {
        host = null;
        context = null;
        contextSlashCount = 0;
        contexts = null;
        wrapper = null;
        jspWildCard = false;
        contextPath.recycle();
        requestPath.recycle();
        wrapperPath.recycle();
        pathInfo.recycle();
        redirectPath.recycle();
    }

}
