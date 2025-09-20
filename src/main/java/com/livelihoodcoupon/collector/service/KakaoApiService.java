package com.livelihoodcoupon.collector.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.HttpStatusCode;
import org.springframework.stereotype.Service;
import org.springframework.web.reactive.function.client.WebClient;

import com.livelihoodcoupon.collector.dto.Coord2RegionCodeResponse;
import com.livelihoodcoupon.collector.dto.KakaoResponse;
import com.livelihoodcoupon.common.exception.KakaoApiException;

import reactor.core.publisher.Mono;

/**
 * 카카오 지도 API 호출을 담당하는 서비스
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li><b>키워드 검색:</b> 특정 위치에서 반경 내 키워드 검색</li>
 *   <li><b>좌표-지역 변환:</b> 위도/경도를 행정구역 코드로 변환</li>
 *   <li><b>에러 처리:</b> HTTP 상태 코드별 예외 처리</li>
 * </ul>
 *
 * <h3>API 제한사항:</h3>
 * <ul>
 *   <li>일일 호출 제한: 300,000회</li>
 *   <li>초당 호출 제한: 10회</li>
 *   <li>페이지당 최대 결과: 15개</li>
 *   <li>최대 페이지 수: 45페이지 (총 675개 결과)</li>
 * </ul>
 */
@Service
public class KakaoApiService {

	/** WebClient 인스턴스 - 비동기 HTTP 클라이언트 */
	private final WebClient webClient;

	/**
	 * KakaoApiService 생성자
	 *
	 * @param apiKey 카카오 API 키 (application yml에서 주입)
	 */
	public KakaoApiService(@Value("${kakao.api.key}") String apiKey) {
		this.webClient = WebClient.builder()
			.baseUrl("https://dapi.kakao.com")  // 카카오 API 기본 URL
			.defaultHeader("Authorization", "KakaoAK " + apiKey)  // 인증 헤더 설정
			.build();
	}

	/**
	 * 키워드로 장소를 검색하는 메서드
	 *
	 * <h3>검색 원리:</h3>
	 * <ol>
	 *   <li>지정된 좌표(위도, 경도)를 중심으로 반경 내에서 검색</li>
	 *   <li>키워드와 일치하는 장소들을 거리순으로 정렬하여 반환</li>
	 *   <li>페이지네이션을 통해 최대 45페이지까지 조회 가능</li>
	 * </ol>
	 *
	 * @param keyword 검색할 키워드 (예: "소비쿠폰")
	 * @param lng 중심점 경도 (X 좌표)
	 * @param lat 중심점 위도 (Y 좌표)
	 * @param radius 검색 반경 (미터 단위, 최대 20,000m)
	 * @param page 페이지 번호 (1부터 시작)
	 * @return 검색 결과 (KakaoResponse 객체)
	 * @throws KakaoApiException API 호출 실패 시
	 */
	public KakaoResponse searchPlaces(String keyword, double lng, double lat, int radius, int page) {
		return webClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/v2/local/search/keyword.json")  // 카카오 키워드 검색 API 엔드포인트
				.queryParam("query", keyword)           // 검색 키워드
				.queryParam("x", lng)                   // 중심점 경도
				.queryParam("y", lat)                   // 중심점 위도
				.queryParam("radius", radius)           // 검색 반경 (미터)
				.queryParam("page", page)               // 페이지 번호
				.queryParam("size", 15)                 // 페이지당 결과 수 (최대 15개)
				.build())
			.retrieve()
			.onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
				.flatMap(errorBody -> Mono.error(
					new KakaoApiException("Kakao API Error: " + response.statusCode() + " - " + errorBody,
						response.statusCode(), errorBody))))
			.bodyToMono(KakaoResponse.class)
			.block();  // 동기 호출 (비동기 처리는 상위에서 관리)
	}

	public Coord2RegionCodeResponse getRegionInfo(double lng, double lat) {
		return webClient.get()
			.uri(uriBuilder -> uriBuilder
				.path("/v2/local/geo/coord2regioncode.json")
				.queryParam("x", lng)
				.queryParam("y", lat)
				.build())
			.retrieve()
			.onStatus(HttpStatusCode::isError, response -> response.bodyToMono(String.class)
				.flatMap(errorBody -> Mono.error(
					new KakaoApiException("Kakao API Error: " + response.statusCode() + " - " + errorBody,
						response.statusCode(), errorBody))))
			.bodyToMono(Coord2RegionCodeResponse.class)
			.block();
	}
}
