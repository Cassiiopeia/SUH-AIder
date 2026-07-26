package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.PullProgress;
import kr.suhsaechan.ai.model.PullResult;
import kr.suhsaechan.ai.support.TestModels;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Tag;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.condition.EnabledIfEnvironmentVariable;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * Pull API 통합 테스트 (실서버 필요)
 *
 * <p>실제 모델을 내려받으므로 시간이 걸립니다.</p>
 *
 * <pre>
 * SUH_AIDER_IT=true ./gradlew integrationTest
 * </pre>
 */
@Tag("integration")
@EnabledIfEnvironmentVariable(named = "SUH_AIDER_IT", matches = "true")
@SpringBootTest
@ActiveProfiles("dev")
class PullIntegrationTest {

  @Autowired
  private SuhAiderEngine engine;

  @Test
  @DisplayName("스트리밍 다운로드는 진행률을 주고 반드시 종료 통지를 한다")
  void pullModelStream() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PullResult> resultRef = new AtomicReference<>();
    AtomicBoolean progressReceived = new AtomicBoolean(false);

    PullHandle handle = engine.pullModelStream(TestModels.PULL_TARGET, new PullCallback() {
      @Override
      public void onProgress(PullProgress progress) {
        progressReceived.set(true);
      }

      @Override
      public void onComplete(PullResult result) {
        resultRef.set(result);
        latch.countDown();
      }
    });

    assertTrue(latch.await(10, TimeUnit.MINUTES), "10분 내에 종료 통지가 와야 한다");

    // v1.x는 실패가 onError로 갈리면 resultRef가 비어 NPE가 났다.
    // 종료 계약이 onComplete 하나로 통일돼 항상 결과가 들어온다.
    PullResult result = resultRef.get();
    assertNotNull(result, "onComplete는 항상 결과를 전달해야 한다");
    assertTrue(progressReceived.get(), "진행률을 최소 한 번은 받아야 한다");
    assertTrue(handle.isDone());
  }

  @Test
  @DisplayName("비동기 다운로드는 결과를 Future로 돌려준다")
  void pullModelAsync() throws Exception {
    PullResult result = engine.pullModelAsync(TestModels.PULL_TARGET).get(10, TimeUnit.MINUTES);

    assertNotNull(result);
    assertNotNull(result.getModelName());
  }

  @Test
  @DisplayName("취소하면 취소 결과로 종료된다")
  void pullModelCancel() throws Exception {
    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PullResult> resultRef = new AtomicReference<>();

    PullHandle handle = engine.pullModelStream(TestModels.PULL_TARGET, new PullCallback() {
      @Override
      public void onProgress(PullProgress progress) {
        // 진행률은 확인하지 않는다
      }

      @Override
      public void onComplete(PullResult result) {
        resultRef.set(result);
        latch.countDown();
      }
    });

    Thread.sleep(2000);
    handle.cancel();

    assertTrue(latch.await(1, TimeUnit.MINUTES), "취소 후에도 종료 통지가 와야 한다");
    assertNotNull(resultRef.get());
  }
}
