package com.rabbittick.persister.messaging;

import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.doThrow;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;

import java.math.BigDecimal;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.core.MessageProperties;
import org.springframework.dao.DataIntegrityViolationException;

import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import java.util.List;

import com.rabbittick.persister.domain.orderbook.OrderBookService;
import com.rabbittick.persister.domain.trade.TradeService;
import com.rabbittick.persister.domain.ticker.TickerService;
import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.Metadata;
import com.rabbittick.persister.global.dto.OrderBookPayload;
import com.rabbittick.persister.global.dto.OrderBookUnitPayload;
import com.rabbittick.persister.global.dto.TickerPayload;
import com.rabbittick.persister.global.dto.TradePayload;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.simple.SimpleMeterRegistry;

@ExtendWith(MockitoExtension.class)
class MarketDataConsumerTest {
	
	@Mock
	private TickerService tickerService;

	@Mock
	private TradeService tradeService;

	@Mock
	private OrderBookService orderBookService;

	@Mock
	private Channel channel;

	private ObjectMapper objectMapper;

	private MarketDataConsumer consumer;
	
	private MeterRegistry meterRegistry;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper().findAndRegisterModules();
		meterRegistry = new SimpleMeterRegistry();
		consumer = new MarketDataConsumer(
			objectMapper,
			tickerService,
			tradeService,
			orderBookService,
			meterRegistry
		);
	}

	@Test
	void handleMessage_ackOnTicker() throws Exception {
		// given
		Message message = buildJsonMessage(buildTickerMessage("TICKER"), 1L);

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(tickerService).saveTicker(any());
		verify(channel).basicAck(1L, false);
	}

	@Test
	void handleMessage_ackOnTrade() throws Exception {
		// given
		Message message = buildJsonMessage(buildTradeMessage("TRADE"), 6L);

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(tradeService).saveTrade(any());
		verify(channel).basicAck(6L, false);
	}

	@Test
	void handleMessage_ackOnOrderBook() throws Exception {
		// given
		Message message = buildJsonMessage(buildOrderBookMessage("ORDERBOOK"), 11L);

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(orderBookService).saveOrderBook(any());
		verify(channel).basicAck(11L, false);
	}

	@Test
	void handleMessage_ackOnDuplicateTicker() throws Exception {
		// given
		Message message = buildJsonMessage(buildTickerMessage("TICKER"), 2L);
		doThrow(new DataIntegrityViolationException("duplicate"))
			.when(tickerService).saveTicker(any());

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(channel).basicAck(2L, false);
	}

	@Test
	void handleMessage_ackOnDuplicateTrade() throws Exception {
		// given
		Message message = buildJsonMessage(buildTradeMessage("TRADE"), 8L);
		doThrow(new DataIntegrityViolationException("duplicate"))
			.when(tradeService).saveTrade(any());

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(channel).basicAck(8L, false);
	}

	@Test
	void handleMessage_ackOnDuplicateOrderBook() throws Exception {
		// given
		Message message = buildJsonMessage(buildOrderBookMessage("ORDERBOOK"), 12L);
		doThrow(new DataIntegrityViolationException("duplicate"))
			.when(orderBookService).saveOrderBook(any());

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(channel).basicAck(12L, false);
	}

	@Test
	void handleMessage_ackOnUnsupportedType() throws Exception {
		// given
		Message message = buildJsonMessage(buildTickerMessage("UNKNOWN"), 9L);

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(tickerService, never()).saveTicker(any());
		verify(channel).basicAck(9L, false);
	}

	@Test
	void handleMessage_ackOnMissingDataType() throws Exception {
		// given
		Message message = buildJsonMessage(buildTickerMessage(null), 10L);

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(channel).basicAck(10L, false);
	}

	@Test
	void handleMessage_throwsOnInvalidJson_forRetryAndDlq() {
		// given
		Message message = buildRawMessage("{invalid-json", 4L);

		// when & then
		assertThatThrownBy(() -> consumer.handleMarketDataMessage(message, channel))
			.isInstanceOf(RuntimeException.class);
	}

	@Test
	void handleMessage_acceptsStringWrappedJsonForTicker() throws Exception {
		// given
		String json = objectMapper.writeValueAsString(buildTickerMessage("TICKER"));
		String wrapped = objectMapper.writeValueAsString(json);
		Message message = buildRawMessage(wrapped, 5L);

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(tickerService).saveTicker(any());
		verify(channel).basicAck(5L, false);
	}

	@Test
	void handleMessage_acceptsStringWrappedJsonForTrade() throws Exception {
		// given
		String json = objectMapper.writeValueAsString(buildTradeMessage("TRADE"));
		String wrapped = objectMapper.writeValueAsString(json);
		Message message = buildRawMessage(wrapped, 7L);

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(tradeService).saveTrade(any());
		verify(channel).basicAck(7L, false);
	}

	@Test
	void handleMessage_acceptsStringWrappedJsonForOrderBook() throws Exception {
		// given
		String json = objectMapper.writeValueAsString(buildOrderBookMessage("ORDERBOOK"));
		String wrapped = objectMapper.writeValueAsString(json);
		Message message = buildRawMessage(wrapped, 13L);

		// when
		consumer.handleMarketDataMessage(message, channel);

		// then
		verify(orderBookService).saveOrderBook(any());
		verify(channel).basicAck(13L, false);
	}

	private MarketDataMessage<TickerPayload> buildTickerMessage(String dataType) {
		Metadata metadata = Metadata.builder()
			.messageId("ticker-message-id")
			.exchange("UPBIT")
			.dataType(dataType)
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

		return new MarketDataMessage<>(metadata, payload);
	}

	private MarketDataMessage<TradePayload> buildTradeMessage(String dataType) {
		Metadata metadata = Metadata.builder()
			.messageId("trade-message-id")
			.exchange("UPBIT")
			.dataType(dataType)
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

		return new MarketDataMessage<>(metadata, payload);
	}

	private MarketDataMessage<OrderBookPayload> buildOrderBookMessage(String dataType) {
		Metadata metadata = Metadata.builder()
			.messageId("orderbook-message-id")
			.exchange("UPBIT")
			.dataType(dataType)
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

		return new MarketDataMessage<>(metadata, payload);
	}

	private Message buildJsonMessage(Object value, long deliveryTag) throws Exception {
		byte[] body = objectMapper.writeValueAsBytes(value);
		MessageProperties properties = new MessageProperties();
		properties.setDeliveryTag(deliveryTag);
		return new Message(body, properties);
	}

	private Message buildRawMessage(String body, long deliveryTag) {
		MessageProperties properties = new MessageProperties();
		properties.setDeliveryTag(deliveryTag);
		return new Message(body.getBytes(), properties);
	}
}
