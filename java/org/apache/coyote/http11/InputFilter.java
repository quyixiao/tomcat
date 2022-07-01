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

package org.apache.coyote.http11;

import java.io.IOException;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.tomcat.util.buf.ByteChunk;

/**
 * Input filter interface.
 *
 * @author Remy Maucherat
 * 输入过滤器接口InputFilter ，继承InputBuffer 类，额外提供setBuffer 方法以设置前一个缓冲 。
 * 一般情况下，我们通过请求体读取器InputStreamInputBuffer 获取的仅仅是源数据，即未经过任务处理发送方发来的字节，但是有些时候这个读取的
 * 过程中希望做一些额外的处理，而且这些额外的处理可能是根据不同的条件做不同的处理，考虑到程序的解耦与扩展，于是引入过滤器过滤模式，输出
 * 过滤器InputFilter ，在读取数据过程中，对于额外的操作，只需要通过添加不同的过滤器即可以实现， 例如添加对HTTP1.1 协议分场传输的相关操作
 * 过滤器。
 *
 *
 *
 */
public interface InputFilter extends InputBuffer {


    /**
     * Read bytes.
     *
     * @return Number of bytes read.
     * 输入缓冲接口，InputBuffer 提供读取操作
     */
    @Override
    public int doRead(ByteChunk chunk, Request unused)
        throws IOException;


    /**
     * Some filters need additional parameters from the request. All the
     * necessary reading can occur in that method, as this method is called
     * after the request header processing is complete.
     */
    public void setRequest(Request request);


    /**
     * Make the filter ready to process the next request.
     */
    public void recycle();


    /**
     * Get the name of the encoding handled by this filter.
     */
    public ByteChunk getEncodingName();


    /**
     * Set the next buffer in the filter pipeline.
     */
    public void setBuffer(InputBuffer buffer);


    /**
     * End the current request.
     *
     * @return 0 is the expected return value. A positive value indicates that
     * too many bytes were read. This method is allowed to use buffer.doRead
     * to consume extra bytes. The result of this method can't be negative (if
     * an error happens, an IOException should be thrown instead).
     */
    public long end()
        throws IOException;


    /**
     * Amount of bytes still available in a buffer.
     */
    public int available();


}
