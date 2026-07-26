package kr.suhsaechan.ai.api;

import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.exception.SuhAiderErrorCode;
import kr.suhsaechan.ai.exception.SuhAiderException;
import kr.suhsaechan.ai.http.SuhAiderHttpExecutor;
import kr.suhsaechan.ai.model.ModelInfo;
import kr.suhsaechan.ai.model.ModelListResponse;
import kr.suhsaechan.ai.util.FormatUtils;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;
import java.util.Map;
import java.util.Optional;

/**
 * 모델 조회·캐시·삭제 API
 *
 * <h3>캐시 동시성</h3>
 * <p>캐시는 <b>불변 스냅샷 교체</b> 방식입니다. 갱신 시 새 리스트를 만들어 참조만 바꾸므로
 * 읽는 쪽은 락 없이 안전하고, 이미 꺼내 간 리스트는 절대 변하지 않습니다.</p>
 *
 * <p>v1.x는 가변 {@code ArrayList}를 {@code Collections.unmodifiableList()}로 감싸 반환했습니다.
 * 이건 복사본이 아니라 <b>뷰</b>라서, 다운로드 완료 스레드가 캐시에 모델을 추가하는 순간
 * 소비자가 순회 중이던 리스트가 {@code ConcurrentModificationException}으로 터졌습니다.</p>
 */
@Slf4j
public class ModelApi {

    private static final String PATH_TAGS = "/api/tags";
    private static final String PATH_DELETE = "/api/delete";

    private final SuhAiderHttpExecutor http;
    private final SuhAiderConfig config;

    /**
     * 캐시된 모델 목록 (항상 불변 리스트)
     */
    private volatile List<ModelInfo> availableModels = Collections.emptyList();

    /**
     * 캐시 초기화 완료 여부
     */
    private volatile boolean modelsInitialized = false;

    public ModelApi(SuhAiderHttpExecutor http, SuhAiderConfig config) {
        this.http = http;
        this.config = config;
    }

    /**
     * AI 서버 Health Check
     *
     * @return 서버가 정상 작동 중이면 true
     */
    public boolean isHealthy() {
        log.debug("AI 서버 Health Check 시작: {}", config.getBaseUrl());

        try {
            String body = http.getRaw("/");
            boolean healthy = body != null && body.toLowerCase().contains("ollama is running");
            log.info("Health Check 결과: {}", healthy ? "정상" : "비정상");
            return healthy;
        } catch (SuhAiderException e) {
            log.warn("Health Check 실패: {}", e.getMessage());
            return false;
        }
    }

    /**
     * 설치된 모델 목록 조회 (서버 직접 조회)
     *
     * @return 모델 목록
     * @throws SuhAiderException 통신 또는 파싱 오류 시
     */
    public ModelListResponse getModels() {
        log.debug("모델 목록 조회 시작");

        ModelListResponse response = http.get(PATH_TAGS, ModelListResponse.class);
        log.info("모델 목록 조회 완료 - 모델 개수: {}",
                response.getModels() != null ? response.getModels().size() : 0);

        return response;
    }

    /**
     * 서버에서 모델 목록을 가져와 캐시에 반영
     *
     * <p>실패해도 예외를 던지지 않습니다. 모델 캐시는 편의 기능이고,
     * 실제 모델 유효성은 요청 시 서버가 검증하기 때문입니다.</p>
     */
    public void initialize() {
        try {
            log.info("사용 가능한 모델 목록 로딩 중...");

            ModelListResponse response = getModels();
            List<ModelInfo> models = response.getModels();

            if (models == null || models.isEmpty()) {
                log.warn("서버에서 모델 목록을 가져왔으나 비어있습니다");
                return;
            }

            this.availableModels = Collections.unmodifiableList(new ArrayList<>(models));
            this.modelsInitialized = true;

            log.info("모델 목록 로드 완료 - 총 {}개", availableModels.size());
            availableModels.forEach(model ->
                    log.debug("  - {}: {} ({})",
                            model.getName(),
                            model.getDetails() != null ? model.getDetails().getParameterSize() : "N/A",
                            FormatUtils.formatBytes(model.getSize())));

        } catch (Exception e) {
            log.error("모델 목록 초기화 실패: {}", e.getMessage());
            log.warn("모델 검증 없이 진행합니다 (요청 시 서버에서 검증됨)");
        }
    }

    /**
     * 캐시된 모델 목록 반환
     *
     * @return 불변 모델 목록 (초기화 전이면 빈 리스트)
     */
    public List<ModelInfo> getAvailableModels() {
        return availableModels;
    }

