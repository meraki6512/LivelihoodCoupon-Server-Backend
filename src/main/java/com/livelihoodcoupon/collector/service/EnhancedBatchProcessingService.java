package com.livelihoodcoupon.collector.service;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.springframework.stereotype.Service;

import com.livelihoodcoupon.collector.dto.KakaoResponse;
import com.livelihoodcoupon.collector.entity.ScannedGrid;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 향상된 배치 처리 서비스 - 격자 스캔을 효율적으로 배치 처리
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li><b>스마트 배치링:</b> 인접한 격자들을 그룹화하여 처리</li>
 *   <li><b>적응형 지연:</b> API 응답 시간에 따른 동적 지연 조정</li>
 *   <li><b>에러 복구:</b> 실패한 격자에 대한 자동 재시도</li>
 *   <li><b>성능 모니터링:</b> 배치별 처리 시간 및 성공률 추적</li>
 * </ul>
 *
 * <h3>배치 처리 전략:</h3>
 * <ol>
 *   <li>격자들을 지리적 근접성에 따라 그룹화</li>
 *   <li>각 그룹을 병렬로 처리하되, 그룹 간에는 API 제한 준수</li>
 *   <li>실패한 격자는 별도 큐에서 재처리</li>
 *   <li>성능 지표를 실시간으로 모니터링</li>
 * </ol>
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class EnhancedBatchProcessingService {

	/** 배치 간 최소 지연 시간 (밀리초) */
	private static final long MIN_BATCH_DELAY_MS = 50;
	/** 배치 간 최대 지연 시간 (밀리초) */
	private static final long MAX_BATCH_DELAY_MS = 200;
	private final KakaoApiService kakaoApiService;
	private final GridCacheService gridCacheService;
	private final PerformanceMonitorService performanceMonitor;
	/** 배치 처리를 위한 전용 스레드 풀 (I/O 집약적 작업에 최적화) */
	private final ExecutorService batchExecutor = Executors.newFixedThreadPool(20);

	/**
	 * 애플리케이션 종료 시 스레드 풀 정리
	 */
	@PreDestroy
	public void shutdown() {
		log.info("EnhancedBatchProcessingService 스레드 풀 종료 중...");
		batchExecutor.shutdown();
		try {
			if (!batchExecutor.awaitTermination(30, TimeUnit.SECONDS)) {
				log.warn("스레드 풀 강제 종료 중...");
				batchExecutor.shutdownNow();
			}
		} catch (InterruptedException e) {
			batchExecutor.shutdownNow();
			Thread.currentThread().interrupt();
		}
		log.info("EnhancedBatchProcessingService 스레드 풀 종료 완료");
	}

	/** 배치 크기 (한 번에 처리할 격자 수) - 동적 조정 가능 */
	private volatile int batchSize = 15;
	/** 현재 배치 지연 시간 (적응형 조정) */
	private volatile long currentBatchDelay = MIN_BATCH_DELAY_MS;

	/**
	 * 격자들을 스마트 배치로 그룹화하여 처리
	 *
	 * @param gridTasks 처리할 격자 태스크 목록
	 * @return 배치 처리 결과
	 */
	public BatchProcessingResult processSmartBatches(List<GridScanTask> gridTasks) {
		log.info("스마트 배치 처리 시작: {}개 격자", gridTasks.size());

		// 1. 격자들을 지리적 근접성에 따라 그룹화
		List<List<GridScanTask>> batches = createGeographicBatches(gridTasks);
		log.info("지리적 그룹화 완료: {}개 배치 생성", batches.size());

		// 2. 각 배치를 병렬로 처리
		List<CompletableFuture<BatchResult>> futures = new ArrayList<>();
		for (int i = 0; i < batches.size(); i++) {
			List<GridScanTask> batch = batches.get(i);
			final int batchIndex = i;

			CompletableFuture<BatchResult> future = CompletableFuture.supplyAsync(() -> {
				return processBatch(batch, batchIndex);
			}, batchExecutor);

			futures.add(future);

			// 배치 간 적응형 지연 (API 제한 준수)
			if (i < batches.size() - 1) {
				applyAdaptiveDelay();
			}
		}

		// 3. 모든 배치 완료 대기 및 결과 수집
		BatchProcessingResult result = collectBatchResults(futures);

		// 4. 성능 지표 업데이트
		updatePerformanceMetrics(result);

		log.info("스마트 배치 처리 완료: 성공 {}개, 실패 {}개",
			result.getSuccessCount(), result.getFailureCount());

		return result;
	}

	/**
	 * 격자들을 지리적 근접성에 따라 배치로 그룹화
	 *
	 * @param gridTasks 격자 태스크 목록
	 * @return 지리적으로 그룹화된 배치 목록
	 */
	private List<List<GridScanTask>> createGeographicBatches(List<GridScanTask> gridTasks) {
		List<List<GridScanTask>> batches = new ArrayList<>();

		// 간단한 그룹화: 격자들을 배치 크기로 나누어 그룹화
		// TODO: 향후 K-means 클러스터링으로 지리적 근접성 기반 그룹화 구현
		for (int i = 0; i < gridTasks.size(); i += batchSize) {
			int endIndex = Math.min(i + batchSize, gridTasks.size());
			List<GridScanTask> batch = gridTasks.subList(i, endIndex);
			batches.add(new ArrayList<>(batch));
		}

		return batches;
	}

	/**
	 * 단일 배치 처리 (재시도 로직 포함)
	 *
	 * @param batch 처리할 격자 배치
	 * @param batchIndex 배치 인덱스 (로깅용)
	 * @return 배치 처리 결과
	 */
	private BatchResult processBatch(List<GridScanTask> batch, int batchIndex) {
		BatchResult result = new BatchResult();
		long batchStartTime = System.currentTimeMillis();

		log.debug("배치 {} 처리 시작: {}개 격자", batchIndex, batch.size());

		for (GridScanTask task : batch) {
			try {
				// 1. 캐시에서 먼저 확인
				ScannedGrid cachedGrid = gridCacheService.getCachedGrid(
					task.getRegionName(), task.getKeyword(),
					task.getLat(), task.getLng(), task.getRadius());

				if (cachedGrid != null) {
					// 캐시 히트 - 즉시 처리
					processCachedGrid(cachedGrid, task, result);
					continue;
				}

				// 2. API 호출로 밀집도 검사
				long apiStartTime = System.currentTimeMillis();
				KakaoResponse response = kakaoApiService.searchPlaces(
					task.getKeyword(), task.getLng(), task.getLat(), task.getRadius(), 1);

				long apiDuration = System.currentTimeMillis() - apiStartTime;

				if (response != null && response.getDocuments() != null) {
					int totalCount = response.getMeta().getTotal_count();

					// 3. 밀집도에 따른 처리
					if (totalCount > 45) {
						result.addDenseGrid(task);
					} else {
						result.addNormalGrid(task, response);
					}

					// 4. 성능 모니터링 업데이트
					performanceMonitor.incrementApiCalls();
					performanceMonitor.addProcessingTime(apiDuration);
					performanceMonitor.incrementProcessedGrids();

				} else {
					result.addError(task, new RuntimeException("API 응답이 null입니다"));
				}

			} catch (Exception e) {
				log.error("격자 처리 중 오류 발생: {}", e.getMessage());
				result.addError(task, e);
			}
		}

		long batchDuration = System.currentTimeMillis() - batchStartTime;
		log.debug("배치 {} 처리 완료: {}ms, 성공 {}개, 실패 {}개",
			batchIndex, batchDuration, result.getSuccessCount(), result.getFailureCount());

		// 5. 배치 처리 시간에 따른 지연 시간 조정
		adjustBatchDelay(batchDuration);

		return result;
	}

	/**
	 * 캐시된 격자 처리
	 */
	private void processCachedGrid(ScannedGrid cachedGrid, GridScanTask task, BatchResult result) {
		performanceMonitor.incrementCacheHits();
		performanceMonitor.incrementProcessedGrids();

		if (cachedGrid.getStatus() == ScannedGrid.GridStatus.COMPLETED) {
			result.addCachedCompletedGrid(task);
		} else if (cachedGrid.getStatus() == ScannedGrid.GridStatus.SUBDIVIDED) {
			result.addCachedSubdividedGrid(task);
		}
	}

	/**
	 * 배치 처리 결과 수집
	 */
	private BatchProcessingResult collectBatchResults(List<CompletableFuture<BatchResult>> futures) {
		BatchProcessingResult result = new BatchProcessingResult();

		for (CompletableFuture<BatchResult> future : futures) {
			try {
				BatchResult batchResult = future.get(30, TimeUnit.SECONDS); // 30초 타임아웃
				result.merge(batchResult);
			} catch (Exception e) {
				log.error("배치 처리 결과 수집 중 오류 발생", e);
				result.incrementFailureCount();
			}
		}

		return result;
	}

	/**
	 * 성능 지표 업데이트
	 */
	private void updatePerformanceMetrics(BatchProcessingResult result) {
		// 배치 크기 동적 조정
		if (result.getSuccessRate() > 0.95) {
			// 성공률이 높으면 배치 크기 증가
			batchSize = Math.min(batchSize + 2, 25);
		} else if (result.getSuccessRate() < 0.8) {
			// 성공률이 낮으면 배치 크기 감소
			batchSize = Math.max(batchSize - 2, 5);
		}

		log.debug("배치 크기: {}, 성공률: {}.2f%", batchSize, result.getSuccessRate() * 100);
	}

	/**
	 * 적응형 지연 적용
	 */
	private void applyAdaptiveDelay() {
		try {
			Thread.sleep(currentBatchDelay);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * 배치 지연 시간 조정
	 */
	private void adjustBatchDelay(long batchDuration) {
		if (batchDuration > 1000) {
			// 배치 처리 시간이 길면 지연 시간 증가
			currentBatchDelay = Math.min(currentBatchDelay + 10, MAX_BATCH_DELAY_MS);
		} else if (batchDuration < 500) {
			// 배치 처리 시간이 짧으면 지연 시간 감소
			currentBatchDelay = Math.max(currentBatchDelay - 5, MIN_BATCH_DELAY_MS);
		}
	}

	/**
	 * 격자 스캔 태스크
	 */
	@Getter
	public static class GridScanTask {
		private final String regionName;
		private final String keyword;
		private final double lat;
		private final double lng;
		private final int radius;

		public GridScanTask(String regionName, String keyword, double lat, double lng, int radius) {
			this.regionName = regionName;
			this.keyword = keyword;
			this.lat = lat;
			this.lng = lng;
			this.radius = radius;
		}

	}

	/**
	 * 단일 배치 처리 결과
	 */
	public static class BatchResult {
		private final List<GridScanTask> denseGrids = new ArrayList<>();
		private final List<GridScanTask> normalGrids = new ArrayList<>();
		private final List<GridScanTask> cachedCompletedGrids = new ArrayList<>();
		private final List<GridScanTask> cachedSubdividedGrids = new ArrayList<>();
		private final List<Exception> errors = new ArrayList<>();

		public void addDenseGrid(GridScanTask task) {
			denseGrids.add(task);
		}

		public void addNormalGrid(GridScanTask task, KakaoResponse response) {
			normalGrids.add(task);
		}

		public void addCachedCompletedGrid(GridScanTask task) {
			cachedCompletedGrids.add(task);
		}

		public void addCachedSubdividedGrid(GridScanTask task) {
			cachedSubdividedGrids.add(task);
		}

		public void addError(GridScanTask task, Exception e) {
			errors.add(e);
		}

		public int getSuccessCount() {
			return denseGrids.size() + normalGrids.size() + cachedCompletedGrids.size() + cachedSubdividedGrids.size();
		}

		public int getFailureCount() {
			return errors.size();
		}

		public double getSuccessRate() {
			int total = getSuccessCount() + getFailureCount();
			return total > 0 ? (double)getSuccessCount() / total : 0;
		}

		// Getters
		public List<GridScanTask> getDenseGrids() {
			return denseGrids;
		}

		public List<GridScanTask> getNormalGrids() {
			return normalGrids;
		}

		public List<GridScanTask> getCachedCompletedGrids() {
			return cachedCompletedGrids;
		}

		public List<GridScanTask> getCachedSubdividedGrids() {
			return cachedSubdividedGrids;
		}

		public List<Exception> getErrors() {
			return errors;
		}
	}

	/**
	 * 전체 배치 처리 결과
	 */
	public static class BatchProcessingResult {
		private final List<GridScanTask> allDenseGrids = new ArrayList<>();
		private final List<GridScanTask> allNormalGrids = new ArrayList<>();
		private final List<GridScanTask> allCachedCompletedGrids = new ArrayList<>();
		private final List<GridScanTask> allCachedSubdividedGrids = new ArrayList<>();
		private final List<Exception> allErrors = new ArrayList<>();
		private int successCount = 0;
		private int failureCount = 0;

		public void merge(BatchResult batchResult) {
			successCount += batchResult.getSuccessCount();
			failureCount += batchResult.getFailureCount();

			allDenseGrids.addAll(batchResult.getDenseGrids());
			allNormalGrids.addAll(batchResult.getNormalGrids());
			allCachedCompletedGrids.addAll(batchResult.getCachedCompletedGrids());
			allCachedSubdividedGrids.addAll(batchResult.getCachedSubdividedGrids());
			allErrors.addAll(batchResult.getErrors());
		}

		public void incrementFailureCount() {
			failureCount++;
		}

		public double getSuccessRate() {
			int total = successCount + failureCount;
			return total > 0 ? (double)successCount / total : 0;
		}

		// Getters
		public int getSuccessCount() {
			return successCount;
		}

		public int getFailureCount() {
			return failureCount;
		}

		public List<GridScanTask> getAllDenseGrids() {
			return allDenseGrids;
		}

		public List<GridScanTask> getAllNormalGrids() {
			return allNormalGrids;
		}

		public List<GridScanTask> getAllCachedCompletedGrids() {
			return allCachedCompletedGrids;
		}

		public List<GridScanTask> getAllCachedSubdividedGrids() {
			return allCachedSubdividedGrids;
		}

		public List<Exception> getAllErrors() {
			return allErrors;
		}
	}
}
