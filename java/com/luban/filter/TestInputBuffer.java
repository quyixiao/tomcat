package com.luban.filter;

import java.io.IOException;

public interface TestInputBuffer {
    public int doFilter(byte [] chunk ) throws IOException;
}
