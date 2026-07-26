package kr.suhsaechan.ai.http;

import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import okio.BufferedSource;

import java.io.IOException;

/**
 * NDJSON(줄 단위 JSON) 스트림 파서
 *
 * <p>Ollama의 스트리밍 응답은 한 줄에 JSON 하나씩 내려옵니다.
 * v1.x에서는 이 루프가 generate/chat/pull 세 곳에 복제돼 있었고,
 * 각 사본이 예외 처리와 종료 조건을 조금씩 다르게 구현했습니다.</p>
 */
@Slf4j
public final class NdjsonStreamReader {

    private NdjsonStreamReader() {
        // 유틸리티 클래스
    }

    /**
     * 줄 단위 JSON 처리 핸들러
     */
    @FunctionalInterface
    public interface LineHandler {

        /**
         * 파싱된 한 줄을 처리
         *
         * @param node 파싱된 JSON 노드
         * @return 스트림 처리를 중단하려면 true
         */
        boolean handle(JsonNode node);
    }

    /**
     * 스트림을 줄 단위로 읽어 핸들러에 전달
     *
     * <p>개별 줄의 파싱 실패는 건너뜁니다. 부분적으로 깨진 청크 하나 때문에
     * 스트림 전체를 버리는 것보다 나머지를 살리는 편이 유용하기 때문입니다.</p>
     *
     * @param source  응답 스트림
     * @param mapper  JSON 매퍼
     * @param handler 줄 처리 핸들러
     * @return 핸들러가 중단을 요청해 끝났으면 true, 스트림이 소진돼 끝났으면 false
     * @throws IOException 스트림 읽기 실패 시
     */
    public static boolean read(BufferedSource source, ObjectMapper mapper, LineHandler handler) throws IOException {
        while (!source.exhausted()) {
            String line = source.readUtf8Line();

            if (line == null || line.trim().isEmpty()) {
                continue;
            }

            JsonNode node;
            try {
                node = mapper.readTree(line);
            } catch (JsonProcessingException e) {
                log.warn("청크 파싱 실패 (건너뜀): {}", line);
                continue;
            }

            if (handler.handle(node)) {
                return true;
            }
        }
        return false;
    }
}
