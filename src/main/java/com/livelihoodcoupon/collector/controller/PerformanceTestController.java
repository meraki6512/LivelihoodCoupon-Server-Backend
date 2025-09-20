package com.livelihoodcoupon.collector.controller;

import java.util.List;
import java.util.Objects;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.livelihoodcoupon.collector.dto.LoadTestRequest;
import com.livelihoodcoupon.collector.dto.PerformanceStats;
import com.livelihoodcoupon.collector.dto.RegionNamesRequest;
import com.livelihoodcoupon.collector.service.PerformanceTestService;
import com.livelihoodcoupon.collector.service.RegionLoader;
import com.livelihoodcoupon.collector.vo.RegionData;
import com.livelihoodcoupon.common.exception.BusinessException;
import com.livelihoodcoupon.common.exception.ErrorCode;
import com.livelihoodcoupon.common.response.CustomApiResponse;

import io.micrometer.core.annotation.Counted;
import io.micrometer.core.annotation.Timed;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 성능 테스트 및 모니터링을 위한 REST API 컨트롤러
 *
 * <h3>제공 API:</h3>
 * <ul>
 *   <li><b>GET /admin/performance/compare:</b> 기존 vs 개선된 버전 성능 비교</li>
 *   <li><b>POST /admin/performance/load-test:</b> 부하 테스트 실행</li>
 *   <li><b>POST /admin/performance/memory-test:</b> 메모리 사용량 모니터링</li>
 *   <li><b>GET /admin/performance/stats:</b> 현재 성능 통계 조회</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/admin/performance")
@RequiredArgsConstructor
public class PerformanceTestController {

	private final PerformanceTestService performanceTestService;
	private final RegionLoader regionLoader;

