package kr.suhsaechan.ai.model;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Function Calling용 Tool 정의 클래스
 * Ollama /api/chat의 tools 파라미터에 사용됩니다.
 *
 * <p>사용 예제:</p>
 * <pre>
 * FunctionTool tool = FunctionTool.builder()
 *     .name("route_rag")
 *     .description("Use when user asks about config location")
 *     .parameter(FunctionParameter.builder()
 *         .name("query")
 *         .type("string")
 *         .description("Search query")
 *         .required(true)
 *         .build())
 *     .build();
 * </pre>
 *
 * @see FunctionRequest
 * @see ChatRequest.Tool
 */
@Data
@Builder(toBuilder = true)
@NoArgsConstructor
@AllArgsConstructor
public class FunctionTool {

    /**
     * 함수 이름 (필수)
     * 예: "route_rag", "route_system", "search_docs"
     */
    private String name;

    /**
     * 함수 설명 (필수)
     * AI가 이 함수를 선택할지 결정하는 데 사용됩니다.
     * 예: "Use when user asks about config location"
     */
    private String description;

    /**
     * 함수 파라미터 목록
     */
    @Builder.Default
    private List<FunctionParameter> parameters = new ArrayList<>();

    /**
     * 함수 파라미터 정의
     */
    @Data
    @Builder
    @NoArgsConstructor
    @AllArgsConstructor
    public static class FunctionParameter {

        /**
         * 파라미터 이름
         * 예: "query", "action"
         */
        private String name;

        /**
         * 파라미터 타입
         * "string", "integer", "boolean", "number"
         */
        @Builder.Default
        private String type = "string";

        /**
         * 파라미터 설명
         */
        private String description;

        /**
         * 필수 여부
         */
        @Builder.Default
        private boolean required = false;

        /**
         * Enum 제한값 목록 (선택)
         * 예: ["get_status", "get_logs", "check_port"]
         */
        private List<String> enumValues;

        /**
         * 간편 생성 - 필수 파라미터
         *
         * @param name 파라미터 이름
         * @param type 파라미터 타입
         * @param description 설명
         * @return FunctionParameter 인스턴스
         */
        public static FunctionParameter required(String name, String type, String description) {
            return FunctionParameter.builder()
                    .name(name)
                    .type(type)
                    .description(description)
                    .required(true)
                    .build();
        }

        /**
         * 간편 생성 - 선택 파라미터
         *
         * @param name 파라미터 이름
         * @param type 파라미터 타입
         * @param description 설명
         * @return FunctionParameter 인스턴스
         */
        public static FunctionParameter optional(String name, String type, String description) {
            return FunctionParameter.builder()
                    .name(name)
                    .type(type)
                    .description(description)
                    .required(false)
                    .build();
        }

        /**
         * 간편 생성 - Enum 파라미터
         *
         * @param name 파라미터 이름
         * @param description 설명
         * @param enumValues 허용 값 목록
         * @return FunctionParameter 인스턴스
         */
        public static FunctionParameter enumType(String name, String description, String... enumValues) {
            return FunctionParameter.builder()
                    .name(name)
                    .type("string")
                    .description(description)
                    .required(true)
                    .enumValues(List.of(enumValues))
                    .build();
        }
    }

    // ========== 빌더 헬퍼 메서드 ==========

    /**
     * 파라미터 추가 (체이닝)
     *
     * @param parameter 추가할 파라미터
     * @return 현재 FunctionTool 인스턴스
     */
    public FunctionTool addParameter(FunctionParameter parameter) {
        if (this.parameters == null) {
            this.parameters = new ArrayList<>();
        }
        this.parameters.add(parameter);
        return this;
    }

    /**
     * 간편 파라미터 추가
     *
     * @param name 파라미터 이름
     * @param type 파라미터 타입
     * @param description 설명
     * @param required 필수 여부
     * @return 현재 FunctionTool 인스턴스
     */
    public FunctionTool addParameter(String name, String type, String description, boolean required) {
        return addParameter(FunctionParameter.builder()
                .name(name)
                .type(type)
                .description(description)
                .required(required)
                .build());
    }

    // ========== 변환 메서드 ==========

    /**
     * ChatRequest.Tool로 변환
     * Ollama API 형식에 맞게 변환합니다.
     *
     * @return ChatRequest.Tool 인스턴스
     */
    public ChatRequest.Tool toChatRequestTool() {
        Map<String, Object> properties = new LinkedHashMap<>();
        List<String> requiredList = new ArrayList<>();

        if (parameters != null) {
            for (FunctionParameter param : parameters) {
                Map<String, Object> paramDef = new LinkedHashMap<>();
                paramDef.put("type", param.getType());

                if (param.getDescription() != null) {
                    paramDef.put("description", param.getDescription());
                }
                if (param.getEnumValues() != null && !param.getEnumValues().isEmpty()) {
                    paramDef.put("enum", param.getEnumValues());
                }

                properties.put(param.getName(), paramDef);

                if (param.isRequired()) {
                    requiredList.add(param.getName());
                }
            }
        }

        Map<String, Object> parametersSchema = new LinkedHashMap<>();
        parametersSchema.put("type", "object");
        parametersSchema.put("properties", properties);
        parametersSchema.put("required", requiredList);
        parametersSchema.put("additionalProperties", false);

        return ChatRequest.Tool.builder()
                .type("function")
                .function(ChatRequest.Tool.Function.builder()
                        .name(this.name)
                        .description(this.description)
                        .parameters(parametersSchema)
                        .build())
                .build();
    }

    // ========== 정적 팩토리 메서드 ==========

    /**
     * 간편 생성 - 파라미터 없는 Tool
     *
     * @param name 함수 이름
     * @param description 함수 설명
     * @return FunctionTool 인스턴스
     */
    public static FunctionTool of(String name, String description) {
        return FunctionTool.builder()
                .name(name)
                .description(description)
                .build();
    }

    /**
     * 간편 생성 - 단일 파라미터 Tool
     *
     * @param name 함수 이름
     * @param description 함수 설명
     * @param paramName 파라미터 이름
     * @param paramType 파라미터 타입
     * @param paramDesc 파라미터 설명
     * @return FunctionTool 인스턴스
     */
    public static FunctionTool of(String name, String description,
                                   String paramName, String paramType, String paramDesc) {
        return FunctionTool.builder()
                .name(name)
                .description(description)
                .parameters(List.of(
                        FunctionParameter.required(paramName, paramType, paramDesc)
                ))
                .build();
    }
}
