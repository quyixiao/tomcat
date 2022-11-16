package com.luban;

import org.apache.tomcat.util.buf.CharChunk;

public class TestFound {

    public static void main(String[] args) {
        Wrapper wrapper = new Wrapper("aa", null);
        Wrapper wrapper1 = new Wrapper("ab", null);
        Wrapper wrapper2 = new Wrapper("bc", null);
        Wrapper[] wrappers = new Wrapper[]{wrapper, wrapper1, wrapper2};

        CharChunk charChunk = new CharChunk();
        charChunk.setChars(new char[]{'b', 'c'}, 0, 2);
        System.out.println(charChunk.getBuffer());
        System.out.println(find(wrappers, charChunk, 0, 2));

    }


    protected abstract static class MapElement {

        public final String name;
        public final Object object;

        public MapElement(String name, Object object) {
            this.name = name;
            this.object = object;
        }
    }


    protected static class Wrapper extends MapElement {


        public Wrapper(String name, /* Wrapper */Object wrapper) {
            super(name, wrapper);
        }
    }

    private static final int find(MapElement[] map, CharChunk name,
                                  int start, int end) {

        int a = 0;     // 开始位置
        int b = map.length - 1; // 结束位置

        // Special cases: -1 and 0
        if (b == -1) {
            return -1;
        }

        // 因为map是一个排好序了的数组，所以先比较name是不是小于map[0].name，如果小于那么肯定在map中不存在name了
        if (compare(name, start, end, map[0].name) < 0) {
            return -1;
        }
        // 如果map的长度为0，则默认取map[0]
        if (b == 0) {
            return 0;
        }

        int i = 0;
        while (true) {
            i = (b + a) / 2; // 折半
            int result = compare(name, start, end, map[i].name);
            if (result == 1) { // 如果那么大于map[i].name，则表示name应该在右侧，将a变大为i
                a = i;
            } else if (result == 0) { // 相等
                return i;
            } else {
                b = i; // 将b缩小为i
            }
            if ((b - a) == 1) { // 表示缩小到两个元素了，那么取b进行比较
                int result2 = compare(name, start, end, map[b].name);
                if (result2 < 0) { // name小于b,则返回a
                    return a;
                } else {
                    return b;  // 否则返回b
                }
            }
        }

    }


    /**
     * Compare given char chunk with String.
     * Return -1, 0 or +1 if inferior, equal, or superior to the String.
     */
    private static final int compare(CharChunk name, int start, int end,
                                     String compareTo) {
        int result = 0;
        char[] c = name.getBuffer();
        int len = compareTo.length();
        if ((end - start) < len) {
            len = end - start;
        }
        for (int i = 0; (i < len) && (result == 0); i++) {
            if (c[i + start] > compareTo.charAt(i)) {
                result = 1;
            } else if (c[i + start] < compareTo.charAt(i)) {
                result = -1;
            }
        }
        if (result == 0) {
            if (compareTo.length() > (end - start)) {
                result = -1;
            } else if (compareTo.length() < (end - start)) {
                result = 1;
            }
        }
        return result;
    }


}
