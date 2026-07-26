package kr.suhsaechan.ai;

import org.springframework.boot.autoconfigure.SpringBootApplication;

/**
 * 테스트 전용 부트 애플리케이션
 *
 * <p>{@code @SpringBootTest}가 컨텍스트를 띄우려면 {@code @SpringBootConfiguration}이
 * 하나 필요합니다. v1.x는 이 역할을 {@code src/main}의 {@code AiApplication}이 했는데,
 * 그러면 라이브러리 배포 jar에 {@code @SpringBootApplication}이 실려 소비자 앱의
 * 컴포넌트 스캔과 자동설정에 개입할 수 있습니다.</p>
 *
 * <p>테스트 소스에 두면 배포 산출물에는 포함되지 않으면서 테스트는 그대로 동작합니다.</p>
 */
@SpringBootApplication
public class TestApplication {
}
