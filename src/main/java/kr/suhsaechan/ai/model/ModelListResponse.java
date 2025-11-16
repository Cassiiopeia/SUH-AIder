package kr.suhsaechan.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.List;

/**
 * Ollama 모델 목록 API 응답 DTO
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ModelListResponse {

    /**
     * 설치된 모델 목록
     */
    private List<ModelInfo> models;
}
