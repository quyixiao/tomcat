package com.luban.broadcast;


import java.net.DatagramPacket;
import java.net.DatagramSocket;

public class BroadcastReceiver {

    public static void main(String[] args)  throws Exception{
        DatagramSocket ds = new DatagramSocket(8888);
        byte [] buf = new byte[5];
        DatagramPacket dp = new DatagramPacket(buf, buf.length);
        ds.receive(dp);
        System.out.println(new String(buf));
    }
}
