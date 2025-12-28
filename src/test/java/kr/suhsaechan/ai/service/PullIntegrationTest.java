package kr.suhsaechan.ai.service;

import kr.suhsaechan.ai.model.PullProgress;
import kr.suhsaechan.ai.model.PullResult;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.context.ActiveProfiles;

import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Pull API 통합 테스트
 *
 * 테스트 대상 모델:
 * - qwen3-vl:2b (비전 모델)
 * - granite4:350m (소형 모델)
 *
 * 실행 방법:
 * 1. src/test/resources/application-dev.yml에 서버 설정
 * 2. IDE에서 mainTest() 실행
 *
 * 주의: 이 테스트는 실제 모델을 다운로드하므로 시간이 오래 걸릴 수 있습니다.
 */
@SpringBootTest
@ActiveProfiles("dev")
class PullIntegrationTest {

  // 테스트용 모델 (사용하지 않는 작은 모델들)
  private static final String TEST_MODEL_1 = "qwen3-vl:2b";
  private static final String TEST_MODEL_2 = "granite4:350m";

  @Autowired
  private SuhAiderEngine engine;

  @Test
  void mainTest() throws Exception {
    System.out.println("========================================");
    System.out.println("Pull API 통합 테스트 시작");
    System.out.println("========================================\n");

//    testPullModelSimple();
    testPullModelStream();
//    testPullModelAsync();
//    testPullModelCancel();
//    testPullModelsParallel();

    System.out.println("\n========================================");
    System.out.println("Pull API 통합 테스트 완료");
    System.out.println("========================================");
  }

  /**
   * 간단한 동기 다운로드 테스트
   */
  void testPullModelSimple() {
    System.out.println("✅ 간단한 동기 다운로드 테스트");
    System.out.println("  - 모델: " + TEST_MODEL_2);

    boolean success = engine.pullModel(TEST_MODEL_2);

    System.out.println("  - 성공 여부: " + success);
    System.out.println();
  }

  /**
   * 스트리밍 다운로드 테스트 (진행률 콜백)
   */
  void testPullModelStream() throws Exception {
    System.out.println("✅ 스트리밍 다운로드 테스트 (진행률 콜백)");
    System.out.println("  - 모델: " + TEST_MODEL_1);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PullResult> resultRef = new AtomicReference<>();
    AtomicBoolean progressReceived = new AtomicBoolean(false);

    PullHandle handle = engine.pullModelStream(TEST_MODEL_1, new PullCallback() {
      private long lastLogTime = 0;

      @Override
      public void onProgress(PullProgress progress) {
        progressReceived.set(true);
        // 1초마다 진행률 출력
        long now = System.currentTimeMillis();
        if (now - lastLogTime >= 1000) {
          System.out.println("  - 진행: " + progress.getFormattedProgress() + " [" + progress.getStatus() + "]");
          lastLogTime = now;
        }
      }

      @Override
      public void onComplete(PullResult result) {
        resultRef.set(result);
        latch.countDown();
      }

      @Override
      public void onError(Throwable error) {
        System.err.println("  - 에러 발생: " + error.getMessage());
        latch.countDown();
      }
    });

    System.out.println("  - Handle 모델: " + handle.getModelName());

    // 최대 10분 대기
    boolean completed = latch.await(10, TimeUnit.MINUTES);

    if (completed) {
      PullResult result = resultRef.get();
      System.out.println("  - 성공 여부: " + result.isSuccess());
      System.out.println("  - 소요 시간: " + result.getFormattedDuration());
      System.out.println("  - 진행률 수신: " + progressReceived.get());
    } else {
      System.out.println("  - 타임아웃 발생");
    }
    System.out.println();
  }

  /**
   * 비동기 다운로드 테스트
   */
  void testPullModelAsync() throws Exception {
    System.out.println("✅ 비동기 다운로드 테스트");
    System.out.println("  - 모델: " + TEST_MODEL_2);

    CompletableFuture<PullResult> future = engine.pullModelAsync(TEST_MODEL_2);

    System.out.println("  - 비동기 작업 시작됨");

    // 다른 작업 수행 가능
    System.out.println("  - (다른 작업 수행 중...)");
    Thread.sleep(1000);

    // 결과 대기
    PullResult result = future.get(10, TimeUnit.MINUTES);

    System.out.println("  - 성공 여부: " + result.isSuccess());
    System.out.println("  - 소요 시간: " + result.getFormattedDuration());
    System.out.println();
  }

  /**
   * 다운로드 취소 테스트
   */
  void testPullModelCancel() throws Exception {
    System.out.println("✅ 다운로드 취소 테스트");
    System.out.println("  - 모델: " + TEST_MODEL_1);

    CountDownLatch latch = new CountDownLatch(1);
    AtomicReference<PullResult> resultRef = new AtomicReference<>();

    PullHandle handle = engine.pullModelStream(TEST_MODEL_1, new PullCallback() {
      @Override
      public void onProgress(PullProgress progress) {
        System.out.println("  - 진행: " + progress.getFormattedProgress());
      }

      @Override
      public void onComplete(PullResult result) {
        resultRef.set(result);
        latch.countDown();
      }

      @Override
      public void onError(Throwable error) {
        System.err.println("  - 에러: " + error.getMessage());
        latch.countDown();
      }
    });

    // 2초 후 취소
    Thread.sleep(2000);
    System.out.println("  - 취소 요청...");
    handle.cancel();
    System.out.println("  - isCancelled: " + handle.isCancelled());

    // 완료 대기
    latch.await(30, TimeUnit.SECONDS);

    PullResult result = resultRef.get();
    if (result != null) {
      System.out.println("  - 결과 - 취소됨: " + result.isCancelled());
      System.out.println("  - 결과 - 성공: " + result.isSuccess());
    }
    System.out.println();
  }

  /**
   * 병렬 다운로드 테스트
   */
  void testPullModelsParallel() throws Exception {
    System.out.println("✅ 병렬 다운로드 테스트");
    System.out.println("  - 모델들: " + TEST_MODEL_1 + ", " + TEST_MODEL_2);

    List<String> models = List.of(TEST_MODEL_1, TEST_MODEL_2);
    long startTime = System.currentTimeMillis();

    CompletableFuture<List<PullResult>> future = engine.pullModelsAsync(models);
    List<PullResult> results = future.get(15, TimeUnit.MINUTES);

    long totalTime = System.currentTimeMillis() - startTime;

    System.out.println("  - 총 소요 시간: " + (totalTime / 1000) + "초");
    for (PullResult result : results) {
      System.out.printf("  - %s: %s (%s)%n",
          result.getModelName(),
          result.isSuccess() ? "성공" : "실패",
          result.getFormattedDuration());
    }
    System.out.println();
  }
}
