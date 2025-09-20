package com.livelihoodcoupon;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.context.event.ContextRefreshedEvent;
import org.springframework.context.event.EventListener;

@SpringBootApplication
public class LivelihoodCouponApplication {

	private static final Logger log = LoggerFactory.getLogger(LivelihoodCouponApplication.class);

	public static void main(String[] args) {
		SpringApplication.run(LivelihoodCouponApplication.class, args);
	}

	/**
	 * 애플리케이션 시작 후 메모리 정보 로깅
	 */
	@EventListener(ContextRefreshedEvent.class)
	public void logMemoryInfo() {
		Runtime runtime = Runtime.getRuntime();
		long maxMemory = runtime.maxMemory();
		long totalMemory = runtime.totalMemory();
		long freeMemory = runtime.freeMemory();
		long usedMemory = totalMemory - freeMemory;

		log.info("=== 메모리 정보 ===");
		log.info("최대 메모리: {}MB", maxMemory / 1024 / 1024);
		log.info("총 할당 메모리: {}MB", totalMemory / 1024 / 1024);
		log.info("사용 중인 메모리: {}MB", usedMemory / 1024 / 1024);
		log.info("사용 가능한 메모리: {}MB", freeMemory / 1024 / 1024);
		log.info("메모리 사용률: {:.1f}%", (double) usedMemory / maxMemory * 100);
		log.info("==================");
	}

}
