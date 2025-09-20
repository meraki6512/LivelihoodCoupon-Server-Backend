package com.livelihoodcoupon.collector.service;

import static org.assertj.core.api.Assertions.*;
import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import com.livelihoodcoupon.collector.vo.RegionData;

@ExtendWith(MockitoExtension.class)
class PerformanceTestServiceTest {

	@InjectMocks
	private PerformanceTestService performanceTestService;

	@Mock
	private LegacyCouponDataCollector legacyCollector;
	@Mock
	private IntegratedCouponDataCollector integratedCollector;
	@Mock
	private PerformanceMonitorService performanceMonitor;

	private List<RegionData> testRegions;

	@BeforeEach
	void setUp() {
		RegionData region1 = new RegionData();
		region1.setName("테스트지역1");
		region1.setPolygon(List.of(
			List.of(127.0, 37.0),
			List.of(127.01, 37.0),
			List.of(127.01, 37.01),
			List.of(127.0, 37.01),
			List.of(127.0, 37.0)
		));

		RegionData region2 = new RegionData();
		region2.setName("테스트지역2");
		region2.setPolygon(List.of(
			List.of(127.1, 37.1),
			List.of(127.11, 37.1),
			List.of(127.11, 37.11),
			List.of(127.1, 37.11),
			List.of(127.1, 37.1)
		));

		testRegions = List.of(region1, region2);
	}

	@Test
	@DisplayName("성능 테스트 - 통합 버전만 테스트해야 한다")
	void testPerformance_callsIntegratedCollector() {
		// when
		PerformanceTestService.PerformanceTestResult result = performanceTestService.testPerformance(testRegions);

		// then
		verify(integratedCollector, times(2)).collectForSingleRegionIntegrated(any(RegionData.class));
		verify(legacyCollector, never()).collectForSingleRegion(any(RegionData.class));
		verify(performanceMonitor, atLeastOnce()).resetStats();
		verify(performanceMonitor, atLeastOnce()).printPerformanceStats();
		
		assertThat(result.getVersion()).isEqualTo("Integrated");
		assertThat(result.getTestRegions()).hasSize(2);
	}

	@Test
	@DisplayName("성능 비교 테스트 - 기존 버전과 통합 버전을 모두 테스트해야 한다")
	void compareVersions_callsBothCollectors() {
		// when
		PerformanceTestService.PerformanceComparisonResult result = performanceTestService.compareVersions(testRegions);

		// then
		verify(legacyCollector, times(2)).collectForSingleRegion(any(RegionData.class));
		verify(integratedCollector, times(2)).collectForSingleRegionIntegrated(any(RegionData.class));
		
		assertThat(result).isNotNull();
		assertThat(result.getOriginalMetrics()).isNotNull();
		assertThat(result.getIntegratedMetrics()).isNotNull();
	}

	@Test
	@DisplayName("성능 테스트 - 예외 발생 시 에러 메시지를 설정해야 한다")
	void testPerformance_whenException_setsError() {
		// given
		doThrow(new RuntimeException("테스트 예외")).when(integratedCollector)
			.collectForSingleRegionIntegrated(any(RegionData.class));

		// when
		PerformanceTestService.PerformanceTestResult result = performanceTestService.testPerformance(testRegions);

		// then
		assertThat(result.getError()).isEqualTo("테스트 예외");
		assertThat(result.getTotalTimeMs()).isGreaterThan(0);
	}
}
