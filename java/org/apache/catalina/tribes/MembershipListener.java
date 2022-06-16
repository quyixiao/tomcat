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

package org.apache.catalina.tribes;

/**
 * The MembershipListener interface is used as a callback to the
 * membership service. It has two methods that will notify the listener
 * when a member has joined the group and when a member has disappeared (crashed)
 *
 * @author Filip Hanik
 *
 *
 *
 *
 *
 *  Tribes 为了更加清晰，更好的划分职责，它分成I/O层和应用层， I/O 层专心负责网络传输方面的逻辑处理，把接收到的数据往应用层传送，当然
 *  应用层发送的数据也通过此I/O 层发送，数据传送往应用层后，必须要留一些处理入口供应用层进行逻辑处理， 而考虑到系统的解耦， 在处理这个入口
 *  的最好方式是使用监听器模式，在底层发生各种事件时， 触发所有安装的监听器，使之执行监听器里面的处理逻辑 ， 这些事件主要包含了集群成员的加入
 *  和退出，消息报文的接收完毕等信息，所以整个消息流转过程分为两个监听器，一类是为了集群成员的变化相关的监听器MembershipListener ，另外
 *  一类是与集群消息接收，发送相关的监听器ChannelListener ，应用层只要关注这两个接口，写好各种处理逻辑的监听器添加到通道中即可 。
 *
 *  下面这两个监听器接口， 从接口定义的方法可以很清晰的看到各个方法被调用时机， MembershipLIstener 类型中的memberAdded 是成员加入时调用的方法
 *  memberDisappeared是成员退出时调用的方法， ChannelListener 类型中的 Accept 用于判断是否接受消息，messageReceived 用于对消息进行处理
 *  ，应用层把逻辑分别写到这几个方法就可以在对应的时刻执行相应的逻辑 。
 *
 *
 *
 * 我们可以在应用层定义若干监听器并且添加GroupChannel  中的两个监听器列表中， GroupChannel 其实可以看成是一个封装了I/O 层的抽象容器，
 * 它会在各个适当的时期遍历监听器列表中的所有监听器并且调用监听器对应的方法， 即执行应用层定义的业务逻辑，至此完成了数据从I/O 层流程应用层
 * 并完成处理，两种类型的监听器给应用层提供了处理入口，应用层只须要关注逻辑处理， 而其他的I/O 操作则交由I/O 层， 这两个层通过监听器模式串联
 * 起来 ，优雅将模块解耦 。
 *
 *
 */
public interface MembershipListener {
    /**
     * A member was added to the group
     * @param member Member - the member that was added
     */
    public void memberAdded(Member member);

    /**
     * A member was removed from the group<br>
     * If the member left voluntarily, the Member.getCommand will contain the Member.SHUTDOWN_PAYLOAD data
     * @param member Member
     * @see Member#SHUTDOWN_PAYLOAD
     */
    public void memberDisappeared(Member member);

}