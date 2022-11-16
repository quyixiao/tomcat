package com.luban.filter;

import java.io.IOException;

public class TestUpperFilter implements TestInputFilter{
    protected TestInputBuffer buffer;

    @Override
    public int doFilter(byte[] chunk) throws IOException {
        int i=   buffer.doFilter(chunk);
        if (i == -1 ){
            return -1 ;
        }
        for (int j = 0 ;j < chunk.length ;j ++){
            chunk[j] = (byte) (chunk[j] - 'a' + 'A');
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
