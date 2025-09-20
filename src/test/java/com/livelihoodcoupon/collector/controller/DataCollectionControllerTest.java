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
import com.livelihoodcoupon.collector.service.IntegratedCouponDataCollector;
import com.livelihoodcoupon.collector.service.RegionLoader;
import com.livelihoodcoupon.collector.vo.RegionData;

@WebMvcTest(DataCollectionController.class)
class DataCollectionControllerTest {

	@Autowired
	private MockMvc mockMvc;

	@MockitoBean
	private IntegratedCouponDataCollector collector;
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
	@DisplayName("전국 데이터 수집 API 테스트")
	void collectNationwide_shouldReturnSuccess() throws Exception {
		// given
		when(regionLoader.loadRegions()).thenReturn(testRegions);

		// when & then
		mockMvc.perform(get("/admin/collect/nationwide"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("Nationwide integrated data collection started successfully."));

		verify(collector).collectForRegionsIntegrated(testRegions);
	}

	@Test
	@DisplayName("특정 지역 데이터 수집 API 테스트")
	void collectForRegionByName_shouldReturnSuccess() throws Exception {
		// given
		when(regionLoader.loadRegions()).thenReturn(testRegions);

		// when & then
		mockMvc.perform(get("/admin/collect/서울특별시 종로구"))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("Integrated data collection for '서울특별시 종로구' started successfully."));

		verify(collector).collectForSingleRegionIntegrated(any(RegionData.class));
	}

	@Test
	@DisplayName("존재하지 않는 지역 요청 시 404 에러")
	void collectForRegionByName_whenRegionNotFound_shouldReturn404() throws Exception {
		// given
		when(regionLoader.loadRegions()).thenReturn(testRegions);

		// when & then
		mockMvc.perform(get("/admin/collect/존재하지않는지역"))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.message").value("Region '존재하지않는지역' not found."));
	}

	@Test
	@DisplayName("여러 지역명으로 데이터 수집 API 테스트")
	void collectByRegionNames_shouldReturnSuccess() throws Exception {
		// given
		when(regionLoader.loadRegions()).thenReturn(testRegions);

		DataCollectionController.RegionCollectionRequest request = new DataCollectionController.RegionCollectionRequest();
		request.setRegionNames(List.of("서울특별시 종로구"));

		// when & then
		mockMvc.perform(post("/admin/collect/regions")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isOk())
			.andExpect(jsonPath("$.success").value(true))
			.andExpect(jsonPath("$.message").value("지역명 기반 데이터 수집이 완료되었습니다. 처리된 지역: 1개"));

		verify(collector).collectForRegionsIntegrated(anyList());
	}

	@Test
	@DisplayName("존재하지 않는 지역명 요청 시 404 에러")
	void collectByRegionNames_whenNoMatchingRegions_shouldReturn404() throws Exception {
		// given
		when(regionLoader.loadRegions()).thenReturn(testRegions);

		DataCollectionController.RegionCollectionRequest request = new DataCollectionController.RegionCollectionRequest();
		request.setRegionNames(List.of("존재하지않는지역"));

		// when & then
		mockMvc.perform(post("/admin/collect/regions")
				.contentType(MediaType.APPLICATION_JSON)
				.content(objectMapper.writeValueAsString(request)))
			.andExpect(status().isNotFound())
			.andExpect(jsonPath("$.success").value(false))
			.andExpect(jsonPath("$.message").value("요청된 지역명과 일치하는 지역을 찾을 수 없습니다."));
	}
}
