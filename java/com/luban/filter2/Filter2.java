package com.luban.filter2;

public class Filter2 implements IFilter{


    @Override
    public void doFilter(FilterChain filterChain) {

        System.out.println("过滤器2执行");
        filterChain.doFilter();

    }
}
