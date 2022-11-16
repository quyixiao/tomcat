package com.luban;

import org.apache.catalina.connector.Request;
import org.apache.catalina.connector.Response;
import org.apache.catalina.valves.ValveBase;

import javax.servlet.ServletException;
import java.io.IOException;

public class PrintIpValve  extends ValveBase {
    @Override
    public void invoke(Request request, Response response) throws IOException, ServletException {

        System.out.println(request.getRemoteAddr());
        getNext().invoke(request,response);

    }
}
