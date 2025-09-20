package com.livelihoodcoupon.collector.dto;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 성능 통계 DTO
 *
 * <p>시스템의 성능 지표를 담는 데이터 전송 객체입니다.</p>
 * <p>캐시 히트율, API 호출 수, 처리 시간 등의 핵심 성능 메트릭을 포함합니다.</p>
 *
 * @author livelihoodCoupon
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class PerformanceStats {

	/**
	 * 총 API 호출 수
	 * <p>카카오 API를 통해 수행된 총 호출 횟수입니다.</p>
	 */
	private long totalApiCalls;

	/**
	 * 캐시 히트 수
	 * <p>격자 캐시에서 성공적으로 데이터를 찾은 횟수입니다.</p>
	 */
	private long cacheHits;

	/**
	 * 캐시 미스 수
	 * <p>격자 캐시에서 데이터를 찾지 못한 횟수입니다.</p>
	 */
	private long cacheMisses;

	/**
	 * 캐시 히트율 (0.0 ~ 1.0)
	 * <p>캐시 성공률을 나타내며, 높을수록 성능이 좋습니다.</p>
	 * <p>계산식: cacheHits / (cacheHits + cacheMisses)</p>
	 */
	private double cacheHitRate;

	/**
	 * 처리된 격자 수
	 * <p>데이터 수집 과정에서 처리된 총 격자 개수입니다.</p>
	 */
	private int processedGrids;

	/**
	 * 총 처리 시간 (밀리초)
	 * <p>전체 데이터 수집 과정에서 소요된 총 시간입니다.</p>
	 */
	private long totalProcessingTimeMs;

	/**
	 * 격자당 평균 처리 시간 (밀리초)
	 * <p>하나의 격자를 처리하는 데 걸리는 평균 시간입니다.</p>
	 * <p>계산식: totalProcessingTimeMs / processedGrids</p>
	 */
	private double avgProcessingTimePerGrid;

	/**
	 * 메모리 사용량 (MB)
	 * <p>현재 힙 메모리 사용량을 메가바이트 단위로 나타냅니다.</p>
	 */
	private long memoryUsageMB;
}
