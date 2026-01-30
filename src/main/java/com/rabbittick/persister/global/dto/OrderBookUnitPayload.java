package com.rabbittick.persister.global.dto;

import java.math.BigDecimal;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 표준화된 호가 단위 정보를 담는 DTO 클래스.
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class OrderBookUnitPayload {
	
	/**
	 * 매도 호가 가격.
	 */
	private BigDecimal askPrice;

	/**
	 * 매도 호가 수량.
	 */
	private BigDecimal askSize;

	/**
	 * 매수 호가 가격.
	 */
	private BigDecimal bidPrice;

	/**
	 * 매수 호가 수량.
	 */
	private BigDecimal bidSize;
}
