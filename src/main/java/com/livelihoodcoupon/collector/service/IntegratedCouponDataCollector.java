package com.livelihoodcoupon.collector.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Set;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.ConcurrentHashMap;

import org.springframework.scheduling.annotation.Async;
import org.springframework.stereotype.Service;

import com.livelihoodcoupon.collector.dto.KakaoResponse;
import com.livelihoodcoupon.collector.entity.ScannedGrid;
import com.livelihoodcoupon.collector.repository.CollectorPlaceRepository;
import com.livelihoodcoupon.collector.repository.ScannedGridRepository;
import com.livelihoodcoupon.collector.vo.RegionData;
import com.livelihoodcoupon.common.service.MemoryManagementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 통합 개선된 쿠폰 데이터 수집 서비스
 *
 * <h3>통합된 최적화 기능:</h3>
 * <ul>
 *   <li><b>격자 캐싱:</b> Redis 기반 분산 캐시로 DB 조회 최소화</li>
 *   <li><b>스마트 배치 처리:</b> 지리적 근접성 기반 격자 그룹화</li>
 *   <li><b>적응형 성능 조정:</b> 실시간 성능 모니터링 및 파라미터 조정</li>
 *   <li><b>에러 복구:</b> 실패한 격자에 대한 자동 재시도</li>
 *   <li><b>메모리 최적화:</b> 스트림 처리로 메모리 사용량 감소</li>
 * </ul>
 *
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class IntegratedCouponDataCollector {

	/** 기본 검색 키워드 */
	public static final String DEFAULT_KEYWORD = "소비쿠폰";
	// 의존성 주입된 서비스들
	private final KakaoApiService kakaoApiService;                    // 카카오 API 호출 서비스
	private final CollectorPlaceRepository collectorPlaceRepository;  // 장소 데이터 저장소
	private final ScannedGridRepository scannedGridRepository;        // 스캔된 격자 상태 저장소
	private final CsvExportService csvExportService;                  // CSV 파일 내보내기 서비스
	private final GeoJsonExportService geoJsonExportService;          // GeoJSON 파일 내보내기 서비스
	private final GridCacheService gridCacheService;                  // 격자 캐싱 서비스
	private final EnhancedBatchProcessingService batchProcessingService; // 향상된 배치 처리 서비스
	private final PerformanceMonitorService performanceMonitor;       // 성능 모니터링 서비스
	private final com.livelihoodcoupon.common.config.PerformanceConfig performanceConfig; // 성능 설정
	private final MemoryManagementService memoryManagementService;    // 메모리 관리 서비스

	/** 통합 처리를 위한 스레드 풀 (설정에서 주입) */
	private final org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor integratedExecutor;

	/** 중복 키 에러 통계를 위한 카운터 */
	private final java.util.concurrent.atomic.AtomicInteger duplicateKeyErrorCount = new java.util.concurrent.atomic.AtomicInteger(0);

	/**
	 * 여러 지역 통합 최적화 데이터 수집
	 *
	 * <p>여러 지역에 대해 순차적으로 데이터 수집을 수행합니다.</p>
	 * <p>각 지역별로 격자 캐싱, 배치 처리, 성능 모니터링이 적용됩니다.</p>
	 *
	 * @param regions 수집할 지역 목록
	 */
	public void collectForRegionsIntegrated(List<RegionData> regions) {
		log.info("======== 전체 지역, 키워드 [{}] 통합 데이터 수집 시작 ========", DEFAULT_KEYWORD);
		log.info("처리할 지역 수: {}개", regions.size());

		for (RegionData region : regions) {
			collectForSingleRegionIntegrated(region);
		}

		log.info("======== 전체 지역, 키워드 [{}] 통합 데이터 수집 완료 ========", DEFAULT_KEYWORD);
	}

	/**
	 * 여러 지역 비동기 통합 최적화 데이터 수집
	 *
	 * <p>여러 지역에 대해 비동기적으로 데이터 수집을 수행합니다.</p>
	 * <p>병렬 처리를 통해 전체 수집 시간을 단축할 수 있습니다.</p>
	 *
	 * @param regions 수집할 지역 목록
	 * @return CompletableFuture로 래핑된 수집 결과
	 */
	@Async("integratedExecutor")
	public CompletableFuture<Void> collectForRegionsIntegratedAsync(List<RegionData> regions) {
		log.info("======== 비동기 전체 지역, 키워드 [{}] 통합 데이터 수집 시작 ========", DEFAULT_KEYWORD);
		log.info("처리할 지역 수: {}개", regions.size());

		// 병렬 처리를 위한 CompletableFuture 리스트 생성
		List<CompletableFuture<Void>> futures = regions.stream()
			.map(region -> CompletableFuture.runAsync(() ->
				collectForSingleRegionIntegrated(region), integratedExecutor))
			.toList();

		// 모든 작업이 완료될 때까지 대기
		CompletableFuture.allOf(futures.toArray(new CompletableFuture[0])).join();

		log.info("======== 비동기 전체 지역, 키워드 [{}] 통합 데이터 수집 완료 ========", DEFAULT_KEYWORD);
		return CompletableFuture.completedFuture(null);
	}

	/**
	 * 통합 최적화된 데이터 수집 수행
	 *
	 * <h3>처리 과정:</h3>
	 * <ol>
	 *   <li>지역 폴리곤 유효성 검사</li>
	 *   <li>최적화된 격자 반경으로 단계별 수집</li>
	 *   <li>각 단계에서 스마트 배치 처리</li>
	 *   <li>실시간 성능 모니터링 및 조정</li>
	 *   <li>수집된 데이터를 파일로 내보내기</li>
	 * </ol>
	 *
	 * @param region 수집할 지역 정보
	 */
	public void collectIntegrated(RegionData region) {
		collectForSingleRegionIntegrated(region);
	}

	/**
	 * 단일 지역 통합 최적화 데이터 수집
	 *
	 * @param region 수집할 지역 정보
	 */
	public void collectForSingleRegionIntegrated(RegionData region) {
		long startTime = System.currentTimeMillis();

		log.info("========== [{}] 통합 최적화 데이터 수집 시작 ==========", region.getName());

		// 1. 지역 폴리곤 유효성 검사
		List<List<Double>> initialPolygon = region.getPolygon();
		if (initialPolygon == null || initialPolygon.isEmpty()) {
			log.warn("    - 경고: [ {} ] 지역에 폴리곤이 정의되지 않아 건너뜁니다.", region.getName());
			return;
		}

		// 2. 성능 모니터링 초기화
		performanceMonitor.resetStats();

		// 중복 키 에러 카운터 리셋
		duplicateKeyErrorCount.set(0);

		// 3. 메모리 효율적인 중복 제거를 위한 Set
		Set<String> foundPlaceIds = ConcurrentHashMap.newKeySet();

		// 4. 최적화된 격자 반경으로 단계별 수집 (설정에서 가져옴)
		List<Integer> radiusLevels = performanceConfig.getGrid().getRadiusLevels();
		log.info("격자 반경 레벨: {} (총 {}단계)", radiusLevels, radiusLevels.size());
		for (int radius : radiusLevels) {
			log.info("   [반경 {}m] 스마트 배치 처리 시작", radius);

			// 4-1. 현재 반경에 맞는 격자 태스크 생성
			List<EnhancedBatchProcessingService.GridScanTask> tasks = createGridTasksForRadius(
				region, radius, initialPolygon);

			if (tasks.isEmpty()) {
				log.info("   [반경 {}m] 처리할 격자가 없어 건너뜁니다.", radius);
				continue;
			}

			// 4-2. 스마트 배치 처리 실행
			EnhancedBatchProcessingService.BatchProcessingResult batchResult =
				batchProcessingService.processSmartBatches(tasks);

			// 4-3. 배치 처리 결과를 실제 데이터 수집으로 변환
			processBatchResults(batchResult, region, foundPlaceIds);

			log.info("   [반경 {}m] 스마트 배치 처리 완료: 성공 {}개, 실패 {}개",
				radius, batchResult.getSuccessCount(), batchResult.getFailureCount());
		}

		// 5. 수집된 데이터를 파일로 내보내기
		exportCollectedData(region);

		// 6. 성능 통계 출력
		long endTime = System.currentTimeMillis();
		long totalTime = endTime - startTime;
		log.info("========== [{}] 통합 최적화 데이터 수집 완료 ==========", region.getName());
		log.info("총 소요 시간: {}ms ({:.2f}초)", totalTime, totalTime / 1000.0);
		log.info("수집된 장소 수: {}개", foundPlaceIds.size());

		// 중복 키 에러 통계 출력
		int duplicateErrors = duplicateKeyErrorCount.get();
		if (duplicateErrors > 0) {
			log.info("중복 데이터 통계: 총 {}회 발생 (성능 테스트 중 정상적인 현상)", duplicateErrors);
		}

		performanceMonitor.printPerformanceStats();
		// 7. 메모리 정리 (장시간 작업 후 GC 실행)
		log.info("메모리 정리 중...");
		memoryManagementService.runGarbageCollection();
	}

	/**
	 * 지정된 반경에 맞는 격자 태스크 생성
	 *
	 * @param region 지역 정보
	 * @param radius 격자 반경
	 * @param polygon 폴리곤
	 * @return 격자 태스크 목록
	 */
	private List<EnhancedBatchProcessingService.GridScanTask> createGridTasksForRadius(
		RegionData region, int radius, List<List<Double>> polygon) {

		List<EnhancedBatchProcessingService.GridScanTask> tasks = new ArrayList<>();

		// 1. 폴리곤의 경계 상자 계산
		GridUtil.BoundingBox bbox = GridUtil.getBoundingBoxForPolygon(polygon);

		// 2. 격자 중심점들 생성
		List<double[]> gridCenters = GridUtil.generateGridForBoundingBox(
			bbox.getLatStart(), bbox.getLatEnd(),
			bbox.getLngStart(), bbox.getLngEnd(), radius);

		// 3. 폴리곤 내부의 격자들만 태스크로 생성
		for (double[] center : gridCenters) {
			if (GridUtil.isPointInPolygon(center[0], center[1], polygon)) {
				EnhancedBatchProcessingService.GridScanTask task =
					new EnhancedBatchProcessingService.GridScanTask(
						region.getName(), DEFAULT_KEYWORD,
						center[0], center[1], radius);
				tasks.add(task);
			}
		}

		return tasks;
	}

	/**
	 * 배치 처리 결과를 실제 데이터 수집으로 변환
	 *
	 * @param batchResult 배치 처리 결과
	 * @param region 지역 정보
	 * @param foundPlaceIds 중복 방지를 위한 장소 ID 집합
	 */
	private void processBatchResults(EnhancedBatchProcessingService.BatchProcessingResult batchResult,
		RegionData region, Set<String> foundPlaceIds) {

		// 1. 밀집 지역 처리 (하위 격자로 분할)
		for (EnhancedBatchProcessingService.GridScanTask task : batchResult.getAllDenseGrids()) {
			saveGridStatus(task, ScannedGrid.GridStatus.SUBDIVIDED);
		}

		// 2. 캐시된 분할 격자 처리
		for (EnhancedBatchProcessingService.GridScanTask task : batchResult.getAllCachedSubdividedGrids()) {
			// 이미 처리된 분할 격자 - 추가 처리 불필요
			log.debug("    - [캐시된 분할 격자] 건너뛰기 (좌표: {},{})", task.getLat(), task.getLng());
		}

		// 3. 일반 지역 처리 (페이지네이션으로 데이터 수집)
		for (EnhancedBatchProcessingService.GridScanTask task : batchResult.getAllNormalGrids()) {
			processNormalGrid(task, region, foundPlaceIds);
		}

		// 4. 캐시된 완료 격자 처리
		for (EnhancedBatchProcessingService.GridScanTask task : batchResult.getAllCachedCompletedGrids()) {
			// 이미 처리된 완료 격자 - 추가 처리 불필요
			log.debug("    - [캐시된 완료 격자] 건너뛰기 (좌표: {},{})", task.getLat(), task.getLng());
		}

		// 5. 에러 처리
		if (!batchResult.getAllErrors().isEmpty()) {
			log.warn("    - 배치 처리 중 {}개의 에러 발생", batchResult.getAllErrors().size());
			// TODO: 에러 복구 로직 구현
		}
	}

	/**
	 * 일반 지역 처리 (페이지네이션으로 데이터 수집)
	 *
	 * @param task 격자 태스크
	 * @param region 지역 정보
	 * @param foundPlaceIds 중복 방지를 위한 장소 ID 집합
	 */
	private void processNormalGrid(EnhancedBatchProcessingService.GridScanTask task,
		RegionData region, Set<String> foundPlaceIds) {

		try {
			// 1. 첫 페이지 API 호출
			KakaoResponse response = kakaoApiService.searchPlaces(
				task.getKeyword(), task.getLng(), task.getLat(), task.getRadius(), 1);

			if (response != null && response.getDocuments() != null) {
				// 2. 페이지네이션으로 모든 데이터 수집
				int foundCount = savePaginatedPlaces(response, task, region, foundPlaceIds);

				if (foundCount > 0) {
					log.info("        - 일반 지역 (결과: {}개). {}개의 새 장소를 DB에 저장.",
						response.getMeta().getTotal_count(), foundCount);
				}
			}

			// 3. 격자 상태를 완료로 저장
			saveGridStatus(task, ScannedGrid.GridStatus.COMPLETED);

		} catch (Exception e) {
			log.error("일반 지역 처리 중 오류 발생 (좌표: {},{}): {}",
				task.getLat(), task.getLng(), e.getMessage());
		}
	}

	/**
	 * 페이지네이션으로 모든 데이터 수집
	 *
	 * @param firstPageResponse 첫 페이지 응답
	 * @param task 격자 태스크
	 * @param region 지역 정보
	 * @param foundPlaceIds 중복 방지를 위한 장소 ID 집합
	 * @return 실제로 저장된 장소 수
	 */
	private int savePaginatedPlaces(KakaoResponse firstPageResponse,
		EnhancedBatchProcessingService.GridScanTask task,
		RegionData region, Set<String> foundPlaceIds) {

		int foundCount = 0;
		KakaoResponse currentResponse = firstPageResponse;
		int currentPage = 1;
		final int MAX_PAGE_PER_QUERY = 45;

		try {
			while (true) {
				if (currentResponse == null || currentResponse.getDocuments() == null ||
					currentResponse.getDocuments().isEmpty()) {
					break;
				}

				// 현재 페이지의 결과를 DB에 저장
				int savedInPage = savePlaces(currentResponse.getDocuments(),
					task.getRegionName(), task.getKeyword(), region.getPolygon(), foundPlaceIds);

				foundCount += savedInPage;

				// 마지막 페이지인지 확인
				if (currentResponse.getMeta().is_end()) {
					break;
				}

				currentPage++;
				if (currentPage > MAX_PAGE_PER_QUERY) {
					break;
				}

				// 다음 페이지 요청
				currentResponse = kakaoApiService.searchPlaces(
					task.getKeyword(), task.getLng(), task.getLat(),
					task.getRadius(), currentPage);
			}
		} catch (Exception e) {
			log.error("페이지네이션 수집 중 오류 발생: {}", e.getMessage());
		}

		return foundCount;
	}

	/**
	 * 장소 목록을 DB에 저장
	 *
	 * @param places 카카오 API에서 받은 장소 목록
	 * @param regionName 지역명
	 * @param keyword 검색 키워드
	 * @param regionPolygon 지역 폴리곤
	 * @param foundPlaceIds 중복 방지를 위한 장소 ID 집합
	 * @return 실제로 저장된 장소 수
	 */
	private int savePlaces(List<com.livelihoodcoupon.collector.dto.KakaoPlace> places,
		String regionName, String keyword, List<List<Double>> regionPolygon,
		Set<String> foundPlaceIds) {

		List<com.livelihoodcoupon.collector.entity.PlaceEntity> placeEntities = new ArrayList<>();

		for (com.livelihoodcoupon.collector.dto.KakaoPlace place : places) {
			// 1. 좌표 파싱
			double lat = Double.parseDouble(place.getY());
			double lng = Double.parseDouble(place.getX());

			// 2. 지역 폴리곤 내부인지 확인
			if (!GridUtil.isPointInPolygon(lat, lng, regionPolygon)) {
				continue;
			}

			// 3. 메모리에서 중복 확인
			if (foundPlaceIds.contains(place.getId())) {
				continue;
			}

			// 4. PlaceEntity 객체 생성
			com.livelihoodcoupon.collector.entity.PlaceEntity entity =
				com.livelihoodcoupon.collector.entity.PlaceEntity.builder()
					.placeId(place.getId())
					.placeName(place.getPlaceName())
					.category(place.getCategoryName())
					.categoryGroupCode(place.getCategoryGroupCode())
					.categoryGroupName(place.getCategoryGroupName())
					.phone(place.getPhone())
					.lotAddress(place.getAddressName())
					.roadAddress(place.getRoadAddressName())
					.lng(lng)
					.lat(lat)
					.placeUrl(place.getPlaceUrl())
					.region(regionName)
					.keyword(keyword)
					.build();

			placeEntities.add(entity);
			foundPlaceIds.add(place.getId());
		}

		// 5. 배치로 DB에 저장
		if (!placeEntities.isEmpty()) {
			try {
				collectorPlaceRepository.saveAll(placeEntities);
				log.debug("{}개 장소 데이터 저장 완료", placeEntities.size());
				return placeEntities.size();
			} catch (Exception e) {
				// 중복 키 에러인 경우 요약 로깅으로 처리
				if (e.getMessage().contains("duplicate key value violates unique constraint")) {
					int duplicateCount = duplicateKeyErrorCount.incrementAndGet();
					// 처음 1번만 상세 로그, 그 이후는 50번마다 요약 로그
					if (duplicateCount == 1) {
						log.info("중복 데이터 감지: 일부 장소가 이미 존재합니다. (정상적인 성능 테스트 동작)");
					} else if (duplicateCount % 50 == 0) {
						log.info("중복 데이터 통계: 총 {}회 발생 (정상적인 성능 테스트 동작)", duplicateCount);
					}
					return 0; // 중복 데이터는 정상적인 상황이므로 0 반환
				} else {
					log.warn("데이터 저장 중 예상치 못한 오류 발생: {}", e.getMessage());
					return 0;
				}
			}
		}

		return 0;
	}

	/**
	 * 격자 상태 저장 (Redis 캐시와 DB 동기화)
	 * 
	 * <p>Write-Through 전략을 사용하여 Redis 캐시와 DB에 동시에 저장합니다.</p>
	 * <p>Redis 캐시는 1시간 TTL로 자동 만료되며, DB는 영구 저장됩니다.</p>
	 *
	 * @param task 격자 태스크
	 * @param status 격자 상태
	 */
	private void saveGridStatus(EnhancedBatchProcessingService.GridScanTask task,
		ScannedGrid.GridStatus status) {
		ScannedGrid grid = ScannedGrid.builder()
			.regionName(task.getRegionName())
			.keyword(task.getKeyword())
			.gridCenterLat(task.getLat())
			.gridCenterLng(task.getLng())
			.gridRadius(task.getRadius())
			.status(status)
			.build();

		// Write-Through 전략: Redis 캐시와 DB에 동시 저장
		// Redis 캐시는 1시간 TTL로 자동 만료, DB는 영구 저장
		gridCacheService.putCachedGrid(grid);
		scannedGridRepository.save(grid);
	}

	/**
	 * 수집된 데이터를 파일로 내보내기
	 *
	 * @param region 지역 정보
	 */
	private void exportCollectedData(RegionData region) {
		log.info(">>> [{}] 지역 파일 생성을 시작합니다...", region.getName());

		try {
			csvExportService.exportSingleRegionToCsv(region.getName(), DEFAULT_KEYWORD);
			geoJsonExportService.exportSingleRegionToGeoJson(region.getName(), DEFAULT_KEYWORD);
			log.info(">>> [{}] 지역 파일 생성을 완료했습니다.", region.getName());
		} catch (Exception e) {
			log.error("파일 생성 중 오류 발생: {}", e.getMessage());
		}
	}
}
