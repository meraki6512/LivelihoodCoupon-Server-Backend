package com.livelihoodcoupon.collector.dto;

import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 지역명 기반 요청 DTO
 *
 * <p>성능 테스트나 데이터 수집 시 여러 지역을 한 번에 처리하기 위한 요청 객체입니다.</p>
 *
 * @author livelihoodCoupon
 * @since 1.0.0
 */
@Data
@NoArgsConstructor
@AllArgsConstructor
public class RegionNamesRequest {

	/**
	 * 처리할 지역명 목록
	 * <p>예: ["서울특별시 종로구", "경기도 수원시 장안구"]</p>
	 */
	private List<String> regionNames;
}
