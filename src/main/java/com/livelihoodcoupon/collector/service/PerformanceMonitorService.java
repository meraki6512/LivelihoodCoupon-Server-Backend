package com.livelihoodcoupon.collector.service;

import java.util.concurrent.atomic.AtomicInteger;
import java.util.concurrent.atomic.AtomicLong;

import org.springframework.stereotype.Service;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 데이터 수집 성능을 모니터링하는 서비스
 *
 * <h3>모니터링 지표:</h3>
 * <ul>
 *   <li><b>API 호출 수:</b> 총 API 호출 횟수</li>
 *   <li><b>캐시 히트율:</b> 캐시 성공률</li>
 *   <li><b>처리 속도:</b> 격자당 평균 처리 시간</li>
 *   <li><b>메모리 사용량:</b> 힙 메모리 사용률</li>
 * </ul>
 */
@Slf4j
@Getter
@Service
public class PerformanceMonitorService {

	private final AtomicLong totalApiCalls = new AtomicLong(0);
	private final AtomicLong cacheHits = new AtomicLong(0);
	private final AtomicLong cacheMisses = new AtomicLong(0);
	private final AtomicInteger processedGrids = new AtomicInteger(0);
	private final AtomicLong totalProcessingTime = new AtomicLong(0);

	/**
	 * API 호출 카운트 증가
	 */
	public void incrementApiCalls() {
		totalApiCalls.incrementAndGet();
	}

	/**
	 * 캐시 히트 카운트 증가
	 */
	public void incrementCacheHits() {
		cacheHits.incrementAndGet();
	}

	/**
	 * 캐시 미스 카운트 증가
	 */
	public void incrementCacheMisses() {
		cacheMisses.incrementAndGet();
	}

	/**
	 * 처리된 격자 수 증가
	 */
	public void incrementProcessedGrids() {
		processedGrids.incrementAndGet();
	}

	/**
	 * 처리 시간 추가
	 */
	public void addProcessingTime(long timeMs) {
		totalProcessingTime.addAndGet(timeMs);
	}

	/**
	 * 성능 통계 출력
	 */
	public void printPerformanceStats() {
		long totalCacheRequests = cacheHits.get() + cacheMisses.get();
		double cacheHitRate = totalCacheRequests > 0 ?
			(double)cacheHits.get() / totalCacheRequests * 100 : 0;

		double avgProcessingTime = processedGrids.get() > 0 ?
			(double)totalProcessingTime.get() / processedGrids.get() : 0;

		log.info("=== 성능 통계 ===");
		log.info("총 API 호출 수: {}", totalApiCalls.get());
		log.info("캐시 히트율: {:.2f}% ({}/{})", cacheHitRate, cacheHits.get(), totalCacheRequests);
		log.info("처리된 격자 수: {}", processedGrids.get());
		log.info("격자당 평균 처리 시간: {:.2f}ms", avgProcessingTime);
		log.info("메모리 사용량: {}MB", getMemoryUsageMB());
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
	 * 통계 초기화
	 */
	public void resetStats() {
		totalApiCalls.set(0);
		cacheHits.set(0);
		cacheMisses.set(0);
		processedGrids.set(0);
		totalProcessingTime.set(0);
		log.info("성능 통계 초기화 완료");
	}

	/**
	 * 총 API 호출 수 반환
	 */
	public long getTotalApiCalls() {
		return totalApiCalls.get();
	}

	/**
	 * 캐시 히트 수 반환
	 */
	public long getCacheHits() {
		return cacheHits.get();
	}

	/**
	 * 캐시 미스 수 반환
	 */
	public long getCacheMisses() {
		return cacheMisses.get();
	}

	/**
	 * 처리된 격자 수 반환
	 */
	public int getProcessedGrids() {
		return processedGrids.get();
	}

	/**
	 * 총 처리 시간 반환 (ms)
	 */
	public long getTotalProcessingTime() {
		return totalProcessingTime.get();
	}

	/**
	 * 성능 통계 DTO 반환
	 */
	public PerformanceStatsDto getPerformanceStatsDto() {
		PerformanceStatsDto dto = new PerformanceStatsDto();
		dto.setApiCalls(totalApiCalls.get());
		dto.setCacheHits(cacheHits.get());
		dto.setCacheMisses(cacheMisses.get());
		dto.setProcessedGrids(processedGrids.get());
		dto.setTotalTimeMs(totalProcessingTime.get());
		dto.setMemoryUsageMB(getMemoryUsageMB());
		return dto;
	}

	/**
	 * 성능 통계 DTO
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class PerformanceStatsDto {
		private long apiCalls;
		private long cacheHits;
		private long cacheMisses;
		private long processedGrids;
		private long totalTimeMs;
		private long memoryUsageMB;
	}
}
