package kr.suhsaechan.ai.config;

import kr.suhsaechan.ai.service.SuhAiderEngine;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.SchedulingConfigurer;
import org.springframework.scheduling.config.ScheduledTaskRegistrar;
import org.springframework.scheduling.support.CronTrigger;

import java.util.TimeZone;

/**
 * SUH-AIDER 모델 목록 자동 갱신 스케줄러 설정
 *
 * <p>이 설정은 {@code suh.aider.model-refresh.scheduling-enabled=true}일 때만 활성화됩니다.</p>
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
@EnableScheduling
@ConditionalOnProperty(
        prefix = "suh.aider.model-refresh",
        name = "scheduling-enabled",
        havingValue = "true"
)
@RequiredArgsConstructor
public class SuhAiderSchedulerConfig implements SchedulingConfigurer {

    private final SuhAiderConfig config;
    private final SuhAiderEngine engine;

    @Override
    public void configureTasks(ScheduledTaskRegistrar taskRegistrar) {
        String cron = config.getModelRefresh().getCron();
        String timezone = config.getModelRefresh().getTimezone();

        log.info("모델 목록 자동 갱신 스케줄러 등록 - cron: {}, timezone: {}", cron, timezone);

        taskRegistrar.addTriggerTask(
                this::refreshModels,
                triggerContext -> {
                    CronTrigger cronTrigger = new CronTrigger(
                            cron,
                            TimeZone.getTimeZone(timezone)
                    );
                    return cronTrigger.nextExecution(triggerContext);
                }
        );
    }

    /**
     * 스케줄러에 의해 호출되는 모델 목록 갱신 메서드
     */
    private void refreshModels() {
        log.info("스케줄러에 의한 모델 목록 갱신 시작");
        try {
            boolean success = engine.refreshModels();
            if (success) {
                log.info("스케줄러에 의한 모델 목록 갱신 완료 - 총 {}개 모델",
                        engine.getAvailableModels().size());
            } else {
                log.warn("스케줄러에 의한 모델 목록 갱신 실패");
            }
        } catch (Exception e) {
            log.error("스케줄러에 의한 모델 목록 갱신 중 오류 발생: {}", e.getMessage());
        }
    }
}
