package kr.suhsaechan.ai.util;

import kr.suhsaechan.ai.model.ChunkingConfig;
import lombok.extern.slf4j.Slf4j;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.Collections;
import java.util.List;

/**
 * 텍스트 청킹 유틸리티
 * 긴 텍스트를 설정에 따라 분할
 */
@Slf4j
public class TextChunker {

    private TextChunker() {
        // 유틸리티 클래스
    }

    /**
     * 텍스트를 청킹 설정에 따라 분할
     *
     * @param text   원본 텍스트
     * @param config 청킹 설정
     * @return 분할된 텍스트 리스트
     */
    public static List<String> chunk(String text, ChunkingConfig config) {
        if (text == null || text.isEmpty()) {
            return Collections.emptyList();
        }

        if (!config.isEnabled()) {
            return Collections.singletonList(text);
        }

        List<String> chunks = switch (config.getStrategy()) {
            case FIXED_SIZE -> chunkByFixedSize(text, config);
            case SENTENCE -> chunkBySentence(text, config);
            case PARAGRAPH -> chunkByParagraph(text, config);
        };

        log.debug("청킹 완료 - 원본: {}자, {}개 청크 생성", text.length(), chunks.size());
        return chunks;
    }

    /**
     * 고정 크기 청킹 (오버랩 포함)
     * // 토큰 ≈ 문자/4 근사치
     */
    private static List<String> chunkByFixedSize(String text, ChunkingConfig config) {
        List<String> chunks = new ArrayList<>();
        int size = config.getChunkSize();
        int overlap = config.getOverlapSize();
        int step = Math.max(size - overlap, 1);  // 최소 1 보장

        for (int i = 0; i < text.length(); i += step) {
            int end = Math.min(i + size, text.length());
            chunks.add(text.substring(i, end));

            if (end == text.length()) {
                break;
            }
        }

        return chunks;
    }

    /**
     * 문장 단위 청킹
     * // 문장 종결자(. ! ?) 기준
     */
    private static List<String> chunkBySentence(String text, ChunkingConfig config) {
        // 문장 종결자 뒤에서 분리 (마침표, 느낌표, 물음표)
        String[] sentences = text.split("(?<=[.!?])\\s+");
        return mergeToChunkSize(Arrays.asList(sentences), config);
    }

    /**
     * 단락 단위 청킹
     * // \n\n 기준 분할
     */
    private static List<String> chunkByParagraph(String text, ChunkingConfig config) {
        String[] paragraphs = text.split("\n\n+");
        return mergeToChunkSize(Arrays.asList(paragraphs), config);
    }

    /**
     * 분할된 파트들을 chunkSize 이하로 병합
     */
    private static List<String> mergeToChunkSize(List<String> parts, ChunkingConfig config) {
        List<String> chunks = new ArrayList<>();
        StringBuilder current = new StringBuilder();

        for (String part : parts) {
            String trimmedPart = part.trim();
            if (trimmedPart.isEmpty()) {
                continue;
            }

            // 현재 버퍼 + 새 파트가 chunkSize 초과하면 버퍼 저장 후 초기화
            if (current.length() + trimmedPart.length() + 1 > config.getChunkSize()) {
                if (current.length() > 0) {
                    chunks.add(current.toString().trim());
                    current = new StringBuilder();
                }

                // 단일 파트가 chunkSize 초과하면 강제 분할
                if (trimmedPart.length() > config.getChunkSize()) {
                    chunks.addAll(forceSplit(trimmedPart, config.getChunkSize()));
                    continue;
                }
            }

            if (current.length() > 0) {
                current.append(" ");
            }
            current.append(trimmedPart);
        }

        // 남은 버퍼 저장
        if (current.length() > 0) {
            chunks.add(current.toString().trim());
        }

        return chunks;
    }

    /**
     * chunkSize 초과 텍스트 강제 분할
     */
    private static List<String> forceSplit(String text, int chunkSize) {
        List<String> chunks = new ArrayList<>();

        for (int i = 0; i < text.length(); i += chunkSize) {
            int end = Math.min(i + chunkSize, text.length());
            chunks.add(text.substring(i, end));
        }

        return chunks;
    }
}
