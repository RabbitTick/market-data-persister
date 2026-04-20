package com.rabbittick.persister.domain.orderbook;

import java.math.BigDecimal;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;

import org.hibernate.annotations.CreationTimestamp;

import jakarta.persistence.CascadeType;
import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.OneToMany;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import jakarta.persistence.UniqueConstraint;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 호가 데이터를 저장하는 엔티티.
 *
 * 주요 책임:
 *
 * orderbook 스키마와의 정확한 매핑
 * UNIQUE 제약을 통한 멱등성 기반 지원
 */
@Entity
@Table(
	name = "orderbook",
	uniqueConstraints = @UniqueConstraint(
		name = "uk_orderbook_unique",
		columnNames = { "exchange", "market_code", "timestamp" }
	)
)
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrderBook {

	/**
	 * 내부 식별자 (Surrogate Key).
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderbook_seq")
	@SequenceGenerator(name = "orderbook_seq", sequenceName = "orderbook_seq", allocationSize = 500)
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
	 * 데이터 생성 시각 (Unix timestamp, milliseconds).
	 */
	@Column(nullable = false)
	private Long timestamp;

	/**
	 * 총 매도 잔량.
	 */
	@Column(name = "total_ask_size", nullable = false, precision = 20, scale = 8)
	private BigDecimal totalAskSize;

	/**
	 * 총 매수 잔량.
	 */
	@Column(name = "total_bid_size", nullable = false, precision = 20, scale = 8)
	private BigDecimal totalBidSize;

	/**
	 * 호가 단위 목록.
	 */
	@OneToMany(mappedBy = "orderBook", cascade = CascadeType.ALL, orphanRemoval = true)
	@Builder.Default
	private List<OrderBookUnit> orderbookUnits = new ArrayList<>();

	/**
	 * 데이터 적재 시각.
	 */
	@CreationTimestamp
	@Column(name = "created_at", nullable = false, updatable = false)
	private LocalDateTime createdAt;

	/**
	 * 호가 단위를 추가하고 양방향 연관관계를 설정한다.
	 * 부모-자식 연관관계가 일관되도록 함께 갱신한다.
	 *
	 * @param unit 추가할 호가 단위 엔티티
	 */
	public void addUnit(OrderBookUnit unit) {
		orderbookUnits.add(unit);
		unit.setOrderBook(this);
	}
}