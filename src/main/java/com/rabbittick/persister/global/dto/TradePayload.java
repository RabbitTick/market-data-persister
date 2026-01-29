package com.rabbittick.persister.global.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 표준화된 거래 체결 데이터를 담는 Payload DTO 클래스.
 *
 * 주요 책임:
 *
 * 거래소별 trade 데이터의 표준화된 표현
 * 가격 및 거래량 정보의 정밀한 처리
 * 매수/매도 구분 및 호가 정보 제공
 * 데이터 무결성 및 고유성 보장
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class TradePayload {
	
	/**
	 * 마켓 코드 (예: KRW-BTC).
	 */
	private String marketCode;

	/**
	 * 메시지 수신 시각 (Unix timestamp, milliseconds).
	 */
	private long timestamp;

	/**
	 * 거래일 (yyyy-MM-dd 형식).
	 */
	private String tradeDate;

	/**
	 * 거래시각 (HH:mm:ss 형식).
	 */
	private String tradeTime;

	/**
	 * 거래 체결 시각 (Unix timestamp, milliseconds).
	 */
	private long tradeTimestamp;

	/**
	 * 체결 가격.
	 */
	private BigDecimal tradePrice;

	/**
	 * 체결량.
	 */
	private BigDecimal tradeVolume;

	/**
	 * 매수/매도 구분.
	 */
	private String askBid;

	/**
	 * 전일 종가.
	 */
	private BigDecimal prevClosingPrice;

	/**
	 * 가격 변화 방향.
	 */
	private String change;

	/**
	 * 변화 금액.
	 */
	private BigDecimal changePrice;

	/**
	 * 체결 고유 ID.
	 */
	private long sequentialId;

	/**
	 * 최우선 매도호가.
	 */
	private BigDecimal bestAskPrice;

	/**
	 * 최우선 매도호가 수량.
	 */
	private BigDecimal bestAskSize;

	/**
	 * 최우선 매수호가.
	 */
	private BigDecimal bestBidPrice;

	/**
	 * 최우선 매수호가 수량.
	 */
	private BigDecimal bestBidSize;

	/**
	 * 스트림 타입.
	 */
	private String streamType;
}
