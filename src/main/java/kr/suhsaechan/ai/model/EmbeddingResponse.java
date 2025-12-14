package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * SUH-AIDER Embedding API 응답 DTO
 * Ollama /api/embed 응답 형식
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class EmbeddingResponse {

    /**
     * 사용된 모델명
     */
    private String model;

    /**
     * 임베딩 벡터 리스트
     * 각 입력에 대응하는 벡터 (List<Double>)
     */
    private List<List<Double>> embeddings;

    /**
     * 전체 처리 시간 (나노초)
     */
    @JsonProperty("total_duration")
    private Long totalDuration;

    /**
     * 모델 로드 시간 (나노초)
     */
    @JsonProperty("load_duration")
    private Long loadDuration;

    /**
     * 프롬프트 평가 토큰 수
     */
    @JsonProperty("prompt_eval_count")
    private Integer promptEvalCount;
}