    /**
     * 특정 모델이 사용 가능한지 확인
     *
     * <p>캐시가 초기화되지 않았으면 판단하지 않고 {@code true}를 반환합니다.
     * 실제 유효성은 요청 시 서버가 검증합니다. 캐시 상태를 알고 싶으면
     * {@link #isModelsInitialized()}를 함께 확인하세요.</p>
     *
     * @param modelName 모델명
     * @return 사용 가능하면 true (캐시 미초기화 시에도 true)
     */
    public boolean isModelAvailable(String modelName) {
        if (!modelsInitialized) {
            log.debug("모델 목록이 초기화되지 않았습니다 - 서버에서 검증됩니다");
            return true;
        }
        return containsModel(availableModels, modelName);
    }

    /**
     * 모델 이름으로 상세 정보 조회
     *
     * <p>캐시에 담긴 정보만 반환합니다. 캐시가 초기화되지 않았거나 해당 모델이 없으면
     * {@code empty}입니다. 즉 {@code empty}는 "존재하지 않음"이 아니라
     * "캐시에 정보가 없음"을 뜻합니다. {@link #isModelAvailable(String)}과 함께 보세요.</p>
     *
     * @param modelName 모델명
     * @return 모델 정보 (없으면 empty)
     */
    public Optional<ModelInfo> getModelInfo(String modelName) {
        if (!modelsInitialized) {
            log.debug("모델 목록이 초기화되지 않아 상세 정보를 제공할 수 없습니다: {}", modelName);
            return Optional.empty();
        }
        return availableModels.stream()
                .filter(model -> model.getName() != null && model.getName().equals(modelName))
                .findFirst();
    }

    /**
     * 모델 목록 수동 갱신
     *
     * @return 갱신 후 캐시가 유효하면 true
     */
    public boolean refreshModels() {
        log.info("모델 목록 갱신 시작");
        initialize();
        return modelsInitialized;
    }

    /**
     * 캐시 초기화 완료 여부
     *
     * @return 초기화되었으면 true
     */
    public boolean isModelsInitialized() {
        return modelsInitialized;
    }

    /**
     * 모델 삭제 (존재 확인 포함)
     *
     * @param modelName 삭제할 모델명
     * @return 삭제 성공 시 true
     */
    public boolean deleteModel(String modelName) {
        return deleteModel(modelName, true);
    }

    /**
     * 모델 삭제
     *
     * @param modelName   삭제할 모델명 (예: "llama3.2", "llama3.2:latest")
     * @param checkExists 삭제 전 캐시에서 존재 여부 확인
     * @return 삭제 성공 시 true
     * @throws SuhAiderException 모델 미존재, 통신 오류 등
     */
    public boolean deleteModel(String modelName, boolean checkExists) {
        log.info("모델 삭제 시작 - 모델: {}, 존재 확인: {}", modelName, checkExists);

        if (!StringUtils.hasText(modelName)) {
            throw new SuhAiderException(SuhAiderErrorCode.INVALID_PARAMETER, "모델명이 비어있습니다");
        }

        if (checkExists && modelsInitialized && !containsModel(availableModels, modelName)) {
            log.warn("삭제 대상 모델이 캐시에 없음: {}", modelName);
            throw new SuhAiderException(SuhAiderErrorCode.MODEL_NOT_FOUND,
                    "모델을 찾을 수 없습니다: " + modelName);
        }

        http.delete(PATH_DELETE, Map.of("name", modelName), SuhAiderErrorCode.MODEL_DELETE_FAILED);
        removeFromCache(modelName);

        log.info("모델 삭제 완료: {}", modelName);
        return true;
    }

    // ========== 캐시 갱신 (내부용) ==========

    /**
     * 캐시에 모델 추가 (다운로드 완료 시 호출)
     *
     * <p>쓰기 경합을 막기 위해 동기화합니다. 읽기는 volatile 참조라 락이 필요 없습니다.</p>
     *
     * @param modelName 추가할 모델명
     */
    public synchronized void addToCache(String modelName) {
        if (!modelsInitialized || containsModel(availableModels, modelName)) {
            return;
        }

        List<ModelInfo> next = new ArrayList<>(availableModels);
        next.add(ModelInfo.builder().name(modelName).build());
        this.availableModels = Collections.unmodifiableList(next);

        log.debug("캐시에 모델 추가됨: {}", modelName);
    }

    /**
     * 캐시에서 모델 제거 (삭제 완료 시 호출)
     *
     * @param modelName 제거할 모델명
     */
    public synchronized void removeFromCache(String modelName) {
        if (!modelsInitialized) {
            return;
        }

        List<ModelInfo> next = new ArrayList<>(availableModels);
        boolean removed = next.removeIf(model -> model.getName() != null && model.getName().equals(modelName));

        if (removed) {
            this.availableModels = Collections.unmodifiableList(next);
            log.debug("캐시에서 모델 제거됨: {} ({}개 남음)", modelName, next.size());
        }
    }

    private static boolean containsModel(List<ModelInfo> models, String modelName) {
        return models.stream().anyMatch(model -> model.getName() != null && model.getName().equals(modelName));
    }
}
