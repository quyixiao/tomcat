package com.luban.pool;

import java.util.LinkedList;
import java.util.List;

public class ThreadPool {
    private int work_num = 0;

    private WorkerThread[] workerThreads;

    private List<Runnable> taskQueue = new LinkedList<>();

    private static ThreadPool threadPool;

    public ThreadPool(int work_num) {
        this.work_num = work_num;
        workerThreads = new WorkerThread[work_num];
        for (int i = 0; i < work_num; i++) {
            workerThreads[i] = new WorkerThread();
            workerThreads[i].start();
        }

    }

    public void execute(Runnable task) {
        synchronized (taskQueue) {
            taskQueue.add(task);
        }
    }

    private class WorkerThread extends Thread {
        @Override
        public void run() {
            Runnable r = null;
            while (true) {
                synchronized (taskQueue) {
                    if (!taskQueue.isEmpty()) {
                        r = taskQueue.remove(0);
                        r.run();
                    }
                }
            }
        }
    }
}
