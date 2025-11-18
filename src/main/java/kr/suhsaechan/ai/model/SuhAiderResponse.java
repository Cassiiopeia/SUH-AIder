package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * SUH-AIDER Generate API 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SuhAiderResponse {

    /**
     * 사용된 모델명
     */
    private String model;

    /**
     * 생성된 응답 텍스트
     */
    private String response;

    /**
     * 생성 완료 여부
     */
    private Boolean done;

    /**
     * 전체 처리 시간 (나노초)
     */
    @JsonProperty("total_duration")
    private Long totalDuration;

    /**
     * 로드 시간 (나노초)
     */
    @JsonProperty("load_duration")
    private Long loadDuration;

    /**
     * 프롬프트 평가 개수
     */
    @JsonProperty("prompt_eval_count")
    private Integer promptEvalCount;

    /**
     * 프롬프트 평가 시간 (나노초)
     */
    @JsonProperty("prompt_eval_duration")
    private Long promptEvalDuration;

    /**
     * 응답 평가 개수
     */
    @JsonProperty("eval_count")
    private Integer evalCount;

    /**
     * 응답 평가 시간 (나노초)
     */
    @JsonProperty("eval_duration")
    private Long evalDuration;
}
