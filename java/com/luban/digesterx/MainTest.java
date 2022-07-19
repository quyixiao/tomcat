package com.luban.digesterx;

import jdk.nashorn.internal.runtime.JSONFunctions;
import org.apache.tomcat.util.digester.Digester;
import org.xml.sax.SAXException;

import java.io.File;
import java.io.IOException;

public class MainTest {

    public static void main(String[] args) {
        Digester digester = new Digester();
        digester.setValidating(false);
        digester.setRulesValidation(true);
        // 匹配department节点时，创建Department对象
        digester.addObjectCreate("department", Department.class);
        // 匹配department节点时，设置对象的属性
        digester.addSetProperties("department");
        // 匹配department/user节点时，创建User对象
        digester.addObjectCreate("department/user",User.class);
        // 匹配department/user节点时，设置对象属性
        digester.addSetProperties("department/user");

        // 匹配department/user节点，调用Department对象的addUser
        digester.addSetNext("department/user","addUser");
        // 匹配department/extension节点时，调用Department对象的putExtension方法
        digester.addCallMethod("department/extension","putExtension",2);
        // 调用方法的第一个参数为节点department/extension/property-name的内容
        digester.addCallParam("department/extension/property-name",0);
        // 调用方法的第二个参数为节点 department/extension/property-value的内容
        digester.addCallParam("department/extension/property-value",1);
        try {
            Department department = (Department) digester.parse(new File("/Users/quyixiao/gitlab/tomcat/java/com/luban/digesterx/test.xml"));

            System.out.println(department);
        } catch (Exception e) {
            e.printStackTrace();
        }


    }
}