	/**
	 * 기존 버전과 통합 최적화 버전의 성능 비교
	 *
	 * <p>동일한 지역 데이터에 대해 기존 버전과 개선된 버전의 성능을 비교합니다.</p>
	 * <p>API 호출 수, 처리 시간, 캐시 히트율 등의 지표를 측정합니다.</p>
	 *
	 * @param request 지역명 목록
	 * @return 성능 비교 결과
	 */
	@PostMapping("/compare")
	@Timed("performance.compare.duration")
	@Counted("performance.compare.executions")
	public ResponseEntity<CustomApiResponse<PerformanceTestService.PerformanceComparisonResult>> compareVersions(
		@RequestBody RegionNamesRequest request) {

		log.info("성능 비교 테스트 요청: {}개 지역", request.getRegionNames().size());

		try {
			// 입력 검증
			if (request.getRegionNames() == null || request.getRegionNames().isEmpty()) {
				throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, "지역명 목록이 비어있습니다.");
			}

			// RegionLoader로 regionNames를 RegionData로 변환
			List<RegionData> testRegions = regionLoader.loadRegionsByName(request.getRegionNames());

			if (testRegions.isEmpty()) {
				throw new BusinessException(ErrorCode.NOT_FOUND, "요청된 지역명과 일치하는 지역을 찾을 수 없습니다.");
			}

			PerformanceTestService.PerformanceComparisonResult result =
				performanceTestService.compareVersions(testRegions);

			return ResponseEntity.ok(CustomApiResponse.success(result, "성능 비교 테스트가 완료되었습니다."));

		} catch (BusinessException e) {
			log.warn("성능 비교 테스트 비즈니스 오류: {}", e.getMessage());
			return ResponseEntity.badRequest()
				.body(CustomApiResponse.<PerformanceTestService.PerformanceComparisonResult>error(e.getErrorCode(),
					e.getMessage()));
		} catch (Exception e) {
			log.error("성능 비교 테스트 중 오류 발생", e);
			return ResponseEntity.internalServerError()
				.body(CustomApiResponse.<PerformanceTestService.PerformanceComparisonResult>error(
					ErrorCode.INTERNAL_SERVER_ERROR, "성능 비교 테스트 중 오류가 발생했습니다."));
		}
	}

	/**
	 * 통합 최적화 버전의 성능 테스트
	 *
	 * <p>통합 최적화된 데이터 수집기의 성능을 측정합니다.</p>
	 * <p>처리 시간, API 호출 수, 메모리 사용량 등의 지표를 수집합니다.</p>
	 *
	 * @param testRegions 테스트할 지역 목록
	 * @return 성능 테스트 결과
	 */
	@PostMapping("/test")
	@Timed("performance.test.duration")
	@Counted("performance.test.executions")
	public ResponseEntity<CustomApiResponse<PerformanceTestService.PerformanceTestResult>> testPerformance(
		@RequestBody List<RegionData> testRegions) {

		log.info("성능 테스트 요청: {}개 지역", testRegions.size());

		try {
			// 입력 검증
			if (testRegions.isEmpty()) {
				throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, "테스트할 지역 목록이 비어있습니다.");
			}

			PerformanceTestService.PerformanceTestResult result =
				performanceTestService.testPerformance(testRegions);

			return ResponseEntity.ok(CustomApiResponse.success(result, "성능 테스트가 완료되었습니다."));

		} catch (BusinessException e) {
			log.warn("성능 테스트 비즈니스 오류: {}", e.getMessage());
			return ResponseEntity.badRequest()
				.body(CustomApiResponse.<PerformanceTestService.PerformanceTestResult>error(e.getErrorCode(),
					e.getMessage()));
		} catch (Exception e) {
			log.error("성능 테스트 중 오류 발생", e);
			return ResponseEntity.internalServerError()
				.body(CustomApiResponse.<PerformanceTestService.PerformanceTestResult>error(
					ErrorCode.INTERNAL_SERVER_ERROR, "성능 테스트 중 오류가 발생했습니다."));
		}
	}

	/**
	 * 부하 테스트 실행
	 *
	 * <p>다양한 크기의 지역에 대한 부하 테스트를 수행합니다.</p>
	 * <p>소규모, 중규모, 대규모 지역별로 성능을 측정하여 시스템의 한계를 파악합니다.</p>
	 *
	 * @param request 부하 테스트 요청
	 * @return 부하 테스트 결과
	 */
	@PostMapping("/load-test")
	@Timed("performance.load-test.duration")
	@Counted("performance.load-test.executions")
	public ResponseEntity<CustomApiResponse<PerformanceTestService.LoadTestResult>> performLoadTest(
		@RequestBody LoadTestRequest request) {

		log.info("부하 테스트 요청: 소규모 {}개, 중규모 {}개, 대규모 {}개",
			request.getSmallRegions() != null ? request.getSmallRegions().size() : 0,
			request.getMediumRegions() != null ? request.getMediumRegions().size() : 0,
			request.getLargeRegions() != null ? request.getLargeRegions().size() : 0);

		try {
			// 입력 검증
			if ((request.getSmallRegions() == null || request.getSmallRegions().isEmpty()) &&
				(request.getMediumRegions() == null || request.getMediumRegions().isEmpty()) &&
				(request.getLargeRegions() == null || request.getLargeRegions().isEmpty())) {
				throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, "테스트할 지역이 하나도 없습니다.");
			}

			PerformanceTestService.LoadTestResult result =
				performanceTestService.performLoadTest(
					Objects.requireNonNull(request.getSmallRegions()),
					Objects.requireNonNull(request.getMediumRegions()),
					request.getLargeRegions());

			return ResponseEntity.ok(CustomApiResponse.success(result, "부하 테스트가 완료되었습니다."));

		} catch (BusinessException e) {
			log.warn("부하 테스트 비즈니스 오류: {}", e.getMessage());
			return ResponseEntity.badRequest()
				.body(CustomApiResponse.<PerformanceTestService.LoadTestResult>error(e.getErrorCode(), e.getMessage()));
		} catch (Exception e) {
			log.error("부하 테스트 중 오류 발생", e);
			return ResponseEntity.internalServerError()
				.body(CustomApiResponse.<PerformanceTestService.LoadTestResult>error(ErrorCode.INTERNAL_SERVER_ERROR,
					"부하 테스트 중 오류가 발생했습니다."));
		}
	}

	/**
	 * 메모리 사용량 모니터링
	 *
	 * <p>지정된 지역들에 대한 데이터 수집 과정에서의 메모리 사용량을 모니터링합니다.</p>
	 * <p>힙 메모리 사용량, 가비지 컬렉션 통계 등을 수집합니다.</p>
	 *
	 * @param testRegions 테스트할 지역 목록
	 * @return 메모리 사용량 결과
	 */
	@PostMapping("/memory-test")
	@Timed("performance.memory-test.duration")
	@Counted("performance.memory-test.executions")
	public ResponseEntity<CustomApiResponse<PerformanceTestService.MemoryUsageResult>> monitorMemoryUsage(
		@RequestBody List<RegionData> testRegions) {

		log.info("메모리 사용량 모니터링 요청: {}개 지역", testRegions.size());

		try {
			// 입력 검증
			if (testRegions.isEmpty()) {
				throw new BusinessException(ErrorCode.COMMON_BAD_REQUEST, "테스트할 지역 목록이 비어있습니다.");
			}

			PerformanceTestService.MemoryUsageResult result =
				performanceTestService.monitorMemoryUsage(testRegions);

			return ResponseEntity.ok(CustomApiResponse.success(result, "메모리 사용량 모니터링이 완료되었습니다."));

		} catch (BusinessException e) {
			log.warn("메모리 모니터링 비즈니스 오류: {}", e.getMessage());
			return ResponseEntity.badRequest()
				.body(CustomApiResponse.<PerformanceTestService.MemoryUsageResult>error(e.getErrorCode(),
					e.getMessage()));
		} catch (Exception e) {
			log.error("메모리 모니터링 중 오류 발생", e);
			return ResponseEntity.internalServerError()
				.body(CustomApiResponse.<PerformanceTestService.MemoryUsageResult>error(ErrorCode.INTERNAL_SERVER_ERROR,
					"메모리 모니터링 중 오류가 발생했습니다."));
		}
	}

	/**
	 * 현재 성능 통계 조회
	 *
	 * <p>현재 시스템의 성능 통계를 조회합니다.</p>
	 * <p>캐시 히트율, API 호출 수, 처리 시간 등의 실시간 지표를 제공합니다.</p>
	 *
	 * @return 성능 통계
	 */
	@GetMapping("/stats")
	@Timed("performance.stats.duration")
	@Counted("performance.stats.executions")
	public ResponseEntity<CustomApiResponse<PerformanceStats>> getPerformanceStats() {
		try {
			PerformanceStats stats = new PerformanceStats();

			// 현재 성능 모니터링 데이터 조회
			// TODO: PerformanceMonitorService에서 현재 통계 조회하는 메서드 추가 필요
			// 임시로 기본값 설정
			stats.setTotalApiCalls(0L);
			stats.setCacheHits(0L);
			stats.setCacheMisses(0L);
			stats.setCacheHitRate(0.0);
			stats.setProcessedGrids(0);
			stats.setTotalProcessingTimeMs(0L);
			stats.setAvgProcessingTimePerGrid(0.0);
			stats.setMemoryUsageMB(0L);

			return ResponseEntity.ok(CustomApiResponse.success(stats, "성능 통계를 조회했습니다."));

		} catch (Exception e) {
			log.error("성능 통계 조회 중 오류 발생", e);
			return ResponseEntity.internalServerError()
				.body(CustomApiResponse.<PerformanceStats>error(ErrorCode.INTERNAL_SERVER_ERROR,
					"성능 통계 조회 중 오류가 발생했습니다."));
		}
	}

}
