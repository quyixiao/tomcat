package com.luban.naming;

import javax.naming.*;
import java.util.Properties;

public class CompositeNameTest {
    public static void main(String[] args) throws Exception {
        Properties properties = new Properties();
        properties.put("jndi.syntax.beginquote","\"");
        properties.put("jndi.syntax.endquote","\"");

        properties.put("jndi.syntax.beginquote2","\'");
        properties.put("jndi.syntax.endquote2","\'");
        properties.put("jndi.syntax.direction","left_to_right");
        //properties.put("jndi.syntax.direction","right_to_left");
        properties.put("jndi.syntax.separator","/");
        properties.put("jndi.syntax.escape","|");
        properties.put("jndi.syntax.separator.typeval","\\");
        String value = "\\\'comp'/env/jdbc/mysql";
        System.out.println(value);
        //String value = "/";

        Name name  = new CompoundName(value, properties);
        String b = name.get(0);
        System.out.println("===========" + b);
        Name suffix_1 = name.getSuffix(1);
        System.out.println("++++++"+suffix_1);
    }
}
