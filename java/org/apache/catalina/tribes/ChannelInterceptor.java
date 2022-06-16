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

import org.apache.catalina.tribes.group.InterceptorPayload;

/**
 * A ChannelInterceptor is an interceptor that intercepts
 * messages and membership messages in the channel stack.
 * This allows interceptors to modify the message or perform
 * other actions when a message is sent or received.<br>
 * Interceptors are tied together in a linked list.
 * @see org.apache.catalina.tribes.group.ChannelInterceptorBase
 * 拦截口应该可以说是很经典的模式，它有点类似于过滤器，在某信息从一个地方流向另外一个地方的过程中， 可能需要统一对信息进行处理， 如果考虑到
 * 系统的可扩展性和灵活性， 通常就会使用拦截器模式，它就像一个个卡被设置在信息流动的通道中，并且可以按照实现需要添加和减少关卡， Tribes
 * 为了在应用层提供了对源消息的统一处理的渠道引入通道拦截器，用户在应用层只需要根据自己的需要添加拦截器即可， 例如，压缩，解压 拦截器。
 * 消息输出，输入统计拦截器， 异步消息发送器等。
 *
 * 拦截器的数据流从I/O 流向应用层，中间就会经过一个拦截器栈， 应用层处理完就会返回一个ACK 发送端， 表示已经接收并处理完毕， 消息可靠级别
 * 为SYNC_ACK ,下面尝试用一个最简单的的一些代码和伪代码说明 Tribes 的拦截器的实现， 旨在提示拦截器如何设计 ， 而并非具体的实现，
 * 最终实现的功能如图 所示 ，最底层的协调者ChannelCoordinator 永远作为第一个加入拦截器栈的拦截器，往上则是按照添加的顺序排列 ，
 * 且每个拦截器的previous ,next 分别指向前一个拦截器和下一个拦截器。
 *
 *
 */
public interface ChannelInterceptor extends MembershipListener, Heartbeat {

    /**
     * An interceptor can react to a message based on a set bit on the
     * message options. <br>
     * When a message is sent, the options can be retrieved from ChannelMessage.getOptions()
     * and if the bit is set, this interceptor will react to it.<br>
     * A simple evaluation if an interceptor should react to the message would be:<br>
     * <code>boolean react = (getOptionFlag() == (getOptionFlag() &amp; ChannelMessage.getOptions()));</code><br>
     * The default option is 0, meaning there is no way for the application to trigger the
     * interceptor. The interceptor itself will decide.<br>
     * @return int
     * @see ChannelMessage#getOptions()
     */
    public int getOptionFlag();

    /**
     * Sets the option flag
     * @param flag int
     * @see #getOptionFlag()
     */
    public void setOptionFlag(int flag);

    /**
     * Set the next interceptor in the list of interceptors
     * @param next ChannelInterceptor
     */
    public void setNext(ChannelInterceptor next) ;

    /**
     * Retrieve the next interceptor in the list
     * @return ChannelInterceptor - returns the next interceptor in the list or null if no more interceptors exist
     */
    public ChannelInterceptor getNext();

    /**
     * Set the previous interceptor in the list
     * @param previous ChannelInterceptor
     */
    public void setPrevious(ChannelInterceptor previous);

    /**
     * Retrieve the previous interceptor in the list
     * @return ChannelInterceptor - returns the previous interceptor in the list or null if no more interceptors exist
     */
    public ChannelInterceptor getPrevious();

    /**
     * The <code>sendMessage</code> method is called when a message is being sent to one more destinations.
     * The interceptor can modify any of the parameters and then pass on the message down the stack by
     * invoking <code>getNext().sendMessage(destination,msg,payload)</code><br>
     * Alternatively the interceptor can stop the message from being sent by not invoking
     * <code>getNext().sendMessage(destination,msg,payload)</code><br>
     * If the message is to be sent asynchronous the application can be notified of completion and
     * errors by passing in an error handler attached to a payload object.<br>
     * The ChannelMessage.getAddress contains Channel.getLocalMember, and can be overwritten
     * to simulate a message sent from another node.<br>
     * @param destination Member[] - the destination for this message
     * @param msg ChannelMessage - the message to be sent
     * @param payload InterceptorPayload - the payload, carrying an error handler and future useful data, can be null
     * @throws ChannelException if a serialization error happens.
     * @see ErrorHandler
     * @see InterceptorPayload
     */
    public void sendMessage(Member[] destination, ChannelMessage msg, InterceptorPayload payload) throws ChannelException;

    /**
     * the <code>messageReceived</code> is invoked when a message is received.
     * <code>ChannelMessage.getAddress()</code> is the sender, or the reply-to address
     * if it has been overwritten.
     * @param data ChannelMessage
     */
    public void messageReceived(ChannelMessage data);

    /**
     * The <code>heartbeat()</code> method gets invoked periodically
     * to allow interceptors to clean up resources, time out object and
     * perform actions that are unrelated to sending/receiving data.
     */
    @Override
    public void heartbeat();

    /**
     * Intercepts the <code>Channel.hasMembers()</code> method
     * @return boolean - if the channel has members in its membership group
     * @see Channel#hasMembers()
     */
    public boolean hasMembers() ;

    /**
     * Intercepts the <code>Channel.getMembers()</code> method
     * @return Member[]
     * @see Channel#getMembers()
     */
    public Member[] getMembers() ;

    /**
     * Intercepts the <code>Channel.getLocalMember(boolean)</code> method
     * @param incAliveTime boolean
     * @return Member
     * @see Channel#getLocalMember(boolean)
     */
    public Member getLocalMember(boolean incAliveTime) ;

    /**
     * Intercepts the <code>Channel.getMember(Member)</code> method
     * @param mbr Member
     * @return Member - the actual member information, including stay alive
     * @see Channel#getMember(Member)
     */
    public Member getMember(Member mbr);

    /**
     * Starts up the channel. This can be called multiple times for individual services to start
     * The svc parameter can be the logical or value of any constants
     * @param svc int value of <BR>
     * Channel.DEFAULT - will start all services <BR>
     * Channel.MBR_RX_SEQ - starts the membership receiver <BR>
     * Channel.MBR_TX_SEQ - starts the membership broadcaster <BR>
     * Channel.SND_TX_SEQ - starts the replication transmitter<BR>
     * Channel.SND_RX_SEQ - starts the replication receiver<BR>
     * @throws ChannelException if a startup error occurs or the service is already started.
     * @see Channel
     */
    public void start(int svc) throws ChannelException;

    /**
     * Shuts down the channel. This can be called multiple times for individual services to shutdown
     * The svc parameter can be the logical or value of any constants
     * @param svc int value of <BR>
     * Channel.DEFAULT - will shutdown all services <BR>
     * Channel.MBR_RX_SEQ - stops the membership receiver <BR>
     * Channel.MBR_TX_SEQ - stops the membership broadcaster <BR>
     * Channel.SND_TX_SEQ - stops the replication transmitter<BR>
     * Channel.SND_RX_SEQ - stops the replication receiver<BR>
     * @throws ChannelException if a startup error occurs or the service is already started.
     * @see Channel
     */
    public void stop(int svc) throws ChannelException;

    public void fireInterceptorEvent(InterceptorEvent event);

    interface InterceptorEvent {
        int getEventType();
        String getEventTypeDesc();
        ChannelInterceptor getInterceptor();
    }


}
