package com.luban;

import org.apache.el.parser.ELParser;
import org.apache.el.parser.Node;

import java.io.StringReader;

public class TestEl {
    public static void main(String[] args) throws Exception {
        String expr = "ns:function(1,2)";
        Node n =  (new ELParser(new StringReader(expr))).CompositeExpression();
        System.out.println(n);
    }
}
