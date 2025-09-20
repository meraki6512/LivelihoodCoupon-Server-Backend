package com.livelihoodcoupon.collector.service;

import java.time.Duration;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.cache.annotation.CacheEvict;
import org.springframework.cache.annotation.CachePut;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;

import com.livelihoodcoupon.collector.entity.ScannedGrid;

import lombok.extern.slf4j.Slf4j;

/**
 * 격자 스캔 상태를 Redis에 캐싱하여 DB 조회 횟수를 줄이는 서비스
 *
 * <h3>캐싱 전략:</h3>
 * <ul>
 *   <li><b>캐시 저장소:</b> Redis 기반 분산 캐시</li>
 *   <li><b>캐시 키:</b> regionName_keyword_lat_lng_radius</li>
 *   <li><b>TTL:</b> 1시간 (Redis 자동 만료)</li>
 *   <li><b>쓰기 전략:</b> Write-Through (DB와 동기화)</li>
 *   <li><b>직렬화:</b> JSON 기반 Jackson 직렬화</li>
 * </ul>
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li>격자 상태 조회 및 저장</li>
 *   <li>캐시 무효화</li>
 *   <li>캐시 통계 조회</li>
 *   <li>Spring Cache 어노테이션 활용</li>
 * </ul>
 */
@Slf4j
@Service
public class GridCacheService {

	/** Redis 캐시 TTL (1시간) */
	private static final Duration CACHE_TTL = Duration.ofHours(1);
	/** 캐시 키 접두사 */
	private static final String CACHE_KEY_PREFIX = "grid:";

	@Autowired
	private RedisTemplate<String, ScannedGrid> redisTemplate;

	/**
	 * 캐시에서 격자 상태 조회
	 * 
	 * <p>Spring Cache 어노테이션을 사용하여 Redis에서 격자 상태를 조회합니다.</p>
	 * <p>캐시에 데이터가 없으면 null을 반환하고, 있으면 캐시된 데이터를 반환합니다.</p>
	 *
	 * @param regionName 지역명
	 * @param keyword 키워드
	 * @param lat 위도
	 * @param lng 경도
	 * @param radius 반경
	 * @return 캐시된 격자 상태 (없으면 null)
	 */
	@Cacheable(value = "gridCache", key = "#regionName + '_' + #keyword + '_' + #lat + '_' + #lng + '_' + #radius", unless = "#result == null")
	public ScannedGrid getCachedGrid(String regionName, String keyword, double lat, double lng, int radius) {
		String key = buildCacheKey(regionName, keyword, lat, lng, radius);
		ScannedGrid cachedGrid = redisTemplate.opsForValue().get(key);

		if (cachedGrid != null) {
			log.debug("Redis 캐시 히트: {}", key);
			return cachedGrid;
		}

		log.debug("Redis 캐시 미스: {}", key);
		return null;
	}

	/**
	 * 캐시에 격자 상태 저장
	 * 
	 * <p>Spring Cache 어노테이션을 사용하여 Redis에 격자 상태를 저장합니다.</p>
	 * <p>TTL은 1시간으로 설정되며, Redis가 자동으로 만료 처리를 합니다.</p>
	 *
	 * @param grid 저장할 격자 상태
	 * @return 저장된 격자 상태
	 */
	@CachePut(value = "gridCache", key = "#grid.regionName + '_' + #grid.keyword + '_' + #grid.gridCenterLat + '_' + #grid.gridCenterLng + '_' + #grid.gridRadius")
	public ScannedGrid putCachedGrid(ScannedGrid grid) {
		String key = buildCacheKey(grid.getRegionName(), grid.getKeyword(),
			grid.getGridCenterLat(), grid.getGridCenterLng(), grid.getGridRadius());

		// Redis에 TTL과 함께 저장
		redisTemplate.opsForValue().set(key, grid, CACHE_TTL);
		log.debug("Redis 캐시 저장: {} (TTL: {}시간)", key, CACHE_TTL.toHours());

		return grid;
	}

	/**
	 * 캐시 키 생성
	 * 
	 * <p>Redis 캐시 키를 생성합니다. 접두사를 포함하여 다른 캐시와 구분합니다.</p>
	 *
	 * @param regionName 지역명
	 * @param keyword 키워드
	 * @param lat 위도
	 * @param lng 경도
	 * @param radius 반경
	 * @return 생성된 캐시 키
	 */
	private String buildCacheKey(String regionName, String keyword, double lat, double lng, int radius) {
		return CACHE_KEY_PREFIX + String.format("%s_%s_%.6f_%.6f_%d", regionName, keyword, lat, lng, radius);
	}

	/**
	 * 캐시 크기 반환
	 * 
	 * <p>Redis에서 격자 캐시의 개수를 조회합니다.</p>
	 * <p>패턴 매칭을 사용하여 격자 캐시 키만 카운트합니다.</p>
	 *
	 * @return 캐시된 격자 개수
	 */
	public long getCacheSize() {
		try {
			// Redis에서 격자 캐시 키 패턴으로 개수 조회
			var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
			return keys != null ? keys.size() : 0;
		} catch (Exception e) {
			log.warn("캐시 크기 조회 중 오류 발생: {}", e.getMessage());
			return 0;
		}
	}

	/**
	 * 특정 격자 캐시 무효화
	 * 
	 * <p>지정된 격자의 캐시를 무효화합니다.</p>
	 *
	 * @param regionName 지역명
	 * @param keyword 키워드
	 * @param lat 위도
	 * @param lng 경도
	 * @param radius 반경
	 */
	@CacheEvict(value = "gridCache", key = "#regionName + '_' + #keyword + '_' + #lat + '_' + #lng + '_' + #radius")
	public void evictCachedGrid(String regionName, String keyword, double lat, double lng, int radius) {
		String key = buildCacheKey(regionName, keyword, lat, lng, radius);
		redisTemplate.delete(key);
		log.debug("Redis 캐시 무효화: {}", key);
	}

	/**
	 * 전체 격자 캐시 무효화
	 * 
	 * <p>모든 격자 캐시를 무효화합니다.</p>
	 * <p>주의: 이 작업은 모든 격자 캐시를 삭제하므로 신중하게 사용해야 합니다.</p>
	 */
	@CacheEvict(value = "gridCache", allEntries = true)
	public void clearCache() {
		try {
			// Redis에서 격자 캐시 키 패턴으로 모든 키 삭제
			var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
			if (keys != null && !keys.isEmpty()) {
				redisTemplate.delete(keys);
				log.info("Redis 격자 캐시 초기화 완료: {}개 키 삭제", keys.size());
			} else {
				log.info("Redis 격자 캐시가 이미 비어있음");
			}
		} catch (Exception e) {
			log.error("Redis 캐시 초기화 중 오류 발생: {}", e.getMessage());
		}
	}

	/**
	 * 캐시 통계 정보 조회
	 * 
	 * <p>Redis 캐시의 통계 정보를 조회합니다.</p>
	 *
	 * @return 캐시 통계 정보 문자열
	 */
	public String getCacheStats() {
		try {
			long cacheSize = getCacheSize();
			return String.format("Redis 격자 캐시 통계 - 총 개수: %d, TTL: %d시간",
				cacheSize, CACHE_TTL.toHours());
		} catch (Exception e) {
			log.warn("캐시 통계 조회 중 오류 발생: {}", e.getMessage());
			return "캐시 통계 조회 실패";
		}
	}
}
