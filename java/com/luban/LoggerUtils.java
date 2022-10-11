package com.luban;


import java.io.IOException;
import java.io.PrintWriter;
import java.io.StringWriter;


/****
 * 日志工具类，升级版
 */
public class LoggerUtils {




    /**
     * 不需要传递参数
     *
     * @param msg
     */
    public static void info(String msg) {
        System.out.println();
        System.out.println(msg);
    }

    /**
     * 不需要传递参数
     *
     * @param msg
     */
    public static void all(String msg) {
        Throwable throwable = new Throwable();
        StringBuffer sb = getStringBuffer(msg, throwable, -1);
        System.out.println(sb.toString());
    }

    /**
     * 不需要传递参数
     * @param msg
     */
    public static void info(String msg, int level) {
        try {
            Throwable throwable = new Throwable();
            StringBuffer sb = getStringBuffer(msg, throwable, level);
            System.out.println("【logstack】 " + sb.toString());
        } catch (Exception e) {
            e.printStackTrace();
        }
    }


    public static int getLevel(Throwable throwable) {
        return throwable.getStackTrace().length - 1;
    }

    private static StringBuffer getStringBuffer(String msg, Throwable throwable, int level) {
        StringBuilder cml = getRelate(throwable, level);
        String result = cml.toString().trim();
        if (result.endsWith("=>")) {
            result = result.substring(0, result.lastIndexOf("=>"));
        }
        StringBuffer sb = appendSb("	", result, "	", msg);
        return sb;
    }

    public static StringBuilder getRelate(Throwable throwable, int level) {
        StringBuilder cml = new StringBuilder();
        if (level <= 0 || level >= throwable.getStackTrace().length) {
            level = throwable.getStackTrace().length - 1;
        }

        getLationByN(throwable, cml, level);
        return cml;
    }

    private static void getLationByN(Throwable throwable, StringBuilder cml, int n) {
        for (int i = n; i > 0; i--) {
            getNLation(throwable, cml, i);
            cml.append(" => ");
        }
    }

    private static void getNLation(Throwable throwable, StringBuilder cml, int i) {
        String method = throwable.getStackTrace()[i].getMethodName();

        cml.append(method);
        cml.append(":");
        cml.append(getClassName(throwable.getStackTrace()[i].getClassName()));
        cml.append(":");
        cml.append(throwable.getStackTrace()[i].getLineNumber());

    }


    public static String dealException(Exception e) {
        StringWriter sw = null;
        String str = null;
        try {
            e.printStackTrace();
            sw = new StringWriter();
            //将出错的栈信息输出到printWriter中
            e.printStackTrace(new PrintWriter(sw, true));
            str = sw.toString();
            sw.flush();
        } finally {
            if (sw != null) {
                try {
                    sw.close();
                } catch (IOException e1) {
                    e1.printStackTrace();
                }
            }
        }
        return str;
    }


    public static String getClassName(String className) {
        if (isNotBlank(className) && className.contains(".")) {
            String classNames[] = className.split("\\.", className.length());
            if (classNames != null && classNames.length > 0) {
                return classNames[classNames.length - 1];
            }
        }
        return "";
    }


    public static boolean isNotBlank(String str) {
        return !isBlank(str);
    }

    public static boolean isBlank(String str) {
        if (str == null)
            return true;
        if (str.length() == 0 || str.equals("null"))
            return true;
        return false;
    }


    /**
     * 通过StringBuffer来组装字符串
     *
     * @param strings
     * @return
     */
    public static StringBuffer appendSb(Object... strings) {
        StringBuffer sb = new StringBuffer();
        for (Object str : strings) {
            sb.append(str);
        }
        return sb;
    }

    public static void test(){
        LoggerUtils.info("329832832" +"3232",2);
    }
    public static void main(String[] args) {

        test();
        //LoggerUtils loggerUtils = new LoggerUtils();

       // ManyTreeNode<RuleConfigEntity> nodes = new ManyTreeNode<>();

        //LoggerUtils.info("【递归生成规则树】consumerNo "  + "orderNo: "  + " manyTreeNod " + JSON.toJSONString(nodes),true);
//        test1();



    }

}
