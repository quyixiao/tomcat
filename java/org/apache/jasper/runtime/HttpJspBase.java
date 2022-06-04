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

package org.apache.jasper.runtime;

import java.io.IOException;

import javax.servlet.ServletConfig;
import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.jsp.HttpJspPage;

import org.apache.jasper.compiler.Localizer;

/**
 * This is the super class of all JSP-generated servlets.
 *
 * @author Anil K. Vijendran
 *
 *
 *  经过前面的语法解析及使用访问者模式把HelloWord.jsp 文件编译成相应HelloWord_jsp.java文件，可以看到，Servlet 类名由JSP 文件名加_jsp
 *  拼成，下面看HelloWord_jsp.java 文件的详细内容 ， 类包名默认为org.apache.jsp ,默认有三个导入import ，javax.servlet.* import javax.servlet.http.*
 *  ,import javax.servlet.jsp.* ;
 *
 *  接下来，是真正的类主体，JSP生成Java 类都必须继承org.apache.jasper.runtime.HttpJspBase ， 这个类的结构图如图16.2所示 。
 *  它继承了HttpServlet 是为了将HttpServlet的所有功能都继承下来，另外，又实现了HttpJspPage接口，定义了一个JSP 类的Servlet 的核心处理方法
 *  _jspService ，除此之外，还有_jspInit 和_jspDestory ，它们用于在JSP 初始化和销毁时执行，这些方法其实都是由Servlet 的service ,
 *  init ，destory 方法间接调用，所以JSP 生成Servlet 主要就是实现这三个方法 。
 *
 *  除了继承HttpJspBase外，还须实现org.apache.jasper.runtime，JSPSourceDependent 接口，这个接口只有一个返回Map<String,Long>
 *       类型的getDependants ()方法，Map 键值分别为资源名和最后修改时间 ，这个实现主要是为了记录某些依赖资源是否过时 ， 依赖资源可能是Page
 *       指令导入的，也可能是标签文件引用等，在生成 Servlet 时，如果Jsp 页面中存在上述依赖，则会在Servlet类中添加一个Staic 块，Static
 *       块会把资源及最后修改时间添加到Map 中。
 *
 *
 * 在JSP 类型的Servlet 处理过程中会依赖很多的资源 ， 比如，如果要操作的话，就需要访问HttpSession 对象，如果要操作Context 容器级别的对象
 * ，就要ServletContext 对象，如果要获取Servlet配置信息，就要ServletConfig 对象，最后，还需要输出一个对象用于在处理对象其实非常简单。
 * 这些对象都在核心方法_jspService方法中使用。作为Servlet类，要获取这些对象其实非常简单，因为这些对象本身属于Servlet属性，它们有相关的方法可供
 * 直接获取，但是这里因为JSP 有自己的标准，所以必须按它的标准去实现。
 *
 * 具体的JSP 标准是怎样的呢？首先，为了方便 JSP的实现， 提供了一个统一的工厂类JspFactory 用于获取不同的资源，其实，由于按照标准规定
 * 不能直接使用Servlet上下文，因此需要定义一个PageContext 类封装Servlet 上下文，最后，同样按照标准需要定义一个输出类JspWriter 封装Servlet
 * 的输出 ，所以可以看到，PageContext 对象通过JspFactory 获取，其他的ServletContext对象，ServletConfig 对象，HttpSession 对象及
 * JspWriter 则通过PageContext 对象获取，通过这些对象，再加上前面的语法解析得到的语法树对象，再利用访问者模式对语法树遍历就可以生成核心处理
 * 方法_jspService了
 * 上面介绍了最简单的一个Jsp 页面转化为Servlet的过程 ，旨在说明从JSP 到Servlet转化的原理，实际上需要处理很多的JSP指令标签 。
 *
 *
 *
 *
 */
public abstract class HttpJspBase extends HttpServlet implements HttpJspPage {

    private static final long serialVersionUID = 1L;

    protected HttpJspBase() {
    }

    @Override
    public final void init(ServletConfig config)
        throws ServletException
    {
        super.init(config);
        jspInit();
        _jspInit();
    }

    @Override
    public String getServletInfo() {
        return Localizer.getMessage("jsp.engine.info");
    }

    @Override
    public final void destroy() {
        jspDestroy();
        _jspDestroy();
    }

    /**
     * Entry point into service.
     */
    @Override
    public final void service(HttpServletRequest request, HttpServletResponse response)
        throws ServletException, IOException
    {
        _jspService(request, response);
    }

    @Override
    public void jspInit() {
    }

    public void _jspInit() {
    }

    @Override
    public void jspDestroy() {
    }

    protected void _jspDestroy() {
    }

    @Override
    public abstract void _jspService(HttpServletRequest request,
                                     HttpServletResponse response)
        throws ServletException, IOException;
}
