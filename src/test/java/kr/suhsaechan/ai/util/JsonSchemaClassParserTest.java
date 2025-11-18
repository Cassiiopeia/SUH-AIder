package kr.suhsaechan.ai.util;

import kr.suhsaechan.ai.annotation.*;
import kr.suhsaechan.ai.model.JsonSchema;
import kr.suhsaechan.ai.model.PropertySchema;
import lombok.Data;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.*;

/**
 * JsonSchemaClassParser 테스트
 */
class JsonSchemaClassParserTest {

    @Test
    @DisplayName("기본 필드 타입 추론 테스트")
    void testBasicTypeInference() {
        // Given
        @AiClass(title = "기본 타입 테스트")
        @Data
        class BasicTypes {
            private String name;
            private Integer age;
            private Boolean active;
            private Double score;
        }

        // When
        JsonSchema schema = JsonSchema.fromClass(BasicTypes.class);

        // Then
        assertEquals("object", schema.getType());
        assertEquals("기본 타입 테스트", schema.getTitle());
        assertEquals(4, schema.getProperties().size());

        assertEquals("string", schema.getProperties().get("name").getType());
        assertEquals("integer", schema.getProperties().get("age").getType());
        assertEquals("boolean", schema.getProperties().get("active").getType());
        assertEquals("number", schema.getProperties().get("score").getType());
    }

    @Test
    @DisplayName("@AiSchema 어노테이션 속성 적용 테스트")
    void testAiSchemaAnnotation() {
        // Given
        @AiClass(
                title = "사용자 정보",
                description = "회원가입 시 입력받는 정보"
        )
        @Data
        class UserInfo {
            @AiSchema(
                    description = "사용자 이름",
                    required = true,
                    minLength = 2,
                    maxLength = 50,
                    example = "홍길동"
            )
            private String name;

            @AiSchema(
                    description = "나이",
                    minimum = "0",
                    maximum = "150"
            )
            private Integer age;

            @AiSchema(
                    description = "이메일",
                    format = "email",
                    pattern = "^[A-Za-z0-9+_.-]+@(.+)$"
            )
            private String email;
        }

        // When
        JsonSchema schema = JsonSchema.fromClass(UserInfo.class);

        // Then
        assertEquals("사용자 정보", schema.getTitle());
        assertEquals("회원가입 시 입력받는 정보", schema.getDescription());

        PropertySchema nameSchema = schema.getProperties().get("name");
        assertEquals("사용자 이름", nameSchema.getDescription());
        assertEquals(2, nameSchema.getMinLength());
        assertEquals(50, nameSchema.getMaxLength());
        assertEquals("홍길동", nameSchema.getExample());

        PropertySchema ageSchema = schema.getProperties().get("age");
        assertEquals(0.0, ageSchema.getMinimum());
        assertEquals(150.0, ageSchema.getMaximum());

        PropertySchema emailSchema = schema.getProperties().get("email");
        assertEquals("email", emailSchema.getFormat());
        assertEquals("^[A-Za-z0-9+_.-]+@(.+)$", emailSchema.getPattern());

        // required 필드 확인
        assertTrue(schema.getRequiredFields().contains("name"));
        assertEquals(1, schema.getRequiredFields().size());
    }

    @Test
    @DisplayName("@AiHidden 필드 제외 테스트")
    void testAiHiddenAnnotation() {
        // Given
        @Data
        class UserWithHidden {
            @AiSchema(description = "공개 이름")
            private String publicName;

            @AiHidden
            private String internalId;

            @AiHidden
            private String password;
        }

        // When
        JsonSchema schema = JsonSchema.fromClass(UserWithHidden.class);

        // Then
        assertEquals(1, schema.getProperties().size());
        assertTrue(schema.getProperties().containsKey("publicName"));
        assertFalse(schema.getProperties().containsKey("internalId"));
        assertFalse(schema.getProperties().containsKey("password"));
    }

    @Test
    @DisplayName("@AiArraySchema 배열 타입 테스트")
    void testAiArraySchemaAnnotation() {
        // Given
        @Data
        class ArrayTest {
            @AiSchema(description = "관심사 목록")
            @AiArraySchema(
                    itemType = String.class,
                    minItems = 1,
                    maxItems = 10,
                    uniqueItems = true
            )
            private List<String> interests;

            @AiArraySchema(itemType = Integer.class)
            private int[] scores;
        }

        // When
        JsonSchema schema = JsonSchema.fromClass(ArrayTest.class);

        // Then
        PropertySchema interestsSchema = schema.getProperties().get("interests");
        assertEquals("array", interestsSchema.getType());
        assertEquals(1, interestsSchema.getMinItems());
        assertEquals(10, interestsSchema.getMaxItems());
        assertTrue(interestsSchema.isUniqueItems());
        assertNotNull(interestsSchema.getItems());
        assertEquals("string", interestsSchema.getItems().getType());

        PropertySchema scoresSchema = schema.getProperties().get("scores");
        assertEquals("array", scoresSchema.getType());
        assertEquals("integer", scoresSchema.getItems().getType());
    }

