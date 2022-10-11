package com.luban;

public class ShutdownHookTest {

    public static void main(String[] args) {
        Runtime.getRuntime().addShutdownHook(new ShutdownHook());
        for(int i = 0 ;i < 1000;i ++){
            try {
                Thread.sleep(1000);
                System.out.println("for 循环执行 " + i);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
