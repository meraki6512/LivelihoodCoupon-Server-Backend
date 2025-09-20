package com.livelihoodcoupon.collector.service;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

import lombok.AllArgsConstructor;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 지리적 격자 생성 및 폴리곤 처리 유틸리티 클래스
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li><b>경계 상자 계산:</b> 폴리곤의 최소/최대 위도, 경도 계산</li>
 *   <li><b>격자 생성:</b> 지정된 반경으로 격자 중심점들 생성</li>
 *   <li><b>포인트-폴리곤 포함 관계:</b> 점이 폴리곤 내부에 있는지 판단</li>
 *   <li><b>격자 폴리곤 생성:</b> 격자 중심점과 반경으로 사각형 폴리곤 생성</li>
 * </ul>
 *
 * <h3>좌표계 변환:</h3>
 * <ul>
 *   <li>1도 ≈ 111,000미터 (위도 기준)</li>
 *   <li>경도는 위도에 따라 거리가 달라짐 (cos(위도) 비례)</li>
 * </ul>
 */
public class GridUtil {

	/** 1미터당 위도 차이 (도 단위) - 약 111,000미터가 1도 */
	public static final double DEGREE_PER_METER = 1.0 / 111_000.0;

	/**
	 * 폴리곤의 경계 상자(Bounding Box)를 계산
	 *
	 * @param polygon 폴리곤 좌표 목록 [[lng, lat], [lng, lat], ...]
	 * @return 경계 상자 (최소/최대 위도, 경도)
	 */
	public static BoundingBox getBoundingBoxForPolygon(List<List<Double>> polygon) {
		if (polygon == null || polygon.isEmpty()) {
			return new BoundingBox(0, 0, 0, 0);
		}

		double minLat = Double.MAX_VALUE;
		double maxLat = Double.MIN_VALUE;
		double minLng = Double.MAX_VALUE;
		double maxLng = Double.MIN_VALUE;

		// 모든 점을 순회하며 최소/최대 좌표 찾기
		for (List<Double> point : polygon) {
			double lng = point.get(0);  // 경도
			double lat = point.get(1);  // 위도
			if (lat < minLat)
				minLat = lat;
			if (lat > maxLat)
				maxLat = lat;
			if (lng < minLng)
				minLng = lng;
			if (lng > maxLng)
				maxLng = lng;
		}
		return new BoundingBox(minLat, maxLat, minLng, maxLng);
	}

	/**
	 * 점이 폴리곤 내부에 있는지 판단 (Ray Casting 알고리즘 사용)
	 *
	 * <h3>알고리즘 원리:</h3>
	 * <ol>
	 *   <li>점에서 오른쪽으로 무한히 긴 선을 그음</li>
	 *   <li>이 선이 폴리곤의 변과 교차하는 횟수를 계산</li>
	 *   <li>교차 횟수가 홀수면 내부, 짝수면 외부</li>
	 * </ol>
	 *
	 * @param lat 위도
	 * @param lng 경도
	 * @param polygon 폴리곤 좌표 목록 [[lng, lat], [lng, lat], ...]
	 * @return 점이 폴리곤 내부에 있으면 true
	 */
	public static boolean isPointInPolygon(double lat, double lng, List<List<Double>> polygon) {
		if (polygon == null || polygon.isEmpty()) {
			return false;
		}
		int i;
		int j;
		boolean isInside = false;
		int nvert = polygon.size();

		// Ray Casting 알고리즘: 점에서 오른쪽으로 그은 선이 폴리곤과 교차하는 횟수 계산
		for (i = 0, j = nvert - 1; i < nvert; j = i++) {
			double vert_i_lng = polygon.get(i).get(0);  // 현재 점의 경도
			double vert_i_lat = polygon.get(i).get(1);  // 현재 점의 위도
			double vert_j_lng = polygon.get(j).get(0);  // 이전 점의 경도
			double vert_j_lat = polygon.get(j).get(1);  // 이전 점의 위도

			// 선분이 점의 수평선과 교차하는지 확인
			if (((vert_i_lat > lat) != (vert_j_lat > lat))
				&& (lng < (vert_j_lng - vert_i_lng) * (lat - vert_i_lat) / (vert_j_lat - vert_i_lat) + vert_i_lng)) {
				isInside = !isInside;  // 교차할 때마다 상태 반전
			}
		}
		return isInside;
	}

