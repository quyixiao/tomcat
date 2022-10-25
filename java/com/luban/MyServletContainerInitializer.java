package com.luban;

import javax.servlet.Filter;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;
import java.util.Set;

@HandlesTypes(value = {Filter.class})
public class MyServletContainerInitializer implements ServletContainerInitializer {
    // Set<Class<?>> set获取到的class
    // servletContext servlet上下文
    @Override
    public void onStartup(Set<Class<?>> set, ServletContext servletContext) throws ServletException {
        for (Class clazz : set) {
            System.out.println("获取的class..." + clazz);
        }
    }
}