package com.rabbittick.persister.domain.orderbook;

import java.math.BigDecimal;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.FetchType;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.JoinColumn;
import jakarta.persistence.ManyToOne;
import jakarta.persistence.SequenceGenerator;
import jakarta.persistence.Table;
import lombok.AccessLevel;
import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Getter;
import lombok.NoArgsConstructor;
import lombok.Setter;

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
	 * SEQUENCE 전략으로 배치 INSERT를 활성화한다.
	 */
	@Id
	@GeneratedValue(strategy = GenerationType.SEQUENCE, generator = "orderbook_unit_seq")
	@SequenceGenerator(name = "orderbook_unit_seq", sequenceName = "orderbook_unit_seq", allocationSize = 500)
	private Long id;

	/**
	 * 연관관계 주인.
	 * OrderBook.addUnit()에서 setOrderBook() 호출을 위해 @Setter를 적용한다.
	 */
	@Setter
	@ManyToOne(fetch = FetchType.LAZY)
	@JoinColumn(name = "orderbook_id")
	private OrderBook orderBook;

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
