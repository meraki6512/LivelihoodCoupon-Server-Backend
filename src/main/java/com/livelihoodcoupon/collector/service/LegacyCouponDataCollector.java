package com.livelihoodcoupon.collector.service;

import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;
import java.util.concurrent.Callable;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.CopyOnWriteArrayList;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import jakarta.annotation.PreDestroy;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClientResponseException;

import com.livelihoodcoupon.collector.dto.KakaoPlace;
import com.livelihoodcoupon.collector.dto.KakaoResponse;
import com.livelihoodcoupon.collector.entity.PlaceEntity;
import com.livelihoodcoupon.collector.entity.ScannedGrid;
import com.livelihoodcoupon.collector.repository.CollectorPlaceRepository;
import com.livelihoodcoupon.collector.repository.ScannedGridRepository;
import com.livelihoodcoupon.collector.vo.RegionData;
import com.livelihoodcoupon.common.service.MdcLogging;

import lombok.RequiredArgsConstructor;

/**
 * 카카오 지도 API의 검색 결과 수 제한을 극복하기 위한 재귀적 격자 분할 데이터 수집 서비스 (기존 버전)
 *
 * <h3>기존 로직 (성능 비교용):</h3>
 * <ul>
 *   <li><b>재귀적 격자 분할:</b> 밀집 지역을 작은 격자로 분할하여 모든 데이터 수집</li>
 *   <li><b>병렬 처리:</b> ExecutorService를 사용한 격자별 병렬 처리</li>
 *   <li><b>페이지네이션:</b> 카카오 API의 45페이지 제한을 극복</li>
 *   <li><b>중복 제거:</b> 메모리 Set을 사용한 장소 ID 중복 방지</li>
 * </ul>
 *
 * <h3>성능 비교 목적:</h3>
 * <ul>
 *   <li>캐싱 없이 매번 DB 조회</li>
 *   <li>배치 처리 없이 개별 저장</li>
 *   <li>성능 모니터링 없이 기본 처리</li>
 * </ul>
 */
@Service
@RequiredArgsConstructor
public class LegacyCouponDataCollector {

	public static final String DEFAULT_KEYWORD = "소비쿠폰";
	private static final Logger log = LoggerFactory.getLogger(LegacyCouponDataCollector.class);
	private static final int INITIAL_GRID_RADIUS_METERS = 512;
	private static final int MAX_PAGE_PER_QUERY = 45;
	private static final int DENSE_AREA_THRESHOLD = 45;
	private static final int MAX_RECURSION_DEPTH = 9;
	private static final int API_CALL_DELAY_MS = 30;
	private static final int MAX_RETRIES = 5;
	private static final long INITIAL_RETRY_DELAY_MS = 1000;

	private final KakaoApiService kakaoApiService;
	private final CollectorPlaceRepository collectorPlaceRepository;
	private final ScannedGridRepository scannedGridRepository;
	private final CsvExportService csvExportService;
	private final GeoJsonExportService geoJsonExportService;

	private final ExecutorService executor = Executors.newFixedThreadPool(Runtime.getRuntime().availableProcessors());

	@PreDestroy
	public void shutdownExecutor() {
		log.info("LegacyCouponDataCollector 스레드 풀 종료 중...");
		executor.shutdown();
		try {
			if (!executor.awaitTermination(30, TimeUnit.SECONDS)) {
				log.warn("Legacy 스레드 풀 강제 종료 중...");
				executor.shutdownNow();
			}
		} catch (InterruptedException e) {
			executor.shutdownNow();
			Thread.currentThread().interrupt();
		}
		log.info("LegacyCouponDataCollector 스레드 풀 종료 완료");
	}

	/**
	 * 여러 지역에 대한 데이터 수집 (기존 버전)
	 */
	public void collectForRegions(List<RegionData> regions) {
		try (MdcLogging.MdcContext ignored = MdcLogging.withContext("traceId", UUID.randomUUID().toString())) {
			log.info("======== 전체 지역, 키워드 [{}] 기존 방식 데이터 수집 시작 ========", DEFAULT_KEYWORD);
			for (RegionData region : regions) {
				collectForSingleRegion(region);
			}
			log.info("======== 전체 지역, 키워드 [{}] 기존 방식 데이터 수집 완료 ========", DEFAULT_KEYWORD);
		}
	}

