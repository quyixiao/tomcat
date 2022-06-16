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

import java.io.IOException;


/**
 * ChannelReceiver Interface<br>
 * The <code>ChannelSender</code> interface is the data sender component
 * at the bottom layer, the IO layer (for layers see the javadoc for the {@link Channel} interface).<br>
 * The channel sender must support "silent" members, ie, be able to send a message to a member
 * that is not in the membership, but is part of the destination parameter
 * @author Filip Hanik
 *
 *  前面的集群成员维护嗠为我们提供了集群内所有成员的地址商品等信息， 通过MembershipService 可以轻易从节点本地的成员列表获取集群所有的成员信息
 *  有了这些成员信息后，就可以使用可靠的TCP/IP 协议进行通信了，本节讨论的是实际中真正用于消息的传递通道的相关机制及实现细节 。
 *
 * 4 个节点在本地都拥有一张集群成员的信息列表，这时，Node1 有这样的一个需求 ， 为了保证数据的安全性， 在向自己的内存中存放一份数据的同时，还要把
 * 数据同步到其他的三个节点的内存中，
 *
 * 最理想的状态是数据成功发送给Node2 ,Node3 ,Node4 , 这样从整体来看ChannelSender 像提供了一个多通道的平行发送方式，所以它称为平行发送器。
 * 但在现实中并不能保证同一批消息对于所有的节点都发送成功，有可能发送到Node3 时，因为某种原因失败了，而Node2 ，Node4 都成功了， 这时通常要
 * 采用一些策略来应对，例如重新发送，Tribes所有使用策略的优点是尝试进行若干次发送，若干次失败后，将向上抛出异常信息， 异常信息包含哪些节点发送
 * 失败及其原因，默认的尝试次数是1， 为了确保数据确实被节点接收到，需要在应用层引入一个协议以保证传输的可靠性， 即通知机制，发送都发送消息给接收者。
 * 接收者接收到返回一个ACK 表示自己已经接收成功了，Tribes 中详细的协议报文定义如下，START_DATA ,消息的长度 4 个字节，
 *
 *
 */
public interface ChannelSender extends Heartbeat
{
    /**
     * Notify the sender of a member being added to the group.<br>
     * Optional. This can be an empty implementation, that does nothing
     * @param member Member
     */
    public void add(Member member);
    /**
     * Notification that a member has been removed or crashed.
     * Can be used to clean up open connections etc
     * @param member Member
     */
    public void remove(Member member);

    /**
     * Start the channel sender
     * @throws IOException if preprocessing takes place and an error happens
     */
    public void start() throws IOException;

    /**
     * Stop the channel sender
     */
    public void stop();

    /**
     * A channel heartbeat, use this method to clean up resources
     */
    @Override
    public void heartbeat() ;

    /**
     * Send a message to one or more recipients.
     * @param message ChannelMessage - the message to be sent
     * @param destination Member[] - the destinations
     * @throws ChannelException - if an error happens, the ChannelSender MUST report
     * individual send failures on a per member basis, using ChannelException.addFaultyMember
     * @see ChannelException#addFaultyMember(Member,java.lang.Exception)
     */
    public void sendMessage(ChannelMessage message, Member[] destination) throws ChannelException;
}
