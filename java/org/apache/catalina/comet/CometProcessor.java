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


package org.apache.catalina.comet;

import java.io.IOException;

import javax.servlet.Servlet;
import javax.servlet.ServletException;

/**
 * This interface should be implemented by servlets which would like to handle
 * asynchronous IO, receiving events when data is available for reading, and
 * being able to output data without the need for being invoked by the container.
 * Note: When this interface is implemented, the service method of the servlet will
 * never be called, and will be replaced with a begin event.
 *
 *
 * Comet 模式是一种服务端推技术，它的核心思想提供了一种能让当服务器端向客户端发送数据的方式，Comet模式为什么会出现呢？ 风开始时人们在客户端通过不断的自动
 * 刷新整个页面来更新数据，接下来觉得体验不好，又使用了AJAX 不断人客户端轮询服务器以更新数据，然后使用Comet 模式由服务器端通过推送数据。
 * Comet 模式能大大郑源发送到服务器的请求，从而词句了很多的开销，而且它们还具备更好的实时性。
 *
 * 客户端发送一条请求到服务端，服务端接收到数据后，一直保持连接不关闭，接着，客户端发送一个操作报文告诉服务器需要做什么操作，服务器处理完
 * 事件1后会给客户端响应，然后处理完事件2后又会给客户端响应，接着客户端继续发送操作报文维生服务器，服务器再进行响应。
 *
 * 一般Comet 模式需要NIO 配合，而在BIO 中无法使用Comet 模式，在Tomcat 内部集成了Comet模式的思想比较清晰，引用了一个CometProcess接口。
 * 此接口只有一个event 。
 *
 *
 */
public interface CometProcessor extends Servlet{

    /**
     * Process the given Comet event.
     *
     * @param event The Comet event that will be processed
     * @throws IOException
     * @throws ServletException
     */
    public void event(CometEvent event)
        throws IOException, ServletException;

}
