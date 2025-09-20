package com.livelihoodcoupon.collector.entity;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Table;

import com.livelihoodcoupon.common.entity.BaseEntity;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

@Entity
@Table(name = "collector_place")
@Getter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PlaceEntity extends BaseEntity {

	@Column(unique = true, nullable = false)
	private String placeId; // Kakao's unique place ID

	private String region;
	private String placeName;
	private String roadAddress;
	private String lotAddress;
	private Double lat;
	private Double lng;
	private String phone;
	private String category;
	private String keyword;

	// New fields from API documentation
	private String categoryGroupCode;
	private String categoryGroupName;
	private String placeUrl;
	private Double distance;
}
