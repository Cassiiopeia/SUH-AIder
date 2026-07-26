package kr.suhsaechan.ai.service;

import jakarta.annotation.PostConstruct;
import kr.suhsaechan.ai.api.ChatApi;
import kr.suhsaechan.ai.api.EmbeddingApi;
import kr.suhsaechan.ai.api.FunctionApi;
import kr.suhsaechan.ai.api.GenerateApi;
import kr.suhsaechan.ai.api.ModelApi;
import kr.suhsaechan.ai.api.PullApi;
import kr.suhsaechan.ai.config.SuhAiderConfig;
import kr.suhsaechan.ai.config.SuhAiderExecutors;
import kr.suhsaechan.ai.model.ChatMessage;
import kr.suhsaechan.ai.model.ChatRequest;
import kr.suhsaechan.ai.model.ChatResponse;
import kr.suhsaechan.ai.model.ChunkingConfig;
import kr.suhsaechan.ai.model.EmbeddingRequest;
import kr.suhsaechan.ai.model.EmbeddingResponse;
import kr.suhsaechan.ai.model.FunctionRequest;
import kr.suhsaechan.ai.model.FunctionResponse;
import kr.suhsaechan.ai.model.ModelInfo;
import kr.suhsaechan.ai.model.ModelListResponse;
import kr.suhsaechan.ai.model.PullProgress;
import kr.suhsaechan.ai.model.PullResult;
import kr.suhsaechan.ai.model.SuhAiderRequest;
import kr.suhsaechan.ai.model.SuhAiderResponse;
import lombok.Getter;
import lombok.extern.slf4j.Slf4j;
import org.springframework.util.StringUtils;

import java.util.List;
import java.util.Optional;
import java.util.concurrent.CompletableFuture;
import java.util.function.Consumer;

/**
 * SUH-AIDER AI 서버와 통신하는 진입점
 *
 * <p>v2.0에서 이 클래스는 <b>위임 파사드</b>입니다. 실제 로직은 도메인별 API 클래스에 있고
 * 여기서는 호출을 넘기기만 합니다. v1.x에서 1,917줄이던 이 파일이 이렇게 줄어든 이유이며,
 * 호출 문법은 그대로 유지됩니다.</p>
 *
 * <p>세부 API만 필요하면 {@link ModelApi}, {@link ChatApi} 등을 직접 주입받을 수 있습니다.
 * 각각 Bean으로 등록돼 있어 테스트에서 개별 대체가 쉽습니다.</p>
 *
 * <pre>
 * // 파사드로 사용 (기존과 동일)
 * String answer = engine.chat("gemma4:e2b", "안녕?");
 *
 * // 필요한 API만 주입받아 사용
 * public MyService(ChatApi chatApi) { ... }
 * </pre>
 */
@Slf4j
@Getter
public class SuhAiderEngine {

    private final ModelApi modelApi;
    private final GenerateApi generateApi;
    private final ChatApi chatApi;
    private final EmbeddingApi embeddingApi;
    private final PullApi pullApi;
    private final FunctionApi functionApi;
    private final SuhAiderConfig config;
    private final SuhAiderExecutors executors;

    public SuhAiderEngine(ModelApi modelApi,
                          GenerateApi generateApi,
                          ChatApi chatApi,
                          EmbeddingApi embeddingApi,
                          PullApi pullApi,
                          FunctionApi functionApi,
                          SuhAiderConfig config,
                          SuhAiderExecutors executors) {
        this.modelApi = modelApi;
        this.generateApi = generateApi;
        this.chatApi = chatApi;
        this.embeddingApi = embeddingApi;
        this.pullApi = pullApi;
        this.functionApi = functionApi;
        this.config = config;
        this.executors = executors;
    }

