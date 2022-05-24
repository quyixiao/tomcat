package com.luban;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.Socket;

public class SocketClient {

    public static void main(String[] args)  throws Exception{
        Socket socket  = null;
        socket = new Socket("localhost",8888);
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        dos.writeUTF("我是客户端，请求链接");
        System.out.println(dis.readUTF());
        socket.close();
    }
}
