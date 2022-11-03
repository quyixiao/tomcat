package com.luban;

import org.apache.catalina.core.ApplicationContext;
import org.apache.catalina.core.ApplicationContextFacade;
import org.apache.catalina.core.StandardContext;

import javax.servlet.Filter;
import javax.servlet.ServletContainerInitializer;
import javax.servlet.ServletContext;
import javax.servlet.ServletException;
import javax.servlet.annotation.HandlesTypes;
import java.lang.reflect.Field;
import java.util.Set;

@HandlesTypes(value = { Filter.class})
public class MyServletContainerInitializer implements ServletContainerInitializer {
    // Set<Class<?>> set获取到的class
    // servletContext servlet上下文
    @Override
    public void onStartup(Set<Class<?>> set, ServletContext servletContext) throws ServletException {

        if (servletContext instanceof ApplicationContextFacade) {
            try {
                Field field = ApplicationContextFacade.class.getDeclaredField("context");
                field.setAccessible(true);
                ApplicationContext applicationContext = (ApplicationContext) field.get(servletContext);
                System.out.println(applicationContext);
                Field contextF = ApplicationContext.class.getDeclaredField("context");

                contextF.setAccessible(true);
                StandardContext standardContext = (StandardContext) contextF.get(applicationContext);

                System.out.println(standardContext);
                standardContext.addContainerListener(new BeforeContextInitializedContainerListener());


            } catch (Exception e) {
                e.printStackTrace();
            }
        }
    }
}