package com.luban;

import org.apache.catalina.comet.CometEvent;
import org.apache.catalina.comet.CometProcessor;

import javax.servlet.ServletException;
import javax.servlet.http.HttpServlet;
import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import java.io.IOException;
import java.io.InputStream;
import java.util.ArrayList;

public class CometServlet extends HttpServlet implements CometProcessor {


    protected ArrayList connections = new ArrayList();

    @Override
    public void event(CometEvent event) throws IOException, ServletException {
        HttpServletRequest request = event.getHttpServletRequest();
        HttpServletResponse response = event.getHttpServletResponse();
        if (event.getEventType() == CometEvent.EventType.BEGIN) {
            synchronized (connections) {
                connections.add(response);
            }
        } else if (event.getEventType() == CometEvent.EventType.ERROR) {
            synchronized (connections) {
                connections.remove(response);
            }
        } else if (event.getEventType() == CometEvent.EventType.END) {
            synchronized (connections) {
                connections.remove(response);
            }
        } else if (event.getEventType() == CometEvent.EventType.READ) {
            synchronized (connections) {
                InputStream is = request.getInputStream();
                byte[] buf = new byte[512];
                do {
                    int n = is.read(buf);
                    if (n > 0) {
                        System.out.println(new String(buf, 0, n));
                    } else if (n < 0) {
                        return;
                    }
                } while (is.available() > 0);
            }
        }
    }
}
