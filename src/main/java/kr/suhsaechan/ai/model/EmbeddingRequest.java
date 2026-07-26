package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonIgnore;
import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.Map;

/**
 * SUH-AIDER Embedding API 요청 DTO
 * Ollama /api/embed 엔드포인트 사용 (권장)
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingRequest {

    /**
     * 임베딩 모델명 (필수)
     * 예: "nomic-embed-text", "mxbai-embed-large"
     */
    private String model;

    /**
     * 임베딩할 텍스트 (필수)
     * String 또는 List<String> 가능
     */
    private Object input;

    /**
     * 컨텍스트 길이 초과 시 입력 끝부분 자르기
     * true: 자름 / false: 에러 반환
     *
     * <p>null이면 {@code suh.aider.embedding.truncate} 설정값이 적용됩니다.
     * v1.x는 여기에 {@code @Builder.Default = true}가 있어 절대 null이 되지 않았고,
     * 그 결과 설정값이 영영 반영되지 않는 버그가 있었습니다.</p>
     */
    private Boolean truncate;

    /**
     * 요청 후 모델 메모리 유지 시간
     * 예: "5m", "1h", "-1" (영구)
     */
    @JsonProperty("keep_alive")
    private String keepAlive;

    /**
     * 임베딩 차원 수 (모델 지원 시)
     */
    private Integer dimensions;

    /**
     * 추가 모델 옵션
     */
    private Map<String, Object> options;

    /**
     * 청킹 설정 (내부 처리용, API 전송 안 함)
     */
    @JsonIgnore
    private ChunkingConfig chunkingConfig;
}
