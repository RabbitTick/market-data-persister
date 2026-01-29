package com.rabbittick.persister.domain.ticker;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.TickerPayload;

/**
 * MarketDataMessage를 Ticker 엔티티로 변환하는 매퍼.
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
public class TickerMapper {
	
	/**
	 * MarketDataMessage를 Ticker 엔티티로 변환한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 * @return 변환된 Ticker 엔티티
	 * @throws NullPointerException 필수 필드가 null인 경우
	 */
	public Ticker toEntity(MarketDataMessage<TickerPayload> message) {
		Objects.requireNonNull(message, "message는 null일 수 없다");
		Objects.requireNonNull(message.getMetadata(), "metadata는 null일 수 없다");
		Objects.requireNonNull(message.getPayload(), "payload는 null일 수 없다");

		TickerPayload payload = message.getPayload();
		validatePayload(payload);

		return Ticker.builder()
			.exchange(message.getMetadata().getExchange())
			.marketCode(payload.getMarketCode())
			.tradePrice(payload.getTradePrice())
			.tradeVolume(payload.getTradeVolume())
			.openingPrice(payload.getOpeningPrice())
			.highPrice(payload.getHighPrice())
			.lowPrice(payload.getLowPrice())
			.prevClosingPrice(payload.getPrevClosingPrice())
			.accTradePrice24h(payload.getAccTradePrice24h())
			.accTradeVolume24h(payload.getAccTradeVolume24h())
			.timestamp(payload.getTimestamp())
			.build();
	}

	/**
	 * TickerPayload 필수 필드를 검증한다.
	 *
	 * @param payload 검증할 payload
	 * @throws NullPointerException 필수 필드가 null인 경우
	 */
	private void validatePayload(TickerPayload payload) {
		Objects.requireNonNull(payload.getMarketCode(), "marketCode는 null일 수 없다");
		Objects.requireNonNull(payload.getTradePrice(), "tradePrice는 null일 수 없다");
		Objects.requireNonNull(payload.getTradeVolume(), "tradeVolume는 null일 수 없다");
		Objects.requireNonNull(payload.getOpeningPrice(), "openingPrice는 null일 수 없다");
		Objects.requireNonNull(payload.getHighPrice(), "highPrice는 null일 수 없다");
		Objects.requireNonNull(payload.getLowPrice(), "lowPrice는 null일 수 없다");
		Objects.requireNonNull(payload.getPrevClosingPrice(), "prevClosingPrice는 null일 수 없다");
		Objects.requireNonNull(payload.getAccTradePrice24h(), "accTradePrice24h는 null일 수 없다");
		Objects.requireNonNull(payload.getAccTradeVolume24h(), "accTradeVolume24h는 null일 수 없다");
		Objects.requireNonNull(payload.getTimestamp(), "timestamp는 null일 수 없다");
	}
}
