package com.rabbittick.persister.domain.trade;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.Metadata;
import com.rabbittick.persister.global.dto.TradePayload;

class TradeMapperTest {
	
	private final TradeMapper mapper = new TradeMapper();

	@Test
	void toEntity_mapsTradeFields() {
		// given
		Metadata metadata = Metadata.builder()
			.messageId("trade-message-id")
			.exchange("UPBIT")
			.dataType("TRADE")
			.collectedAt("2025-08-28T16:49:00.123Z")
			.version("1.0")
			.build();

		TradePayload payload = TradePayload.builder()
			.marketCode("KRW-BTC")
			.timestamp(1672531200000L)
			.tradeDate("2025-08-28")
			.tradeTime("16:49:00")
			.tradeTimestamp(1672531200000L)
			.tradePrice(new BigDecimal("70000000.00"))
			.tradeVolume(new BigDecimal("0.0012"))
			.askBid("ASK")
			.prevClosingPrice(new BigDecimal("69500000.00"))
			.change("EVEN")
			.changePrice(new BigDecimal("0.00"))
			.sequentialId(1000L)
			.bestAskPrice(new BigDecimal("70010000.00"))
			.bestAskSize(new BigDecimal("1.0"))
			.bestBidPrice(new BigDecimal("69990000.00"))
			.bestBidSize(new BigDecimal("1.2"))
			.streamType("SNAPSHOT")
			.build();

		MarketDataMessage<TradePayload> message = new MarketDataMessage<>(metadata, payload);

		// when
		Trade trade = mapper.toEntity(message);

		// then
		assertThat(trade.getExchange()).isEqualTo("UPBIT");
		assertThat(trade.getMarketCode()).isEqualTo("KRW-BTC");
		assertThat(trade.getTimestamp()).isEqualTo(1672531200000L);
		assertThat(trade.getTradeDate()).isEqualTo("2025-08-28");
		assertThat(trade.getTradeTime()).isEqualTo("16:49:00");
		assertThat(trade.getTradeTimestamp()).isEqualTo(1672531200000L);
		assertThat(trade.getTradePrice()).isEqualByComparingTo("70000000.00");
		assertThat(trade.getTradeVolume()).isEqualByComparingTo("0.0012");
		assertThat(trade.getAskBid()).isEqualTo("ASK");
		assertThat(trade.getPrevClosingPrice()).isEqualByComparingTo("69500000.00");
		assertThat(trade.getChange()).isEqualTo("EVEN");
		assertThat(trade.getChangePrice()).isEqualByComparingTo("0.00");
		assertThat(trade.getSequentialId()).isEqualTo(1000L);
		assertThat(trade.getBestAskPrice()).isEqualByComparingTo("70010000.00");
		assertThat(trade.getBestAskSize()).isEqualByComparingTo("1.0");
		assertThat(trade.getBestBidPrice()).isEqualByComparingTo("69990000.00");
		assertThat(trade.getBestBidSize()).isEqualByComparingTo("1.2");
		assertThat(trade.getStreamType()).isEqualTo("SNAPSHOT");
	}
}
