package com.livelihoodcoupon.common.response;

import java.time.LocalDateTime;

import com.fasterxml.jackson.annotation.JsonInclude;
import com.livelihoodcoupon.common.exception.ErrorCode;

import lombok.Builder;
import lombok.Getter;

@Getter
@Builder
@JsonInclude(JsonInclude.Include.NON_NULL) // null 값은 응답에 포함하지 않음
public class CustomApiResponse<T> {
	private final boolean success;
	private final T data;
	private final Error error;
	private final LocalDateTime timestamp;

	// 성공 응답
	public static <T> CustomApiResponse<T> success(T data) {
		return CustomApiResponse.<T>builder()
			.success(true)
			.data(data)
			.timestamp(LocalDateTime.now())
			.build();
	}
	
	// 성공 응답 (메시지 포함)
	public static <T> CustomApiResponse<T> success(T data, String message) {
		return CustomApiResponse.<T>builder()
			.success(true)
			.data(data)
			.timestamp(LocalDateTime.now())
			.build();
	}

	// 성공 응답 (데이터 없음)
	public static CustomApiResponse<?> success() {
		return CustomApiResponse.builder()
			.success(true)
			.timestamp(LocalDateTime.now())
			.build();
	}

	// 에러 응답
	public static <T> CustomApiResponse<T> error(ErrorCode errorCode) {
		return CustomApiResponse.<T>builder()
			.success(false)
			.error(Error.builder()
				.code(errorCode.getCode())
				.message(errorCode.getMessage())
				.build())
			.timestamp(LocalDateTime.now())
			.build();
	}

	// 에러 응답 (커스텀 메시지)
	public static <T> CustomApiResponse<T> error(ErrorCode errorCode, String message) {
		return CustomApiResponse.<T>builder()
			.success(false)
			.error(Error.builder()
				.code(errorCode.getCode())
				.message(message)
				.build())
			.timestamp(LocalDateTime.now())
			.build();
	}

	@Getter
	@Builder
	public static class Error {
		private final String code;
		private final String message;
	}
}
