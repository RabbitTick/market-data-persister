package com.rabbittick.persister.domain.orderbook;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;

/**
 * 호가 단위 데이터를 저장하는 엔티티.
 */
@Entity
@Table(name = "orderbook_unit")
@Getter
@NoArgsConstructor(access = AccessLevel.PROTECTED)
@AllArgsConstructor(access = AccessLevel.PRIVATE)
@Builder
public class OrderBookUnit {
	
	/**
	 * 내부 식별자 (Surrogate Key).
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.IDENTITY)
	private Long id;

	/**
	 * 매도 호가 가격.
	 */
	@Column(name = "ask_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal askPrice;

	/**
	 * 매도 호가 수량.
	 */
	@Column(name = "ask_size", nullable = false, precision = 20, scale = 8)
	private BigDecimal askSize;

	/**
	 * 매수 호가 가격.
	 */
	@Column(name = "bid_price", nullable = false, precision = 20, scale = 8)
	private BigDecimal bidPrice;

	/**
	 * 매수 호가 수량.
	 */
	@Column(name = "bid_size", nullable = false, precision = 20, scale = 8)
	private BigDecimal bidSize;
}
