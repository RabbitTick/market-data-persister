package com.rabbittick.persister.domain.orderbook;

import java.util.List;
import java.util.Objects;

import org.springframework.stereotype.Component;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.OrderBookPayload;
import com.rabbittick.persister.global.dto.OrderBookUnitPayload;

/**
 * MarketDataMessage를 OrderBook 엔티티로 변환하는 매퍼.
 *
 * 주요 책임:
 *
 * 입력 메시지/페이로드 필수 필드 검증
 * 엔티티 필드 매핑
 *
 * 이 매퍼는 저장 계층으로 전달되는 데이터의 품질을 보장하며,
 * 누락된 필드가 있을 경우 명확한 예외를 발생시킨다.
 */
@Component
public class OrderBookMapper {
	
	/**
	 * MarketDataMessage를 OrderBook 엔티티로 변환한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 * @return 변환된 OrderBook 엔티티
	 * @throws NullPointerException 필수 필드가 null인 경우
	 * @throws IllegalArgumentException 필수 숫자 필드가 유효하지 않은 경우
	 */
	public OrderBook toEntity(MarketDataMessage<OrderBookPayload> message) {
		Objects.requireNonNull(message, "message는 null일 수 없다");
		Objects.requireNonNull(message.getMetadata(), "metadata는 null일 수 없다");
		Objects.requireNonNull(message.getPayload(), "payload는 null일 수 없다");

		OrderBookPayload payload = message.getPayload();
		validatePayload(payload);

		List<OrderBookUnit> units = payload.getOrderbookUnits().stream()
			.map(this::toUnitEntity)
			.toList();

		return OrderBook.builder()
			.exchange(message.getMetadata().getExchange())
			.marketCode(payload.getMarketCode())
			.timestamp(payload.getTimestamp())
			.totalAskSize(payload.getTotalAskSize())
			.totalBidSize(payload.getTotalBidSize())
			.orderbookUnits(units)
			.build();
	}

	/**
	 * 호가 단위 payload를 OrderBookUnit 엔티티로 변환한다.
	 *
	 * @param unit 호가 단위 payload
	 * @return 변환된 OrderBookUnit 엔티티
	 * @throws NullPointerException 필수 필드가 null인 경우
	 */
	private OrderBookUnit toUnitEntity(OrderBookUnitPayload unit) {
		Objects.requireNonNull(unit, "orderbookUnit은 null일 수 없다");
		Objects.requireNonNull(unit.getAskPrice(), "askPrice는 null일 수 없다");
		Objects.requireNonNull(unit.getAskSize(), "askSize는 null일 수 없다");
		Objects.requireNonNull(unit.getBidPrice(), "bidPrice는 null일 수 없다");
		Objects.requireNonNull(unit.getBidSize(), "bidSize는 null일 수 없다");

		return OrderBookUnit.builder()
			.askPrice(unit.getAskPrice())
			.askSize(unit.getAskSize())
			.bidPrice(unit.getBidPrice())
			.bidSize(unit.getBidSize())
			.build();
	}

	/**
	 * OrderBookPayload 필수 필드를 검증한다.
	 *
	 * @param payload 검증할 payload
	 * @throws NullPointerException 필수 필드가 null인 경우
	 * @throws IllegalArgumentException 필수 숫자 필드가 유효하지 않은 경우
	 */
	private void validatePayload(OrderBookPayload payload) {
		Objects.requireNonNull(payload.getMarketCode(), "marketCode는 null일 수 없다");
		Objects.requireNonNull(payload.getTotalAskSize(), "totalAskSize는 null일 수 없다");
		Objects.requireNonNull(payload.getTotalBidSize(), "totalBidSize는 null일 수 없다");
		Objects.requireNonNull(payload.getOrderbookUnits(), "orderbookUnits는 null일 수 없다");

		if (payload.getTimestamp() <= 0) {
			throw new IllegalArgumentException("timestamp는 양수여야 한다");
		}
		if (payload.getOrderbookUnits().isEmpty()) {
			throw new IllegalArgumentException("orderbookUnits는 비어 있을 수 없다");
		}
	}
}