	/**
	 * 경계 상자 내에서 지정된 반경으로 격자 중심점들을 생성
	 *
	 * <h3>격자 생성 원리:</h3>
	 * <ol>
	 *   <li>격자 크기 = 반경 × 2 (격자는 정사각형)</li>
	 *   <li>위도 간격 = 격자 크기 / 111,000 (고정)</li>
	 *   <li>경도 간격 = 격자 크기 / (111,000 × cos(중간위도)) (위도에 따라 조정)</li>
	 *   <li>격자 중심점 = 격자 시작점 + (격자 크기 / 2)</li>
	 * </ol>
	 *
	 * @param latStart 최소 위도
	 * @param latEnd 최대 위도
	 * @param lngStart 최소 경도
	 * @param lngEnd 최대 경도
	 * @param gridCellRadiusMeters 격자 반경 (미터)
	 * @return 격자 중심점 목록 [[lat, lng], [lat, lng], ...]
	 */
	public static List<double[]> generateGridForBoundingBox(double latStart, double latEnd, double lngStart,
		double lngEnd, int gridCellRadiusMeters) {
		List<double[]> gridCenters = new ArrayList<>();

		// 격자 크기 계산 (반경 × 2)
		double latStep = gridCellRadiusMeters * 2 * DEGREE_PER_METER;

		// 경도 간격은 위도에 따라 조정 (중간 위도 기준)
		double midLat = (latStart + latEnd) / 2.0;
		double lngStep = gridCellRadiusMeters * 2 * DEGREE_PER_METER / Math.cos(Math.toRadians(midLat));

		// 격자 중심점들 생성
		for (double lat = latStart; lat <= latEnd; lat += latStep) {
			for (double lng = lngStart; lng <= lngEnd; lng += lngStep) {
				// 격자 중심점 = 격자 시작점 + (격자 크기 / 2)
				gridCenters.add(new double[] {lat + (latStep / 2), lng + (lngStep / 2)});
			}
		}
		return gridCenters;
	}

	/**
	 * 격자 중심점과 반경으로 사각형 폴리곤을 생성
	 *
	 * <h3>폴리곤 생성 과정:</h3>
	 * <ol>
	 *   <li>중심점에서 반경만큼 떨어진 4개 모서리 좌표 계산</li>
	 *   <li>경도 오프셋은 위도에 따라 조정 (cos(위도) 비례)</li>
	 *   <li>시계방향으로 4개 모서리를 연결하여 사각형 폴리곤 생성</li>
	 * </ol>
	 *
	 * @param centerLat 격자 중심점 위도
	 * @param centerLng 격자 중심점 경도
	 * @param radius 격자 반경 (미터)
	 * @return 사각형 폴리곤 좌표 목록 [[lng, lat], [lng, lat], ...]
	 */
	public static List<List<Double>> createPolygonForCell(double centerLat, double centerLng, int radius) {
		// 위도 오프셋 계산 (고정)
		double latOffset = radius * DEGREE_PER_METER;

		// 경도 오프셋 계산 (위도에 따라 조정)
		double lngOffset = radius * DEGREE_PER_METER / Math.cos(Math.toRadians(centerLat));

		// 4개 모서리 좌표 계산
		double latStart = centerLat - latOffset;  // 남쪽
		double latEnd = centerLat + latOffset;    // 북쪽
		double lngStart = centerLng - lngOffset;  // 서쪽
		double lngEnd = centerLng + lngOffset;    // 동쪽

		// 시계방향으로 4개 모서리를 연결하여 사각형 폴리곤 생성
		return new ArrayList<>(Arrays.asList(
			Arrays.asList(lngStart, latStart),  // 남서쪽
			Arrays.asList(lngEnd, latStart),    // 남동쪽
			Arrays.asList(lngEnd, latEnd),      // 북동쪽
			Arrays.asList(lngStart, latEnd),    // 북서쪽
			Arrays.asList(lngStart, latStart)   // 폴리곤 닫기
		));
	}

	/**
	 * 지리적 경계 상자를 나타내는 데이터 클래스
	 * 폴리곤의 최소/최대 위도, 경도를 저장
	 */
	@Data
	@NoArgsConstructor
	@AllArgsConstructor
	public static class BoundingBox {
		/** 최소 위도 */
		private double latStart;
		/** 최대 위도 */
		private double latEnd;
		/** 최소 경도 */
		private double lngStart;
		/** 최대 경도 */
		private double lngEnd;
	}
}
