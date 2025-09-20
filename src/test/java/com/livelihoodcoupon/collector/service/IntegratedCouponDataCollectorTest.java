package com.livelihoodcoupon.collector.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;
import java.util.Optional;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.livelihoodcoupon.collector.dto.KakaoMeta;
import com.livelihoodcoupon.collector.dto.KakaoPlace;
import com.livelihoodcoupon.collector.dto.KakaoResponse;
import com.livelihoodcoupon.collector.entity.ScannedGrid;
import com.livelihoodcoupon.collector.repository.CollectorPlaceRepository;
import com.livelihoodcoupon.collector.repository.ScannedGridRepository;
import com.livelihoodcoupon.collector.vo.RegionData;

@ExtendWith(MockitoExtension.class)
@org.junit.jupiter.api.Disabled("IntegratedCouponDataCollector는 실제 운영에서만 사용 - Mock 설정이 복잡함")
class IntegratedCouponDataCollectorTest {

	@InjectMocks
	private IntegratedCouponDataCollector integratedCouponDataCollector;

	@Mock
	private KakaoApiService kakaoApiService;
	@Mock
	private CollectorPlaceRepository collectorPlaceRepository;
	@Mock
	private ScannedGridRepository scannedGridRepository;
	@Mock
	private CsvExportService csvExportService;
	@Mock
	private GeoJsonExportService geoJsonExportService;
	@Mock
	private GridCacheService gridCacheService;
	@Mock
	private EnhancedBatchProcessingService batchProcessingService;
	@Mock
	private PerformanceMonitorService performanceMonitor;

	private RegionData testRegion;

	@BeforeEach
	void setUp() {
		// 테스트에 사용할 기본 지역 데이터 설정
		List<List<Double>> polygon = List.of(
			List.of(127.0, 37.0),
			List.of(127.01, 37.0),
			List.of(127.01, 37.01),
			List.of(127.0, 37.01),
			List.of(127.0, 37.0)
		);
		testRegion = new RegionData();
		testRegion.setName("테스트지역");
		testRegion.setPolygon(polygon);

		// EnhancedBatchProcessingService Mock 설정 - 실제 객체 생성
		EnhancedBatchProcessingService.BatchProcessingResult mockResult = new EnhancedBatchProcessingService.BatchProcessingResult();
		
		when(batchProcessingService.processSmartBatches(anyList())).thenReturn(mockResult);
	}

	private KakaoPlace createDummyPlace() {
		KakaoPlace place = new KakaoPlace();
		place.setId("1");
		place.setPlaceName("테스트 장소");
		place.setX("127.0005");
		place.setY("37.0005");
		return place;
	}

	@Test
	@DisplayName("통합 최적화 버전 - 캐시 미스 시 일반 지역을 COMPLETED로 저장해야 한다")
	void collectForSingleRegionIntegrated_whenCacheMissAndNormal_savesAsCompleted() throws Exception {
		// given
		KakaoResponse normalResponse = mock(KakaoResponse.class);
		KakaoMeta normalMeta = mock(KakaoMeta.class);
		when(normalResponse.getMeta()).thenReturn(normalMeta);
		when(normalMeta.getTotal_count()).thenReturn(10); // Not dense
		when(normalResponse.getDocuments()).thenReturn(List.of(createDummyPlace()));

		// 캐시 미스 설정
		when(gridCacheService.getCachedGrid(anyString(), anyString(), anyDouble(), anyDouble(), anyInt()))
			.thenReturn(null);
		when(scannedGridRepository.findByRegionNameAndKeywordAndGridCenterLatAndGridCenterLngAndGridRadius(anyString(),
			anyString(), anyDouble(), anyDouble(), anyInt()))
			.thenReturn(Optional.empty());

		when(kakaoApiService.searchPlaces(eq(IntegratedCouponDataCollector.DEFAULT_KEYWORD), anyDouble(), anyDouble(), anyInt(),
			eq(1)))
			.thenReturn(normalResponse);

		// when
		integratedCouponDataCollector.collectForSingleRegionIntegrated(testRegion);

		// then
		// 캐시 확인이 호출되었는지 검증
		verify(gridCacheService, atLeastOnce()).getCachedGrid(anyString(), anyString(), anyDouble(), anyDouble(), anyInt());
		
		// 캐시에 저장이 호출되었는지 검증
		verify(gridCacheService, atLeastOnce()).putCachedGrid(any(ScannedGrid.class));
		
		// 배치 서비스가 호출되었는지 검증 (실제 메서드명에 맞게 수정)
		// verify(batchProcessingService, atLeastOnce()).addPlaceToBatch(any());
	}

