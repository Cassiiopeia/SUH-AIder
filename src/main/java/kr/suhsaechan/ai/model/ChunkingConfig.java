package kr.suhsaechan.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 텍스트 청킹 설정
 * 긴 텍스트를 임베딩 모델 컨텍스트에 맞게 분할
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class ChunkingConfig {

    /**
     * 청킹 전략
     */
    public enum Strategy {
        FIXED_SIZE,    // 고정 문자 수 (기본)
        SENTENCE,      // 문장 단위
        PARAGRAPH      // 단락 단위 (\n\n)
    }

    /**
     * 청킹 전략 (기본: FIXED_SIZE)
     */
    @Builder.Default
    private Strategy strategy = Strategy.FIXED_SIZE;

    /**
     * 청크당 최대 문자 수 (기본: 500)
     * // 토큰 ≈ 문자/4 근사치 적용
     */
    @Builder.Default
    private int chunkSize = 500;

    /**
     * 청크 간 오버랩 문자 수 (기본: 50)
     * // 의미 손실 방지용 (10~20% 권장)
     */
    @Builder.Default
    private int overlapSize = 50;

    /**
     * 청킹 활성화 여부 (기본: false)
     */
    @Builder.Default
    private boolean enabled = false;

    /**
     * 간편 생성 - 고정 크기 청킹
     */
    public static ChunkingConfig fixedSize(int chunkSize, int overlapSize) {
        return ChunkingConfig.builder()
                .strategy(Strategy.FIXED_SIZE)
                .chunkSize(chunkSize)
                .overlapSize(overlapSize)
                .enabled(true)
                .build();
    }

    /**
     * 간편 생성 - 문장 단위 청킹
     */
    public static ChunkingConfig sentence(int maxChunkSize) {
        return ChunkingConfig.builder()
                .strategy(Strategy.SENTENCE)
                .chunkSize(maxChunkSize)
                .overlapSize(0)
                .enabled(true)
                .build();
    }

    /**
     * 간편 생성 - 단락 단위 청킹
     */
    public static ChunkingConfig paragraph(int maxChunkSize) {
        return ChunkingConfig.builder()
                .strategy(Strategy.PARAGRAPH)
                .chunkSize(maxChunkSize)
                .overlapSize(0)
                .enabled(true)
                .build();
    }
}
