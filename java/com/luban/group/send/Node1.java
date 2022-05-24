package com.luban.group.send;

import java.net.DatagramPacket;
import java.net.InetAddress;
import java.net.MulticastSocket;
import java.nio.charset.StandardCharsets;

public class Node1 {

    private static int port = 8000;
    private static String address = "228.0.0.4";

    public static void main(String[] args)  throws Exception {
        InetAddress group = InetAddress.getByName(address);
        MulticastSocket mss = null;
        mss = new MulticastSocket(port);
        mss.joinGroup(group);
        while(true){
            String message = "hello from node1 ";
            byte [] buffer  = message.getBytes();
            DatagramPacket dp = new DatagramPacket(buffer,buffer.length,group,port);
            mss.send(dp);
            Thread.sleep(1000);
        }
    }

}
