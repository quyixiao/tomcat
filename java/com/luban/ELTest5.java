package com.luban;

public class ELTest5 {


    public static void main(String[] args) {
        StringBuilder sb = new StringBuilder();
        for(int i = 128 ; i<255 ; i ++){
            char curChar = (char)i ;
            int hiByte = (int) (curChar >> 8);
            int i1 = hiByte >> 6;
            long l1 = 1L << (hiByte & 077);
            int i2 = (curChar & 0xff) >> 6;
            long l2 = 1L << (curChar & 077);

            if (!jjCanMove_1(hiByte, i1, i2, l1, l2)){
                sb.append(i ).append(" ");
            }


        }
        System.out.println(sb);
    }

    static final long[] jjbitVec0 = {
            0xfffffffffffffffeL, 0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffffffffffffffL
    };
    static final long[] jjbitVec2 = {
            0x0L, 0x0L, 0xffffffffffffffffL, 0xffffffffffffffffL
    };

    static final long[] jjbitVec3 = {
            0x1ff00000fffffffeL, 0xffffffffffffc000L, 0xffffffffL, 0x600000000000000L
    };
    static final long[] jjbitVec4 = {
            0x0L, 0x0L, 0x0L, 0xff7fffffff7fffffL
    };
    static final long[] jjbitVec5 = {
            0x0L, 0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffffffffffffffL
    };
    static final long[] jjbitVec6 = {
            0xffffffffffffffffL, 0xffffffffffffffffL, 0xffffL, 0x0L
    };
    static final long[] jjbitVec7 = {
            0xffffffffffffffffL, 0xffffffffffffffffL, 0x0L, 0x0L
    };
    static final long[] jjbitVec8 = {
            0x3fffffffffffL, 0x0L, 0x0L, 0x0L
    };


    private static final boolean jjCanMove_1(int hiByte, int i1, int i2, long l1, long l2) {
        switch (hiByte) {
            case 0:
                return ((jjbitVec4[i2] & l2) != 0L);
            case 48:
                return ((jjbitVec5[i2] & l2) != 0L);
            case 49:
                return ((jjbitVec6[i2] & l2) != 0L);
            case 51:
                return ((jjbitVec7[i2] & l2) != 0L);
            case 61:
                return ((jjbitVec8[i2] & l2) != 0L);
            default:
                if ((jjbitVec3[i1] & l1) != 0L)
                    return true;
                return false;
        }
    }


    private static final boolean jjCanMove_0(int hiByte, int i1, int i2, long l1, long l2) {
        switch (hiByte) {
            case 0:
                return ((jjbitVec2[i2] & l2) != 0L);
            default:
                if ((jjbitVec0[i1] & l1) != 0L)
                    return true;
                return false;
        }
    }
}
