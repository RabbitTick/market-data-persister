package com.rabbittick.persister.domain.orderbook;

import static org.assertj.core.api.Assertions.assertThat;

import java.math.BigDecimal;
import java.util.List;

import org.junit.jupiter.api.Test;

import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.Metadata;
import com.rabbittick.persister.global.dto.OrderBookPayload;
import com.rabbittick.persister.global.dto.OrderBookUnitPayload;

class OrderBookMapperTest {
	
	private final OrderBookMapper mapper = new OrderBookMapper();

	@Test
	void toEntity_mapsOrderBookFields() {
		// given
		Metadata metadata = Metadata.builder()
			.messageId("orderbook-message-id")
			.exchange("UPBIT")
			.dataType("ORDERBOOK")
			.collectedAt("2025-08-28T16:49:00.123Z")
			.version("1.0")
			.build();

		List<OrderBookUnitPayload> units = List.of(
			OrderBookUnitPayload.builder()
				.askPrice(new BigDecimal("70010000.00"))
				.askSize(new BigDecimal("1.0"))
				.bidPrice(new BigDecimal("69990000.00"))
				.bidSize(new BigDecimal("1.2"))
				.build(),
			OrderBookUnitPayload.builder()
				.askPrice(new BigDecimal("70020000.00"))
				.askSize(new BigDecimal("0.8"))
				.bidPrice(new BigDecimal("69980000.00"))
				.bidSize(new BigDecimal("1.1"))
				.build()
		);

		OrderBookPayload payload = OrderBookPayload.builder()
			.marketCode("KRW-BTC")
			.timestamp(1672531200000L)
			.totalAskSize(new BigDecimal("10.5"))
			.totalBidSize(new BigDecimal("9.8"))
			.orderbookUnits(units)
			.build();

		MarketDataMessage<OrderBookPayload> message = new MarketDataMessage<>(metadata, payload);

		// when
		OrderBook orderBook = mapper.toEntity(message);

		// then
		assertThat(orderBook.getExchange()).isEqualTo("UPBIT");
		assertThat(orderBook.getMarketCode()).isEqualTo("KRW-BTC");
		assertThat(orderBook.getTimestamp()).isEqualTo(1672531200000L);
		assertThat(orderBook.getTotalAskSize()).isEqualByComparingTo("10.5");
		assertThat(orderBook.getTotalBidSize()).isEqualByComparingTo("9.8");
		assertThat(orderBook.getOrderbookUnits()).hasSize(2);
		assertThat(orderBook.getOrderbookUnits().get(0).getAskPrice()).isEqualByComparingTo("70010000.00");
		assertThat(orderBook.getOrderbookUnits().get(0).getBidPrice()).isEqualByComparingTo("69990000.00");
	}
}