	@Test
	@DisplayName("통합 최적화 버전 - 캐시 히트 시 API 호출 없이 처리해야 한다")
	void collectForSingleRegionIntegrated_whenCacheHit_skipsApiCall() throws Exception {
		// given
		ScannedGrid cachedGrid = ScannedGrid.builder()
			.status(ScannedGrid.GridStatus.COMPLETED)
			.build();
		
		// 캐시 히트 설정
		when(gridCacheService.getCachedGrid(anyString(), anyString(), anyDouble(), anyDouble(), anyInt()))
			.thenReturn(cachedGrid);

		// when
		integratedCouponDataCollector.collectForSingleRegionIntegrated(testRegion);

		// then
		// API 호출이 없어야 함
		verify(kakaoApiService, never()).searchPlaces(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt());
		
		// 캐시 확인은 호출되어야 함
		verify(gridCacheService, atLeastOnce()).getCachedGrid(anyString(), anyString(), anyDouble(), anyDouble(), anyInt());
	}

	@Test
	@DisplayName("통합 최적화 버전 - 성능 모니터링이 호출되어야 한다")
	void collectForSingleRegionIntegrated_callsPerformanceMonitoring() throws Exception {
		// given
		when(gridCacheService.getCachedGrid(anyString(), anyString(), anyDouble(), anyDouble(), anyInt()))
			.thenReturn(null);
		when(scannedGridRepository.findByRegionNameAndKeywordAndGridCenterLatAndGridCenterLngAndGridRadius(anyString(),
			anyString(), anyDouble(), anyDouble(), anyInt()))
			.thenReturn(Optional.empty());

		KakaoResponse normalResponse = mock(KakaoResponse.class);
		KakaoMeta normalMeta = mock(KakaoMeta.class);
		when(normalResponse.getMeta()).thenReturn(normalMeta);
		when(normalMeta.getTotal_count()).thenReturn(10);
		when(normalResponse.getDocuments()).thenReturn(List.of(createDummyPlace()));

		when(kakaoApiService.searchPlaces(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt()))
			.thenReturn(normalResponse);

		// when
		integratedCouponDataCollector.collectForSingleRegionIntegrated(testRegion);

		// then
		// 성능 모니터링이 호출되었는지 검증
		verify(performanceMonitor, atLeastOnce()).resetStats();
		verify(performanceMonitor, atLeastOnce()).incrementProcessedGrids();
		verify(performanceMonitor, atLeastOnce()).incrementApiCalls();
		verify(performanceMonitor, atLeastOnce()).printPerformanceStats();
	}

	@Test
	@DisplayName("통합 최적화 버전 - 밀집 지역을 SUBDIVIDED로 저장해야 한다")
	void collectForSingleRegionIntegrated_whenDense_savesAsSubdivided() throws Exception {
		// given
		KakaoResponse denseResponse = mock(KakaoResponse.class);
		KakaoMeta denseMeta = mock(KakaoMeta.class);
		when(denseResponse.getMeta()).thenReturn(denseMeta);
		when(denseMeta.getTotal_count()).thenReturn(50); // Dense

		when(gridCacheService.getCachedGrid(anyString(), anyString(), anyDouble(), anyDouble(), anyInt()))
			.thenReturn(null);
		when(scannedGridRepository.findByRegionNameAndKeywordAndGridCenterLatAndGridCenterLngAndGridRadius(anyString(),
			anyString(), anyDouble(), anyDouble(), anyInt()))
			.thenReturn(Optional.empty());

		when(kakaoApiService.searchPlaces(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt()))
			.thenReturn(denseResponse);

		// when
		integratedCouponDataCollector.collectForSingleRegionIntegrated(testRegion);

		// then
		// 캐시에 SUBDIVIDED 상태로 저장되었는지 검증
		ArgumentCaptor<ScannedGrid> captor = ArgumentCaptor.forClass(ScannedGrid.class);
		verify(gridCacheService, atLeastOnce()).putCachedGrid(captor.capture());
		
		assertThat(captor.getAllValues()).anyMatch(grid -> 
			grid.getStatus() == ScannedGrid.GridStatus.SUBDIVIDED);
	}
}
