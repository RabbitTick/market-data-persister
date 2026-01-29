package com.rabbittick.persister.global.dto;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

/**
 * 표준화된 시장 데이터 메시지를 담는 DTO 클래스.
 *
 * 주요 책임:
 *
 * 메타데이터와 실제 데이터의 분리
 * 데이터 타입 확장에 대한 일관된 메시지 구조 제공
 * 소비자에서의 공통 처리 포맷 제공
 *
 * @param <T> 실제 데이터의 타입 (TickerPayload, TradePayload 등)
 */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class MarketDataMessage<T> {
	
	/**
	 * 메시지 메타데이터.
	 *
	 * 발신자, 타입, 수집 시각, 버전 정보를 포함한다.
	 */
	private Metadata metadata;

	/**
	 * 실제 시장 데이터 페이로드.
	 */
	private T payload;
}
