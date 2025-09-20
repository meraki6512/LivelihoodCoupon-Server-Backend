package com.livelihoodcoupon.common.config;

import java.time.Duration;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.cache.CacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.data.redis.cache.RedisCacheConfiguration;
import org.springframework.data.redis.cache.RedisCacheManager;
import org.springframework.data.redis.connection.RedisConnectionFactory;
import org.springframework.data.redis.core.RedisTemplate;
import org.springframework.data.redis.serializer.GenericJackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.Jackson2JsonRedisSerializer;
import org.springframework.data.redis.serializer.StringRedisSerializer;

import com.livelihoodcoupon.collector.entity.ScannedGrid;

import lombok.Data;

/**
 * Redis 캐싱 설정 클래스
 *
 * <p>Redis 연결, 직렬화, 캐시 매니저 설정을 담당합니다.</p>
 * <p>Spring Cache 추상화를 통해 @Cacheable, @CachePut 등의 어노테이션을 사용할 수 있습니다.</p>
 *
 * @author livelihoodCoupon
 * @since 1.0.0
 */
@Configuration
@ConfigurationProperties(prefix = "spring.data.redis")
@Data
public class RedisConfig {

	/**
	 * Redis 호스트 (기본값: localhost)
	 */
	private String host = "localhost";

	/**
	 * Redis 포트 (기본값: 6379)
	 */
	private int port = 6379;

	/**
	 * Redis 연결 타임아웃 (기본값: 2000ms)
	 */
	private Duration timeout = Duration.ofMillis(2000);

	/**
	 * RedisTemplate 빈 생성
	 * 
	 * <p>ScannedGrid 엔티티를 위한 전용 RedisTemplate을 생성합니다.</p>
	 * <p>JSON 직렬화를 사용하여 객체를 Redis에 저장합니다.</p>
	 *
	 * @param connectionFactory Redis 연결 팩토리
	 * @return ScannedGrid용 RedisTemplate
	 */
	@Bean
	public RedisTemplate<String, ScannedGrid> redisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, ScannedGrid> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);

		// JSON 직렬화 설정
		Jackson2JsonRedisSerializer<ScannedGrid> serializer = new Jackson2JsonRedisSerializer<>(ScannedGrid.class);
		template.setDefaultSerializer(serializer);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(serializer);
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(serializer);

		template.afterPropertiesSet();
		return template;
	}

	/**
	 * 일반적인 객체를 위한 RedisTemplate 빈 생성
	 * 
	 * <p>다양한 타입의 객체를 저장할 수 있는 범용 RedisTemplate입니다.</p>
	 *
	 * @param connectionFactory Redis 연결 팩토리
	 * @return 범용 RedisTemplate
	 */
	@Bean
	public RedisTemplate<String, Object> genericRedisTemplate(RedisConnectionFactory connectionFactory) {
		RedisTemplate<String, Object> template = new RedisTemplate<>();
		template.setConnectionFactory(connectionFactory);

		// JSON 직렬화 설정
		GenericJackson2JsonRedisSerializer serializer = new GenericJackson2JsonRedisSerializer();
		template.setDefaultSerializer(serializer);
		template.setKeySerializer(new StringRedisSerializer());
		template.setValueSerializer(serializer);
		template.setHashKeySerializer(new StringRedisSerializer());
		template.setHashValueSerializer(serializer);

		template.afterPropertiesSet();
		return template;
	}

	/**
	 * Redis Cache Manager 빈 생성
	 * 
	 * <p>Spring Cache 추상화를 위한 CacheManager를 설정합니다.</p>
	 * <p>기본 TTL은 1시간으로 설정되며, 캐시별로 다른 TTL을 설정할 수 있습니다.</p>
	 *
	 * @param connectionFactory Redis 연결 팩토리
	 * @return Redis 기반 CacheManager
	 */
	@Bean
	public CacheManager cacheManager(RedisConnectionFactory connectionFactory) {
		// 기본 캐시 설정 (1시간 TTL)
		RedisCacheConfiguration defaultConfig = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofHours(1));

		// 격자 캐시 전용 설정 (1시간 TTL)
		RedisCacheConfiguration gridCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofHours(1));

		// 장소 상세 정보 캐시 설정 (30분 TTL)
		RedisCacheConfiguration placeCacheConfig = RedisCacheConfiguration.defaultCacheConfig()
			.entryTtl(Duration.ofMinutes(30));

		return RedisCacheManager.builder(connectionFactory)
			.cacheDefaults(defaultConfig)
			.withCacheConfiguration("gridCache", gridCacheConfig)
			.withCacheConfiguration("placeDetails", placeCacheConfig)
			.build();
	}
}
