package com.rabbittick.persister.global.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 표준화된 티커 데이터를 담는 Payload DTO.
 *
 * 주요 책임:
 *
 * 거래소별 티커 데이터의 표준 표현 제공
 * 가격/거래량 정밀도 보장(BigDecimal)
 * 저장 계층에서의 필드 매핑 기준 제공
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TickerPayload {
	
	/**
	 * 마켓 코드 (예: KRW-BTC).
	 */
	private String marketCode;

	/**
	 * 현재가 (최근 체결가).
	 */
	private BigDecimal tradePrice;

	/**
	 * 최근 거래량.
	 */
	private BigDecimal tradeVolume;

	/**
	 * 시가 (당일 첫 거래가).
	 */
	private BigDecimal openingPrice;

	/**
	 * 고가 (당일 최고가).
	 */
	private BigDecimal highPrice;

	/**
	 * 저가 (당일 최저가).
	 */
	private BigDecimal lowPrice;

	/**
	 * 전일 종가.
	 */
	private BigDecimal prevClosingPrice;

	/**
	 * 24시간 누적 거래대금.
	 */
	private BigDecimal accTradePrice24h;

	/**
	 * 24시간 누적 거래량.
	 */
	private BigDecimal accTradeVolume24h;

	/**
	 * 티커 데이터 생성 시각 (Unix timestamp, milliseconds).
	 */
	private long timestamp;
}
