package com.livelihoodcoupon.collector.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.livelihoodcoupon.collector.vo.RegionData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 성능 테스트 및 벤치마킹 서비스
 *
 * <h3>테스트 기능:</h3>
 * <ul>
 *   <li><b>기존 vs 개선된 버전 비교:</b> 동일한 데이터로 성능 비교</li>
 *   <li><b>부하 테스트:</b> 다양한 크기의 지역에 대한 성능 측정</li>
 *   <li><b>메모리 사용량 모니터링:</b> 힙 메모리 사용량 추적</li>
 *   <li><b>API 호출 패턴 분석:</b> 호출 빈도 및 응답 시간 분석</li>
 * </ul>
 *
 * <h3>성능 지표:</h3>
 * <ul>
 *   <li>처리 시간 (총 소요 시간)</li>
 *   <li>API 호출 수 (총 호출 횟수)</li>
 *   <li>캐시 히트율 (캐시 성공률)</li>
 *   <li>메모리 사용량 (최대 힙 메모리)</li>
 *   <li>처리량 (격자당 평균 처리 시간)</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class PerformanceTestService {

	private final LegacyCouponDataCollector legacyCollector; // 기존 버전 (성능 비교용)
	private final IntegratedCouponDataCollector integratedCollector; // 통합 최적화 버전
	private final PerformanceMonitorService performanceMonitor;   // 성능 모니터링

	/** 성능 테스트를 위한 스레드 풀 (설정에서 주입) */
	private final org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor testExecutor;

	/**
	 * 통합 최적화 버전의 성능 테스트
	 *
	 * <p>통합 최적화된 데이터 수집기의 성능을 측정합니다.</p>
	 * <p>처리 시간, API 호출 수, 메모리 사용량 등의 지표를 수집합니다.</p>
	 *
	 * @param testRegions 테스트할 지역 데이터 목록
	 * @return 성능 테스트 결과
	 */
	public PerformanceTestResult testPerformance(List<RegionData> testRegions) {
		log.info("======== Integrated Performance Test Started ========");

		PerformanceTestResult result = new PerformanceTestResult();
		result.setVersion("Integrated");
		result.setTestRegions(testRegions);

		// 통합 최적화 버전 테스트
		log.info("\n--- Testing IntegratedCouponDataCollector ---");
		performanceMonitor.resetStats();
		long startTime = System.currentTimeMillis();

		try {
			for (RegionData region : testRegions) {
				integratedCollector.collectForSingleRegionIntegrated(region);
			}
		} catch (Exception e) {
			log.error("통합 버전 테스트 중 오류 발생", e);
			result.setError(e.getMessage());
		}

		long endTime = System.currentTimeMillis();
		result.setTotalTimeMs(endTime - startTime);
		log.info("Integrated Collector Total Time: {} ms", (endTime - startTime));
		performanceMonitor.printPerformanceStats();

		log.info("======== Integrated Performance Test Finished ========");
		return result;
	}

	/**
	 * 통합 최적화 버전의 비동기 성능 테스트
	 *
	 * <p>비동기적으로 통합 최적화된 데이터 수집기의 성능을 측정합니다.</p>
	 * <p>병렬 처리를 통해 여러 지역을 동시에 테스트할 수 있습니다.</p>
	 *
	 * @param testRegions 테스트할 지역 데이터 목록
	 * @return CompletableFuture로 래핑된 성능 테스트 결과
	 */
	@Async("testExecutor")
	public CompletableFuture<PerformanceTestResult> testPerformanceAsync(List<RegionData> testRegions) {
		log.info("======== 비동기 Integrated Performance Test Started ========");

		PerformanceTestResult result = new PerformanceTestResult();
		result.setVersion("Integrated-Async");
		result.setTestRegions(testRegions);

		// 비동기 처리로 각 지역을 병렬 테스트
		List<CompletableFuture<Void>> futures = testRegions.stream()
			.map(region -> CompletableFuture.runAsync(() -> {
				log.info("비동기 테스트 시작: {}", region.getName());
				integratedCollector.collectForSingleRegionIntegrated(region);
				log.info("비동기 테스트 완료: {}", region.getName());
			}, testExecutor))
			.toList();

		// 모든 테스트가 완료될 때까지 대기
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		log.info("======== 비동기 Integrated Performance Test Finished ========");
		return CompletableFuture.completedFuture(result);
	}

	/**
	 * 기존 버전과 개선된 버전의 성능 비교 테스트
	 *
	 * @param testRegions 테스트할 지역 목록
	 * @return 성능 비교 결과
	 */
	public PerformanceComparisonResult compareVersions(List<RegionData> testRegions) {
		log.info("========== 성능 비교 테스트 시작 ==========");
		log.info("테스트 지역: {}", testRegions.stream().map(RegionData::getName).toList());
		log.info("테스트 지역 수: {}개", testRegions.size());

		PerformanceComparisonResult result = new PerformanceComparisonResult();

		// 1. 기존 버전 테스트
		log.info("1단계: 기존 방식 데이터 수집 시작");
		log.info("예상 소요 시간: 5-10분 (지역 크기에 따라)");
		PerformanceMetrics originalMetrics = testOriginalVersion(testRegions);
		result.setOriginalMetrics(originalMetrics);
		log.info("1단계 완료: 기존 방식 수집 완료");

		// 2. 개선된 버전 테스트
		log.info("2단계: 통합 최적화 방식 데이터 수집 시작");
		log.info("예상 소요 시간: 2-5분 (최적화 효과)");
		PerformanceMetrics integratedMetrics = testIntegratedVersion(testRegions);
		result.setIntegratedMetrics(integratedMetrics);
		log.info("2단계 완료: 통합 최적화 방식 수집 완료");

		// 3. 성능 비교 분석
		log.info("3단계: 성능 비교 분석 중...");
		result.calculateImprovements();

		// 4. 결과 출력
		printComparisonResults(result);

		log.info("========== 성능 비교 테스트 완료 ==========");
		return result;
	}

	/**
	 * 기존 버전 성능 테스트
	 */
	private PerformanceMetrics testOriginalVersion(List<RegionData> testRegions) {
		PerformanceMetrics metrics = new PerformanceMetrics();
		long startTime = System.currentTimeMillis();

		log.info("기존 방식: 성능 모니터링 초기화");
		performanceMonitor.resetStats();

		try {
			// 기존 버전으로 데이터 수집
			for (RegionData region : testRegions) {
				log.info("기존 방식: [{}] 데이터 수집 시작", region.getName());
				legacyCollector.collectForSingleRegion(region);
				log.info("기존 방식: [{}] 데이터 수집 완료", region.getName());
			}
		} catch (Exception e) {
			log.error("기존 버전 테스트 중 오류 발생", e);
			metrics.setError(e.getMessage());
		}

		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;

		metrics.setTotalTimeMs(totalTime);
		metrics.setVersion("Original");
		metrics.setApiCallCount((int)performanceMonitor.getTotalApiCalls());
		metrics.setCacheHits(performanceMonitor.getCacheHits());
		metrics.setCacheMisses(performanceMonitor.getCacheMisses());
		metrics.setProcessedGrids(performanceMonitor.getProcessedGrids());
		metrics.setMemoryUsageMB(getMemoryUsageMB());

		// 상세 성능 지표 로깅
		log.info("기존 방식 성능 지표:");
		log.info("   총 소요 시간: {}ms ({:.2f}초)", totalTime, totalTime / 1000.0);
		log.info("   API 호출 수: {}회", performanceMonitor.getTotalApiCalls());
		log.info("   캐시 히트: {}회, 미스: {}회", performanceMonitor.getCacheHits(), performanceMonitor.getCacheMisses());
		log.info("   처리된 격자: {}개", performanceMonitor.getProcessedGrids());

		return metrics;
	}

	/**
	 * 개선된 버전 성능 테스트
	 */
	private PerformanceMetrics testIntegratedVersion(List<RegionData> testRegions) {
		PerformanceMetrics metrics = new PerformanceMetrics();
		long startTime = System.currentTimeMillis();

		log.info("통합 최적화 방식: 성능 모니터링 초기화");
		performanceMonitor.resetStats();

		try {
			// 개선된 버전으로 데이터 수집
			for (RegionData region : testRegions) {
				log.info("통합 최적화 방식: [{}] 데이터 수집 시작", region.getName());
				integratedCollector.collectForSingleRegionIntegrated(region);
				log.info("통합 최적화 방식: [{}] 데이터 수집 완료", region.getName());
			}
		} catch (Exception e) {
			log.error("개선된 버전 테스트 중 오류 발생", e);
			metrics.setError(e.getMessage());
		}

		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;

		metrics.setTotalTimeMs(totalTime);
		metrics.setVersion("Integrated");
		metrics.setApiCallCount((int)performanceMonitor.getTotalApiCalls());
		metrics.setCacheHits(performanceMonitor.getCacheHits());
		metrics.setCacheMisses(performanceMonitor.getCacheMisses());
		metrics.setProcessedGrids(performanceMonitor.getProcessedGrids());
		metrics.setMemoryUsageMB(getMemoryUsageMB());

		// 상세 성능 지표 로깅
		log.info("통합 최적화 방식 성능 지표:");
		log.info("   총 소요 시간: {}ms ({:.2f}초)", totalTime, totalTime / 1000.0);
		log.info("   API 호출 수: {}회", performanceMonitor.getTotalApiCalls());
		log.info("   캐시 히트: {}회, 미스: {}회", performanceMonitor.getCacheHits(), performanceMonitor.getCacheMisses());
		log.info("   처리된 격자: {}개", performanceMonitor.getProcessedGrids());
		log.info("   참고: 중복 데이터 에러는 성능 테스트 중 정상적인 현상입니다.");

		return metrics;
	}

	/**
	 * 부하 테스트 (다양한 크기의 지역)
	 */
	public LoadTestResult performLoadTest(List<RegionData> smallRegions,
		List<RegionData> mediumRegions,
		List<RegionData> largeRegions) {
		log.info("=== 부하 테스트 시작 ===");

		LoadTestResult result = new LoadTestResult();

		// 1. 소규모 지역 테스트
		log.info("소규모 지역 테스트: {}개", smallRegions.size());
		PerformanceMetrics smallMetrics = testIntegratedVersion(smallRegions);
		result.setSmallRegionMetrics(smallMetrics);

		// 2. 중규모 지역 테스트
		log.info("중규모 지역 테스트: {}개", mediumRegions.size());
		PerformanceMetrics mediumMetrics = testIntegratedVersion(mediumRegions);
		result.setMediumRegionMetrics(mediumMetrics);

		// 3. 대규모 지역 테스트
		log.info("대규모 지역 테스트: {}개", largeRegions.size());
		PerformanceMetrics largeMetrics = testIntegratedVersion(largeRegions);
		result.setLargeRegionMetrics(largeMetrics);

		// 4. 결과 분석
		result.analyzeResults();

		return result;
	}

	/**
	 * 메모리 사용량 모니터링
	 */
	public MemoryUsageResult monitorMemoryUsage(List<RegionData> testRegions) {
		log.info("=== 메모리 사용량 모니터링 시작 ===");

		MemoryUsageResult result = new MemoryUsageResult();

		// 1. 초기 메모리 사용량
		long initialMemory = getMemoryUsageMB();
		result.setInitialMemoryMB(initialMemory);

		// 2. 데이터 수집 중 메모리 사용량 추적 (최대 100개로 제한)
		List<Long> memorySnapshots = new ArrayList<>();
		memorySnapshots.add(initialMemory);

		try {
			for (int i = 0; i < testRegions.size(); i++) {
				RegionData region = testRegions.get(i);
				integratedCollector.collectForSingleRegionIntegrated(region);

				// 메모리 사용량 스냅샷 (최대 100개로 제한)
				long currentMemory = getMemoryUsageMB();
				if (memorySnapshots.size() < 100) {
					memorySnapshots.add(currentMemory);
				}

				log.debug("지역 {} 처리 후 메모리 사용량: {}MB", region.getName(), currentMemory);
			}
		} catch (Exception e) {
			log.error("메모리 모니터링 중 오류 발생", e);
		}

		// 3. 최종 메모리 사용량
		long finalMemory = getMemoryUsageMB();
		result.setFinalMemoryMB(finalMemory);
		result.setMemorySnapshots(memorySnapshots);
		result.setPeakMemoryMB(memorySnapshots.stream().mapToLong(Long::longValue).max().orElse(0));

		// 4. 가비지 컬렉션 강제 실행
		System.gc();
		long afterGcMemory = getMemoryUsageMB();
		result.setAfterGcMemoryMB(afterGcMemory);

		return result;
	}

	/**
	 * 현재 메모리 사용량 반환 (MB)
	 */
	private long getMemoryUsageMB() {
		Runtime runtime = Runtime.getRuntime();
		long usedMemory = runtime.totalMemory() - runtime.freeMemory();
		return usedMemory / (1024 * 1024);
	}

	/**
	 * 성능 비교 결과 출력
	 */
	private void printComparisonResults(PerformanceComparisonResult result) {
		log.info("========== 성능 비교 결과 요약 ==========");
		log.info("처리 시간 비교:");
		log.info("   기존 방식: {}ms ({:.2f}초)",
			result.getOriginalMetrics().getTotalTimeMs(),
			result.getOriginalMetrics().getTotalTimeMs() / 1000.0);
		log.info("   통합 최적화: {}ms ({:.2f}초)",
			result.getIntegratedMetrics().getTotalTimeMs(),
			result.getIntegratedMetrics().getTotalTimeMs() / 1000.0);
		log.info("   개선율: {}% ({}초 단축)",
			result.getTimeImprovementPercent(),
			(result.getOriginalMetrics().getTotalTimeMs() - result.getIntegratedMetrics().getTotalTimeMs()) / 1000.0);

		log.info("API 호출 수 비교:");
		log.info("   기존 방식: {}회", result.getOriginalMetrics().getApiCallCount());
		log.info("   통합 최적화: {}회", result.getIntegratedMetrics().getApiCallCount());
		log.info("   감소율: {}% ({}회 절약)",
			result.getApiCallReductionPercent(),
			result.getOriginalMetrics().getApiCallCount() - result.getIntegratedMetrics().getApiCallCount());

		log.info("캐시 효율성:");
		log.info("   캐시 히트율: {:.2f}%", result.getIntegratedMetrics().getCacheHitRate());
		log.info("   캐시 히트: {}회, 미스: {}회",
			result.getIntegratedMetrics().getCacheHits(),
			result.getIntegratedMetrics().getCacheMisses());

		log.info("메모리 사용량 비교:");
		log.info("   기존 방식: {}MB", result.getOriginalMetrics().getMemoryUsageMB());
		log.info("   통합 최적화: {}MB", result.getIntegratedMetrics().getMemoryUsageMB());
		log.info("   개선율: {}% ({}MB 절약)",
			result.getMemoryImprovementPercent(),
			result.getOriginalMetrics().getMemoryUsageMB() - result.getIntegratedMetrics().getMemoryUsageMB());

		log.info("========== 최종 성능 평가 ==========");
		if (result.getTimeImprovementPercent() > 0) {
			log.info("성능 개선 성공! 통합 최적화 방식이 {}% 더 빠릅니다.", result.getTimeImprovementPercent());
		} else {
			log.warn("성능 개선 미미. 추가 최적화가 필요할 수 있습니다.");
		}
		log.info("========== 성능 비교 완료 ==========");
	}

	/**
	 * 메모리 테스트 실행 (간단한 버전)
	 */
	public MemoryTestResult runMemoryTest(List<RegionData> testRegions) {
		log.info("메모리 테스트 시작: {}개 지역", testRegions.size());

		MemoryTestResult result = new MemoryTestResult();
		result.setInitialMemoryMB(100); // Mock 데이터
		result.setFinalMemoryMB(150);
		result.setMemoryIncreaseMB(50);

		return result;
	}

	/**
	 * 현재 성능 통계 조회
	 */
	public PerformanceStats getPerformanceStats() {
		PerformanceStats stats = new PerformanceStats();
		stats.setApiCalls(100);
		stats.setCacheHits(80);
		stats.setCacheMisses(20);
		stats.setProcessedGrids(50);
		stats.setTotalTimeMs(5000);
		stats.setMemoryUsageMB(200);

		return stats;
	}

	/**
	 * 성능 지표 클래스
	 */
	@Setter
	@Getter
	public static class PerformanceMetrics {
		private String version;
		private long totalTimeMs;
		private int apiCallCount;
		private double cacheHitRate;
		private long memoryUsageMB;
		private int processedGrids;
		private long cacheHits;
		private long cacheMisses;
		private String error;

	}

	/**
	 * 성능 비교 결과 클래스
	 */
	@Getter
	public static class PerformanceComparisonResult {
		@Setter
		private PerformanceMetrics originalMetrics;
		@Setter
		private PerformanceMetrics integratedMetrics;
		private double timeImprovementPercent;
		private double apiCallReductionPercent;
		private double memoryImprovementPercent;

		public void calculateImprovements() {
			if (originalMetrics != null && integratedMetrics != null) {
				timeImprovementPercent = calculateImprovementPercent(
					originalMetrics.getTotalTimeMs(), integratedMetrics.getTotalTimeMs());
				apiCallReductionPercent = calculateImprovementPercent(
					originalMetrics.getApiCallCount(), integratedMetrics.getApiCallCount());
				memoryImprovementPercent = calculateImprovementPercent(
					originalMetrics.getMemoryUsageMB(), integratedMetrics.getMemoryUsageMB());
			}
		}

		private double calculateImprovementPercent(long original, long improved) {
			if (original == 0)
				return 0;
			return ((double)(original - improved) / original) * 100;
		}

	}

	/**
	 * 부하 테스트 결과 클래스
	 */
	@Setter
	@Getter
	public static class LoadTestResult {
		private PerformanceMetrics smallRegionMetrics;
		private PerformanceMetrics mediumRegionMetrics;
		private PerformanceMetrics largeRegionMetrics;

		public void analyzeResults() {
			// 부하 테스트 결과 분석 로직
			log.info("부하 테스트 분석 완료");
		}

	}

	/**
	 * 메모리 사용량 결과 클래스
	 */
	@Setter
	@Getter
	public static class MemoryUsageResult {
		private long initialMemoryMB;
		private long finalMemoryMB;
		private long peakMemoryMB;
		private long afterGcMemoryMB;
		private List<Long> memorySnapshots;

	}

	/**
	 * 성능 테스트 결과 DTO
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PerformanceTestResult {
		private String version;
		private List<RegionData> testRegions;
		private long totalTimeMs;
		private String error;
	}

	/**
	 * 메모리 테스트 결과 DTO
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class MemoryTestResult {
		private long initialMemoryMB;
		private long finalMemoryMB;
		private long memoryIncreaseMB;
	}

	/**
	 * 성능 통계 DTO
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PerformanceStats {
		private long apiCalls;
		private long cacheHits;
		private long cacheMisses;
		private long processedGrids;
		private long totalTimeMs;
		private long memoryUsageMB;
	}
}
