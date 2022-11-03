package com.luban;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.*;

public class ThreadTest {


    public static void main(String[] args) {
        ThreadPoolExecutor es = new ThreadPoolExecutor(
                5,
                10, 10, TimeUnit.SECONDS,
                new LinkedBlockingQueue<>(),
                Executors.defaultThreadFactory());
        es.allowCoreThreadTimeOut(true);


        List<Future<?>> results = new ArrayList<Future<?>>();

        results.add(es.submit(new DeployDescriptor()));
        for (Future<?> result : results) {
            try {
                result.get();
            } catch (Exception e) {
                e.printStackTrace();
            }
        }

        System.out.println("执行xxxx");
    }


    private static class DeployDescriptor implements Runnable {


        public DeployDescriptor() {

        }

        @Override
        public void run() {
            try {
                System.out.println("开始执行");
                Thread.sleep(3000);
                System.out.println("结束执行");
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}


