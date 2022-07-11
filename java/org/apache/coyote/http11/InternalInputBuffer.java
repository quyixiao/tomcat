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

import java.io.EOFException;
import java.io.IOException;
import java.io.InputStream;
import java.net.Socket;
import java.nio.charset.Charset;

import org.apache.coyote.InputBuffer;
import org.apache.coyote.Request;
import org.apache.juli.logging.Log;
import org.apache.juli.logging.LogFactory;
import org.apache.tomcat.util.buf.ByteChunk;
import org.apache.tomcat.util.buf.MessageBytes;
import org.apache.tomcat.util.http.parser.HttpParser;
import org.apache.tomcat.util.net.AbstractEndpoint;
import org.apache.tomcat.util.net.SocketWrapper;

/**
 * Implementation of InputBuffer which provides HTTP request header parsing as
 * well as transfer decoding.
 *
 * @author <a href="mailto:remm@apache.org">Remy Maucherat</a>
 *
 * 互联网中的信息从一端向另外一端过程相当的复杂，中间可能通过若干个硬件，为了提高发送和接收效率，在发送端及接收端将引入缓冲区，所以两端
 * 的套接字都拥有各自的缓冲区，当然，这种缓冲区的引入也带来了不确定的延时， 在发送端一般先将消息写入缓冲区， 直到缓冲区填满才发送， 而
 * 接收端则一次只读取最多不超过缓冲区大小的消息。
 *
 * Tomcat 在处理客户端的请求时需要读取客户端的请求数据，它同样需要一个缓冲区，用于接收字节流，在Tomcat 中称为套接字输入缓冲装置 。 它主要的
 * 责任是提供一种缓冲模式，以从Socket 中读取字节流，提供了填充缓冲区的方法，提供了解析HTTP 协议请求的方法，提供了解析HTTP 协议请求头方法 。
 * 以及按照解析的结果组装请求对象Request 。
 *
 *
 *
 *
 */
public class InternalInputBuffer extends AbstractInputBuffer<Socket> {

    private static final Log log = LogFactory.getLog(InternalInputBuffer.class);


    /**
     * Underlying input stream.
     */
    private InputStream inputStream;


    /**
     * Default constructor.
     */
    public InternalInputBuffer(Request request, int headerBufferSize,
            boolean rejectIllegalHeaderName, HttpParser httpParser) {

        this.request = request;
        headers = request.getMimeHeaders();

        // 请求头的缓冲区域大小，一个请求的请求头数据不能超过这个区域，默认为8192，也就是8*1024个字节=8kb
        buf = new byte[headerBufferSize];

        this.rejectIllegalHeaderName = rejectIllegalHeaderName;
        this.httpParser = httpParser;

        inputStreamInputBuffer = new InputStreamInputBuffer();

        filterLibrary = new InputFilter[0];
        activeFilters = new InputFilter[0];
        lastActiveFilter = -1;

        parsingHeader = true;
        swallowInput = true;

    }


    /**
     * Read the request line. This function is meant to be used during the
     * HTTP request header parsing. Do NOT attempt to read the request body
     * using it.
     *
     * @throws IOException If an exception occurs during the underlying socket
     * read operations, or if the given buffer is not big enough to accommodate
     * the whole line.
     * buf : 字节数组 用于存放缓冲字节流，它的大小由程序设定，Tomcat 中的默认设置为8 * 1024 ，即8KB
     * pos: 整形 , 表示读取指针，读取到哪个位置值即为多少
     * lastValid: 整型， 表示从操作系统底层读取的数据填充到buf中的最后位置。
     * end : 整型 ，表示缓冲区buf中HTTP 协议请求报文头问结束的位置 ， 同时也表示报文体的开始位置 。
     * 同时也表示报文体的开始位置，从图6.12中从上往下看，最开始的缓冲区buf 是空的， 接着读取套接字操作系统底层的若干字节流读取到buf中
     * 于是状态如2所示 ，读取到的字节流将buf 从头往后进行填充，同时post为0，lastValid 为此读取后最后的位置值，然后第二次读取的操作系统
     * 底层若干字节流，每次读取多少并不确定，字节流应该接在2 中， lastValid指定的位置后面而非从头开始，此时pos 及lastValid根据实际情况 。
     * 被赋予新值，假如再读取一次则最终姿态为5，多出一个end变量，它的含义是HTTP 请求报文的请求行及请求头结束的位置 。
     *
     *
     *
     */
    @Override
    public boolean parseRequestLine(boolean useAvailableDataOnly)