    /**
     * 초기화 - 설정 검증 및 모델 목록 로드
     *
     * <p>잘못된 baseUrl은 여기서 즉시 실패시킵니다. 첫 API 호출 시점까지 미루면
     * 원인 파악이 어려워집니다.</p>
     */
    @PostConstruct
    public void init() {
        config.validate();
        log.info("SuhAiderEngine 초기화 - baseUrl: {}", config.getBaseUrl());

        if (config.getSecurity() == null || !StringUtils.hasText(config.getSecurity().getApiKey())) {
            log.warn("Security Header가 설정되지 않았습니다. 인증이 필요한 서버에서는 401/403 오류가 발생할 수 있습니다.");
            log.warn("설정 방법: suh.aider.security.api-key");
        } else {
            log.info("Security Header 설정됨 - header: {}", config.getSecurity().getHeaderName());
        }

        if (config.getModelRefresh().isLoadOnStartup()) {
            modelApi.initialize();
        } else {
            log.info("모델 목록 초기화 건너뜀 (load-on-startup: false)");
        }

        if (config.getModelRefresh().isSchedulingEnabled()) {
            log.info("모델 목록 자동 갱신 스케줄링 활성화 - cron: {}, timezone: {}",
                    config.getModelRefresh().getCron(), config.getModelRefresh().getTimezone());
        }

        log.info("SuhAiderEngine 초기화 완료");
    }

    // ========== 모델 관리 ==========

    /**
     * AI 서버 Health Check
     *
     * @return 서버가 정상 작동 중이면 true
     */
    public boolean isHealthy() {
        return modelApi.isHealthy();
    }

    /**
     * 설치된 모델 목록 조회 (서버 직접 조회)
     *
     * @return 모델 목록
     */
    public ModelListResponse getModels() {
        return modelApi.getModels();
    }

    /**
     * 캐시된 모델 목록 반환
     *
     * @return 불변 모델 목록
     */
    public List<ModelInfo> getAvailableModels() {
        return modelApi.getAvailableModels();
    }

    /**
     * 특정 모델이 사용 가능한지 확인
     *
     * @param modelName 모델명
     * @return 사용 가능하면 true (캐시 미초기화 시에도 true)
     */
    public boolean isModelAvailable(String modelName) {
        return modelApi.isModelAvailable(modelName);
    }

    /**
     * 모델 상세 정보 조회
     *
     * @param modelName 모델명
     * @return 모델 정보 (캐시에 없으면 empty)
     */
    public Optional<ModelInfo> getModelInfo(String modelName) {
        return modelApi.getModelInfo(modelName);
    }

    /**
     * 모델 목록 수동 갱신
     *
     * @return 갱신 후 캐시가 유효하면 true
     */
    public boolean refreshModels() {
        return modelApi.refreshModels();
    }

    /**
     * 모델 캐시 초기화 완료 여부
     *
     * @return 초기화되었으면 true
     */
    public boolean isModelsInitialized() {
        return modelApi.isModelsInitialized();
    }

    /**
     * 모델 삭제
     *
     * @param modelName 삭제할 모델명
     * @return 삭제 성공 시 true
     */
    public boolean deleteModel(String modelName) {
        return modelApi.deleteModel(modelName);
    }

    /**
     * 모델 삭제 (존재 확인 여부 지정)
     *
     * @param modelName   삭제할 모델명
     * @param checkExists 삭제 전 캐시에서 존재 여부 확인
     * @return 삭제 성공 시 true
     */
    public boolean deleteModel(String modelName, boolean checkExists) {
        return modelApi.deleteModel(modelName, checkExists);
    }

    // ========== 텍스트 생성 ==========

    /**
     * 텍스트 생성
     *
     * @param request 요청 (model, prompt 필수)
     * @return 생성 결과
     */
    public SuhAiderResponse generate(SuhAiderRequest request) {
        return generateApi.generate(request);
    }

    /**
     * 간편 텍스트 생성
     *
     * @param model  모델명
     * @param prompt 프롬프트
     * @return 생성된 텍스트
     */
    public String generate(String model, String prompt) {
        return generateApi.generate(model, prompt);
    }

    /**
     * 스트리밍 텍스트 생성
     *
     * @param request  요청
     * @param callback 스트리밍 콜백
     */
    public void generateStream(SuhAiderRequest request, StreamCallback callback) {
        generateApi.generateStream(request, callback);
    }

    /**
     * 간편 스트리밍 텍스트 생성
     *
     * @param model    모델명
     * @param prompt   프롬프트
     * @param callback 스트리밍 콜백
     */
    public void generateStream(String model, String prompt, StreamCallback callback) {
        generateApi.generateStream(model, prompt, callback);
    }

