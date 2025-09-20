package com.livelihoodcoupon.collector.controller;

import java.util.List;
import java.util.Optional;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.livelihoodcoupon.collector.service.IntegratedCouponDataCollector;
import com.livelihoodcoupon.collector.service.RegionLoader;
import com.livelihoodcoupon.collector.vo.RegionData;
import com.livelihoodcoupon.common.exception.BusinessException;
import com.livelihoodcoupon.common.exception.ErrorCode;
import com.livelihoodcoupon.common.response.CustomApiResponse;

import lombok.Getter;
import lombok.RequiredArgsConstructor;
import lombok.Setter;
import lombok.extern.slf4j.Slf4j;

@Slf4j
@RestController
@RequiredArgsConstructor
@RequestMapping("/admin/collect")
public class DataCollectionController {

	private final IntegratedCouponDataCollector collector;
	private final RegionLoader regionLoader;

	@GetMapping("/nationwide")
	public ResponseEntity<CustomApiResponse<?>> collectNationwide() {
		try {
			List<RegionData> regions = regionLoader.loadRegions();
			collector.collectForRegionsIntegrated(regions);
			return ResponseEntity.ok(
				CustomApiResponse.success("Nationwide integrated data collection started successfully."));
		} catch (Exception e) {
			log.error("Error starting nationwide data collection: {}", e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
				"Error starting nationwide data collection: " + e.getMessage());
		}
	}

	@GetMapping("/{regionName}")
	public ResponseEntity<CustomApiResponse<?>> collectForRegionByName(@PathVariable String regionName) {
		try {
			List<RegionData> allRegions = regionLoader.loadRegions();
			Optional<RegionData> targetRegion = allRegions.stream()
				.filter(r -> r.getName().equalsIgnoreCase(regionName))
				.findFirst();

			if (targetRegion.isPresent()) {
				collector.collectForSingleRegionIntegrated(targetRegion.get());
				return ResponseEntity.ok(
					CustomApiResponse.success(
						"Integrated data collection for '" + regionName + "' started successfully."));
			} else {
				throw new BusinessException(ErrorCode.NOT_FOUND, "Region '" + regionName + "' not found.");
			}
		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("Error starting data collection for '{}': {}", regionName, e.getMessage(), e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
				"Error starting data collection for '" + regionName + "': " + e.getMessage());
		}
	}

	/**
	 * 여러 지역명으로 데이터 수집
	 *
	 * @param request 지역명 목록
	 * @return 수집 결과
	 */
	@PostMapping("/regions")
	public ResponseEntity<CustomApiResponse<?>> collectByRegionNames(@RequestBody RegionCollectionRequest request) {
		try {
			List<RegionData> allRegions = regionLoader.loadRegions();
			List<RegionData> targetRegions = allRegions.stream()
				.filter(region -> request.getRegionNames().contains(region.getName()))
				.toList();

			if (targetRegions.isEmpty()) {
				throw new BusinessException(ErrorCode.NOT_FOUND, "요청된 지역명과 일치하는 지역을 찾을 수 없습니다.");
			}

			collector.collectForRegionsIntegrated(targetRegions);
			return ResponseEntity.ok(CustomApiResponse.success(
				"지역명 기반 데이터 수집이 완료되었습니다. 처리된 지역: " + targetRegions.size() + "개"));

		} catch (BusinessException e) {
			throw e;
		} catch (Exception e) {
			log.error("지역명 기반 데이터 수집 중 오류 발생", e);
			throw new BusinessException(ErrorCode.INTERNAL_SERVER_ERROR,
				"지역명 기반 데이터 수집 중 오류가 발생했습니다: " + e.getMessage());
		}
	}

	/**
	 * 지역명 기반 수집 요청 DTO
	 */
	@Setter
	@Getter
	public static class RegionCollectionRequest {
		private List<String> regionNames;

	}
}