        throws IOException {

        int start = 0;

        //
        // Skipping blank lines
        //

        byte chr = 0;
        do {
            // 把buf里面的字符一个个取出来进行判断，遇到非回车换行符则会退出

            // Read new bytes if needed
            // 如果一直读到的回车换行符则再次调用fill,从inputStream里面读取数据填充到buf中
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            // Set the start time once we start reading data (even if it is
            // just skipping blank lines)
            if (request.getStartTime() < 0) {
                request.setStartTime(System.currentTimeMillis());
            }
            chr = buf[pos++];
        } while ((chr == Constants.CR) || (chr == Constants.LF));

        pos--;

        // Mark the current buffer position
        start = pos;

        //
        // Reading the method name
        // Method name is a token
        //

        boolean space = false;



        while (!space) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            // Spec says method name is a token followed by a single SP but
            // also be tolerant of multiple SP and/or HT.
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                space = true;
                // 下面代码其实是调用了ByteChunk 的setBytes 方法，把字节流及末坐标设置好， 后面的request.method.toString() 同样
                // 调用了ByteChunk 的toString 方法，根据指定的编码进行转码，这里是ISO_8859_1,这样一来就达到了延迟处理模式效果 。
                // 在需要时才根据指定的编码转码并获取字符串，如果不需要，则无须转码，处理性能得到提高 。

                // Tomcat 对于套接字的信息都用消息字节表示，好处是实现一种延迟处理模式，提高性能，实际上，Tomcat 还引入字符串缓存。
                // 在转码之前会先从缓存中查找是否有对应的编码的字符串， 如果存在 ，则不必再执行转码动作，而是直接返回对应的字符串，
                // 性能进一步得到优化，为了提高性能，我们必须要多做一些额外的工作，这也是Tomcat 接收到信息不直接用字符串保存的原因 。
                request.method().setBytes(buf, start, pos - start);
            } else if (!HttpParser.isToken(buf[pos])) {
                throw new IllegalArgumentException(sm.getString("iib.invalidmethod"));
            }

