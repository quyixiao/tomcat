package com.luban.filter;

import java.io.IOException;

public class TestInternalInputBuffer implements TestInputBuffer {

    boolean isEnd = false;
    byte[] buf = new byte[4];
    protected int lastAciveFilter = -1;
    protected TestInputFilter[] activeFilters = new TestInputFilter[2];
    TestInputBuffer inputBuffer =  new TestInputStreamInputBuffer();

    public void addActiveFilter(TestInputFilter filter) {
        if (lastAciveFilter == -1) {
            filter.setBuffer(inputBuffer);
        } else {
            for (int i = 0; i <= lastAciveFilter; i++) {
                if (activeFilters[i] == filter) {
                    return;
                }
            }
            filter.setBuffer(activeFilters[lastAciveFilter]);
        }
        activeFilters[++lastAciveFilter] = filter;
    }

    @Override
    public int doRead(byte[] chunk) throws IOException {
        if (lastAciveFilter == -1) {
            return inputBuffer.doRead(chunk);
        } else {
            return activeFilters[lastAciveFilter].doRead(chunk);
        }
    }


    protected class TestInputStreamInputBuffer implements TestInputBuffer {
        public int doRead(byte[] chunk) throws IOException {
            if (isEnd == false) {
                buf[0] = 'a';
                buf[1] = 'b';
                buf[2] = 'a';
                buf[3] = 'd';
                System.arraycopy(buf, 0, chunk, 0, 4);
                isEnd = true;
                return chunk.length;
            } else {
                return -1;
            }
        }
    }
}
