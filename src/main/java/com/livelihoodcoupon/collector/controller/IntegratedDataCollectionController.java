package com.livelihoodcoupon.collector.controller;

import java.util.List;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import com.livelihoodcoupon.collector.service.IntegratedCouponDataCollector;
import com.livelihoodcoupon.collector.vo.RegionData;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

/**
 * 통합 최적화된 데이터 수집을 위한 REST 컨트롤러
 *
 * <h3>제공 API:</h3>
 * <ul>
 *   <li><b>POST /admin/integrated/collect:</b> 통합 최적화 데이터 수집</li>
 *   <li><b>POST /admin/integrated/collect-region:</b> 단일 지역 통합 수집</li>
 *   <li><b>GET /admin/integrated/status:</b> 수집 상태 조회</li>
 * </ul>
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li><b>캐싱 통합:</b> 격자 스캔 상태 캐싱으로 API 호출 최적화</li>
 *   <li><b>배치 처리:</b> 격자들을 그룹화하여 효율적 처리</li>
 *   <li><b>성능 모니터링:</b> 실시간 성능 지표 추적</li>
 *   <li><b>적응형 지연:</b> API 서버 상태에 따른 동적 지연 조정</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/admin/integrated")
@RequiredArgsConstructor
public class IntegratedDataCollectionController {

	private final IntegratedCouponDataCollector integratedCollector;

	/**
	 * 여러 지역에 대한 통합 최적화 데이터 수집
	 *
	 * <h3>요청 예시:</h3>
	 * <pre>
	 * POST /admin/integrated/collect
	 * Content-Type: application/json
	 *
	 * [
	 *   {
	 *     "name": "구례군",
	 *     "polygon": [[127.1, 35.1], [127.2, 35.1], [127.2, 35.2], [127.1, 35.2], [127.1, 35.1]]
	 *   }
	 * ]
	 * </pre>
	 *
	 * @param regions 수집할 지역 목록
	 * @return 수집 결과 메시지
	 */
	@PostMapping("/collect")
	public ResponseEntity<String> collectForRegions(@RequestBody List<RegionData> regions) {
		log.info("통합 데이터 수집 요청: {}개 지역", regions.size());

		try {
			integratedCollector.collectForRegionsIntegrated(regions);
			return ResponseEntity.ok("통합 데이터 수집이 완료되었습니다. 로그를 확인하세요.");
		} catch (Exception e) {
			log.error("통합 데이터 수집 중 오류 발생", e);
			return ResponseEntity.internalServerError()
				.body("데이터 수집 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

	/**
	 * 단일 지역에 대한 통합 최적화 데이터 수집
	 *
	 * <h3>요청 예시:</h3>
	 * <pre>
	 * POST /admin/integrated/collect-region
	 * Content-Type: application/json
	 *
	 * {
	 *   "name": "구례군",
	 *   "polygon": [[127.1, 35.1], [127.2, 35.1], [127.2, 35.2], [127.1, 35.2], [127.1, 35.1]]
	 * }
	 * </pre>
	 *
	 * @param region 수집할 지역 정보
	 * @return 수집 결과 메시지
	 */
	@PostMapping("/collect-region")
	public ResponseEntity<String> collectForSingleRegion(@RequestBody RegionData region) {
		log.info("단일 지역 통합 데이터 수집 요청: {}", region.getName());

		try {
			integratedCollector.collectForSingleRegionIntegrated(region);
			return ResponseEntity.ok("지역 [" + region.getName() + "] 통합 데이터 수집이 완료되었습니다.");
		} catch (Exception e) {
			log.error("지역 [{}] 통합 데이터 수집 중 오류 발생", region.getName(), e);
			return ResponseEntity.internalServerError()
				.body("지역 [" + region.getName() + "] 데이터 수집 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

	/**
	 * 데이터 수집 상태 조회
	 *
	 * @param regionName 지역명 (선택사항)
	 * @return 수집 상태 정보
	 */
	@PostMapping("/status")
	public ResponseEntity<CollectionStatus> getCollectionStatus(@RequestParam(required = false) String regionName) {
		log.info("수집 상태 조회 요청: {}", regionName != null ? regionName : "전체");

		// TODO: 실제 구현에서는 수집 진행 상태를 추적하는 로직이 필요
		CollectionStatus status = new CollectionStatus();
		status.setStatus("IDLE");
		status.setMessage("수집 상태 조회 기능은 향후 구현 예정입니다.");

		return ResponseEntity.ok(status);
	}

	/**
	 * 수집 상태 정보 DTO
	 */
	@Setter
	@Getter
	public static class CollectionStatus {
		private String status;
		private String message;
		private int processedRegions;
		private int totalRegions;
	}
}
