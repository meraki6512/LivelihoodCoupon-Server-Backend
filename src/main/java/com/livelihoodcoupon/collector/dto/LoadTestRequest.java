package com.livelihoodcoupon.collector.dto;

import java.util.List;

import com.livelihoodcoupon.collector.vo.RegionData;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 부하 테스트 요청 DTO
 *
 * <p>다양한 크기의 지역에 대한 부하 테스트를 수행하기 위한 요청 객체입니다.</p>
 * <p>지역을 크기별로 분류하여 각각의 성능을 측정할 수 있습니다.</p>
 *
 * @author livelihoodCoupon
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class LoadTestRequest {

	/**
	 * 소규모 지역 목록
	 * <p>면적이 작은 지역들로, 빠른 처리 시간을 기대할 수 있습니다.</p>
	 */
	private List<RegionData> smallRegions;

	/**
	 * 중규모 지역 목록
	 * <p>중간 크기의 지역들로, 일반적인 성능을 측정하는 데 사용됩니다.</p>
	 */
	private List<RegionData> mediumRegions;

	/**
	 * 대규모 지역 목록
	 * <p>면적이 큰 지역들로, 시스템의 한계 성능을 측정하는 데 사용됩니다.</p>
	 */
	private List<RegionData> largeRegions;
}
