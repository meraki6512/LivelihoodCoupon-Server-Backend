package com.livelihoodcoupon.common.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.scheduling.annotation.EnableScheduling;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

/**
 * 메모리 관리 서비스
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li><b>정기적인 GC 실행:</b> 메모리 사용률이 높을 때 강제 GC 실행</li>
 *   <li><b>메모리 모니터링:</b> 주기적으로 메모리 상태 체크 및 로깅</li>
 *   <li><b>메모리 경고:</b> 메모리 사용률이 임계치를 초과할 때 경고</li>
 * </ul>
 *
 * @author livelihoodCoupon
 * @since 1.0.0
 */
@Service
@EnableScheduling
public class MemoryManagementService {

	private static final Logger log = LoggerFactory.getLogger(MemoryManagementService.class);

	/** 메모리 사용률 임계치 (80%) */
	private static final double MEMORY_THRESHOLD = 0.8;

	/** GC 실행 간격 (5분) */
	private static final long GC_INTERVAL_MS = 5 * 60 * 1000;

	/** 마지막 GC 실행 시간 */
	private long lastGcTime = 0;

	/**
	 * 5분마다 메모리 상태 체크 및 필요시 GC 실행
	 */
	@Scheduled(fixedRate = GC_INTERVAL_MS)
	public void monitorAndManageMemory() {
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		double memoryUsageRatio = (double)usedMemory / maxMemory;

		// 메모리 사용률 로깅
		log.debug("메모리 상태 - 사용률: {:.1f}%, 사용: {}MB, 최대: {}MB",
			memoryUsageRatio * 100, usedMemory / 1024 / 1024, maxMemory / 1024 / 1024);

		// 메모리 사용률이 임계치를 초과하면 경고
		if (memoryUsageRatio > MEMORY_THRESHOLD) {
			log.warn("주의) 메모리 사용률이 높습니다: {:.1f}% (임계치: {:.1f}%)",
				memoryUsageRatio * 100, MEMORY_THRESHOLD * 100);
		}

		// 메모리 사용률이 70% 이상이거나, 마지막 GC로부터 10분이 지났으면 GC 실행
		long currentTime = System.currentTimeMillis();
		boolean shouldRunGc = memoryUsageRatio > 0.7 || (currentTime - lastGcTime) > 10 * 60 * 1000;

		if (shouldRunGc) {
			runGarbageCollection();
		}
	}

	/**
	 * 강제 가비지 컬렉션 실행
	 */
	public void runGarbageCollection() {
		long beforeMemory = getUsedMemoryMB();
		long startTime = System.currentTimeMillis();

		log.info("🗑️ 강제 가비지 컬렉션 실행 중... (실행 전 메모리: {}MB)", beforeMemory);

		// GC 실행
		System.gc();

		// 잠시 대기 후 메모리 상태 확인
		try {
			Thread.sleep(100);
		} catch (InterruptedException e) {
			Thread.currentThread().interrupt();
		}

		long afterMemory = getUsedMemoryMB();
		long duration = System.currentTimeMillis() - startTime;
		long freedMemory = beforeMemory - afterMemory;

		log.info("가비지 컬렉션 완료 - 실행 후 메모리: {}MB, 해제된 메모리: {}MB, 소요시간: {}ms",
			afterMemory, freedMemory, duration);

		lastGcTime = System.currentTimeMillis();
	}

	/**
	 * 현재 사용 중인 메모리 반환 (MB)
	 */
	private long getUsedMemoryMB() {
		Runtime runtime = Runtime.getRuntime();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		return (totalMemory - freeMemory) / 1024 / 1024;
	}

	/**
	 * 메모리 상태 정보 반환
	 */
	public MemoryInfo getMemoryInfo() {
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		return MemoryInfo.builder()
			.maxMemoryMB(maxMemory / 1024 / 1024)
			.totalMemoryMB(totalMemory / 1024 / 1024)
			.usedMemoryMB(usedMemory / 1024 / 1024)
			.freeMemoryMB(freeMemory / 1024 / 1024)
			.usageRatio((double)usedMemory / maxMemory)
			.build();
	}

	/**
	 * 메모리 정보 DTO
	 */
	@lombok.Data
	@lombok.Builder
	public static class MemoryInfo {
		private long maxMemoryMB;
		private long totalMemoryMB;
		private long usedMemoryMB;
		private long freeMemoryMB;
		private double usageRatio;
	}
}
