package com.luban.naming;

import javax.naming.NamingException;
import java.util.StringTokenizer;

public class StringTokenizerTest {

    public static void main(String[] args) {
        StringTokenizer tokenizer = new StringTokenizer("jdbc/mysql/myDB", "/");
        while (tokenizer.hasMoreTokens()) {
            String token = tokenizer.nextToken();
            if ((!token.equals("")) && (tokenizer.hasMoreTokens())) {
                System.out.println(token);
            }
        }
    }
}
