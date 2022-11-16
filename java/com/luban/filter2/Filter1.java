package com.luban.filter2;

public class Filter1  implements IFilter{


    @Override
    public void doFilter(FilterChain filterChain) {
        System.out.println("过滤器1执行");
        filterChain.doFilter();
    }
}
