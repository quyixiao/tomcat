package com.luban;

import java.util.HashMap;
import java.util.Map;

public class Test16Hx {
    public static void main(String[] args) {
        // 01 20        SourceDebugExtension
        // 01 00 14
        String a = "53 6F 75 72 63 65 44 65 62 75 67 45 78 74 65 6E 73 69 6F 6E";
        for(String d :  a.split(" ") ){
            System.out.print((char)(int)covert(d));
        }
        System.out.println();
        System.out.println("==================================================");
        String b0 =  "00 FB 00 00 00 82";
        String b = "53 4D 41 50 0A 69 6E 64 65 78 5F 6A 73 70 2E 6A 61 76 61 0A 4A 53 50 0A 2A 53 20 4A 53 50 0A 2A 46 0A 2B 20 30 20 69 6E 64 65 78 2E 6A 73 70 0A 69 6E 64 65 78 2E 6A 73 70 0A 2A 4C 0A 31 2C 39 3A 39 33 0A 39 3A 31 30 31 0A 31 31 2C 31 30 3A 31 30 33 0A 32 30 3A 31 31 33 0A 32 31 2C 32 3A 31 31 34 2C 33 0A 32 33 2C 35 3A 31 32 30 0A 32 37 3A 31 35 30 2C 39 0A 32 37 3A 31 32 36 0A 2A 45 0A";


        System.out.println("指向常量池中索引：" + covert("FB"));
        System.out.println("指向常量池中索引：" + covert("F9"));
        System.out.println("指向常量池中索引：" + covert("FA"));
        String c[]  = b.split(" ");
        System.out.println("长度为：" + c.length);
        for(String d :  c ){
            System.out.print((char)(int)covert(d));
        }

    }


    public static int covert(String content){
        int number=0;
        String [] HighLetter = {"A","B","C","D","E","F"};
        Map<String,Integer> map = new HashMap<>();
        for(int i = 0;i <= 9;i++){
            map.put(i+"",i);
        }
        for(int j= 10;j<HighLetter.length+10;j++){
            map.put(HighLetter[j-10],j);
        }
        String[]str = new String[content.length()];
        for(int i = 0; i < str.length; i++){
            str[i] = content.substring(i,i+1);
        }
        for(int i = 0; i < str.length; i++){
            number += map.get(str[i])*Math.pow(16,str.length-1-i);
        }
        return number;
    }

}
