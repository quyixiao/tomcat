package com.luban.socket;

import java.io.IOException;
import java.io.OutputStreamWriter;
import java.net.InetSocketAddress;
import java.net.Socket;

public class SocketClient {

    public static void main(String[] args) throws IOException, InterruptedException {
        InetSocketAddress saddr =   new InetSocketAddress("127.0.0.1",9000);
        Socket s = new Socket();
        int stmo = 2 * 1000;
        int utmo = 2 * 1000;
        s.setSoTimeout(stmo);
        s.connect(saddr,utmo);

        //向服务端发送数据
        OutputStreamWriter sw;
        sw = new OutputStreamWriter(s.getOutputStream(), "ISO-8859-1");
        sw.write("OPTIONS * HTTP/1.0\r\n" +
                "User-Agent: Tomcat wakeup connection\r\n\r\n");
        sw.flush();

        System.out.println("向服务端发送数据结束");
        byte[] bytes = new byte[1024];
        //接收服务端回传的数据
        s.getInputStream().read(bytes);
        System.out.println("接收到服务端的数据：" + new String(bytes));
        s.close();
    }
}