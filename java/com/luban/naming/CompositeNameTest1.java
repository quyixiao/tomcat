package com.luban.naming;

import javax.naming.CompositeName;
import javax.naming.CompoundName;
import javax.naming.Name;
import java.util.Properties;

public class CompositeNameTest1 {
    public static void main(String[] args) throws Exception {
        String value = "'comp/env/jdbc/mysql'";
        System.out.println(value);
        Name name  = new CompositeName(value);
        String b = name.get(0);
        System.out.println( b);
        Name suffix_1 = name.getSuffix(1);
        System.out.println(suffix_1);
    }
}
