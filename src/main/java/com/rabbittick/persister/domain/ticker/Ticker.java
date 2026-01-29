package com.rabbittick.persister.domain.ticker;

import java.math.BigDecimal;
import java.time.LocalDateTime;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 티커 데이터를 저장하는 엔티티.
 *
 * 주요 책임:
 *
 * 티커 스키마와의 정확한 매핑
 * UNIQUE 제약을 통한 멱등성 기반 지원
 */
@Entity
@Table(
	name = "ticker",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_ticker_unique",
		columnNames = { "exchange", "market_code", "timestamp" }
	)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Ticker {
	
	/**
	 * 내부 식별자 (Surrogate Key).
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 거래소 이름 (예: UPBIT).
	 */
	@Column(nullable = false, length = 20)
	private String exchange;

	/**
	 * 마켓 코드 (예: KRW-BTC).
	 */
	@Column(name = "market_code", nullable = false, length = 20)
	private String marketCode;

	/**
	 * 현재가.
	 */
	@Column(name = "trade_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal tradePrice;

	/**
	 * 최근 거래량.
	 */
	@Column(name = "trade_volume", nullable = false, precision = 20, scale = 8)
	private BigDecimal tradeVolume;

	/**
	 * 시가.
	 */
	@Column(name = "opening_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal openingPrice;

	/**
	 * 고가.
	 */
	@Column(name = "high_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal highPrice;

	/**
	 * 저가.
	 */
	@Column(name = "low_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal lowPrice;

	/**
	 * 전일 종가.
	 */
	@Column(name = "prev_closing_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal prevClosingPrice;

	/**
	 * 24시간 누적 거래대금.
	 */
	@Column(name = "acc_trade_price_24h", nullable = false, precision = 30, scale = 8)
	private BigDecimal accTradePrice24h;

	/**
	 * 24시간 누적 거래량.
	 */
	@Column(name = "acc_trade_volume_24h", nullable = false, precision = 20, scale = 8)
	private BigDecimal accTradeVolume24h;

	/**
	 * 데이터 발생 시각 (Unix timestamp, milliseconds).
	 */
	@Column(nullable = false)
	private Long timestamp;

	/**
	 * 데이터 적재 시각.
	 */
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}