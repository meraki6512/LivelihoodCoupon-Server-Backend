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
import org.mockito.Spy;
import org.mockito.junit.jupiter.MockitoExtension;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livelihoodcoupon.collector.dto.KakaoMeta;
import com.livelihoodcoupon.collector.dto.KakaoPlace;
import com.livelihoodcoupon.collector.dto.KakaoResponse;
import com.livelihoodcoupon.collector.entity.ScannedGrid;
import com.livelihoodcoupon.collector.repository.CollectorPlaceRepository;
import com.livelihoodcoupon.collector.repository.ScannedGridRepository;
import com.livelihoodcoupon.collector.vo.RegionData;

@ExtendWith(MockitoExtension.class)
class CouponDataCollectorTest {

	@InjectMocks
	private LegacyCouponDataCollector legacyCouponDataCollector;

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
	@Spy
	private ObjectMapper objectMapper = new ObjectMapper();

	private RegionData testRegion;

	@BeforeEach
	void setUp() {
		// 테스트에 사용할 기본 지역 데이터 설정
		// GridUtil이 최소 하나의 격자를 생성하도록, 격자 반경(512m)보다 큰 Polygon을 사용합니다.
		// 약 1km x 1km 크기의 사각형으로 설정합니다.
		List<List<Double>> polygon = List.of(
			List.of(127.0, 37.0),
			List.of(127.01, 37.0),
			List.of(127.01, 37.01),
			List.of(127.0, 37.01),
			List.of(127.0, 37.0) // Polygon은 시작점과 끝점이 같아야 합니다.
		);
		testRegion = new RegionData();
		testRegion.setName("테스트지역");
		testRegion.setPolygon(polygon);
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
	@DisplayName("신규 지역의 격자가 [일반 지역]일 경우, COMPLETED로 상태를 저장해야 한다")
	void collectForSingleRegion_whenCellIsNormal_savesAsCompleted() throws Exception {
		// given
		// API가 "일반 지역" 응답을 반환하도록 설정
		KakaoResponse normalResponse = mock(KakaoResponse.class);
		KakaoMeta normalMeta = mock(KakaoMeta.class);
		when(normalResponse.getMeta()).thenReturn(normalMeta);
		when(normalMeta.getTotal_count()).thenReturn(10); // Not dense
		when(normalResponse.getDocuments()).thenReturn(List.of(createDummyPlace()));

		when(kakaoApiService.searchPlaces(eq(LegacyCouponDataCollector.DEFAULT_KEYWORD), anyDouble(), anyDouble(), anyInt(),
			eq(1)))
			.thenReturn(normalResponse);

		when(scannedGridRepository.findByRegionNameAndKeywordAndGridCenterLatAndGridCenterLngAndGridRadius(anyString(),
			anyString(), anyDouble(), anyDouble(),
			anyInt()))
			.thenReturn(Optional.empty());

		// when
		legacyCouponDataCollector.collectForSingleRegion(testRegion);

		// then
		// 장소 저장이 호출되었는지 검증
		verify(collectorPlaceRepository, atLeastOnce()).saveAll(any());

		// 격자 상태가 COMPLETED로 저장되었는지 검증
		ArgumentCaptor<ScannedGrid> captor = ArgumentCaptor.forClass(ScannedGrid.class);
		verify(scannedGridRepository, atLeastOnce()).save(captor.capture());
		assertThat(captor.getValue().getStatus()).isEqualTo(ScannedGrid.GridStatus.COMPLETED);
	}

	@Test
	@DisplayName("신규 지역의 격자가 [밀집 지역]일 경우, SUBDIVIDED로 상태를 저장해야 한다")
	void collectForSingleRegion_whenCellIsDense_savesAsSubdivided() throws Exception {
		// given
		// 512m 격자는 "밀집 지역"으로, 하위 256m 격자는 "일반 지역"으로 응답하도록 설정
		KakaoResponse denseResponse = mock(KakaoResponse.class, "dense");
		KakaoMeta denseMeta = mock(KakaoMeta.class, "denseMeta");
		when(denseResponse.getMeta()).thenReturn(denseMeta);
		when(denseMeta.getTotal_count()).thenReturn(50); // Dense

		KakaoResponse normalResponse = mock(KakaoResponse.class, "normal");
		KakaoMeta normalMeta = mock(KakaoMeta.class, "normalMeta");
		when(normalResponse.getMeta()).thenReturn(normalMeta);
		when(normalMeta.getTotal_count()).thenReturn(10); // Not Dense

		// 512m 반경 호출 시에는 denseResponse를 반환
		when(kakaoApiService.searchPlaces(eq(LegacyCouponDataCollector.DEFAULT_KEYWORD), anyDouble(), anyDouble(), eq(512),
			eq(1)))
			.thenReturn(denseResponse);
		// 256m 반경 호출 시에는 normalResponse를 반환하여 추가 재귀를 방지
		when(kakaoApiService.searchPlaces(eq(LegacyCouponDataCollector.DEFAULT_KEYWORD), anyDouble(), anyDouble(), eq(256),
			eq(1)))
			.thenReturn(normalResponse);

		when(scannedGridRepository.findByRegionNameAndKeywordAndGridCenterLatAndGridCenterLngAndGridRadius(anyString(),
			anyString(), anyDouble(), anyDouble(),
			anyInt()))
			.thenReturn(Optional.empty());

		// when
		legacyCouponDataCollector.collectForSingleRegion(testRegion);

		// then
		// 밀집 지역이므로 최상위 레벨에서는 장소 저장이 호출되지 않아야 함
		verify(collectorPlaceRepository, never()).saveAll(any());

		// 512m 격자 상태가 SUBDIVIDED로 저장되었는지 검증
		ArgumentCaptor<ScannedGrid> captor = ArgumentCaptor.forClass(ScannedGrid.class);
		verify(scannedGridRepository, atLeastOnce()).save(captor.capture());

		assertThat(captor.getAllValues()).anyMatch(grid ->
			grid.getGridRadius() == 512 && grid.getStatus() == ScannedGrid.GridStatus.SUBDIVIDED);
	}

	@Test
	@DisplayName("SUBDIVIDED로 기록된 격자는 API 호출 없이 하위 탐색을 수행해야 한다")
	void collectForSingleRegion_whenGridIsSubdivided_resumesFromNextLevel() throws Exception {
		// given
		// 512m 격자는 SUBDIVIDED로, 하위 256m 격자는 신규 격자로 설정
		ScannedGrid subdividedGrid = ScannedGrid.builder().status(ScannedGrid.GridStatus.SUBDIVIDED).build();
		when(scannedGridRepository.findByRegionNameAndKeywordAndGridCenterLatAndGridCenterLngAndGridRadius(anyString(),
			anyString(), anyDouble(), anyDouble(),
			eq(512)))
			.thenReturn(Optional.of(subdividedGrid));
		when(scannedGridRepository.findByRegionNameAndKeywordAndGridCenterLatAndGridCenterLngAndGridRadius(anyString(),
			anyString(), anyDouble(), anyDouble(),
			eq(256)))
			.thenReturn(Optional.empty());

		// 하위 256m 격자는 "일반 지역"이라고 응답하도록 설정
		KakaoResponse subLevelResponse = mock(KakaoResponse.class);
		KakaoMeta subLevelMeta = mock(KakaoMeta.class);
		when(subLevelResponse.getMeta()).thenReturn(subLevelMeta);
		when(subLevelMeta.getTotal_count()).thenReturn(5); // Not dense
		when(subLevelMeta.is_end()).thenReturn(true); // 페이지네이션 종료
		when(subLevelResponse.getDocuments()).thenReturn(List.of(createDummyPlace()));
		when(kakaoApiService.searchPlaces(eq(LegacyCouponDataCollector.DEFAULT_KEYWORD), anyDouble(), anyDouble(), eq(256),
			eq(1)))
			.thenReturn(subLevelResponse);

		// when
		legacyCouponDataCollector.collectForSingleRegion(testRegion);

		// then
		// 512m 격자에 대한 API 호출은 없어야 함
		verify(kakaoApiService, never()).searchPlaces(anyString(), anyDouble(), anyDouble(), eq(512), anyInt());

		// 하위 256m 격자에 대한 API는 호출되어야 함
		verify(kakaoApiService, atLeastOnce()).searchPlaces(anyString(), anyDouble(), anyDouble(), eq(256), anyInt());

		// 하위 256m 격자는 COMPLETED로 저장되어야 함
		ArgumentCaptor<ScannedGrid> captor = ArgumentCaptor.forClass(ScannedGrid.class);
		verify(scannedGridRepository, atLeastOnce()).save(captor.capture());
		assertThat(captor.getAllValues()).anyMatch(grid ->
			grid.getGridRadius() == 256 && grid.getStatus() == ScannedGrid.GridStatus.COMPLETED);
	}

	@Test
	@DisplayName("COMPLETED로 기록된 격자는 API 호출 없이 완전히 건너뛰어야 한다")
	void collectForSingleRegion_whenGridIsCompleted_skipsProcessing() {
		// given
		// Mock a grid cell that is already marked as COMPLETED
		ScannedGrid completedGrid = ScannedGrid.builder().status(ScannedGrid.GridStatus.COMPLETED).build();
		when(scannedGridRepository.findByRegionNameAndKeywordAndGridCenterLatAndGridCenterLngAndGridRadius(anyString(),
			anyString(), anyDouble(), anyDouble(),
			anyInt()))
			.thenReturn(Optional.of(completedGrid));

		// when
		legacyCouponDataCollector.collectForSingleRegion(testRegion);

		// then
		// Verify that NO API calls were made at all
		verify(kakaoApiService, never()).searchPlaces(anyString(), anyDouble(), anyDouble(), anyInt(), anyInt());

		// Verify that no places were saved
		verify(collectorPlaceRepository, never()).saveAll(any());

		// Verify that no new progress was saved
		verify(scannedGridRepository, never()).save(any());
	}
}