	/**
	 * 단일 지역에 대한 데이터 수집 (기존 버전)
	 */
	public void collectForSingleRegion(RegionData region) {
		long startTime = System.currentTimeMillis();

		List<List<Double>> initialPolygon = region.getPolygon();
		if (initialPolygon == null || initialPolygon.isEmpty()) {
			log.warn("    - 경고: [ {} ] 지역에 폴리곤(polygon)이 정의되지 않아 건너뜁니다.", region.getName());
			return;
		}

		log.info(">>> [ {} ] 지역, 키워드 [ {} ]로 기존 방식 데이터 수집을 시작합니다.", region.getName(), DEFAULT_KEYWORD);

		Set<String> foundPlaceIds = ConcurrentHashMap.newKeySet();

		List<List<List<Double>>> currentLevelPolygons = new ArrayList<>();
		currentLevelPolygons.add(initialPolygon);
		int currentRadius = INITIAL_GRID_RADIUS_METERS;
		int depth = 0;

		while (!currentLevelPolygons.isEmpty() && depth < MAX_RECURSION_DEPTH) {
			log.info("    - [{}단계, 반경 {}m] 총 {}개 지역 병렬 탐색 시작...", depth, currentRadius, currentLevelPolygons.size());

			List<Callable<List<List<List<Double>>>>> tasks = new ArrayList<>();
			for (List<List<Double>> polygonToScan : currentLevelPolygons) {
				final int radiusForTask = currentRadius;
				Callable<List<List<List<Double>>>> task = () -> scanPolygon(region.getName(), DEFAULT_KEYWORD,
					polygonToScan, radiusForTask, foundPlaceIds);
				tasks.add(task);
			}

			List<List<List<Double>>> nextLevelPolygons = new CopyOnWriteArrayList<>();
			try {
				List<Future<List<List<List<Double>>>>> futures = executor.invokeAll(tasks);
				for (Future<List<List<List<Double>>>> future : futures) {
					nextLevelPolygons.addAll(future.get());
				}
			} catch (InterruptedException | ExecutionException e) {
				log.error("Legacy parallel collection failed at depth {}", depth, e);
				Thread.currentThread().interrupt();
				break;
			}

			currentLevelPolygons = new ArrayList<>(nextLevelPolygons);
			currentRadius /= 2;
			depth++;
		}

		if (!currentLevelPolygons.isEmpty() && depth >= MAX_RECURSION_DEPTH) {
			log.warn("    - 최대 재귀 깊이({})에 도달하여 마지막 {}개 지역 강제 수집 시작...", depth, currentLevelPolygons.size());
			forceCollectInParallel(region.getName(), DEFAULT_KEYWORD, currentLevelPolygons, currentRadius,
				foundPlaceIds);
		}

		log.info(">>> [ {} ] 지역, 키워드 [ {} ] 기존 방식 데이터 수집 및 DB 저장 완료.", region.getName(), DEFAULT_KEYWORD);

		log.info(">>> [ {} ] 지역 파일 생성을 시작합니다...", region.getName());
		csvExportService.exportSingleRegionToCsv(region.getName(), DEFAULT_KEYWORD);
		geoJsonExportService.exportSingleRegionToGeoJson(region.getName(), DEFAULT_KEYWORD);
		log.info(">>> [ {} ] 지역 파일 생성을 완료했습니다.", region.getName());

		long endTime = System.currentTimeMillis();
		log.info(">>> [ {} ] 지역 기존 방식 수집 완료. 총 소요 시간: {}ms", region.getName(), (endTime - startTime));
	}

