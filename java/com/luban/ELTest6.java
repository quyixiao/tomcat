package com.luban;

import org.apache.catalina.tribes.util.Arrays;

public class ELTest6 {

   public static String IDENTIFIER = "<IDENTIFIER>";                                         // 51
   public static String FUNCTIONSUFFIX = "<FUNCTIONSUFFIX>";                                 // 52
   public static String IMPL_OBJ_START = "#";                                                // 53
   public static String LETTER = "<LETTER>";                                                 // 54
   public static String DIGIT = "<DIGIT>";                                                   // 55
   public static String ILLEGAL_CHARACTER = "<ILLEGAL_CHARACTER>";                           // 56
    public static void main(String[] args) {

        long [] a1 = new long[]{0x200000000L,0x2000000000L,0x20000000L,0x80000000L,0x8000000L,0x4000000000L,0x1000000000000L,
                0x20100000000L, 0x2000L,0x11000000L,0x40000000000L,0x44000000L, 0x4000000000000L,0x1400004000L,0x10000000000L,
                0x1000L,0x8000000000L };
        char ac [] = new char[]{'!','&','<','=','>','a','d','e','f','g','i','l','m','n','o','t','|' };
        char ad [] = new char[]{'a','i','m','n','o','r','u'};
        StringBuilder sb = new StringBuilder();
        for(char a: ac){
            sb.append(a).append(" ");
        }
        StringBuilder sb2 = new StringBuilder();
        for(char b : ad){
            sb2.append(b).append(" ");
        }
        System.out.println(sb);
        System.out.println(sb2);
                long a2[] = new long[]{0x2000L,0x1000000000000L,0x20000000000L,0x44000000000L,0x4001000000000L,0x1000L,0x4000L};
        System.out.println(ac.length   + "," + a1.length);

        StringBuilder sb3 = new StringBuilder();
        for(int i = 0 ;i < a1.length ; i ++){
            for(int j = 0 ;j < ad.length ;j ++){
                jjMoveStringLiteralDfa2_1(a1[i], a2[j], ac[i],ad[j],sb3);
            }

        }
        System.out.println(sb3);

    }


    private static int jjMoveStringLiteralDfa2_1(long old0, long active0 ,char c,char d ,StringBuilder sb3) {
        if (((active0 &= old0)) == 0L) {
            //System.out.println("满足条件 " + c  + d);
            return  -2;//jjStartNfa_1(0, old0);
        }
        sb3.append(c).append(d).append(" ");


        long a1[] = new long[]{0x6000L,0x20000000000L,0x40000000000L,0x1000L};
        char c1 [ ] = new char[]{'l','p','s','u'};
        for(int i = 0 ;i < a1.length ; i ++){

            jjMoveStringLiteralDfa3_1(active0,a1[i], c  +"" + d , c1[i]);
        }
        return -1;
    }





    private static int jjMoveStringLiteralDfa3_1(long old0, long active0,String c, char c1) {
        if (((active0 &= old0)) == 0L){
            return jjStartNfa_1(1, old0);
        }
        System.out.println("=================================" + c + ""+c1 );
        return -2 ;
    }


    private static final int jjStartNfa_1(int pos, long active0) {
        int caseIndex = jjStopStringLiteralDfa_1(pos, active0);
        //System.out.println("caseIndex = " + caseIndex);
        return -1;
    }

    private static final int jjStopStringLiteralDfa_1(int pos, long active0) {
        int jjmatchedPos = 0 ;
        String jjmatchedKind = "";
        switch (pos) {
            case 0:
                if ((active0 & 0x10000L) != 0L)
                    return 1;
                if ((active0 & 0x5075555007000L) != 0L) {
                    jjmatchedKind = IDENTIFIER;
                    System.out.println("============" + 0);
                    return 30;
                }
                return -1;
            case 1:
                if ((active0 & 0x5065000007000L) != 0L) {
                    jjmatchedKind = IDENTIFIER;
                    jjmatchedPos = 1;
                    System.out.println("============" + 1);
                    return 30;
                }
                if ((active0 & 0x10555000000L) != 0L)
                    return 30;
                return -1;
            case 2:
                if ((active0 & 0x5005000000000L) != 0L)
                    return 30;
                if ((active0 & 0x60000007000L) != 0L) {
                    jjmatchedKind = IDENTIFIER;
                    jjmatchedPos = 2;
                    System.out.println("============" + 2);
                    return 30;
                }
                return -1;
            case 3:
                if ((active0 & 0x5000L) != 0L)
                    return 30;
                if ((active0 & 0x60000002000L) != 0L) {
                    jjmatchedKind = IDENTIFIER;
                    jjmatchedPos = 3;
                    System.out.println("============" + 3);
                    return 30;
                }
                return -1;
            case 4:
                if ((active0 & 0x40000000000L) != 0L) {
                    jjmatchedKind = IDENTIFIER;
                    jjmatchedPos = 4;
                    System.out.println("============" + 4);
                    return 30;
                }
                if ((active0 & 0x20000002000L) != 0L)
                    return 30;
                return -1;
            case 5:
                if ((active0 & 0x40000000000L) != 0L) {
                    jjmatchedKind = IDENTIFIER;
                    jjmatchedPos = 5;
                    System.out.println("============" + 5);
                    return 30;
                }
                return -1;
            case 6:
                if ((active0 & 0x40000000000L) != 0L) {
                    jjmatchedKind = IDENTIFIER;
                    jjmatchedPos = 6;
                    System.out.println("============" + 6);
                    return 30;
                }
                return -1;
            case 7:
                if ((active0 & 0x40000000000L) != 0L) {
                    jjmatchedKind = IDENTIFIER;
                    jjmatchedPos = 7;
                    System.out.println("============" + 7);
                    return 30;
                }
                return -1;
            case 8:
                if ((active0 & 0x40000000000L) != 0L) {
                    jjmatchedKind = IDENTIFIER;
                    jjmatchedPos = 8;
                    System.out.println("============" + 8);
                    return 30;
                }
                return -1;
            default:
                return -1;
        }
    }
}
