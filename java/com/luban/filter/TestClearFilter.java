package com.luban.filter;

import org.apache.coyote.InputBuffer;

import java.io.IOException;

public class TestClearFilter implements TestInputFilter {

    protected TestInputBuffer buffer;

    @Override
    public int doRead(byte[] chunk) throws IOException {
        int i = buffer.doRead(chunk);
        if (i == -1) {
            return -1;
        }
        for (int j = 0; j < chunk.length; j++) {
            if (chunk[j] == 'a') {
                chunk[j] = 'f';
            }
        }
        return i;
    }

    public TestInputBuffer getBuffer() {
        return buffer;
    }

    @Override
    public void setBuffer(TestInputBuffer buffer) {
        this.buffer = buffer;
    }
}
