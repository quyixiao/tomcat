package com.luban.broadcast;

import java.net.DatagramPacket;
import java.net.DatagramSocket;
import java.net.InetAddress;

public class BroadcastSender {


    public static void main(String[] args)  throws Exception{
        InetAddress ip = InetAddress.getByName("192.168.1.255");
        DatagramSocket ds = new DatagramSocket();
        String str = "hello";
        DatagramPacket dp = new DatagramPacket(str.getBytes(), str.getBytes().length ,ip ,8888);
        ds.send(dp);
        ds.close();
    }
}
