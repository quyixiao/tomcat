package com.luban;

import org.apache.tomcat.util.buf.ByteChunk;

import java.io.FileNotFoundException;
import java.io.FileOutputStream;
import java.io.IOException;

public class TestOutputBuffer implements ByteChunk.ByteOutputChannel {
    private ByteChunk fileBuffer;
    private FileOutputStream fileOutputStream;

    public TestOutputBuffer() {
        this.fileBuffer = new ByteChunk();
        fileBuffer.setByteOutputChannel(this);
        fileBuffer.allocate(3, 7);
        try {
            fileOutputStream = new FileOutputStream("/Users/quyixiao/gitlab/tomcat/conf/hello.txt");
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }
    }

    @Override
    public void realWriteBytes(byte[] buf, int off, int len) throws IOException {
        fileOutputStream.write(buf, off, len);
    }

    public void flush() throws IOException {
        fileBuffer.flushBuffer();
    }

    public int dowrite(byte[] bytes) throws IOException {
        for (int i = 0; i < bytes.length; i++) {
            fileBuffer.append(bytes[i]);
        }
        return bytes.length;
    }

    public static void main(String[] args) throws Exception {
        TestOutputBuffer testOutputBuffer = new TestOutputBuffer();
        byte[] bytes = {1, 2, 3, 4, 5, 6, 7, 8};
        testOutputBuffer.dowrite(bytes);
        Thread.sleep(10 * 1000);
        testOutputBuffer.flush();
    }

}
