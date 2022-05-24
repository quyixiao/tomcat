package com.luban;

import java.io.DataInputStream;
import java.io.DataOutputStream;
import java.net.ServerSocket;
import java.net.Socket;

public class SocketServer {

    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = null;
        serverSocket = new ServerSocket(8888);
        Socket socket = serverSocket.accept();
        DataOutputStream dos = new DataOutputStream(socket.getOutputStream());
        DataInputStream dis = new DataInputStream(socket.getInputStream());
        System.out.println("服务器接收到客户端请求：" + dis.readUTF());
        dos.writeUTF("接受连接请求：，连接成功");
        socket.close();
        serverSocket.close();
    }
}
