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
 * ChannelReceiver Interface<br>
 * The <code>ChannelReceiver</code> interface is the data receiver component
 * at the bottom layer, the IO layer (for layers see the javadoc for the {@link Channel} interface).
 * This class may optionally implement a thread pool for parallel processing of incoming messages.
 * @author Filip Hanik
 *
 *  当把若干个机器组合成一个集群时，集群为了使这些机器能协同工作，成员之间的通信是必不可少的， 当然，可以说这也是集群实现中重点要解决的
 *  核心问题，一个强大的通信协同机制是集群的基础，本章将对Tomcat 集群通信的核心组件Tribes 进行剖析。
 *
 *  简单的说,Tribes 是一个具备让你通过网络向组成员发送和接收信息，动态检测其他节点组通信能力的调试可扩展和独立的消息框架，组成成员之间
 *  信息提制及成员维护是一个相对复杂的事情，因为这不仅要考虑各种通信协议，还要有必要的机制提供不同的消息传输保证级别，且成员关系的维护要及时准确 。
 *  另外，针对不同的I/O场景需要提供不同的I/O模式，这些都是组成员消息传输过程中需要深入考虑的问题， 而Tribes 很好的将点对点，点对组通信抽象
 *  得既简单又相对灵活 。
 *
 *  Tribes 拥有消息可靠的传输机制，它默认的基于TCP 协议传输，TCP拥有三次握手机制保证且有流量控制机制，另外在应用层面消息可靠性分为三个级别
 *
 *  1. NO_ACK 级别，这是可靠级别最低的，使用此级别时， 则认为Tribes 一旦把消息发送给Socket的发送队列，就认为发送成功，尽管传输过程中发生异常。
 *  导致接收方可能没有接收到，当然，这种级别的发送最快的方式 。
 *
 *  2. ACK 级别，这是最推荐使用的一种级别，它能保证接收方肯定接收到消息，Tribes 向其他节点发送消息后，只有接收到接收都确认消息，才会认为
 *  发送成功，这种机制能在更高层保证消息的可靠性，不过发送的效率会影响，因为每个消息需要确认，得不到确认的会重发。
 *
 *  3. SYNC_ACK 级别，这种级别不仅保证传输的成功，还保证执行成功，Tribes 向其他节点发送消息后，接收者到的消息进行处理，直到处理成功才返回
 *  ACK 确认，如果接收成功而处理失败，接收者会返回ACK_FAIL 给发送者，发送者将会重发，当然，这种级别的消息发送效率是最低，最慢的。
 *
 *
 *
 *
 *
 *
 */
public interface ChannelReceiver extends Heartbeat {
    public static final int MAX_UDP_SIZE = 65535;

    /**
     * Start listening for incoming messages on the host/port
     * @throws java.io.IOException
     */
    public void start() throws java.io.IOException;

    /**
     * Stop listening for messages
     */
    public void stop();

    /**
     * String representation of the IPv4 or IPv6 address that this host is listening
     * to.
     * @return the host that this receiver is listening to
     */
    public String getHost();


    /**
     * Returns the listening port
     * @return port
     */
    public int getPort();

    /**
     * Returns the secure listening port
     * @return port, -1 if a secure port is not activated
     */
    public int getSecurePort();

    /**
     * Returns the UDP port
     * @return port, -1 if the UDP port is not activated.
     */
    public int getUdpPort();

    /**
     * Sets the message listener to receive notification of incoming
     * @param listener MessageListener
     * @see MessageListener
     */
    public void setMessageListener(MessageListener listener);

    /**
     * Returns the message listener that is associated with this receiver
     * @return MessageListener
     * @see MessageListener
     */
    public MessageListener getMessageListener();

}
