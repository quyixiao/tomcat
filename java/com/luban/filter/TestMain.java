package com.luban.filter;

public class TestMain {
    public static void main(String[] args) throws Exception {
        TestInternalInputBuffer inputBuffer = new TestInternalInputBuffer();

        TestClearFilter clearFilter = new TestClearFilter();
        TestUpperFilter upperFilter = new TestUpperFilter();
        inputBuffer.addFilter(clearFilter);
        inputBuffer.addFilter(upperFilter);

        byte[] chunk = new byte[4];
        int i = 0;
        while (i != -1) {
            i = inputBuffer.doFilter(chunk);
            if (i == -1) {
                break;
            }
        }
        System.out.println(new String(chunk));
    }
}
