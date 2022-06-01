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


package org.apache.catalina;


import java.util.EventObject;


/**
 * General event for notifying listeners of significant changes on a component
 * that implements the Lifecycle interface.  In particular, this will be useful
 * on Containers, where these events replace the ContextInterceptor concept in
 * Tomcat 3.x.
 *
 * @author Craig R. McClanahan
 *
 * 如果我们面对这么多的状态之间的转换，我们肯定会有这样的需求，我们希望某些状态的事情性之前之后做些什么，Tomcat 在这里使用了事件监听器
 * 模式来实现这样的功能，一般来说，事件监听器需要三个参与者。
 *
 * 事件对象，用于封装事件的信息，在事件监听器接口的统一方法作为参数使用，一般继承java.util.EventObject类。
 * 事件源，触发事件源头，不同的事件源会触发不同的事件类型。
 * 事件监听器负责监听事件源发出的事件，更确切的说，应该是每当发生事件时，事件源就会调用监听器的统一方法去处理，监听器一般实现java.util.EventListener接口。
 * 事件源提供了注册事件监听器方法，维护多个事件监听器对象，事件源将事件对象发给已经注册的所有事件监听器，这里其实是调用事件监听器的统一方法，把事件对象
 * 作为参数传过去，接着会在这个统一的方法里根据事件对象做出相应的处理。
 *
 * Tomcat 中的事件监听器也类似，如图11.2所示 ，LifecycleEvent 类就是事件对象，继承了EventObject类，LifesycleListener 为事件监听器接口 。
 * 里面只定义了一个方法lifesycleEvent (LefecycleEvent event),很明显，LifecycleEvent 作为这个方法的参数，最后缺一个事件源，一 般来说   。
 * 组件和容器就是事件源，Tomcat 提供了一个辅助类LifecycleSupport ,用于帮助管理该组件或容器上的监听器，里面维护了一个监听器数组，并提供了注册同。
 * 錅，触发监听器等方法，这样整个监听器框架就完成了，假如想要实现一个监听器功能，比如XXXLifecycleListener ，只要扩展LifecycleListener 接口并重写里面的
 * LifecycleEvent方法，然后调用LifecycleListener 接口并重写里面的LifecycleEvent 方法，然后调用笔LifecycleSupport 的addLifecycleListener 方法注册
 * 即可 。 后面当发生某些事件时，就可以监听了。
 *
 *
 *
 *
 */
public final class LifecycleEvent extends EventObject {

    private static final long serialVersionUID = 1L;


    // ----------------------------------------------------------- Constructors

    /**
     * Construct a new LifecycleEvent with the specified parameters.
     *
     * @param lifecycle Component on which this event occurred
     * @param type Event type (required)
     * @param data Event data (if any)
     */
    public LifecycleEvent(Lifecycle lifecycle, String type, Object data) {

        super(lifecycle);
        this.type = type;
        this.data = data;
    }


    // ----------------------------------------------------- Instance Variables


    /**
     * The event data associated with this event.
     */
    private Object data = null;


    /**
     * The event type this instance represents.
     */
    private String type = null;


    // ------------------------------------------------------------- Properties


    /**
     * Return the event data of this event.
     */
    public Object getData() {

        return (this.data);

    }


    /**
     * Return the Lifecycle on which this event occurred.
     */
    public Lifecycle getLifecycle() {

        return (Lifecycle) getSource();

    }


    /**
     * Return the event type of this event.
     */
    public String getType() {

        return (this.type);

    }


}
