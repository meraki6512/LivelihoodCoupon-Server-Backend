package com.livelihoodcoupon.collector.entity;

import java.io.Serializable;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;

import com.fasterxml.jackson.annotation.JsonCreator;
import com.fasterxml.jackson.annotation.JsonProperty;
import com.livelihoodcoupon.common.entity.BaseEntity;

import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Getter
@NoArgsConstructor
public class ScannedGrid extends BaseEntity implements Serializable {

	/**
	 * Redis 직렬화를 위한 serialVersionUID
	 */
	private static final long serialVersionUID = 1L;

	@Column(nullable = false)
	private String regionName;

	@Column(nullable = false)
	private String keyword;

	@Column(nullable = false)
	private double gridCenterLat;

	@Column(nullable = false)
	private double gridCenterLng;

	@Column(nullable = false)
	private int gridRadius;
	@Enumerated(EnumType.STRING)
	@Column(nullable = false)
	private GridStatus status;

	@Builder
	@JsonCreator
	public ScannedGrid(
		@JsonProperty("regionName") String regionName,
		@JsonProperty("keyword") String keyword,
		@JsonProperty("gridCenterLat") double gridCenterLat,
		@JsonProperty("gridCenterLng") double gridCenterLng,
		@JsonProperty("gridRadius") int gridRadius,
		@JsonProperty("status") GridStatus status) {
		this.regionName = regionName;
		this.keyword = keyword;
		this.gridCenterLat = gridCenterLat;
		this.gridCenterLng = gridCenterLng;
		this.gridRadius = gridRadius;
		this.status = status;
	}

	public enum GridStatus {
		COMPLETED,  // 수집 완료
		SUBDIVIDED  // 하위 격자로 분할됨
	}
}
