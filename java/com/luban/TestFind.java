package com.luban;

public class TestFind {


    public static void main(String[] args) {
        String [] array = new String []{"a","b","d","e","f","g"};
        int index = find(array,"c");
        System.out.println(index);
    }

    private static final int find(String[] map, String name) {

        int a = 0;
        int b = map.length - 1;

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }
        if (name.compareTo(map[0]) < 0) {
            return -1;
        }
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) >>> 1;
            int result = name.compareTo(map[i]);
            if (result > 0) {
                a = i;
            } else if (result == 0) {
                return i;
            } else {
                b = i;
            }
            if ((b - a) == 1) {
                int result2 = name.compareTo(map[b]);
                if (result2 < 0) {
                    return a;
                } else {
                    return b;
                }
            }
        }

    }


}
