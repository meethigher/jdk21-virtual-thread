import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class Main {


    /**
     * 基于15个线程池实现的协程
     * 执行一万个任务，每个任务耗时1000毫秒，总共耗费2637毫秒
     */
    public static void virtualThread(int count) throws Exception {
        StopWatcher stopWatcher = new StopWatcher();
        stopWatcher.start();
        CountDownLatch countDownLatch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            Thread.ofVirtual().start(new TestRunner(countDownLatch));
        }
        countDownLatch.await();
        stopWatcher.stop();
        System.out.printf("本次执行耗时：%s毫秒", stopWatcher.getTimeInterval().toMillis());
    }

    /**
     * 基于15个池化线程
     * 执行一万个任务，每个任务耗时1000毫秒，总共耗费11分钟
     */
    public static void platformThread(int count) throws Exception {
        StopWatcher stopWatcher = new StopWatcher();
        stopWatcher.start();
        CountDownLatch countDownLatch = new CountDownLatch(count);
        for (int i = 0; i < count; i++) {
            CompletableFuture.runAsync(new TestRunner(countDownLatch));
        }
        countDownLatch.await();
        stopWatcher.stop();

        System.out.printf("本次执行耗时：%s毫秒", stopWatcher.getTimeInterval().toMillis());
    }


    public static void createThread() throws Exception {
        //创建线程，即平台线程。两种方式
        Thread platformThread = new Thread(new TestRunner(null));
        Thread platformThread1 = Thread.ofPlatform().unstarted(new TestRunner(null));
        //创建虚拟线程。跟一下源码，可知他是依赖于池化的ForkJoinPool的
        Thread virtualThread = Thread.ofVirtual().unstarted(new TestRunner(null));
    }

    public static void main(String[] args) throws Exception {
        int count = 1;
        //virtualThread(count);
        platformThread(count);
    }


    public static class TestRunner implements Runnable {

        private final CountDownLatch countDownLatch;

        public TestRunner(CountDownLatch countDownLatch) {
            this.countDownLatch = countDownLatch;
        }

        @Override
        public void run() {
            try {
                System.out.println(Thread.currentThread() + " start " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                Thread.sleep(1000);
                System.out.println(Thread.currentThread() + " stop " + new SimpleDateFormat("yyyy-MM-dd HH:mm:ss").format(new Date()));
                countDownLatch.countDown();
            } catch (Exception e) {
                System.out.println(e.getMessage());
            }
        }
    }


    public static class StopWatcher {
        private long start;

        private long stop;

        public StopWatcher() {
        }

        public void start() {
            this.start = System.currentTimeMillis();
        }

        public void stop() {
            this.stop = System.currentTimeMillis();
        }

        public Duration getTimeInterval() {
            return Duration.ofMillis(this.stop - this.start);
        }


    }
}