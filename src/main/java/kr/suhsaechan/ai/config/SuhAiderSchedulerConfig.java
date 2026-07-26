package kr.suhsaechan.ai.config;

import jakarta.annotation.PostConstruct;
import jakarta.annotation.PreDestroy;
import kr.suhsaechan.ai.api.ModelApi;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.support.CronExpression;

import java.time.Duration;
import java.time.ZoneId;
import java.time.ZonedDateTime;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;

/**
 * 모델 목록 자동 갱신 스케줄러
 *
 * <p>{@code suh.aider.model-refresh.scheduling-enabled=true}일 때만 활성화됩니다.</p>
 *
 * <p>v1.x는 {@code @EnableScheduling}을 붙여 소비자 애플리케이션의 스케줄링 인프라를
 * 통째로 켜버렸습니다. 라이브러리가 호스트 앱의 전역 설정을 바꾸는 건 부적절해서,
 * v2.0에서는 전용 스케줄러 스레드 하나를 직접 운영합니다.</p>
 *
 * <p>설정 예시:</p>
 * <pre>
 * suh:
 *   aider:
 *     model-refresh:
 *       scheduling-enabled: true      # 스케줄링 활성화 (기본: false)
 *       cron: "0 0 4 * * *"           # 매일 오전 4시 (기본값)
 *       timezone: Asia/Seoul          # 시간대 (기본값)
 * </pre>
 */
@Slf4j
@Configuration
@ConditionalOnProperty(
        prefix = "suh.aider.model-refresh",
        name = "scheduling-enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class SuhAiderSchedulerConfig {

    private final SuhAiderConfig config;
    private final ModelApi modelApi;

    private ScheduledExecutorService scheduler;
    private CronExpression cronExpression;
    private ZoneId zoneId;

    /**
     * 스케줄러 시작
     */
    @PostConstruct
    public void start() {
        String cron = config.getModelRefresh().getCron();
        String timezone = config.getModelRefresh().getTimezone();

        try {
            this.cronExpression = CronExpression.parse(cron);
            this.zoneId = ZoneId.of(timezone);
        } catch (Exception e) {
            log.error("모델 갱신 스케줄 설정이 올바르지 않아 자동 갱신을 비활성화합니다 - cron: {}, timezone: {} ({})",
                    cron, timezone, e.getMessage());
            return;
        }

        this.scheduler = Executors.newSingleThreadScheduledExecutor(runnable -> {
            Thread thread = new Thread(runnable, "suh-aider-model-refresh");
            thread.setDaemon(true);
            return thread;
        });

        log.info("모델 목록 자동 갱신 스케줄러 등록 - cron: {}, timezone: {}", cron, timezone);
        scheduleNext();
    }

    /**
     * 스케줄러 종료
     */
    @PreDestroy
    public void stop() {
        if (scheduler != null) {
            scheduler.shutdownNow();
            log.info("모델 목록 자동 갱신 스케줄러 종료");
        }
    }

    /**
     * 다음 실행 시각을 계산해 예약
     */
    private void scheduleNext() {
        ZonedDateTime now = ZonedDateTime.now(zoneId);
        ZonedDateTime next = cronExpression.next(now);

        if (next == null) {
            log.warn("다음 실행 시각을 계산할 수 없어 자동 갱신을 중단합니다");
            return;
        }

        long delayMs = Math.max(0, Duration.between(now, next).toMillis());
        log.debug("다음 모델 갱신 예정: {}", next);

        scheduler.schedule(this::refreshAndReschedule, delayMs, TimeUnit.MILLISECONDS);
    }

    /**
     * 갱신 수행 후 다음 실행 재예약
     *
     * <p>갱신 실패가 스케줄 자체를 멈추지 않도록 예외를 삼킵니다.</p>
     */
    private void refreshAndReschedule() {
        try {
            log.info("스케줄러에 의한 모델 목록 갱신 시작");
            if (modelApi.refreshModels()) {
                log.info("모델 목록 갱신 완료 - 총 {}개 모델", modelApi.getAvailableModels().size());
            } else {
                log.warn("모델 목록 갱신 실패");
            }
        } catch (Exception e) {
            log.error("모델 목록 갱신 중 오류 발생: {}", e.getMessage());
        } finally {
            if (scheduler != null && !scheduler.isShutdown()) {
                scheduleNext();
            }
        }
    }
}
