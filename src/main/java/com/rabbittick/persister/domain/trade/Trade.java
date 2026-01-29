package com.rabbittick.persister.domain.trade;

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
 * 거래 체결 데이터를 저장하는 엔티티.
 *
 * 주요 책임:
 *
 * trade 스키마와의 정확한 매핑
 * UNIQUE 제약을 통한 멱등성 기반 지원
 */
@Entity
@Table(
	name = "trade",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_trade_unique",
		columnNames = { "exchange", "market_code", "sequential_id" }
	)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class Trade {

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
	 * 메시지 수신 시각 (Unix timestamp, milliseconds).
	 */
	@Column(nullable = false)
	private Long timestamp;

	/**
	 * 거래일 (yyyy-MM-dd 형식).
	 */
	@Column(name = "trade_date", nullable = false, length = 10)
	private String tradeDate;

	/**
	 * 거래시각 (HH:mm:ss 형식).
	 */
	@Column(name = "trade_time", nullable = false, length = 8)
	private String tradeTime;

	/**
	 * 거래 체결 시각 (Unix timestamp, milliseconds).
	 */
	@Column(name = "trade_timestamp", nullable = false)
	private Long tradeTimestamp;

	/**
	 * 체결 가격.
	 */
	@Column(name = "trade_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal tradePrice;

	/**
	 * 체결량.
	 */
	@Column(name = "trade_volume", nullable = false, precision = 20, scale = 8)
	private BigDecimal tradeVolume;

	/**
	 * 매수/매도 구분.
	 */
	@Column(name = "ask_bid", nullable = false, length = 10)
	private String askBid;

	/**
	 * 전일 종가.
	 */
	@Column(name = "prev_closing_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal prevClosingPrice;

	/**
	 * 가격 변화 방향.
	 */
	@Column(name = "`change`", nullable = false, length = 10)
	private String change;

	/**
	 * 변화 금액.
	 */
	@Column(name = "change_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal changePrice;

	/**
	 * 체결 고유 ID.
	 */
	@Column(name = "sequential_id", nullable = false)
	private Long sequentialId;

	/**
	 * 최우선 매도호가.
	 */
	@Column(name = "best_ask_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal bestAskPrice;

	/**
	 * 최우선 매도호가 수량.
	 */
	@Column(name = "best_ask_size", nullable = false, precision = 20, scale = 8)
	private BigDecimal bestAskSize;

	/**
	 * 최우선 매수호가.
	 */
	@Column(name = "best_bid_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal bestBidPrice;

	/**
	 * 최우선 매수호가 수량.
	 */
	@Column(name = "best_bid_size", nullable = false, precision = 20, scale = 8)
	private BigDecimal bestBidSize;

	/**
	 * 스트림 타입.
	 */
	@Column(name = "stream_type", nullable = false, length = 20)
	private String streamType;

	/**
	 * 데이터 적재 시각.
	 */
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;
}
