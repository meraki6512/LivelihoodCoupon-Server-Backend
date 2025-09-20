package com.livelihoodcoupon.common.config;

import java.util.List;

import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableAsync;
import org.springframework.scheduling.concurrent.ThreadPoolTaskExecutor;

import lombok.Data;

/**
 * 성능 최적화 관련 설정 클래스
 *
 * <p>application.yml의 performance 설정을 바인딩하고,
 * 스레드 풀과 관련된 빈들을 구성합니다.</p>
 *
 * @author livelihoodCoupon
 * @since 1.0.0
 */
@Configuration
@EnableAsync
@ConfigurationProperties(prefix = "performance")
@Data
public class PerformanceConfig {

	/**
	 * 격자 관련 설정
	 */
	private GridConfig grid = new GridConfig();

	/**
	 * 스레드 풀 설정
	 */
	private ThreadPoolConfig threadPool = new ThreadPoolConfig();

	/**
	 * 캐시 설정
	 */
	private CacheConfig cache = new CacheConfig();

	/**
	 * 모니터링 설정
	 */
	private MonitoringConfig monitoring = new MonitoringConfig();

	/**
	 * 통합 처리용 스레드 풀 빈 생성
	 *
	 * @return 통합 처리용 ThreadPoolTaskExecutor
	 */
	@Bean("integratedExecutor")
	public ThreadPoolTaskExecutor integratedExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(threadPool.getIntegrated().getCoreSize());
		executor.setMaxPoolSize(threadPool.getIntegrated().getMaxSize());
		executor.setQueueCapacity(threadPool.getIntegrated().getQueueCapacity());
		executor.setThreadNamePrefix("integrated-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.initialize();
		return executor;
	}

	/**
	 * 성능 테스트용 스레드 풀 빈 생성
	 *
	 * @return 성능 테스트용 ThreadPoolTaskExecutor
	 */
	@Bean("testExecutor")
	public ThreadPoolTaskExecutor testExecutor() {
		ThreadPoolTaskExecutor executor = new ThreadPoolTaskExecutor();
		executor.setCorePoolSize(threadPool.getTest().getCoreSize());
		executor.setMaxPoolSize(threadPool.getTest().getMaxSize());
		executor.setQueueCapacity(threadPool.getTest().getQueueCapacity());
		executor.setThreadNamePrefix("test-");
		executor.setWaitForTasksToCompleteOnShutdown(true);
		executor.setAwaitTerminationSeconds(60);
		executor.initialize();
		return executor;
	}

	@Data
	public static class GridConfig {
		/**
		 * 격자 반경 레벨 목록 (지수적 감소)
		 */
		private List<Integer> radiusLevels = List.of(1024, 512, 256, 128, 64, 32, 16, 8, 4);

		/**
		 * 기본 격자 반경 (미터)
		 */
		private int defaultRadius = 1024;

		/**
		 * 최소 격자 반경 (미터)
		 */
		private int minRadius = 4;
	}

	@Data
	public static class ThreadPoolConfig {
		private IntegratedConfig integrated = new IntegratedConfig();
		private TestConfig test = new TestConfig();

		@Data
		public static class IntegratedConfig {
			private int coreSize = 4;
			private int maxSize = 8;
			private int queueCapacity = 100;
		}

		@Data
		public static class TestConfig {
			private int coreSize = 2;
			private int maxSize = 4;
			private int queueCapacity = 50;
		}
	}

	@Data
	public static class CacheConfig {
		/**
		 * 격자 캐시 크기
		 */
		private int gridCacheSize = 10000;

		/**
		 * 캐시 만료 시간 (초)
		 */
		private int cacheExpireSeconds = 3600;
	}

	@Data
	public static class MonitoringConfig {
		/**
		 * 성능 모니터링 활성화 여부
		 */
		private boolean enabled = true;

		/**
		 * 메트릭 수집 간격 (초)
		 */
		private int metricsInterval = 30;
	}
}
