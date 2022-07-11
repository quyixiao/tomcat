package com.luban.filter;

import java.io.IOException;

public interface TestInputBuffer {
    public int doRead(byte [] chunk ) throws IOException;
}
