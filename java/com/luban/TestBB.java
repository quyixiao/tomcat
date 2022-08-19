package com.luban;

public class TestBB {

    public static void main(String[] args) {
        long [] a1 = new long[]{0x200000000L,0x2000000000L,0x20000000L,0x80000000L,0x8000000L,0x4000000000L,0x1000000000000L,
                0x20100000000L, 0x2000L,0x11000000L,0x40000000000L,0x44000000L, 0x4000000000000L,0x1400004000L,0x10000000000L,
                0x1000L,0x8000000000L };
        long [] a2 = new long[]{0x2000L, 0x1000000000000L, 0x20000000000L, 0x44000000000L, 0x4001000000000L,0x1000L,0x4000L};
        for(int i = 0 ;i < a1.length ;i ++){
            for(int j = 0 ;j < a2.length ;j ++){
                long a11 = a1[i];
                long a22 = a2[j];
                if (((a11 & a22)) == 0L){
                    System.out.println("i="+i + ",j="+j + ",0x" + Long.toHexString(a11)+ "L,0x" + Long.toHexString(a22)  + "L," + Long.toBinaryString(a11) + ","
                            + Long.toBinaryString(a22) );
                }
            }
        }
    }
}
