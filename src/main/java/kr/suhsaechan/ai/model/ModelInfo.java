package kr.suhsaechan.ai.model;

import com.fasterxml.jackson.annotation.JsonProperty;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * Ollama 모델 정보 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelInfo {

    /**
     * 모델 이름
     */
    private String name;

    /**
     * 모델 식별자 (name과 동일한 경우가 많음)
     */
    private String model;

    /**
     * 수정 일시 (ISO 8601 형식)
     */
    @JsonProperty("modified_at")
    private String modifiedAt;

    /**
     * 모델 크기 (바이트)
     */
    private Long size;

    /**
     * 모델 다이제스트 (해시값)
     */
    private String digest;

    /**
     * 모델 상세 정보 (선택적)
     */
    private ModelDetails details;

    /**
     * 모델 상세 정보 내부 클래스
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class ModelDetails {
        private String format;
        private String family;
        private String[] families;

        @JsonProperty("parameter_size")
        private String parameterSize;

        @JsonProperty("quantization_level")
        private String quantizationLevel;
    }
}