    /**
     * 비동기 스트리밍 텍스트 생성
     *
     * @param request  요청
     * @param callback 스트리밍 콜백
     * @return 완료 추적용 Future
     */
    public CompletableFuture<Void> generateStreamAsync(SuhAiderRequest request, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> generateApi.generateStream(request, callback),
                executors.executor());
    }

    /**
     * 간편 비동기 스트리밍 텍스트 생성
     *
     * @param model    모델명
     * @param prompt   프롬프트
     * @param callback 스트리밍 콜백
     * @return 완료 추적용 Future
     */
    public CompletableFuture<Void> generateStreamAsync(String model, String prompt, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> generateApi.generateStream(model, prompt, callback),
                executors.executor());
    }

    // ========== 대화 ==========

    /**
     * 대화형 응답 생성
     *
     * @param request 요청 (model, messages 필수)
     * @return 응답
     */
    public ChatResponse chat(ChatRequest request) {
        return chatApi.chat(request);
    }

    /**
     * 간편 Chat (메시지 목록)
     *
     * @param model    모델명
     * @param messages 대화 메시지 목록
     * @return 응답
     */
    public ChatResponse chat(String model, List<ChatMessage> messages) {
        return chatApi.chat(model, messages);
    }

    /**
     * 단일 메시지 Chat
     *
     * @param model       모델명
     * @param userMessage 사용자 메시지
     * @return 응답 텍스트
     */
    public String chat(String model, String userMessage) {
        return chatApi.chat(model, userMessage);
    }

    /**
     * 시스템 프롬프트 포함 Chat
     *
     * @param model        모델명
     * @param systemPrompt 시스템 지시문
     * @param userMessage  사용자 메시지
     * @return 응답 텍스트
     */
    public String chat(String model, String systemPrompt, String userMessage) {
        return chatApi.chat(model, systemPrompt, userMessage);
    }

    /**
     * 스트리밍 Chat
     *
     * @param request  요청
     * @param callback 스트리밍 콜백
     */
    public void chatStream(ChatRequest request, StreamCallback callback) {
        chatApi.chatStream(request, callback);
    }

    /**
     * 간편 스트리밍 Chat
     *
     * @param model    모델명
     * @param messages 대화 메시지 목록
     * @param callback 스트리밍 콜백
     */
    public void chatStream(String model, List<ChatMessage> messages, StreamCallback callback) {
        chatApi.chatStream(model, messages, callback);
    }

    /**
     * 비동기 스트리밍 Chat
     *
     * @param request  요청
     * @param callback 스트리밍 콜백
     * @return 완료 추적용 Future
     */
    public CompletableFuture<Void> chatStreamAsync(ChatRequest request, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> chatApi.chatStream(request, callback), executors.executor());
    }

    /**
     * 간편 비동기 스트리밍 Chat
     *
     * @param model    모델명
     * @param messages 대화 메시지 목록
     * @param callback 스트리밍 콜백
     * @return 완료 추적용 Future
     */
    public CompletableFuture<Void> chatStreamAsync(String model, List<ChatMessage> messages, StreamCallback callback) {
        return CompletableFuture.runAsync(() -> chatApi.chatStream(model, messages, callback), executors.executor());
    }

    // ========== 임베딩 ==========

    /**
     * 단일 텍스트 임베딩
     *
     * @param model 임베딩 모델명
     * @param text  임베딩할 텍스트
     * @return 임베딩 벡터
     */
    public List<Double> embed(String model, String text) {
        return embeddingApi.embed(model, text);
    }

    /**
     * 배치 임베딩
     *
     * @param model 임베딩 모델명
     * @param texts 임베딩할 텍스트 목록
     * @return 임베딩 벡터 목록
     */
    public List<List<Double>> embed(String model, List<String> texts) {
        return embeddingApi.embed(model, texts);
    }

    /**
     * 임베딩 (상세 옵션)
     *
     * @param request 요청 (model, input 필수)
     * @return 임베딩 응답
     */
    public EmbeddingResponse embed(EmbeddingRequest request) {
        return embeddingApi.embed(request);
    }

    /**
     * 청킹 + 임베딩 (청킹 설정 직접 지정)
     *
     * @param model          임베딩 모델명
     * @param text           임베딩할 텍스트
     * @param chunkingConfig 청킹 설정
     * @return 임베딩 응답
     */
    public EmbeddingResponse embedWithChunking(String model, String text, ChunkingConfig chunkingConfig) {
        return embeddingApi.embedWithChunking(model, text, chunkingConfig);
    }

    /**
     * 청킹 + 임베딩 (설정 파일 기반)
     *
     * @param model 임베딩 모델명
     * @param text  임베딩할 텍스트
     * @return 임베딩 응답
     */
    public EmbeddingResponse embedWithChunking(String model, String text) {
        return embeddingApi.embedWithChunking(model, text);
    }

    /**
     * 기본 모델로 청킹 + 임베딩
     *
     * @param text 임베딩할 텍스트
     * @return 임베딩 응답
     */
    public EmbeddingResponse embedWithChunking(String text) {
        return embeddingApi.embedWithChunking(text);
    }

    // ========== 모델 다운로드 ==========

    /**
     * 모델 다운로드 (동기)
     *
     * @param modelName 다운로드할 모델명
     * @return 성공 시 true
     */
    public boolean pullModel(String modelName) {
        return pullApi.pullModel(modelName);
    }

    /**
     * 모델 다운로드 (동기, insecure 옵션)
     *
     * @param modelName 다운로드할 모델명
     * @param insecure  TLS 검증 건너뛰기
     * @return 성공 시 true
     */
    public boolean pullModel(String modelName, boolean insecure) {
        return pullApi.pullModel(modelName, insecure);
    }

    /**
     * 모델 다운로드 (스트리밍 + 취소 가능)
     *
     * @param modelName 다운로드할 모델명
     * @param callback  진행률/종료 콜백
     * @return 취소용 핸들
     */
    public PullHandle pullModelStream(String modelName, PullCallback callback) {
        return pullApi.pullModelStream(modelName, callback);
    }

    /**
     * 모델 다운로드 (스트리밍 + 취소 가능, insecure 옵션)
     *
     * @param modelName 다운로드할 모델명
     * @param insecure  TLS 검증 건너뛰기
     * @param callback  진행률/종료 콜백
     * @return 취소용 핸들
     */
    public PullHandle pullModelStream(String modelName, boolean insecure, PullCallback callback) {
        return pullApi.pullModelStream(modelName, insecure, callback);
    }

    /**
     * 모델 다운로드 (비동기)
     *
     * @param modelName 다운로드할 모델명
     * @return 결과 Future
     */
    public CompletableFuture<PullResult> pullModelAsync(String modelName) {
        return pullApi.pullModelAsync(modelName);
    }

    /**
     * 모델 다운로드 (비동기 + 진행률 리스너)
     *
     * @param modelName        다운로드할 모델명
     * @param progressListener 진행률 리스너 (null 가능)
     * @return 결과 Future
     */
    public CompletableFuture<PullResult> pullModelAsync(String modelName, Consumer<PullProgress> progressListener) {
        return pullApi.pullModelAsync(modelName, progressListener);
    }

    /**
     * 여러 모델 병렬 다운로드
     *
     * @param modelNames 다운로드할 모델명 목록
     * @param callback   진행률/종료 콜백
     * @return 핸들 목록
     */
    public List<PullHandle> pullModelsParallel(List<String> modelNames, PullCallback callback) {
        return pullApi.pullModelsParallel(modelNames, callback);
    }

    /**
     * 여러 모델 병렬 다운로드 (비동기)
     *
     * @param modelNames 다운로드할 모델명 목록
     * @return 결과 목록 Future
     */
    public CompletableFuture<List<PullResult>> pullModelsAsync(List<String> modelNames) {
        return pullApi.pullModelsAsync(modelNames);
    }

    // ========== Function Calling ==========

    /**
     * Function Calling 수행
     *
     * @param request 요청 (model, userText, systemPrompt, tools 필수)
     * @return 선택된 Tool과 인자
     */
    public FunctionResponse functionCall(FunctionRequest request) {
        return functionApi.functionCall(request);
    }
}
