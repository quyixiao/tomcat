package com.luban;

public class ELTest2 {

    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder("如果curChar 为 ");
        for (int i = 0 ;i < 64; i++) {
            char curChar = (char) i;
            long l = 1L << curChar;
            if ((0x3ff001000000000L & l) == 0L){
                System.out.println("i = " + i + ",curChar   "+curChar);
                sb.append(curChar + " ");
            }
        }
        System.out.println(sb.toString());
    }
}
