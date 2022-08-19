package com.luban;

import org.apache.el.parser.ELParser;

public class ELTest3 {

    static final long[] jjtoToken = {
            0x11ffffffffffb0fL,
    };



   public  static  String[] tokenImage = {
            "<EOF>",
            "<LITERAL_EXPRESSION>",
            "${",
            "#{",
            " ",
            "\\t",
            "\\n",
            "\\r",
            "<INTEGER_LITERAL>",
            "<FLOATING_POINT_LITERAL>",
            "<EXPONENT>",
            "<STRING_LITERAL>",
            "true",
            "false",
            "null",
            "}",
            ".",
            "(",
            ")",
            "[",
            "]",
            ":",
            ",",
            ">",
            "gt",
            "<",
            "lt",
            ">=",
            "ge",
            "<=",
            "le",
            "==",
            "eq",
            "!=",
            "ne",
            "!",
            "not",
            "&&",
            "and",
            "||",
            "or",
            "empty",
            "instanceof",
            "*",
            "+",
            "-",
            "?",
            "/",
            "div",
            "%",
            "mod",
            "<IDENTIFIER>",
            "<FUNCTIONSUFFIX>",
            "#",
            "<LETTER>",
            "<DIGIT>",
            "<ILLEGAL_CHARACTER>",
    };


    public static void main(String[] args) {
        System.out.println(tokenImage.length);
        for(int i = 0 ; i < 57 ;i ++){
            if ((jjtoToken[i >> 6] & (1L << (i & 077))) != 0L){

            }else{
                System.out.println(i + ", " + tokenImage[i]);
            }
        }
    }
}