	/**
	 * 폴리곤 내 격자들을 스캔하여 데이터 수집 (기존 버전 - 캐싱 없음)
	 */
	private List<List<List<Double>>> scanPolygon(String regionName, String keyword, List<List<Double>> polygon,
		int radius, Set<String> foundPlaceIds) {
		List<List<List<Double>>> denseSubPolygons = new ArrayList<>();
		GridUtil.BoundingBox bbox = GridUtil.getBoundingBoxForPolygon(polygon);
		List<double[]> gridCenters = GridUtil.generateGridForBoundingBox(bbox.getLatStart(),
			bbox.getLatEnd(), bbox.getLngStart(), bbox.getLngEnd(), radius);

		for (double[] center : gridCenters) {
			if (!GridUtil.isPointInPolygon(center[0], center[1], polygon)) {
				continue;
			}

			try {
				// 기존 방식: 캐싱 없이 매번 DB 조회
				Optional<ScannedGrid> existingGrid = scannedGridRepository.findByRegionNameAndKeywordAndGridCenterLatAndGridCenterLngAndGridRadius(
					regionName, keyword, center[0], center[1], radius);

				if (existingGrid.isPresent()) {
					ScannedGrid grid = existingGrid.get();
					ScannedGrid.GridStatus status = grid.getStatus();
					if (status == ScannedGrid.GridStatus.COMPLETED) {
						log.debug("    - [기존 완료 격자] 건너뛰기 (좌표: {},{})", center[0], center[1]);
						continue;
					}
					if (status == ScannedGrid.GridStatus.SUBDIVIDED) {
						log.debug("    - [기존 분할 격자] 하위 탐색 목록에 추가 (좌표: {},{})", center[0], center[1]);
						denseSubPolygons.add(GridUtil.createPolygonForCell(center[0], center[1], radius));
						continue;
					}
				}

				// 신규 격자 - 카카오 API로 밀집도 검사
				log.debug("    - [신규 격자] 밀집도 검사 API 호출 (좌표: {},{})", center[0], center[1]);

				KakaoResponse response = callKakaoApiWithRetry(
					() -> kakaoApiService.searchPlaces(keyword, center[1], center[0], radius, 1), "키워드 검색");

				if (response == null || response.getDocuments() == null)
					continue;

				int totalCount = response.getMeta().getTotal_count();

				if (totalCount > DENSE_AREA_THRESHOLD) {
					// 밀집 지역: 하위 격자로 분할하여 다음 레벨에서 처리
					denseSubPolygons.add(GridUtil.createPolygonForCell(center[0], center[1], radius));

					// 격자 상태를 DB에 저장 (캐싱 없음)
					ScannedGrid subdividedGrid = ScannedGrid.builder()
						.regionName(regionName).keyword(keyword).gridCenterLat(center[0]).gridCenterLng(center[1])
						.gridRadius(radius).status(ScannedGrid.GridStatus.SUBDIVIDED).build();

					scannedGridRepository.save(subdividedGrid);
				} else {
					// 일반 지역: 페이지네이션으로 모든 데이터 수집
					int foundCountInCell = savePaginatedPlaces(response, regionName, keyword, polygon, center, radius,
						foundPlaceIds);

					// 격자 상태를 DB에 저장 (캐싱 없음)
					ScannedGrid completedGrid = ScannedGrid.builder()
						.regionName(regionName).keyword(keyword).gridCenterLat(center[0]).gridCenterLng(center[1])
						.gridRadius(radius).status(ScannedGrid.GridStatus.COMPLETED).build();

					scannedGridRepository.save(completedGrid);

					if (foundCountInCell > 0) {
						log.info("        - 일반 지역 (결과: {}개). {}개의 새 장소를 DB에 저장.", totalCount, foundCountInCell);
					}
				}

			} catch (Exception e) {
				if (e instanceof InterruptedException)
					Thread.currentThread().interrupt();
				log.error("    - 격자 수집 중 오류 발생 (좌표: {},{}): {}", center[0], center[1], e.getMessage());
			}
		}
		return denseSubPolygons;
	}

	/**
	 * 강제 수집 (최대 깊이 도달 시)
	 */
	private void forceCollectInParallel(String regionName, String keyword, List<List<List<Double>>> polygons,
		int radius, Set<String> foundPlaceIds) {
		List<Callable<Void>> tasks = new ArrayList<>();
		for (List<List<Double>> polygon : polygons) {
			tasks.add(() -> {
				forceCollectAtMaxDepth(regionName, keyword, polygon, radius, foundPlaceIds);
				return null;
			});
		}
		try {
			executor.invokeAll(tasks);
		} catch (InterruptedException e) {
			log.error("Parallel force collection failed", e);
			Thread.currentThread().interrupt();
		}
	}

	/**
	 * 최대 깊이에서 강제 수집
	 */
	private void forceCollectAtMaxDepth(String regionName, String keyword, List<List<Double>> polygon,
		int radius, Set<String> foundPlaceIds) {
		GridUtil.BoundingBox bbox = GridUtil.getBoundingBoxForPolygon(polygon);
		List<double[]> gridCenters = GridUtil.generateGridForBoundingBox(bbox.getLatStart(),
			bbox.getLatEnd(), bbox.getLngStart(), bbox.getLngEnd(), radius);

		for (double[] center : gridCenters) {
			if (!GridUtil.isPointInPolygon(center[0], center[1], polygon))
				continue;
			savePaginatedPlaces(null, regionName, keyword, polygon, center, radius, foundPlaceIds);
		}
	}

