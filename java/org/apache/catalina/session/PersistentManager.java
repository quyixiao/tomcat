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
package org.apache.catalina.session;

/**
 * Implementation of the <b>Manager</b> interface that makes use of
 * a Store to swap active Sessions to disk. It can be configured to
 * achieve several different goals:
 *
 * <ul>
 * <li>Persist sessions across restarts of the Container</li>
 * <li>Fault tolerance, keep sessions backed up on disk to allow
 *     recovery in the event of unplanned restarts.</li>
 * <li>Limit the number of active sessions kept in memory by
 *     swapping less active sessions out to disk.</li>
 * </ul>
 *
 * @author Kief Morris (kief@kief.com)
 *
 * 前面提到了会话管理器已经提供了基础的会话管理功能，但是持久化方面做得还是不够，或者说某些情景下无法满足要求，例如 ，把会话以文件或
 * 数据库的形式存储到存储介质中，这些都是标准的会话管理器无法做到的，于是，另外一种会话管理器被设置了出来，持久化会话管理器。
 *
 * 在分析持久化会话管理器之前，不妨先了解另外一种抽象的概念，会话存储设备 （Store ），引入这个概念是为了更方便的实现各种会话存储方式 。
 * 作为存储设备，最重要的无非是读，写操作，读即将会话从存储设备加载到内存中，而写则将会话写入到存储设备中， 所以定义了两个重要的方法
 * load和save之间的相对应，FileStore 和JDBCStore 只要扩展了Store 接口，各自实现了Load 和save方法，即可以分别实现以文件或数据库的形式
 * 存储会话，它们的类图 19.10 所示 。
 *
 *
 */
public final class PersistentManager extends PersistentManagerBase {

    // ----------------------------------------------------- Instance Variables


    /**
     * The descriptive information about this implementation.
     */
    private static final String info = "PersistentManager/1.0";


    /**
     * The descriptive name of this Manager implementation (for logging).
     */
    static String name = "PersistentManager";


    // ------------------------------------------------------------- Properties

    @Override
    public String getInfo() {
        return info;
    }


    @Override
    public String getName() {
        return name;
    }
 }

