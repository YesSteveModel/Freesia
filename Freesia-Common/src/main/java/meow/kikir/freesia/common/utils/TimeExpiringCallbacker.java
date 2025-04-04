package meow.kikir.freesia.common.utils;

import org.jetbrains.annotations.NotNull;

import java.lang.invoke.MethodHandles;
import java.lang.invoke.VarHandle;
import java.util.concurrent.DelayQueue;
import java.util.concurrent.Delayed;
import java.util.concurrent.TimeUnit;

public abstract class TimeExpiringCallbacker implements Delayed {
    private static final DelayQueue<TimeExpiringCallbacker> QUEUE = new DelayQueue<>();
    public static final Object NIL = new Object();

    private static final VarHandle RESULT_HANDLE;

    static {
        try {
            RESULT_HANDLE = MethodHandles.lookup().findVarHandle(
                    TimeExpiringCallbacker.class,
                    "result",
                    Object.class
            );
        } catch (NoSuchFieldException | IllegalAccessException e) {
            throw new RuntimeException(e);
        }

        Thread checkerThread = new Thread(() -> {
            while (!Thread.currentThread().isInterrupted()) {
                try {
                    TimeExpiringCallbacker callbacker = QUEUE.take();
                    callbacker.handleTimeout();
                } catch (InterruptedException e) {
                    Thread.currentThread().interrupt();
                    break;
                } catch (Exception e) {
                    e.printStackTrace();
                }
            }
        }, "Freesia-TimeExpiringCallbacker-Checker");

        checkerThread.setDaemon(true);
        checkerThread.setPriority(Thread.NORM_PRIORITY - 2);
        checkerThread.start();
    }

    private final long expirationDeadline;
    private Object result = null;

    public TimeExpiringCallbacker(long timeoutMs) {
        this.expirationDeadline = System.nanoTime() + TimeUnit.MILLISECONDS.toNanos(timeoutMs);
        QUEUE.add(this);
    }

    public abstract void onFinished(Object result);

    private void handleTimeout() {
        if (RESULT_HANDLE.compareAndSet(this, null, NIL)) {
            this.onFinished(NIL);
        }
    }

    public void done(Object result) {
        if (RESULT_HANDLE.compareAndSet(this, null, result)) {

            QUEUE.remove(this);

            this.onFinished(result);
        }
    }

    @Override
    public long getDelay(@NotNull TimeUnit unit) {
        return unit.convert(expirationDeadline - System.nanoTime(), TimeUnit.NANOSECONDS);
    }

    @Override
    public int compareTo(@NotNull Delayed input) {
        return Long.compare(this.expirationDeadline, ((TimeExpiringCallbacker) input).expirationDeadline);
    }
}