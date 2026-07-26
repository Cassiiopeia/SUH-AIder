package kr.suhsaechan.ai.api;

import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.http.SuhAiderHttpExecutor;
import kr.suhsaechan.ai.model.ChunkingConfig;
import kr.suhsaechan.ai.model.EmbeddingRequest;
import kr.suhsaechan.ai.model.EmbeddingResponse;
import kr.suhsaechan.ai.util.TextChunker;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;

/**
 * 임베딩 API (Ollama {@code /api/embed})
 */
@Slf4j
public class EmbeddingApi {

    private static final String PATH = "/api/embed";

    private final SuhAiderHttpExecutor http;
    private final SuhAiderConfig config;

    public EmbeddingApi(SuhAiderHttpExecutor http, SuhAiderConfig config) {
        this.http = http;
        this.config = config;
    }

    /**
     * 단일 텍스트 임베딩
     *
     * @param model 임베딩 모델명
     * @param text  임베딩할 텍스트
     * @return 임베딩 벡터
     */
    public List<Double> embed(String model, String text) {
        EmbeddingResponse response = embed(EmbeddingRequest.builder()
                .model(model)
                .input(text)
                .build());

        if (response.getEmbeddings() == null || response.getEmbeddings().isEmpty()) {
            throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE, "임베딩 결과가 비어있습니다");
        }
        return response.getEmbeddings().get(0);
    }

    /**
     * 배치 임베딩
     *
     * @param model 임베딩 모델명
     * @param texts 임베딩할 텍스트 목록
     * @return 각 텍스트에 대응하는 임베딩 벡터 목록
     */
    public List<List<Double>> embed(String model, List<String> texts) {
        EmbeddingResponse response = embed(EmbeddingRequest.builder()
                .model(model)
                .input(texts)
                .build());

        if (response.getEmbeddings() == null) {
            throw new SuhAiderException(SuhAiderErrorCode.EMPTY_RESPONSE, "임베딩 결과가 비어있습니다");
        }
        return response.getEmbeddings();
    }

    /**
     * 임베딩 (상세 옵션)
     *
     * @param request 요청 (model, input 필수)
     * @return 임베딩 응답
     * @throws SuhAiderException 파라미터 오류, 통신 오류 시
     */
    public EmbeddingResponse embed(EmbeddingRequest request) {
        if (request == null) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "요청이 null입니다");
        }
        if (!StringUtils.hasText(request.getModel())) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }
        if (request.getInput() == null) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "input이 비어있습니다");
        }

        log.debug("Embed 호출 - model: {}", request.getModel());

        EmbeddingResponse response = http.post(PATH, applyDefaults(request), EmbeddingResponse.class);

        log.info("Embed 완료 - 벡터 개수: {}, 처리 시간: {}ms",
                response.getEmbeddings() != null ? response.getEmbeddings().size() : 0,
                response.getTotalDuration() != null ? response.getTotalDuration() / 1_000_000 : 0);

        return response;
    }

    /**
     * 청킹 + 임베딩 (청킹 설정 직접 지정)
     *
     * @param model          임베딩 모델명
     * @param text           임베딩할 텍스트
     * @param chunkingConfig 청킹 설정
     * @return 각 청크의 임베딩을 담은 응답
     */
    public EmbeddingResponse embedWithChunking(String model, String text, ChunkingConfig chunkingConfig) {
        List<String> chunks = TextChunker.chunk(text, chunkingConfig);
        log.debug("청킹 완료 - 원본: {}자, {}개 청크 생성", text != null ? text.length() : 0, chunks.size());

        if (chunks.isEmpty()) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "청킹 결과가 비어있습니다");
        }

        return embed(EmbeddingRequest.builder()
                .model(model)
                .input(chunks)
                .build());
    }

    /**
     * 청킹 + 임베딩 (설정 파일 기반)
     *
     * @param model 임베딩 모델명
     * @param text  임베딩할 텍스트
     * @return 임베딩 응답
     */
    public EmbeddingResponse embedWithChunking(String model, String text) {
        return embedWithChunking(model, text, buildChunkingConfigFromProperties());
    }

    /**
     * 기본 모델로 청킹 + 임베딩
     *
     * @param text 임베딩할 텍스트
     * @return 임베딩 응답
     */
    public EmbeddingResponse embedWithChunking(String text) {
        return embedWithChunking(config.getEmbedding().getDefaultModel(), text);
    }

    /**
     * 미지정 옵션에 설정 파일 기본값 적용
     *
     * <p>{@code truncate}는 v1.x에서 DTO에 {@code @Builder.Default}가 박혀 있어 절대 null이
     * 아니었고, 그 탓에 {@code suh.aider.embedding.truncate} 설정이 반영되지 않았습니다.</p>
     */
    private EmbeddingRequest applyDefaults(EmbeddingRequest request) {
        SuhAiderConfig.Embedding embeddingConfig = config.getEmbedding();

        return request.toBuilder()
                .truncate(request.getTruncate() != null ? request.getTruncate() : embeddingConfig.isTruncate())
                .keepAlive(request.getKeepAlive() != null ? request.getKeepAlive() : embeddingConfig.getKeepAlive())
                .dimensions(request.getDimensions() != null ? request.getDimensions() : embeddingConfig.getDimensions())
                .chunkingConfig(null)  // Ollama로 전송하지 않는 내부 필드
                .build();
    }

    /**
     * application.yml 설정에서 ChunkingConfig 구성
     */
    private ChunkingConfig buildChunkingConfigFromProperties() {
        SuhAiderConfig.Embedding.Chunking props = config.getEmbedding().getChunking();

        ChunkingConfig.Strategy strategy;
        try {
            strategy = ChunkingConfig.Strategy.valueOf(props.getStrategy());
        } catch (IllegalArgumentException e) {
            log.warn("잘못된 청킹 전략: {}. 기본값 FIXED_SIZE 사용", props.getStrategy());
            strategy = ChunkingConfig.Strategy.FIXED_SIZE;
        }

        return ChunkingConfig.builder()
                .enabled(props.isEnabled())
                .strategy(strategy)
                .chunkSize(props.getChunkSize())
                .overlapSize(props.getOverlapSize())
                .build();
    }
}
