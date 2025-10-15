package com.halliday.ai.common.util;

import java.time.Duration;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.locks.LockSupport;
import java.util.function.Consumer;

/**
 * 重试工具类，提供指数退避执行策略，确保网络调用具备稳健性。
 */
public final class RetryUtils {

    /**
     * 默认退避时间序列，单位毫秒。
     */
    public static final List<Duration> DEFAULT_BACKOFF = List.of(
            Duration.ofMillis(200),
            Duration.ofMillis(400),
            Duration.ofMillis(800)
    );

    private RetryUtils() {
        // 工具类不允许实例化。
    }

    /**
     * 按照提供的退避策略执行带返回值的任务，若失败则重试。
     *
     * @param task      待执行任务
     * @param backoff   退避时间序列
     * @param onFailure 每次失败后的回调，可用于记录日志
     * @param <T>       返回值类型
     * @return 成功执行的结果
     * @throws Exception 在所有重试失败后抛出最后一次异常
     */
    public static <T> T executeWithRetry(Callable<T> task, List<Duration> backoff, Consumer<Exception> onFailure) throws Exception {
        Exception lastException = null;
        for (int attempt = 0; attempt <= backoff.size(); attempt++) {
            try {
                return task.call();
            } catch (Exception ex) {
                lastException = ex;
                if (onFailure != null) {
                    onFailure.accept(ex);
                }
                if (attempt >= backoff.size()) {
                    break;
                }
                Duration delay = backoff.get(attempt);
                LockSupport.parkNanos(delay.toNanos());
            }
        }
        if (lastException != null) {
            throw lastException;
        }
        return task.call();
    }
}
