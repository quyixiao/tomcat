package com.luban;

public class ELTest1 {

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("如果curChar 为 ");
        for (int i = 33; i < 128; i++) {
            char curChar = (char) i;
            long l = 1L << (curChar & 077);
            if ((0x7fffffe87fffffeL & l) == 0L){
                System.out.println("i = " + i + ",curChar   "+curChar);
                sb.append(curChar + " ");
            }
        }
        System.out.println(sb.toString());
    }
}
