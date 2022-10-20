package com.luban;

public class TestStandardHost {

    protected synchronized void startInternal() {
        Object obj = this;
        new Thread(new Runnable() {
            @Override
            public void run() {
                synchronized (obj){
                    System.out.println("1");
                }
            }
        }).start();
        try {
            Thread.sleep(3000);
            System.out.println("2");
        } catch (InterruptedException e) {
            e.printStackTrace();
        }
    }

    public static void main(String[] args) {
        TestStandardHost testHost = new TestStandardHost();
        testHost.startInternal();
    }


}
