package com.rabbittick.persister.messaging;

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
import com.rabbittick.persister.domain.ticker.TickerService;
import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.Metadata;
import com.rabbittick.persister.global.dto.TickerPayload;

@ExtendWith(MockitoExtension.class)
class MarketDataConsumerTest {
	
	@Mock
	private TickerService tickerService;

	@Mock
	private Channel channel;

	private ObjectMapper objectMapper;

	private MarketDataConsumer consumer;

	@BeforeEach
	void setUp() {
		objectMapper = new ObjectMapper().findAndRegisterModules();
		consumer = new MarketDataConsumer(objectMapper, tickerService, true);
	}

	@Test
	void handleMessage_ackOnSuccess() throws Exception {
		// given
		Message message = buildMessage(validTickerMessage("TICKER"), 1L);

		// when
		consumer.handleMessage(message, channel);

		// then
		verify(tickerService).saveTicker(any());
		verify(channel).basicAck(1L, false);
	}

	@Test
	void handleMessage_ackOnDuplicate() throws Exception {
		// given
		Message message = buildMessage(validTickerMessage("TICKER"), 2L);
		doThrow(new DataIntegrityViolationException("duplicate"))
			.when(tickerService).saveTicker(any());

		// when
		consumer.handleMessage(message, channel);

		// then
		verify(channel).basicAck(2L, false);
	}

	@Test
	void handleMessage_ackOnUnsupportedType() throws Exception {
		// given
		Message message = buildMessage(validTickerMessage("TRADE"), 3L);

		// when
		consumer.handleMessage(message, channel);

		// then
		verify(tickerService, never()).saveTicker(any());
		verify(channel).basicAck(3L, false);
	}

	@Test
	void handleMessage_nackOnInvalidJson() throws Exception {
		// given
		Message message = buildRawMessage("{invalid-json", 4L);

		// when
		consumer.handleMessage(message, channel);

		// then
		verify(channel).basicNack(4L, false, true);
	}

	@Test
	void handleMessage_acceptsStringWrappedJson() throws Exception {
		// given
		String json = objectMapper.writeValueAsString(validTickerMessage("TICKER"));
		String wrapped = objectMapper.writeValueAsString(json);
		Message message = buildRawMessage(wrapped, 5L);

		// when
		consumer.handleMessage(message, channel);

		// then
		verify(tickerService).saveTicker(any());
		verify(channel).basicAck(5L, false);
	}

	private MarketDataMessage<TickerPayload> validTickerMessage(String dataType) {
		Metadata metadata = Metadata.builder()
			.messageId("message-id")
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

	private Message buildMessage(Object value, long deliveryTag) throws Exception {
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
