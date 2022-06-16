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
 * MembershipService Interface<br>
 * The <code>MembershipService</code> interface is the membership component
 * at the bottom layer, the IO layer (for layers see the javadoc for the {@link Channel} interface).<br>
 * @author Filip Hanik
 *
 * 整个Tribes 的设计核心可以用图21.1 表示，在I/O 层有三个重要的模块，其中 ，MembershipService 模块主要负责组成员关系的维护 ，
 * 包括维护现有成员及发现新成员， 这些工作都是模块自动运行完成的，你无须关心组成成员的维护工作，ChannelSender 模块负责向组内
 * 其他成员发送消息及其各种机制的详细实现： ChannnelReceiver模块用于接收组内其他成员发送过来的消息，及其各种机制的详细实现。
 * 消息可靠性就是通过ChannelSender 及ChannelReceiver 的协同得到不同的级别的保证，拦截器栈的消息传递到应用层之前对消息进行一些额外的
 * 操作，例如，对某些消息进行过滤器编码等操作，最后在应用层，多数情况下我们只须关心应用层的东西 即使用，应用层主要是监听器，所以要实现
 * 监听器里面批定的方法即可以对I/O 层传输的消息做逻辑处理。
 *
 *
 * 一个集群包含若干成员，要对这些成员进行管理，就必须一张包含所有成员的列表，当要对某个节点做操作时，通过这个列表可以准确找到该节点的地址 ，
 * 进而向该节点发送操作消息，如何维护这张包含所有成员列表的本节讨论的主题 。
 *
 * 成员维护是集群的基础功能，它一般划分一个独立模块或层完成此功能，它提供成员列表查询，成员维护，成员列表改变事件通知能力，由于 Tribes
 * 定位于基于同等节点之间的通信，因此并不存在主节点选举问题，它所要具备的功能就是自动发现节点，即加入新节点后要通知集群其他成员列表，让每
 * 个节点都能及时 的更新成员列表，每个节点都维护一份集群成员列表，Node1,Node2 ,Node3 使用组播通过交换机各自已经维护一分成员列表，且它们隔
 * 一段时间向交换机组播自己的节点消息，即心跳操作，当Node4 加入集群中时，Node4
 *
 *
 * Tribes 的集群是如何实现以上的功能的呢？ 其实成员列表的创建，维护，基于经典的组播方式实现，每个节点都创建一个节点信息发射器和节点信息接收器。
 * 让它们运行于独立的线程中，发射吕用于向组内发送自己的节点消息，而接收器则用于接收其他节点发送过来的节点消息，并进行处理， 而接收器则用于接收其他
 * 节点发送过来的节点消息并进行处理， 要使节点之间的通信能被识别就需要定义一个语义，即约定的报文协议的结构，Tribes 的成员报文是这样定义的。
 * 两个固定的值用于表示报文的开始和结束 ， 开始标识TRIBES_MBR_BEGIN 的值为字节数组 84 ， 82 ， 73 ，66 ，69 ， 83， 45 ，66 ， 1 ， 0
 *
 *
 * 首先，要执行加入组播成员的操作，接着，分别启动接收器线程，发现线程， 一般接收器要优先启动，发送器每隔1 秒组织协议包发送心跳，组播内成员
 * 的接收器对接收的协议报文进行解析，按照一定的逻辑更新各自的节点本地成员列表，如果成员列表已经包含协议包的成员，则只更新存活的时间等消息。
 * 另外，每发送，接收都会检查所有的成员是否超时，如果某个成员的上次更新时间距离现在超过设定的时间间隔，就删除此成员，如果超过间隔的成员被视为已失效
 * 的成员，这里的各个节点更新成员时使用节点的本身时间做比较， 所以不同的节点的时间即使不同也不会有问题。
 *
 *
 *
 */
public interface MembershipService {

    public static final int MBR_RX = Channel.MBR_RX_SEQ;
    public static final int MBR_TX = Channel.MBR_TX_SEQ;

    /**
     * Sets the properties for the membership service. This must be called before
     * the <code>start()</code> method is called.
     * The properties are implementation specific.
     * @param properties - to be used to configure the membership service.
     */
    public void setProperties(java.util.Properties properties);
    /**
     * Returns the properties for the configuration used.
     */
    public java.util.Properties getProperties();
    /**
     * Starts the membership service. If a membership listeners is added
     * the listener will start to receive membership events.
     * Performs a start level 1 and 2
     * @throws java.lang.Exception if the service fails to start.
     */
    public void start() throws java.lang.Exception;

    /**
     * Starts the membership service. If a membership listeners is added
     * the listener will start to receive membership events.
     * @param level - level MBR_RX starts listening for members, level MBR_TX
     * starts broad casting the server
     * @throws java.lang.Exception if the service fails to start.
     * @throws java.lang.IllegalArgumentException if the level is incorrect.
     */
    public void start(int level) throws java.lang.Exception;


    /**
     * Starts the membership service. If a membership listeners is added
     * the listener will start to receive membership events.
     * @param level - level MBR_RX stops listening for members, level MBR_TX
     * stops broad casting the server
     * @throws java.lang.IllegalArgumentException if the level is incorrect.
     */

    public void stop(int level);

    /**
     * @return true if the the group contains members
     */
    public boolean hasMembers();


    /**
     *
     * @param mbr Member
     * @return Member
     */
    public Member getMember(Member mbr);
    /**
     * Returns a list of all the members in the cluster.
     */

    public Member[] getMembers();

    /**
     * Returns the member object that defines this member
     */
    public Member getLocalMember(boolean incAliveTime);

    /**
     * Return all members by name
     */
    public String[] getMembersByName() ;

    /**
     * Return the member by name
     */
    public Member findMemberByName(String name) ;

    /**
     * Sets the local member properties for broadcasting
     */
    public void setLocalMemberProperties(String listenHost, int listenPort, int securePort, int udpPort);

    /**
     * Sets the membership listener, only one listener can be added.
     * If you call this method twice, the last listener will be used.
     * @param listener The listener
     */
    public void setMembershipListener(MembershipListener listener);

    /**
     * removes the membership listener.
     */
    public void removeMembershipListener();

    /**
     * Set a payload to be broadcasted with each membership
     * broadcast.
     * @param payload byte[]
     */
    public void setPayload(byte[] payload);

    public void setDomain(byte[] domain);

    /**
     * Broadcasts a message to all members
     * @param message
     * @throws ChannelException
     */
    public void broadcast(ChannelMessage message) throws ChannelException;

}
