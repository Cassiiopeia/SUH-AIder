package kr.suhsaechan.ai.config;

import jakarta.annotation.PreDestroy;
import lombok.extern.slf4j.Slf4j;

import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.ThreadFactory;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

/**
 * SUH-AIDER 전용 비동기 실행기
 *
 * <p>모델 다운로드와 스트리밍은 수 분에서 수십 분까지 블로킹됩니다.
 * v1.x는 이를 {@code CompletableFuture.runAsync()}로 공용 {@code ForkJoinPool}에 올렸는데,
 * 공용 풀은 CPU 코어 수 - 1 크기라 다운로드 몇 개만으로 포화됩니다. 그러면 소비자 앱의
 * 병렬 스트림과 다른 비동기 작업이 전부 굶습니다. 전용 풀로 격리합니다.</p>
 */
@Slf4j
public class SuhAiderExecutors {

    private final ExecutorService executor;

    /**
     * @param poolSize 스레드풀 크기
     */
    public SuhAiderExecutors(int poolSize) {
        int size = Math.max(1, poolSize);
        this.executor = Executors.newFixedThreadPool(size, new NamedDaemonThreadFactory());
        log.info("SuhAider 전용 스레드풀 생성 - 크기: {}", size);
    }

    /**
     * 비동기 작업 실행기 반환
     *
     * @return ExecutorService
     */
    public ExecutorService executor() {
        return executor;
    }

    /**
     * 애플리케이션 종료 시 스레드풀 정리
     */
    @PreDestroy
    public void shutdown() {
        executor.shutdownNow();
        try {
            if (!executor.awaitTermination(5, TimeUnit.SECONDS)) {
                log.warn("SuhAider 스레드풀이 시간 내에 종료되지 않았습니다");
            }
        } catch (InterruptedException e) {
            Thread.currentThread().interrupt();
        }
        log.info("SuhAider 전용 스레드풀 종료");
    }

    /**
     * 데몬 스레드 팩토리
     *
     * <p>데몬으로 두어 다운로드가 진행 중이어도 애플리케이션 종료를 막지 않습니다.
     * 이름을 붙여 스레드 덤프에서 출처를 바로 알 수 있게 합니다.</p>
     */
    private static class NamedDaemonThreadFactory implements ThreadFactory {

        private final AtomicInteger counter = new AtomicInteger(1);

        @Override
        public Thread newThread(Runnable runnable) {
            Thread thread = new Thread(runnable, "suh-aider-" + counter.getAndIncrement());
            thread.setDaemon(true);
            return thread;
        }
    }
}
