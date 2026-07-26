package kr.suhsaechan.ai.api;

import com.fasterxml.jackson.databind.JsonNode;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.model.ChunkingConfig;
import kr.suhsaechan.ai.model.EmbeddingRequest;
import kr.suhsaechan.ai.support.MockOllamaTest;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 임베딩 API 테스트
 */
class EmbeddingApiTest extends MockOllamaTest {

    @Test
    @DisplayName("truncate 설정값이 요청에 반영된다")
    void appliesTruncateFromConfig() throws Exception {
        // v1.x는 DTO에 @Builder.Default = true가 있어 이 설정이 절대 반영되지 않았다
        config.getEmbedding().setTruncate(false);
        enqueueJson("{\"embeddings\":[[0.1,0.2]]}");

        new EmbeddingApi(http, config).embed("embeddinggemma:latest", "텍스트");

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertFalse(body.get("truncate").asBoolean(), "설정한 truncate=false가 전송되어야 한다");
    }

    @Test
    @DisplayName("요청에 명시한 truncate가 설정값보다 우선한다")
    void requestTruncateWinsOverConfig() throws Exception {
        config.getEmbedding().setTruncate(false);
        enqueueJson("{\"embeddings\":[[0.1]]}");

        new EmbeddingApi(http, config).embed(EmbeddingRequest.builder()
                .model("embeddinggemma:latest")
                .input("텍스트")
                .truncate(true)
                .build());

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertTrue(body.get("truncate").asBoolean());
    }

    @Test
    @DisplayName("keepAlive와 dimensions 기본값을 설정에서 채운다")
    void appliesOtherDefaults() throws Exception {
        config.getEmbedding().setKeepAlive("10m");
        config.getEmbedding().setDimensions(512);
        enqueueJson("{\"embeddings\":[[0.1]]}");

        new EmbeddingApi(http, config).embed("embeddinggemma:latest", "텍스트");

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertEquals("10m", body.get("keep_alive").asText());
        assertEquals(512, body.get("dimensions").asInt());
    }

    @Test
    @DisplayName("내부 전용 chunkingConfig는 전송하지 않는다")
    void doesNotSendInternalChunkingConfig() throws Exception {
        enqueueJson("{\"embeddings\":[[0.1]]}");

        new EmbeddingApi(http, config).embed(EmbeddingRequest.builder()
                .model("m")
                .input("텍스트")
                .chunkingConfig(ChunkingConfig.fixedSize(100, 10))
                .build());

        JsonNode body = objectMapper.readTree(server.takeRequest().getBody().readUtf8());
        assertFalse(body.has("chunkingConfig"));
    }

    @Test
    @DisplayName("단일 임베딩은 첫 번째 벡터를 반환한다")
    void returnsFirstVector() {
        enqueueJson("{\"embeddings\":[[0.1,0.2,0.3]]}");

        List<Double> vector = new EmbeddingApi(http, config).embed("m", "텍스트");

        assertEquals(List.of(0.1, 0.2, 0.3), vector);
    }

    @Test
    @DisplayName("임베딩 결과가 비면 EMPTY_RESPONSE로 실패한다")
    void failsOnEmptyEmbeddings() {
        enqueueJson("{\"embeddings\":[]}");

        SuhAiderException e = assertThrows(SuhAiderException.class,
                () -> new EmbeddingApi(http, config).embed("m", "텍스트"));
        assertEquals(SuhAiderErrorCode.EMPTY_RESPONSE, e.getErrorCode());
    }

    @Test
    @DisplayName("청킹을 켜면 분할된 청크 수만큼 input이 만들어진다")
    void chunksLongText() throws Exception {
        enqueueJson("{\"embeddings\":[[0.1],[0.2],[0.3]]}");

        String longText = "가".repeat(250);
        new EmbeddingApi(http, config).embedWithChunking("m", longText,
                ChunkingConfig.fixedSize(100, 0));

        JsonNode input = objectMapper.readTree(server.takeRequest().getBody().readUtf8()).get("input");
        assertTrue(input.isArray());
        assertEquals(3, input.size(), "250자를 100자씩 나누면 3개");
    }

    @Test
    @DisplayName("model/input 누락은 INVALID_PARAMETER로 실패한다")
    void validatesRequiredParameters() {
        EmbeddingApi api = new EmbeddingApi(http, config);

        assertEquals(SuhAiderErrorCode.INVALID_PARAMETER,
                assertThrows(SuhAiderException.class,
                        () -> api.embed(EmbeddingRequest.builder().input("x").build())).getErrorCode());

        assertEquals(SuhAiderErrorCode.INVALID_PARAMETER,
                assertThrows(SuhAiderException.class,
                        () -> api.embed(EmbeddingRequest.builder().model("m").build())).getErrorCode());
    }
}
