
其他语言，如Go早期就支持了叫做协程的东西，它是轻量化后的线程，而Java异步编程却只有线程的概念。JDK8以后的升级带来的改变总体感觉不大，不过这次JDK21带来的Virtual Thread还是值得体验一把的，可以说是YYDS，终于有理由不使用Java8了！

首先下载[JDK 21](https://openjdk.org/projects/jdk/21/)。

如官方所说，Virtual Thread在JDK19和JDK20时，还是预览版本。在JDK21才正式确定出道。因此现有版本，已经可以正式使用了。

![](https://meethigher.top/blog/2023/jdk21-virtual-thread/image-20231105204017025.png)

下面所有的Virtual Thread我都叫他虚拟线程了，而不是协程，反正只是个名。


> 不过新版本发布之后，想要正式使用，还需要等待IDE更新，不然使用体验没那么好。
>
> 以下测试都是通过JDK原生编译命令执行。

# 一、快速入门

## 1.1 如何创建

**Java的虚拟线程，是基于ForkJoinPool线程池实现的，它适用于密集型阻塞场景。**

**常规情况下，如果存在阻塞，那么线程就会卡在那里了，这段时间是啥也不干，但却又占着茅坑。其实这部分时间还是可以让他做别的事情的，就像netty的事件驱动非阻塞一样，于是虚拟线程应运而生。**

> 说人话就是，虚拟线程适合处理大量阻塞的任务。如果处理计算任务，或者个数较少的阻塞任务，优势并不明显。

Java中的new Thread()获取到的即对应操作系统中的线程。不过在JDK21中，给了他更明确的概念，平台线程PlatformThread。

不求甚解，只求会用。至于如何创建PlatformThread和VirtualThread，请看以下代码。

```java
//线程，即平台线程。两种方式
Thread platformThread = new Thread(new TestRunner(null));
Thread platformThread1 = Thread.ofPlatform().unstarted(new TestRunner(null));
//虚拟线程。跟一下源码，可知他是依赖于池化的ForkJoinPool的
Thread virtualThread = Thread.ofVirtual().unstarted(new TestRunner(null));
```

## 1.2 性能比较

下面比较PlatformThread和VirtualThread处理密集型阻塞任务时的执行性能。

```java
import java.text.SimpleDateFormat;
import java.time.Duration;
import java.util.Date;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;

public class Main {


    /**
     * 基于15个线程池实现的虚拟线程
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

    public static void main(String[] args) throws Exception {
        int count = 10000;
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
```

进行编译，并运行。

```sh
javac Main.java && java Main
```

两种方式，分别模拟处理10000个阻塞任务，每个任务阻塞1秒。

* PlatformThread: 15个池化线程
* VirtualThread: 15个池化线程，但是采用了虚拟线程方式

> 我的硬件情况就不详细描述了，直接对比结果，就能清楚明了感受到差异。

执行结果

1. 耗时对比
   * PlatformThread: 耗时11分钟
   * VirtualThread：耗时2秒
2. CPU使用率对比
   * PlatformThread: 占用10%左右
   * VirtualThread: 占用50%左右

综上，处理密集型阻塞任务，使用VirtualThread更大程度发挥了CPU性能！

> 此处官方已经明确说了，虚拟线程只适合密集型阻塞场景。假如像计算型，反而会降低性能。
>
> 说白了，虚拟线程就是压榨CPU空闲的时间，不允许他闲下来。这点跟操作系统的时间片、Netty的事件驱动类似。

# 二、实际案例

## 2.1 购物

请看如下代码

![](https://meethigher.top/blog/2023/jdk21-virtual-thread/image-20231105212609355.png)

像findUserByName和loadCardFor是通过数据库查询，其实在查询的过程中，**将请求发给数据库，等待数据库响应的过程就是阻塞的。**

这种顺序执行的情况，其中就存在CPU利用不充分的问题，就可以使用异步编程提升性能。但是采用多线程能提高性能吗？

先分析下业务，这是一个购物过程。

1. 用户：查询并获取用户
2. 购物车：通过用户查询并获取购物车，获取购物车的总价格
3. 订单：支付该用户的总价格对应的费用，获取订单
4. 通知：通知用户订单信息

会发现这里面是环环相扣的，没有并行的业务。

即使我们把代码进行了异步如下，有意义吗？没意义！

![](https://meethigher.top/blog/2023/jdk21-virtual-thread/image-20231105220258826.png)

假如同时来100个请求，会发现，阻塞时间的总量根本没变。性能并没有提升。

## 2.1 购物-优化版

那么如何提升性能？就得通过阻塞入手了，让他变成不阻塞。这样单位时间内处理的请求就更多了。

而且，也不能采用上述异步后的代码形式，因为他**难以阅读、难以调试。**

我们希望他

1. 不阻塞
2. 易阅读、易调试

那么如何优化呢？请看如下代码。

```java
private ExecutorService executor = Executors.newVirtualThreadPerTaskExecutor();

public void pay(String name) throws Exception {
    executor.submit(() -> {
        User user = userService.findUserByName(name);
        if (!repo.contains(user)) {
            repo.save(user);
        }
        var cart = cartService.loadCartFor(user);
        var total = cart.items().stream().mapToInt(Item::price).sum();
        var transactionId = paymentService.pay(user, total);
        emailService.send(user, cart, transactionId);
    })
}
```

使用虚拟线程，既能易调试、易阅读，而且将原来阻塞的时间用来处理更多的请求。**这些内部执行过程，都由Java自行处理，不需要开发者关心。**用老外的话说，”这不是魔术，这只是工程化“。

如果不理解，建议将1.2的代码亲自调试一下。

# 三、参考致谢

[JEP 444: Virtual Threads](https://openjdk.org/jeps/444)

[Java 21 new feature: Virtual Threads #RoadTo21 - YouTube](https://www.youtube.com/watch?v=5E0LU85EnTI)

