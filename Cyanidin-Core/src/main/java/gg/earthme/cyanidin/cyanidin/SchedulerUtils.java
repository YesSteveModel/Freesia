package gg.earthme.cyanidin.cyanidin;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

public class SchedulerUtils {
    private static ExecutorService asyncScheduler;

    public static void initAsyncExecutor(){
        asyncScheduler = Executors.newCachedThreadPool();
    }

    public static ExecutorService getAsyncScheduler(){
        return asyncScheduler;
    }

    public static void shutdownAsyncScheduler(){
        asyncScheduler.shutdown();
        while (true) {
            try {
                if (asyncScheduler.awaitTermination(1, TimeUnit.SECONDS)) break;
            } catch (InterruptedException ignored) {
                ;
            }
        }
    }
}