	/**
	 * 페이지네이션으로 모든 데이터 수집 (기존 버전)
	 */
	private int savePaginatedPlaces(KakaoResponse firstPageResponse, String regionName, String keyword,
		List<List<Double>> regionPolygon, double[] center, int radius, Set<String> foundPlaceIds) {
		int foundCount = 0;
		try {
			KakaoResponse currentResponse = firstPageResponse;
			int currentPage = 1;

			if (currentResponse == null) {
				final int pageForFirstCall = currentPage;
				currentResponse = callKakaoApiWithRetry(
					() -> kakaoApiService.searchPlaces(keyword, center[1], center[0], radius, pageForFirstCall),
					"페이지네이션 검색 (1페이지)");
			}

			while (true) {
				if (currentResponse == null || currentResponse.getDocuments() == null || currentResponse.getDocuments()
					.isEmpty()) {
					break;
				}

				int savedInPage = savePlaces(currentResponse.getDocuments(), regionName, keyword, regionPolygon,
					foundPlaceIds);
				if (savedInPage > 0) {
					log.info("        - 페이지 {}에서 {}개의 새 장소를 DB에 저장 시도.", currentPage, savedInPage);
				}
				foundCount += savedInPage;

				if (currentResponse.getMeta().is_end())
					break;

				currentPage++;
				if (currentPage > MAX_PAGE_PER_QUERY)
					break;

				final int pageForNextCall = currentPage;
				currentResponse = callKakaoApiWithRetry(
					() -> kakaoApiService.searchPlaces(keyword, center[1], center[0], radius, pageForNextCall),
					"페이지네이션 검색 ({}페이지)".formatted(currentPage));
			}
		} catch (Exception e) {
			if (e instanceof InterruptedException)
				Thread.currentThread().interrupt();
			log.error("    - 페이지네이션 수집 중 오류 발생: {}", e.getMessage());
		}
		return foundCount;
	}

	/**
	 * 장소 목록을 DB에 저장 (기존 버전 - 배치 처리 없음)
	 */
	private int savePlaces(List<KakaoPlace> places, String regionName, String keyword,
		List<List<Double>> regionPolygon, Set<String> foundPlaceIds) {

		List<PlaceEntity> placeEntities = new ArrayList<>();
		for (KakaoPlace place : places) {
			double lat = Double.parseDouble(place.getY());
			double lng = Double.parseDouble(place.getX());

			if (!GridUtil.isPointInPolygon(lat, lng, regionPolygon)) {
				continue;
			}

			if (foundPlaceIds.contains(place.getId())) {
				continue;
			}

			PlaceEntity entity = PlaceEntity.builder()
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

		if (placeEntities.isEmpty()) {
			return 0;
		}

		try {
			collectorPlaceRepository.saveAll(placeEntities);
			return placeEntities.size();
		} catch (DataIntegrityViolationException e) {
			log.warn("    - 중복 데이터로 인한 저장 실패: {}", e.getMessage());
			return 0;
		}
	}

	/**
	 * 카카오 API 호출 재시도 로직
	 */
	private <T> T callKakaoApiWithRetry(Callable<T> apiCall, String errorMessage) throws Exception {
		int attempts = 0;
		long currentDelay = API_CALL_DELAY_MS;

		while (attempts < MAX_RETRIES) {
			try {
				Thread.sleep(currentDelay);
				return apiCall.call();
			} catch (WebClientResponseException e) {
				if (e.getStatusCode().value() == 429) {
					attempts++;
					log.warn("    - API 호출 429 오류 발생 (재시도 {}/{}) - {}", attempts, MAX_RETRIES, errorMessage);
					currentDelay = (long)(INITIAL_RETRY_DELAY_MS * Math.pow(2, attempts - 1));
					if (currentDelay > 60000)
						currentDelay = 60000;
					log.warn("    - 다음 재시도까지 {}ms 대기...", currentDelay);
				} else {
					throw e;
				}
			} catch (Exception e) {
				throw e;
			}
		}
		throw new RuntimeException("API 호출 최대 재시도 횟수 초과: " + errorMessage);
	}
}
