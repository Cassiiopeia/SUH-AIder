package kr.suhsaechan.ai.model;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * ChatMessage 모델 테스트
 */
class ChatMessageTest {

    @Test
    @DisplayName("user() 팩토리 메서드 테스트")
    void testUserMessage() {
        // Given
        String content = "안녕하세요?";

        // When
        ChatMessage message = ChatMessage.user(content);

        // Then
        assertEquals("user", message.getRole());
        assertEquals(content, message.getContent());
        assertNull(message.getImages());
        assertNull(message.getToolCalls());
    }

    @Test
    @DisplayName("system() 팩토리 메서드 테스트")
    void testSystemMessage() {
        // Given
        String content = "너는 친절한 어시스턴트야";

        // When
        ChatMessage message = ChatMessage.system(content);

        // Then
        assertEquals("system", message.getRole());
        assertEquals(content, message.getContent());
    }

    @Test
    @DisplayName("assistant() 팩토리 메서드 테스트")
    void testAssistantMessage() {
        // Given
        String content = "안녕하세요! 무엇을 도와드릴까요?";

        // When
        ChatMessage message = ChatMessage.assistant(content);

        // Then
        assertEquals("assistant", message.getRole());
        assertEquals(content, message.getContent());
    }

    @Test
    @DisplayName("tool() 팩토리 메서드 테스트")
    void testToolMessage() {
        // Given
        String content = "{\"result\": \"success\"}";

        // When
        ChatMessage message = ChatMessage.tool(content);

        // Then
        assertEquals("tool", message.getRole());
        assertEquals(content, message.getContent());
    }

    @Test
    @DisplayName("이미지 포함 user 메시지 테스트")
    void testUserMessageWithImages() {
        // Given
        String content = "이 이미지에 뭐가 있어?";
        List<String> images = List.of("base64EncodedImage1", "base64EncodedImage2");

        // When
        ChatMessage message = ChatMessage.user(content, images);

        // Then
        assertEquals("user", message.getRole());
        assertEquals(content, message.getContent());
        assertNotNull(message.getImages());
        assertEquals(2, message.getImages().size());
        assertEquals("base64EncodedImage1", message.getImages().get(0));
    }

    @Test
    @DisplayName("Builder 패턴 테스트")
    void testBuilder() {
        // Given & When
        ChatMessage message = ChatMessage.builder()
                .role("user")
                .content("빌더 테스트")
                .build();

        // Then
        assertEquals("user", message.getRole());
        assertEquals("빌더 테스트", message.getContent());
    }

    @Test
    @DisplayName("toBuilder 패턴 테스트")
    void testToBuilder() {
        // Given
        ChatMessage original = ChatMessage.user("원본 메시지");

        // When
        ChatMessage modified = original.toBuilder()
                .content("수정된 메시지")
                .build();

        // Then
        assertEquals("원본 메시지", original.getContent());
        assertEquals("수정된 메시지", modified.getContent());
        assertEquals(original.getRole(), modified.getRole());
    }
}
