package com.livelihoodcoupon.collector.controller;

import static org.mockito.ArgumentMatchers.*;
import static org.mockito.Mockito.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.*;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.util.List;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.WebMvcTest;
import org.springframework.http.MediaType;
import org.springframework.test.context.bean.override.mockito.MockitoBean;
import org.springframework.test.web.servlet.MockMvc;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.livelihoodcoupon.collector.dto.LoadTestRequest;
import com.livelihoodcoupon.collector.dto.PerformanceStats;
import com.livelihoodcoupon.collector.dto.RegionNamesRequest;
import com.livelihoodcoupon.collector.service.PerformanceTestService;
import com.livelihoodcoupon.collector.service.RegionLoader;
import com.livelihoodcoupon.collector.vo.RegionData;

@WebMvcTest(PerformanceTestController.class)
class PerformanceTestControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private PerformanceTestService performanceTestService;
	@MockitoBean
	private RegionLoader regionLoader;

	@Autowired
	private ObjectMapper objectMapper;

	private List<RegionData> testRegions;

	@BeforeEach
	void setUp() {
		RegionData region1 = new RegionData();
		region1.setName("서울특별시 종로구");
		region1.setPolygon(List.of(
			List.of(126.9, 37.5),
			List.of(127.1, 37.5),
			List.of(127.1, 37.7),
			List.of(126.9, 37.7),
			List.of(126.9, 37.5)
		));

		testRegions = List.of(region1);
	}

	@Test
	@DisplayName("성능 비교 테스트 API 테스트")
	void compareVersions_shouldReturnSuccess() throws Exception {
		// given
		RegionNamesRequest request = new RegionNamesRequest();
		request.setRegionNames(List.of("서울특별시 종로구"));
		
		PerformanceTestService.PerformanceComparisonResult mockResult = new PerformanceTestService.PerformanceComparisonResult();
		when(regionLoader.loadRegionsByName(anyList())).thenReturn(testRegions);
		when(performanceTestService.compareVersions(anyList())).thenReturn(mockResult);

		// when & then
		mockMvc.perform(post("/admin/performance/compare")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		verify(regionLoader).loadRegionsByName(anyList());
		verify(performanceTestService).compareVersions(anyList());
	}

	@Test
	@DisplayName("성능 테스트 API 테스트")
	void testPerformance_shouldReturnSuccess() throws Exception {
		// given
		PerformanceTestService.PerformanceTestResult mockResult = new PerformanceTestService.PerformanceTestResult();
		mockResult.setVersion("Integrated");
		when(performanceTestService.testPerformance(anyList())).thenReturn(mockResult);

		// when & then
		mockMvc.perform(post("/admin/performance/test")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(testRegions)))
			.andExpect(status().isOk());

		verify(performanceTestService).testPerformance(anyList());
	}

	@Test
	@DisplayName("부하 테스트 API 테스트")
	void performLoadTest_shouldReturnSuccess() throws Exception {
		// given
		PerformanceTestService.LoadTestResult mockResult = new PerformanceTestService.LoadTestResult();
		when(performanceTestService.performLoadTest(anyList(), anyList(), anyList())).thenReturn(mockResult);

		LoadTestRequest request = new LoadTestRequest();
		request.setSmallRegions(testRegions);
		request.setMediumRegions(testRegions);
		request.setLargeRegions(testRegions);

		// when & then
		mockMvc.perform(post("/admin/performance/load-test")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk());

		verify(performanceTestService).performLoadTest(anyList(), anyList(), anyList());
	}

	@Test
	@DisplayName("메모리 테스트 API 테스트")
	void performMemoryTest_shouldReturnSuccess() throws Exception {
		// given
		PerformanceTestService.MemoryUsageResult mockResult = new PerformanceTestService.MemoryUsageResult();
		when(performanceTestService.monitorMemoryUsage(anyList())).thenReturn(mockResult);

		// when & then
		mockMvc.perform(post("/admin/performance/memory-test")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(testRegions)))
			.andExpect(status().isOk());

		verify(performanceTestService).monitorMemoryUsage(anyList());
	}

	@Test
	@DisplayName("성능 통계 조회 API 테스트")
	void getPerformanceStats_shouldReturnSuccess() throws Exception {
		// when & then
		mockMvc.perform(get("/admin/performance/stats"))
			.andExpect(status().isOk());
	}
}
