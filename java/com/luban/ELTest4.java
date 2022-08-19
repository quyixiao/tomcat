package com.luban;

public class ELTest4 {

    public static final int[] jjnewLexState = {
            -1, -1, 1, 1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, 0, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1, -1,
            -1, -1, -1, -1, -1, -1, -1,
    };


    static final int[] jjnextStates = {
            0, 1, 3, 4, 2, 0, 1, 4, 2, 0, 1, 4, 5, 2, 0, 1,
            2, 6, 16, 17, 18, 23, 24, 11, 12, 14, 6, 7, 9, 3, 4, 21,
            22, 25, 26,
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
       for(int i = 0 ;i < jjnextStates.length ;i ++){
           System.out.println(i + "="+jjnextStates[i]);
       }
    }
}