    @Test
    @DisplayName("allowableValues (enum) 테스트")
    void testAllowableValues() {
        // Given
        @Data
        class EnumTest {
            @AiSchema(
                    description = "회원 등급",
                    allowableValues = {"BRONZE", "SILVER", "GOLD", "PLATINUM"}
            )
            private String membershipLevel;
        }

        // When
        JsonSchema schema = JsonSchema.fromClass(EnumTest.class);

        // Then
        PropertySchema levelSchema = schema.getProperties().get("membershipLevel");
        assertNotNull(levelSchema.getEnumValues());
        assertEquals(4, levelSchema.getEnumValues().size());
        assertTrue(levelSchema.getEnumValues().contains("BRONZE"));
        assertTrue(levelSchema.getEnumValues().contains("PLATINUM"));
    }

    @Test
    @DisplayName("중첩 객체 테스트")
    void testNestedObject() {
        // Given
        @AiClass(title = "주소")
        @Data
        class Address {
            @AiSchema(description = "도시", required = true)
            private String city;

            @AiSchema(description = "우편번호")
            private String zipCode;
        }

        @AiClass(title = "사용자")
        @Data
        class User {
            @AiSchema(description = "이름")
            private String name;

            @AiSchema(description = "주소")
            private Address address;
        }

        // When
        JsonSchema schema = JsonSchema.fromClass(User.class);

        // Then
        PropertySchema addressSchema = schema.getProperties().get("address");
        assertEquals("object", addressSchema.getType());
        assertNotNull(addressSchema.getProperties());
        assertEquals(2, addressSchema.getProperties().size());

        PropertySchema citySchema = addressSchema.getProperties().get("city");
        assertEquals("string", citySchema.getType());
        assertEquals("도시", citySchema.getDescription());
    }

    @Test
    @DisplayName("객체 배열 테스트")
    void testObjectArray() {
        // Given
        @AiClass(title = "친구 정보")
        @Data
        class Friend {
            private String name;
            private Integer age;
        }

        @Data
        class UserWithFriends {
            @AiSchema(description = "친구 목록")
            @AiArraySchema(itemType = Friend.class, maxItems = 100)
            private List<Friend> friends;
        }

        // When
        JsonSchema schema = JsonSchema.fromClass(UserWithFriends.class);

        // Then
        PropertySchema friendsSchema = schema.getProperties().get("friends");
        assertEquals("array", friendsSchema.getType());
        assertEquals(100, friendsSchema.getMaxItems());

        PropertySchema itemSchema = friendsSchema.getItems();
        assertEquals("object", itemSchema.getType());
        assertNotNull(itemSchema.getProperties());
        assertTrue(itemSchema.getProperties().containsKey("name"));
        assertTrue(itemSchema.getProperties().containsKey("age"));
    }

    @Test
    @DisplayName("타입 명시 테스트 (@AiSchema의 type 속성)")
    void testExplicitType() {
        // Given
        @Data
        class ExplicitTypeTest {
            @AiSchema(type = "string", description = "명시적 문자열")
            private Object someField;
        }

        // When
        JsonSchema schema = JsonSchema.fromClass(ExplicitTypeTest.class);

        // Then
        PropertySchema someFieldSchema = schema.getProperties().get("someField");
        assertEquals("string", someFieldSchema.getType());
    }

    @Test
    @DisplayName("숫자 제약 테스트 (exclusive)")
    void testExclusiveConstraints() {
        // Given
        @Data
        class ExclusiveTest {
            @AiSchema(
                    minimum = "0",
                    maximum = "100",
                    exclusiveMinimum = true,
                    exclusiveMaximum = true
            )
            private Double percentage;
        }

        // When
        JsonSchema schema = JsonSchema.fromClass(ExclusiveTest.class);

        // Then
        PropertySchema percentageSchema = schema.getProperties().get("percentage");
        assertEquals(0.0, percentageSchema.getMinimum());
        assertEquals(100.0, percentageSchema.getMaximum());
        assertTrue(percentageSchema.isExclusiveMinimum());
        assertTrue(percentageSchema.isExclusiveMaximum());
    }
}
