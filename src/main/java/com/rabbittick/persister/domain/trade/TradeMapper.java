package com.rabbittick.persister.domain.trade;

import java.util.Objects;

import org.springframework.stereotype.Component;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.TradePayload;

/**
 * MarketDataMessage를 Trade 엔티티로 변환하는 매퍼.
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
public class TradeMapper {
	
	/**
	 * MarketDataMessage를 Trade 엔티티로 변환한다.
	 *
	 * @param message 표준 시장 데이터 메시지
	 * @return 변환된 Trade 엔티티
	 * @throws NullPointerException 필수 필드가 null인 경우
	 */
	public Trade toEntity(MarketDataMessage<TradePayload> message) {
		Objects.requireNonNull(message, "message는 null일 수 없다");
		Objects.requireNonNull(message.getMetadata(), "metadata는 null일 수 없다");
		Objects.requireNonNull(message.getPayload(), "payload는 null일 수 없다");

		TradePayload payload = message.getPayload();
		validatePayload(payload);

		return Trade.builder()
			.exchange(message.getMetadata().getExchange())
			.marketCode(payload.getMarketCode())
			.timestamp(payload.getTimestamp())
			.tradeDate(payload.getTradeDate())
			.tradeTime(payload.getTradeTime())
			.tradeTimestamp(payload.getTradeTimestamp())
			.tradePrice(payload.getTradePrice())
			.tradeVolume(payload.getTradeVolume())
			.askBid(payload.getAskBid())
			.prevClosingPrice(payload.getPrevClosingPrice())
			.change(payload.getChange())
			.changePrice(payload.getChangePrice())
			.sequentialId(payload.getSequentialId())
			.bestAskPrice(payload.getBestAskPrice())
			.bestAskSize(payload.getBestAskSize())
			.bestBidPrice(payload.getBestBidPrice())
			.bestBidSize(payload.getBestBidSize())
			.streamType(payload.getStreamType())
			.build();
	}

	/**
	 * TradePayload 필수 필드를 검증한다.
	 *
	 * @param payload 검증할 payload
	 * @throws NullPointerException 필수 필드가 null인 경우
	 * @throws IllegalArgumentException 필수 숫자 필드가 유효하지 않은 경우
	 */
	private void validatePayload(TradePayload payload) {
		Objects.requireNonNull(payload.getMarketCode(), "marketCode는 null일 수 없다");
		Objects.requireNonNull(payload.getTradeDate(), "tradeDate는 null일 수 없다");
		Objects.requireNonNull(payload.getTradeTime(), "tradeTime은 null일 수 없다");
		Objects.requireNonNull(payload.getTradePrice(), "tradePrice는 null일 수 없다");
		Objects.requireNonNull(payload.getTradeVolume(), "tradeVolume는 null일 수 없다");
		Objects.requireNonNull(payload.getAskBid(), "askBid는 null일 수 없다");
		Objects.requireNonNull(payload.getPrevClosingPrice(), "prevClosingPrice는 null일 수 없다");
		Objects.requireNonNull(payload.getChange(), "change는 null일 수 없다");
		Objects.requireNonNull(payload.getChangePrice(), "changePrice는 null일 수 없다");
		Objects.requireNonNull(payload.getBestAskPrice(), "bestAskPrice는 null일 수 없다");
		Objects.requireNonNull(payload.getBestAskSize(), "bestAskSize는 null일 수 없다");
		Objects.requireNonNull(payload.getBestBidPrice(), "bestBidPrice는 null일 수 없다");
		Objects.requireNonNull(payload.getBestBidSize(), "bestBidSize는 null일 수 없다");
		Objects.requireNonNull(payload.getStreamType(), "streamType은 null일 수 없다");

		if (payload.getTimestamp() <= 0) {
			throw new IllegalArgumentException("timestamp는 양수여야 한다");
		}
		if (payload.getTradeTimestamp() <= 0) {
			throw new IllegalArgumentException("tradeTimestamp는 양수여야 한다");
		}
		if (payload.getSequentialId() <= 0) {
			throw new IllegalArgumentException("sequentialId는 양수여야 한다");
		}
	}
}
