package com.livelihoodcoupon.place.service;

import java.time.Duration;
import java.util.HashSet;
import java.util.Set;

import jakarta.annotation.PostConstruct;

import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.annotation.Profile;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import com.livelihoodcoupon.place.repository.PlaceRepository;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * PlaceId 캐시 서비스 - Redis 기반
 * <p>장소 ID 중복 확인을 위한 캐시 서비스입니다.</p>
 * <p>애플리케이션 시작 시 모든 placeId를 Redis에 로드하고,</p>
 * <p>실시간으로 중복 확인 및 새로운 placeId 추가를 지원합니다.</p>
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li><b>초기 로드:</b> 애플리케이션 시작 시 모든 placeId를 Redis에 로드</li>
 *   <li><b>중복 확인:</b> Redis에서 placeId 존재 여부 확인</li>
 *   <li><b>동적 추가:</b> 새로운 placeId를 Redis에 추가</li>
 *   <li><b>TTL 관리:</b> Redis 자동 만료 기능으로 메모리 관리</li>
 * </ul>
 */
@Slf4j
@Service
@RequiredArgsConstructor
@Profile("!test")
public class PlaceIdCacheService {

	/** Redis 캐시 키 접두사 */
	private static final String CACHE_KEY_PREFIX = "placeId:";
	/** 캐시 TTL (24시간) */
	private static final Duration CACHE_TTL = Duration.ofHours(24);
	private final PlaceRepository placeRepository;
	@Autowired
	private RedisTemplate<String, String> redisTemplate;

	/**
	 * 애플리케이션 시작 시 모든 placeId를 Redis에 로드
	 * <p>기존 placeId들을 Redis에 저장하여 빠른 중복 확인을 지원합니다.</p>
	 */
	@PostConstruct
	@Transactional(readOnly = true)
	public void loadExistingPlaceIds() {
		log.info("기존 모든 장소 ID를 Redis 캐시에 로드 중...");
		long startTime = System.currentTimeMillis();

		try {
			// DB에서 모든 placeId 조회
			Set<String> placeIds = new HashSet<>(placeRepository.findAllPlaceIds());

			// Redis에 배치로 저장
			if (!placeIds.isEmpty()) {
				placeIds.forEach(placeId -> {
					String key = CACHE_KEY_PREFIX + placeId;
					redisTemplate.opsForValue().set(key, "true", CACHE_TTL);
				});
			}

			long endTime = System.currentTimeMillis();
			log.info("{}개의 기존 장소 ID를 Redis에 로드 완료. 소요 시간: {} ms.",
				placeIds.size(), (endTime - startTime));

		} catch (Exception e) {
			log.error("PlaceId Redis 캐시 로드 중 오류 발생: {}", e.getMessage(), e);
		}
	}

	/**
	 * Redis에서 placeId 존재 여부 확인
	 * <p>Redis 캐시에서 placeId의 존재 여부를 확인합니다.</p>
	 *
	 * @param placeId 확인할 장소 ID
	 * @return 존재 여부 (true: 존재, false: 존재하지 않음)
	 */
	public boolean contains(String placeId) {
		try {
			String key = CACHE_KEY_PREFIX + placeId;
			return Boolean.TRUE.equals(redisTemplate.hasKey(key));
		} catch (Exception e) {
			log.warn("PlaceId 캐시 조회 중 오류 발생: {}", e.getMessage());
			// Redis 오류 시 DB에서 직접 확인
			return placeRepository.findByPlaceId(placeId).isPresent();
		}
	}

	/**
	 * Redis에 새로운 placeId 추가
	 * <p>새로 수집된 placeId를 Redis 캐시에 추가합니다.</p>
	 *
	 * @param placeId 추가할 장소 ID
	 */
	public void add(String placeId) {
		try {
			String key = CACHE_KEY_PREFIX + placeId;
			redisTemplate.opsForValue().set(key, "true", CACHE_TTL);
			log.debug("PlaceId Redis 캐시에 추가: {}", placeId);
		} catch (Exception e) {
			log.warn("PlaceId 캐시 추가 중 오류 발생: {}", e.getMessage());
		}
	}

	/**
	 * Redis 캐시 통계 정보 조회
	 * <p>현재 Redis에 캐시된 placeId 개수를 조회합니다.</p>
	 *
	 * @return 캐시된 placeId 개수
	 */
	public long getCacheSize() {
		try {
			var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
			return keys != null ? keys.size() : 0;
		} catch (Exception e) {
			log.warn("PlaceId 캐시 크기 조회 중 오류 발생: {}", e.getMessage());
			return 0;
		}
	}

	/**
	 * Redis 캐시 초기화
	 * <p>모든 placeId 캐시를 삭제합니다.</p>
	 * <p>주의: 이 작업은 모든 placeId 캐시를 삭제하므로 신중하게 사용해야 합니다.</p>
	 */
	public void clearCache() {
		try {
			var keys = redisTemplate.keys(CACHE_KEY_PREFIX + "*");
			if (!keys.isEmpty()) {
				redisTemplate.delete(keys);
				log.info("PlaceId Redis 캐시 초기화 완료: {}개 키 삭제", keys.size());
			} else {
				log.info("PlaceId Redis 캐시가 이미 비어있음");
			}
		} catch (Exception e) {
			log.error("PlaceId Redis 캐시 초기화 중 오류 발생: {}", e.getMessage());
		}
	}
}
