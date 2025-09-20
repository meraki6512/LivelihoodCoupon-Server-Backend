package com.livelihoodcoupon.collector.controller;

import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import com.livelihoodcoupon.common.response.CustomApiResponse;
import com.livelihoodcoupon.common.service.MemoryManagementService;

import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;

/**
 * 헬스체크 컨트롤러
 *
 * <h3>주요 기능:</h3>
 * <ul>
 *   <li><b>애플리케이션 상태 확인:</b> 서비스가 정상 작동하는지 확인</li>
 *   <li><b>메모리 상태 확인:</b> 실시간 메모리 사용량 및 상태</li>
 *   <li><b>디버깅 지원:</b> 문제 발생 시 상태 진단</li>
 * </ul>
 */
@Slf4j
@RestController
@RequestMapping("/admin/health")
@RequiredArgsConstructor
public class HealthController {

	private final MemoryManagementService memoryManagementService;

	/**
	 * 기본 헬스체크
	 */
	@GetMapping
	public ResponseEntity<CustomApiResponse<String>> health() {
		return ResponseEntity.ok(
			CustomApiResponse.success("서비스가 정상적으로 작동 중입니다.")
		);
	}

	/**
	 * 메모리 상태 확인
	 */
	@GetMapping("/memory")
	public ResponseEntity<CustomApiResponse<MemoryManagementService.MemoryInfo>> memoryStatus() {
		MemoryManagementService.MemoryInfo memoryInfo = memoryManagementService.getMemoryInfo();

		log.info("메모리 상태 조회 - 사용률: {:.1f}%, 사용: {}MB, 최대: {}MB",
			memoryInfo.getUsageRatio() * 100,
			memoryInfo.getUsedMemoryMB(),
			memoryInfo.getMaxMemoryMB());

		return ResponseEntity.ok(
			CustomApiResponse.success(memoryInfo)
		);
	}

	/**
	 * 강제 가비지 컬렉션 실행
	 */
	@GetMapping("/gc")
	public ResponseEntity<CustomApiResponse<String>> forceGc() {
		log.info("사용자 요청으로 강제 GC 실행");
		memoryManagementService.runGarbageCollection();

		return ResponseEntity.ok(
			CustomApiResponse.success("가비지 컬렉션이 실행되었습니다.")
		);
	}

	/**
	 * 상세 상태 정보
	 */
	@GetMapping("/status")
	public ResponseEntity<CustomApiResponse<StatusInfo>> detailedStatus() {
		MemoryManagementService.MemoryInfo memoryInfo = memoryManagementService.getMemoryInfo();

		StatusInfo status = StatusInfo.builder()
			.status("RUNNING")
			.memoryInfo(memoryInfo)
			.timestamp(System.currentTimeMillis())
			.build();

		return ResponseEntity.ok(
			CustomApiResponse.success(status)
		);
	}

	/**
	 * 상태 정보 DTO
	 */
	@lombok.Data
	@lombok.Builder
	public static class StatusInfo {
		private String status;
		private MemoryManagementService.MemoryInfo memoryInfo;
		private long timestamp;
	}
}
