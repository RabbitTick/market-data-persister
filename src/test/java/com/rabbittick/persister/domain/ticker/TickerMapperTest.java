package com.rabbittick.persister.domain.ticker;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;

import org.junit.jupiter.api.Test;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.Metadata;
import com.rabbittick.persister.global.dto.TickerPayload;

class TickerMapperTest {
	
	private final TickerMapper mapper = new TickerMapper();

	@Test
	void toEntity_mapsTickerFields() {
		// given
		Metadata metadata = Metadata.builder()
			.messageId("message-id")
			.exchange("UPBIT")
			.dataType("TICKER")
			.collectedAt("2025-08-28T16:49:00.123Z")
			.version("1.0")
			.build();

		TickerPayload payload = TickerPayload.builder()
			.marketCode("KRW-BTC")
			.tradePrice(new BigDecimal("70000000.00"))
			.tradeVolume(new BigDecimal("0.0012"))
			.openingPrice(new BigDecimal("69000000.00"))
			.highPrice(new BigDecimal("71000000.00"))
			.lowPrice(new BigDecimal("68000000.00"))
			.prevClosingPrice(new BigDecimal("69500000.00"))
			.accTradePrice24h(new BigDecimal("1234567890.123"))
			.accTradeVolume24h(new BigDecimal("123.456"))
			.timestamp(1672531200000L)
			.build();

		MarketDataMessage<TickerPayload> message = new MarketDataMessage<>(metadata, payload);

		// when
		Ticker ticker = mapper.toEntity(message);

		// then
		assertThat(ticker.getExchange()).isEqualTo("UPBIT");
		assertThat(ticker.getMarketCode()).isEqualTo("KRW-BTC");
		assertThat(ticker.getTradePrice()).isEqualByComparingTo("70000000.00");
		assertThat(ticker.getTradeVolume()).isEqualByComparingTo("0.0012");
		assertThat(ticker.getOpeningPrice()).isEqualByComparingTo("69000000.00");
		assertThat(ticker.getHighPrice()).isEqualByComparingTo("71000000.00");
		assertThat(ticker.getLowPrice()).isEqualByComparingTo("68000000.00");
		assertThat(ticker.getPrevClosingPrice()).isEqualByComparingTo("69500000.00");
		assertThat(ticker.getAccTradePrice24h()).isEqualByComparingTo("1234567890.123");
		assertThat(ticker.getAccTradeVolume24h()).isEqualByComparingTo("123.456");
		assertThat(ticker.getTimestamp()).isEqualTo(1672531200000L);
	}
}