            pos++;

        }

        // Spec says single SP but also be tolerant of multiple SP and/or HT
        while (space) {
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                pos++;
            } else {
                space = false;
            }
        }

        // Mark the current buffer position
        start = pos;
        int end = 0;
        int questionPos = -1;

        //
        // Reading the URI
        //

        boolean eol = false;

        while (!space) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            // Spec says single SP but it also says be tolerant of HT
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                space = true;
                end = pos;
            } else if ((buf[pos] == Constants.CR)
                       || (buf[pos] == Constants.LF)) {
                // HTTP/0.9 style request
                eol = true;
                space = true;
                end = pos;
            } else if ((buf[pos] == Constants.QUESTION) && (questionPos == -1)) {
                questionPos = pos;
            } else if (questionPos != -1 && !httpParser.isQueryRelaxed(buf[pos])) {
                // %nn decoding will be checked at the point of decoding
                throw new IllegalArgumentException(sm.getString("iib.invalidRequestTarget"));
            } else if (httpParser.isNotRequestTargetRelaxed(buf[pos])) {
                // This is a general check that aims to catch problems early
                // Detailed checking of each part of the request target will
                // happen in AbstractHttp11Processor#prepareRequest()
                throw new IllegalArgumentException(sm.getString("iib.invalidRequestTarget"));
            }

            pos++;

        }

        request.unparsedURI().setBytes(buf, start, end - start);
        if (questionPos >= 0) {
            request.queryString().setBytes(buf, questionPos + 1,
                                           end - questionPos - 1);
            request.requestURI().setBytes(buf, start, questionPos - start);
        } else {
            request.requestURI().setBytes(buf, start, end - start);
        }

        // Spec says single SP but also says be tolerant of multiple SP and/or HT
        while (space) {
            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }
            if (buf[pos] == Constants.SP || buf[pos] == Constants.HT) {
                pos++;
            } else {
                space = false;
            }
        }

        // Mark the current buffer position
        start = pos;
        end = 0;

        //
        // Reading the protocol
        // Protocol is always "HTTP/" DIGIT "." DIGIT
        //
        while (!eol) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.CR) {
                end = pos;
            } else if (buf[pos] == Constants.LF) {
                if (end == 0)
                    end = pos;
                eol = true;
            } else if (!HttpParser.isHttpProtocol(buf[pos])) {
                throw new IllegalArgumentException(sm.getString("iib.invalidHttpProtocol"));
            }

            pos++;

        }

        if ((end - start) > 0) {
            request.protocol().setBytes(buf, start, end - start);
        } else {
            request.protocol().setString("");
        }

        return true;

    }


    /**
     * Parse the HTTP headers.
     */
    @Override
    public boolean parseHeaders()
        throws IOException {
        if (!parsingHeader) {
            throw new IllegalStateException(
                    sm.getString("iib.parseheaders.ise.error"));
        }

        while (parseHeader()) {
            // Loop until we run out of headers
        }

        parsingHeader = false;
        end = pos;
        return true;
    }


    /**
     * Parse an HTTP header.
     *
     * @return false after reading a blank line (which indicates that the
     * HTTP header parsing is done
     */
    @SuppressWarnings("null") // headerValue cannot be null
    // 至此，整个缓冲装置的工作原理基本搞清楚了， 一个完整的过程是从底层字节流读取到对这些字节流的解析并组装成一个请求对象request
    // 方便程序后面使用，由于每次从底层读取到的字节流的大小都不确定，因此通过pos,lastValid变量进行控制，以完成对字节流的准确读取接收。
    // 除此之外，输入缓冲装置还提供了解析请求头部方法，处理逻辑是按照HTTP协议中规定对头部的解析，然后依次放入request对象中， 需要额外
    // 说明的是， Tomcat 实际运行中并不会在将请求行， 请求头部等参数解后直接转化为String类型设置到request 中，而是继续使用ASCII 码
    // 存放这些值，因为对这些ASCII 码转码会导致性能问题， 其中思想只有到需要的时候才会转码，很多的参数没有使用到，就不会进行转码，
    // 以此提高处理性能，这方面的详细内容如图6.12节，请求-Request 会涉及， 最后附笔附上套接字输入缓冲装置的结构图 ，如图6.14所示 。
    private boolean parseHeader()
        throws IOException {

        //
        // Check for blank line
        //

        byte chr = 0;
        while (true) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos];

            if (chr == Constants.CR) { // 回车
                // Skip
            } else if (chr == Constants.LF) { // 换行
                pos++;
                return false;
                // 在解析某一行时遇到一个回车换行了，则表示请求头的数据结束了
            } else {
                break;
            }

            pos++;

        }

        // Mark the current buffer position
        int start = pos;

        //
        // Reading the header name
        // Header name is always US-ASCII
        //

        boolean colon = false;
        MessageBytes headerValue = null;

        while (!colon) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.COLON) {
                colon = true;
                headerValue = headers.addValue(buf, start, pos - start);
            } else if (!HttpParser.isToken(buf[pos])) {
                // Non-token characters are illegal in header names
                // Parsing continues so the error can be reported in context
                // skipLine() will handle the error
                skipLine(start);
                return true;
            }

            chr = buf[pos];
            if ((chr >= Constants.A) && (chr <= Constants.Z)) {
                buf[pos] = (byte) (chr - Constants.LC_OFFSET);
            }

            pos++;

        }

        // Mark the current buffer position
        start = pos;
        int realPos = pos;

        //
        // Reading the header value (which can be spanned over multiple lines)
        //

        boolean eol = false;
        boolean validLine = true;

        while (validLine) {

            boolean space = true;

            // Skipping spaces
            while (space) {

                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill())
                        throw new EOFException(sm.getString("iib.eof.error"));
                }

                if ((buf[pos] == Constants.SP) || (buf[pos] == Constants.HT)) {
                    pos++;
                } else {
                    space = false;
                }

            }

            int lastSignificantChar = realPos;

            // Reading bytes until the end of the line
            while (!eol) {

                // Read new bytes if needed
                if (pos >= lastValid) {
                    if (!fill())
                        throw new EOFException(sm.getString("iib.eof.error"));
                }

                if (buf[pos] == Constants.CR) {
                    // Skip
                } else if (buf[pos] == Constants.LF) {
                    eol = true;
                } else if (buf[pos] == Constants.SP) {
                    buf[realPos] = buf[pos];
                    realPos++;
                } else {
                    buf[realPos] = buf[pos];
                    realPos++;
                    lastSignificantChar = realPos;
                }

                pos++;

            }

            realPos = lastSignificantChar;

            // Checking the first character of the new line. If the character
            // is a LWS, then it's a multiline header

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            chr = buf[pos];
            if ((chr != Constants.SP) && (chr != Constants.HT)) {
                validLine = false;
            } else {
                eol = false;
                // Copying one extra space in the buffer (since there must
                // be at least one space inserted between the lines)
                buf[realPos] = chr;
                realPos++;
            }

        }

        // Set the header value
        headerValue.setBytes(buf, start, realPos - start);
        int length = realPos - start;
        byte b [] = new byte[length];
        for(int i = 0;i < length ;i ++){
            b[i] = buf[start + i];
        }
        System.out.println(" value = " + new String(b));
        return true;

    }


    @Override
    public void recycle() {
        super.recycle();
        inputStream = null;
    }


    // ------------------------------------------------------ Protected Methods


    @Override
    protected void init(SocketWrapper<Socket> socketWrapper,
            AbstractEndpoint<Socket> endpoint) throws IOException {
        inputStream = socketWrapper.getSocket().getInputStream();
    }



    private void skipLine(int start) throws IOException {
        boolean eol = false;
        int lastRealByte = start;
        if (pos - 1 > start) {
            lastRealByte = pos - 1;
        }

        while (!eol) {

            // Read new bytes if needed
            if (pos >= lastValid) {
                if (!fill())
                    throw new EOFException(sm.getString("iib.eof.error"));
            }

            if (buf[pos] == Constants.CR) {
                // Skip
            } else if (buf[pos] == Constants.LF) {
                eol = true;
            } else {
                lastRealByte = pos;
            }
            pos++;
        }

        if (rejectIllegalHeaderName || log.isDebugEnabled()) {
            String message = sm.getString("iib.invalidheader", new String(buf, start,
                    lastRealByte - start + 1, Charset.forName("ISO-8859-1")));
            if (rejectIllegalHeaderName) {
                throw new IllegalArgumentException(message);
            }
            log.debug(message);
        }
    }

    /**
     * Fill the internal buffer using data from the underlying input stream.
     *
     * @return false if at end of stream
     */
    protected boolean fill() throws IOException {
        return fill(true);
    }

    @Override
    protected boolean fill(boolean block) throws IOException {

        int nRead = 0;

        if (parsingHeader) {

            // 如果还在解析请求头，lastValid表示当前解析数据的下标位置，如果该位置等于buf的长度了，表示请求头的数据超过buf了。
            if (lastValid == buf.length) {
                throw new IllegalArgumentException
                    (sm.getString("iib.requestheadertoolarge.error"));
            }

            // 从inputStream中读取数据，len表示要读取的数据长度，pos表示把从inputStream读到的数据放在buf的pos位置
            // nRead表示真实读取到的数据
            nRead = inputStream.read(buf, pos, buf.length - lastValid);
            if (nRead > 0) {
                lastValid = pos + nRead; // 移动lastValid
            }

        } else {
            // 当读取请求体的数据时
            // buf.length - end表示还能存放多少请求体数据，如果小于4500，那么就新生成一个byte数组，这个新的数组专门用来盛放请求体
            if (buf.length - end < 4500) {
                // In this case, the request header was really large, so we allocate a
                // brand new one; the old one will get GCed when subsequent requests
                // clear all references
                buf = new byte[buf.length];
                end = 0;
            }
            pos = end;
            lastValid = pos;
            nRead = inputStream.read(buf, pos, buf.length - lastValid);
            if (nRead > 0) {
                lastValid = pos + nRead;
            }
        }
        return (nRead > 0);

    }


    // ------------------------------------- InputStreamInputBuffer Inner Class


    /**
     * This class is an input buffer which will read its data from an input
     * stream.
     * 前面提到，套接字缓冲装置InputStreamInputBuffer，用于向操作系统的底层读取来自客户端的消息并提供缓冲机制，把报文以字节数组的形式存放 到
     * buf中，同时它提供了HTTP 协议的请求行和请求头的解析方法，当它们都解析完成后，buf 数组中指针指向的位置就是请求体的起始位置，Web
     * 容器后期可能需要处理HTTP报文的请求体，所以必须提供一个获取的通道，这个通道就是请求体读取InputStreamInputBuffer ，它其实是套接字
     * 缓冲数组buf 的已读指针是否已经达到尾部，如果达到尾部，则重新读取操作系统的底层字节，最终读取到目标缓冲区desBuf上
     * InputStreamInputBuffer 包含在套接字缓冲装置中，通过它可以将请求体读取到目标缓冲defBuf 上。
     *
     *  如图6.18所示 ， InputStreamInputBuffer 包含在套接字缓冲装置中，通过它可以将请求休读取到目标缓冲区desBuf上 。
     *
     *  如图6.19所示 ，在套接字输入缓冲装置中， 从操作系统底层读取的字节缓冲在buf中，请求行和请求头被解析后，缓冲区buf 的指针指向请求体
     *  的起始位置，通过请求体读取器InputStreamInputBuffer可进行读操作，它会自动判断buf是否已经读取完，读完则重新从操作系统底层读取字节
     *  到buf中，当其他组件从套接字输入缓冲装置读取请求体时，装置将判定其中是否包含过滤器，假设包含，则通过一层层过滤器完成过滤操作后
     *  才能读取到desBuf ， 这个过程就像被加入了一首处理关卡， 经过每一道关卡都执行相应的操作， 最终完成源数据到目的数据的操作。
     *
     *
     *
     *
     *
     */
    protected class InputStreamInputBuffer
        implements InputBuffer {


        /**
         * Read bytes into the specified chunk.
         */
        @Override
        public int doRead(ByteChunk chunk, Request req )
            throws IOException {

            // 如果buf中的数据都处理完了，则继续从底层获取数据
            if (pos >= lastValid) {
                if (!fill())
                    return -1;
            }

            int length = lastValid - pos;
            chunk.setBytes(buf, pos, length);
            // 因为这里是读取请求体，在解析请求行，请求头时，pos是每解析一个字符就移动一下，
            // 而这里不一样，这里只是负责把请求体的数据读出来即可，对于tomcat来说并不用这部分数据，所以直接把pos移动到lastValid位置
            pos = lastValid;


            return (length);
        }
    }
}
