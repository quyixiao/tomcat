package com.luban;

import org.apache.tomcat.util.buf.CharChunk;

public class NthSlashTest {

    public static void main(String[] args) {
        CharChunk charChunk = new CharChunk();
        charChunk.setChars(new char[]{'a', '/','c'}, 0, 3);

        System.out.println(nthSlash(charChunk,1));
    }


    private static final int nthSlash(CharChunk name, int n) {

        char[] c = name.getBuffer();
        int end = name.getEnd();
        int start = name.getStart();
        int pos = start;
        int count = 0;

        while (pos < end) {
            if ((c[pos++] == '/') && ((++count) == n)) {
                pos--;
                break;
            }
        }

        return (pos);

    }
}
