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

package org.apache.coyote;


/**
 * Action hook. Actions represent the callback mechanism used by
 * coyote servlet containers to request operations on the coyote connectors.
 * Some standard actions are defined in ActionCode, however custom
 * actions are permitted.
 *
 * The param object can be used to pass and return informations related with the
 * action.
 *
 *
 * This interface is typically implemented by ProtocolHandlers, and the param
 * is usually a Request or Response object.
 *
 * @author Remy Maucherat
 * 说起钩子（Hook） ,Window 开发人员比较熟悉，例如 鼠标钩子，键盘钩子等，用于简单的语言描述，就是正常处理流程中安置的钩子，当执行到
 * 安置的钩子的地方时，就将进入到指定的钩子函数中进行处理，待处理完再返回原流程继续处理，钩子是消息处理的一个重要机制，专门用于监控
 * 指定的某些事件消息，它的核心思想是整个复杂的处理流程中所有关键点的触发相应的事件消息，假如添加钩子则会调用钩子函数，函数中可以根据传递过来的
 * 事件消息判断执行不再的逻辑，它就好像透明的让程序挂上额外的处理  。
 *
 * 为什么要使用钩子机制呢？可以这样认为，在一个庞大的系统内，某些基本的处理流程是相对固定的，且涉及到系统内部的逻辑，不应该允许外部人员修改它
 * 但是又要考虑到系统的扩展性，必须预留某些接口让开发者在不改变系统内部基本处理流程的情况下可以自定义一些额外的处理逻辑，于是就引入了钩子
 * 机制，按照钩子的思想，最后实现的效果也是相当于一个适当的位置嵌入了自定义的代码，此机制保证了系统内部不被外界修改的同时又预留了足够的空间。
 *
 * 对于 Java 比较熟悉的就是JVM关闭钩子ShutDownHook了，它提供了一种虚拟机关闭之前额外的操作功能，钩子并不仅仅是具体的某些功能，还是一种机制，
 * 是一种设计方法，下面模拟Tomcat 的响应对象如何使用钩子机制。
 *
 */
public interface ActionHook {


    /**
     * Send an action to the connector.
     *
     * @param actionCode Type of the action
     * @param param Action parameter
     */
    public void action(ActionCode actionCode, Object param);


}
