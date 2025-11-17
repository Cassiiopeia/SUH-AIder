package kr.suhsaechan.ai.util;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;

/**
 * AI 응답에서 순수 JSON 추출 및 검증 유틸리티
 *
 * AI 모델이 마크다운 코드 블록이나 설명문과 함께 JSON을 반환하는 경우,
 * 순수 JSON만 추출합니다.
 */
@Slf4j
public class JsonResponseCleaner {

    private static final ObjectMapper OBJECT_MAPPER = new ObjectMapper();

    /**
     * AI 응답에서 순수 JSON 추출
     *
     * @param rawResponse AI 원본 응답
     * @return 순수 JSON 문자열
     */
    public static String clean(String rawResponse) {
        if (rawResponse == null || rawResponse.trim().isEmpty()) {
            log.warn("빈 응답 수신");
            return rawResponse;
        }

        String cleaned = rawResponse.trim();

        // 1. 마크다운 코드 블록 제거 (```json ... ``` 또는 ``` ... ```)
        cleaned = removeMarkdownCodeBlocks(cleaned);

        // 2. JSON 시작/끝 찾기 ({ ... } 또는 [ ... ])
        cleaned = extractJsonBoundaries(cleaned);

        log.debug("응답 정제 완료 - 원본 {}자 → 정제 {}자", rawResponse.length(), cleaned.length());

        return cleaned.trim();
    }

    /**
     * 마크다운 코드 블록 제거
     */
    private static String removeMarkdownCodeBlocks(String text) {
        String result = text;

        // ```json\n{...}\n``` 패턴 제거
        if (result.contains("```json")) {
            result = result.replaceFirst("```json\\s*", "");
            log.debug("마크다운 json 코드 블록 시작 제거");
        }

        // ```\n{...}\n``` 패턴 제거
        if (result.startsWith("```")) {
            result = result.replaceFirst("```\\s*", "");
            log.debug("마크다운 코드 블록 시작 제거");
        }

        // 끝의 ``` 제거
        if (result.endsWith("```")) {
            int lastIndex = result.lastIndexOf("```");
            result = result.substring(0, lastIndex);
            log.debug("마크다운 코드 블록 끝 제거");
        }

        return result;
    }

    /**
     * JSON 시작/끝 경계 추출
     */
    private static String extractJsonBoundaries(String text) {
        // { 또는 [ 중 먼저 나오는 것 찾기
        int objectStart = text.indexOf('{');
        int arrayStart = text.indexOf('[');

        int jsonStart = -1;
        if (objectStart != -1 && arrayStart != -1) {
            jsonStart = Math.min(objectStart, arrayStart);
        } else if (objectStart != -1) {
            jsonStart = objectStart;
        } else if (arrayStart != -1) {
            jsonStart = arrayStart;
        }

        // JSON 시작 위치가 0이 아니면 앞부분 제거
        if (jsonStart > 0) {
            log.debug("JSON 시작 위치 조정: {} → 0 (앞부분 제거)", jsonStart);
            text = text.substring(jsonStart);
        }

        // } 또는 ] 중 마지막에 나오는 것 찾기
        int objectEnd = text.lastIndexOf('}');
        int arrayEnd = text.lastIndexOf(']');

        int jsonEnd = Math.max(objectEnd, arrayEnd);

        // JSON 끝 이후 텍스트 제거
        if (jsonEnd != -1 && jsonEnd < text.length() - 1) {
            log.debug("JSON 끝 위치 조정: {} → {} (뒷부분 제거)", text.length(), jsonEnd + 1);
            text = text.substring(0, jsonEnd + 1);
        }

        return text;
    }

    /**
     * JSON 유효성 검증
     *
     * @param json JSON 문자열
     * @return 유효하면 true, 아니면 false
     */
    public static boolean isValidJson(String json) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        try {
            OBJECT_MAPPER.readTree(json);
            log.debug("JSON 유효성 검증 성공");
            return true;
        } catch (JsonProcessingException e) {
            log.warn("JSON 유효성 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JSON 유효성 검증 (ObjectMapper 직접 전달)
     *
     * @param json JSON 문자열
     * @param mapper ObjectMapper 인스턴스
     * @return 유효하면 true, 아니면 false
     */
    public static boolean isValidJson(String json, ObjectMapper mapper) {
        if (json == null || json.trim().isEmpty()) {
            return false;
        }

        try {
            mapper.readTree(json);
            log.debug("JSON 유효성 검증 성공");
            return true;
        } catch (JsonProcessingException e) {
            log.warn("JSON 유효성 검증 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * JSON Pretty Print (가독성 향상)
     *
     * @param json JSON 문자열
     * @return Pretty 포맷된 JSON
     */
    public static String prettify(String json) {
        try {
            Object jsonObject = OBJECT_MAPPER.readValue(json, Object.class);
            return OBJECT_MAPPER.writerWithDefaultPrettyPrinter()
                    .writeValueAsString(jsonObject);
        } catch (JsonProcessingException e) {
            log.warn("JSON Pretty Print 실패: {}", e.getMessage());
            return json;
        }
    }
}
