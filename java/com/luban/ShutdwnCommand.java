package com.luban;

import java.io.InputStream;
import java.net.InetAddress;
import java.net.ServerSocket;
import java.net.Socket;

public class ShutdwnCommand {


    public static void main(String[] args) throws Exception {
        ServerSocket serverSocket = null;
        serverSocket = new ServerSocket(8005, 1, InetAddress.getByName("localhost"));
        while (true) {
            Socket socket = null;
            StringBuilder command = new StringBuilder();
            InputStream inputStream = null;
            socket = serverSocket.accept();
            socket.setSoTimeout(10 * 1000);
            inputStream = socket.getInputStream();
            byte[] commands = new byte[8];
            inputStream.read(commands);
            for (byte b : commands) {
                command.append((char) b);
            }
            System.out.println(command.toString());
            if (command.toString().equals("SHUTDOWN")) {
                break;
            }
        }
    }
}
