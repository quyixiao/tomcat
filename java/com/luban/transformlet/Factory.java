package com.luban.transformlet;


public class Factory {


    public static String NewInstruction(int opcode) {
        switch (opcode) {
            case 0x00:
                return "NOP";
            case 0x01:
                return "ACONST_NULL";
            case 0x02:
                return "ICONST_M1";
            case 0x03:
                // 例如指令iconst_<i> 可以压入int常量-1，0，1，2，3，4，5 ,const_0 表示把int类型的0值压入操作数栈
                // 这样iconst_0就不需要专门为入栈操作数直接操作数值了，而且也避免了操作数的读取和解析步骤，在本例子中，把压入
                // 0这个操作的指令由iconst_0改为bipush0 也能获取正确的结果，但是编译码会因此额外增加了1个字节的长度，简单实现
                // 的虚拟机可能要在每次循环时消耗更多的时间用于获取和解析这个操作数，因此使用隐式的操作数可以让编译后的代码更加简洁，更加高效
                return "ICONST_0";
            case 0x04:
                return "ICONST_1";            // 将1压入操作数栈
            case 0x05:                              //
                return "ICONST_2";
            case 0x06:
                return "ICONST_3";
            case 0x07:
                return "ICONST_4";
            case 0x08:
                return "ICONST_5";
            case 0x09:
                return "LCONST_0";
            case 0x0a:
                return "LCONST_1";
            case 0x0b:
                return "FCONST_0";
            case 0x0c:                              //
                return "FCONST_1";
            case 0x0d:
                return "FCONST_2";
            case 0x0e:
                return "DCONST_0";
            case 0x0f:
                return "DCONST_1";
            case 0x10:                                            // 16 * 1 + 0  = 16
                return "BIPUSH";
            case 0x11:
                return "SIPUSH";
            // ldc系列指令从运行时常量池中加载常量值，并把它推入操作 数栈。ldc系列指令属于常量类指令，共3条。其中ldc和ldc_w指令用 于加载int、float和字符串常量
            case 0x12:
                return "LDC";
            case 0x13:
                return "LDC_W";
            case 0x14:
                return "LDC2_W";
            case 0x15:
                return "ILOAD";
            case 0x16:
                return "LLOAD";
            case 0x17:
                return "FLOAD";
            case 0x18:
                return "DLOAD";
            case 0x19:
                return "ALOAD";
            case 0x1a:
                return "ILOAD_0";
            case 0x1b:
                // iload_1指令的作用是将第一个局部变量压入到操作数栈  16 + 11 = 27
                return "ILOAD_1";
            case 0x1c:
                return "ILOAD_2";
            case 0x1d:
                return "ILOAD_3";
            case 0x1e:
                return "LLOAD_0";
            case 0x1f:
                return "LLOAD_1";
            case 0x20:
                return "LLOAD_2";
            case 0x21:
                return "LLOAD_3";
            case 0x22:
                return "FLOAD_0";
            case 0x23:
                return "FLOAD_1";
            case 0x24:
                return "FLOAD_2";
            case 0x25:
                return "FLOAD_3";
            case 0x26:
                return "DLOAD_0";
            case 0x27:
                return "DLOAD_1";
            case 0x28:
                return "DLOAD_2";
            case 0x29:
                return "DLOAD_3";
            case 0x2a:
                return "ALOAD_0";
            case 0x2b:
                return "ALOAD_1";
            case 0x2c:
                return "ALOAD_2";
            case 0x2d:
                return "ALOAD_3";
            case 0x2e:
                return "IALOAD";
            case 0x2f:
                return "LALOAD";
            case 0x30:
                return "FALOAD";
            case 0x31:
                return "DALOAD";
            case 0x32:
                return "AALOAD";
            case 0x33:
                return "BALOAD";
            case 0x34:
                return "CALOAD";
            case 0x35:
                return "SALOAD";
            case 0x36:
                return "ISTORE";
            case 0x37:
                return "LSTORE";
            case 0x38:
                return "FSTORE";
            case 0x39:
                return "DSTORE";
            case 0x3a:          // astore
                return "ASTORE";
            case 0x3b:
                return "ISTORE_0";
            case 0x3c:                                                        // 3 * 16 + 12 = 60
                // istore_1 指令的作用是从操作数栈中弹出一个int类型的值，并保存在第一个局部变量中
                return "ISTORE_1";
            case 0x3d:                                    // 3 * 16 + 13 = 61
                return "ISTORE_2";
            case 0x3e:
                return "ISTORE_3";
            case 0x3f:
                return "LSTORE_0";
            case 0x40:
                return "LSTORE_1";
            case 0x41:
                return "LSTORE_2";
            case 0x42:
                return "LSTORE_3";
            case 0x43:
                return "FSTORE_0";
            case 0x44:
                return "FSTORE_1";
            case 0x45:
                return "FSTORE_2";
            case 0x46:
                return "FSTORE_3";
            case 0x47:
                return "DSTORE_0";
            case 0x48:
                return "DSTORE_1";
            case 0x49:
                return "DSTORE_2";
            case 0x4a:
                return "DSTORE_3";
            case 0x4b:
                return "ASTORE_0";
            case 0x4c:
                return "ASTORE_1";
            case 0x4d:              // 4 * 16 + 13 = 64 + 13 = 77
                return "ASTORE_2";
            case 0x4e:
                return "ASTORE_3";
            case 0x4f:
                return "IASTORE";
            case 0x50:
                return "LASTORE";
            case 0x51:
                return "FASTORE";
            case 0x52:
                return "DASTORE";
            case 0x53:
                return "AASTORE";
            case 0x54:
                return "BASTORE";
            case 0x55:
                return "CASTORE";
            case 0x56:
                return "SASTORE";
            case 0x57:
                return "POP";
            case 0x58:
                return "POP2";
            case 0x59:
                return "DUP";
            case 0x5a:
                return "DUP_X1";
            case 0x5b:
                return "DUP_X2";
            case 0x5c:
                return "DUP2";
            case 0x5d:
                return "DUP2_X1";
            case 0x5e:
                return "DUP2_X2";
            case 0x5f:
                return "SWAP";
            case 0x60:
                return "IADD";
            case 0x61:
                return "LADD";
            case 0x62:
                return "FADD";
            case 0x63:
                return "DADD";
            case 0x64:
                return "ISUB";
            case 0x65:
                return "LSUB";
            case 0x66:
                return "FSUB";
            case 0x67:
                return "DSUB";
            case 0x68:
                return "IMUL";
            case 0x69:
                return "LMUL";
            case 0x6a:
                return "FMUL";
            case 0x6b:
                return "DMUL";
            case 0x6c:
                return "IDIV";
            case 0x6d:
                return "LDIV";
            case 0x6e:
                return "FDIV";
            case 0x6f:
                return "DDIV";
            case 0x70:
                return "IREM";
            case 0x71:
                return "LREM";
            case 0x72:
                return "FREM";
            case 0x73:
                return "DREM";
            case 0x74:
                return "INEG";
            case 0x75:
                return "LNEG";
            case 0x76:
                return "FNEG";
            case 0x77:
                return "DNEG";
            case 0x78:
                return "ISHL";
            case 0x79:
                return "LSHL";
            case 0x7a:
                return "ISHR";
            case 0x7b:
                return "LSHR";
            case 0x7c:
                return "IUSHR";
            case 0x7d:
                return "LUSHR";
            case 0x7e:
                return "IAND";
            case 0x7f:
                return "LAND";
            case 0x80:
                return "IOR";
            case 0x81:
                return "LOR";
            case 0x82:
                return "IXOR";
            case 0x83:
                return "LXOR";
            case 0x84:
                return "IINC";
            case 0x85:
                return "I2L";
            case 0x86:
                return "I2F";
            case 0x87:
                return "I2D";
            case 0x88:
                return "L2I";
            case 0x89:
                return "L2F";
            case 0x8a:
                return "L2D";
            case 0x8b:
                return "F2I";
            case 0x8c:
                return "F2L";
            case 0x8d:
                return "F2D";
            case 0x8e:
                return "D2I";
            case 0x8f:
                return "D2L";
            case 0x90:
                return "D2F";
            case 0x91:
                return "I2B";
            case 0x92:
                return "I2C";
            case 0x93:
                return "I2S";
            case 0x94:
                return "LCMP";
            case 0x95:
                return "FCMPL";
            case 0x96:
                return "FCMPG";
            case 0x97:
                return "DCMPL";
            case 0x98:
                return "DCMPG";
            //条件分支
            case 0x99:
                return "IFEQ";
            case 0x9a:
                return "IFNE";
            case 0x9b:
                return "IFLT";
            case 0x9c:
                return "IFGE";
            case 0x9d:
                return "IFGT";
            case 0x9e:
                return "IFLE";
            case 0x9f:
                return "IF_ICMPEQ";
            case 0xa0:
                return "IF_ICMPNE";
            case 0xa1:
                return "IF_ICMPLT";
            case 0xa2:
                return "IF_ICMPGE";
            case 0xa3:
                return "IF_ICMPGT";
            case 0xa4:
                return "IF_ICMPLE";
            case 0xa5:
                return "IF_ACMPEQ";
            case 0xa6:
                return "IF_ACMPNE";
            case 0xa7:
                return "GOTO";
            // case 0xa8:
            // 	return &JSR{}
            // case 0xa9:
            // 	return &RET{}
            // 复合条件分支
            case 0xaa:
                return "TABLE_SWITCH";
            case 0xab:
                return "LOOKUP_SWITCH";
            case 0xac:
                 return "IRETURN";
            case 0xad:
                return "LRETURN";
            case 0xae:
                return "FRETURN";
            case 0xaf:
                return "DRETURN";
            case 0xb0:
                return "ARETURN";
            case 0xb1:
                return "RETURN";
            case 0xb2:
                return "GET_STATIC";
            case 0xb3:
                return "PUT_STATIC";
            case 0xb4:
                return "GET_FIELD";

            case 0xb5:
                return "PUT_FIELD";
            case 0xb6:
                return "INVOKE_VIRTUAL";
            case 0xb7:
                return "INVOKE_SPECIAL";
            case 0xb8:                             // 17 * 16 = 176 + 8 = 184
                return "INVOKE_STATIC ";
            case 0xb9:
                return "INVOKE_INTERFACE";
          //   case 0xba:
//             	return &INVOKE_DYNAMIC{}
            case 0xbb: //
                return "NEW";
            case 0xbc:
                return "NEW_ARRAY";
            case 0xbd:
                return "ANEW_ARRAY";
            case 0xbe:
                return "ARRAY_LENGTH";
            case 0xbf:
                return "ATHROW";            //
            case 0xc0:
                return "CHECK_CAST";
            case 0xc1:
                return "INSTANCE_OF";
            case 0xc2:
                return "MONITOR_ENTER";
            case 0xc3:
                return "MONITOR_EXIT";
            case 0xc4:
                return "WIDE ";
            case 0xc5:
                return "MULTI_ANEW_ARRAY";
            case 0xc6:
                return "IFNULL";
            case 0xc7:
                return "IFNONNULL";
            //无条件分支：goto ,goto_w ,jsr,jsr_w,ret
            case 0xc8:
                return "GOTO_W";
            // case 0xc9:
            // 	return &JSR_W{}
            // case 0xca: breakpoint
             case 0xfe:
               return "INVOKE_NATIVE";
            // case 0xff: impdep2
            default:
        }
        return null;
    }

}
