package com.luban.filter2;

import java.util.ArrayList;
import java.util.List;

public class FilterChain {

    public static List<IFilter> filters = new ArrayList<>();

    public void addFilter(IFilter filter) {
        filters.add(filter);
    }

    private int index;

    public void doFilter() {
        if (index > filters.size() - 1) {
            System.out.println("过滤器 已经执行完了");
            return;
        }
        filters.get(index++).doFilter(this);
    }

    public static void main(String[] args) {
        Filter1 filter1 = new Filter1();
        Filter2 filter2 = new Filter2();
        FilterChain filterChain = new FilterChain();
        filterChain.addFilter(filter1);
        filterChain.addFilter(filter2);
        filterChain.doFilter();
    }

}
