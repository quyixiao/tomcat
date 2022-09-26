package com.luban;

import java.io.*;

public class ClassReaderUtils {



    public static void writeBytesToFile(byte [] bs , String createPath) throws IOException{

        OutputStream out = new FileOutputStream(createPath);
        InputStream is = new ByteArrayInputStream(bs);
        byte[] buff = new byte[1024];
        int len = 0;
        while((len=is.read(buff))!=-1){
            out.write(buff, 0, len);
        }
        is.close();
        out.close();
    }


    public static byte[] readClass(String path) {
        try {
            InputStream is = new FileInputStream(path);
            return readClass(is);
        } catch (FileNotFoundException e) {
            e.printStackTrace();
        }

        return null;

    }

    public static byte[] readClass(final InputStream is) {
        try {
            if (is == null) {
                return null;
            }
            byte[] b = new byte[is.available()];
            int len = 0;
            while (true) {
                int n = is.read(b, len, b.length - len);
                if (n == -1) {
                    if (len < b.length) {
                        byte[] c = new byte[len];
                        System.arraycopy(b, 0, c, 0, len);
                        b = c;
                    }
                    return b;
                }
                len += n;
                if (len == b.length) {
                    int last = is.read();
                    if (last < 0) {
                        return b;
                    }
                    byte[] c = new byte[b.length + 1000];
                    System.arraycopy(b, 0, c, 0, len);
                    c[len++] = (byte) last;
                    b = c;
                }
            }
        } catch (Exception e) {
            e.printStackTrace();
        } finally {
            try {
                is.close();
            } catch (IOException e) {
                e.printStackTrace();
            }
        }


        return null;
    }
}
