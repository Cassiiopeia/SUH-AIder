package kr.suhsaechan.ai.api;

import com.fasterxml.jackson.databind.ObjectMapper;
import kr.suhsaechan.ai.config.SuhAiderCustomizer;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.util.JsonResponseCleaner;
import lombok.extern.slf4j.Slf4j;

/**
 * 구조화 출력 스키마 처리 공통 로직
 *
 * <p>v1.x는 {@code generate()}만 전역 기본 스키마를 적용하고 {@code chat()}은 무시했습니다.
 * v2.0에서는 두 경로가 동일하게 동작합니다.</p>
 */
@Slf4j
final class SchemaSupport {

    private SchemaSupport() {
        // 유틸리티 클래스
    }

    /**
     * 요청 스키마와 전역 기본 스키마 중 적용할 것을 결정
     *
     * @param requestSchema 개별 요청에 지정된 스키마 (우선)
     * @param customizer    전역 커스터마이저 (null 가능)
     * @return 적용할 스키마 (없으면 null)
     */
    static JsonSchema resolve(JsonSchema requestSchema, SuhAiderCustomizer customizer) {
        if (requestSchema != null) {
            return requestSchema;
        }
        if (customizer != null && customizer.getDefaultResponseSchema() != null) {
            log.debug("전역 기본 responseSchema 적용");
            return customizer.getDefaultResponseSchema();
        }
        return null;
    }

    /**
     * 스키마가 적용된 응답을 방어적으로 정제
     *
     * <p>네이티브 {@code format}을 쓰면 대부분 순수 JSON이 오지만, 소형 모델이 마크다운으로
     * 감싸 보내는 경우가 있어 한 겹 걸러냅니다.</p>
     *
     * @param rawResponse 모델 원본 응답
     * @param mapper      검증용 매퍼
     * @return 정제된 문자열 (입력이 null이면 null)
     */
    static String cleanJsonResponse(String rawResponse, ObjectMapper mapper) {
        if (rawResponse == null) {
            return null;
        }

        String cleaned = JsonResponseCleaner.clean(rawResponse);

        if (!JsonResponseCleaner.isValidJson(cleaned, mapper)) {
            log.warn("AI가 유효하지 않은 JSON 반환 (정제 결과 유지): {}",
                    cleaned.substring(0, Math.min(100, cleaned.length())));
        }

        return cleaned;
    }
}
