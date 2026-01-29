package com.rabbittick.persister.global.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 시장 데이터 메시지의 메타정보 DTO.
 *
 * 주요 책임:
 *
 * 메시지 식별 및 추적 정보 제공
 * 데이터 출처 및 타입 정보 관리
 * 수집 시각과 스키마 버전 관리
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class Metadata {
	
	/**
	 * 메시지 고유 식별자 (UUID 형태).
	 */
	private String messageId;

	/**
	 * 거래소 이름 (UPBIT, BITHUMB 등).
	 */
	private String exchange;

	/**
	 * 데이터 타입 (TICKER, TRADE, ORDERBOOK 등).
	 */
	private String dataType;

	/**
	 * 데이터 수집 시각 (ISO 8601).
	 */
	private String collectedAt;

	/**
	 * 메시지 스키마 버전.
	 */
	private String version;
}
