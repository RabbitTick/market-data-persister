package com.rabbittick.persister.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.rabbitmq.client.Channel;
import com.rabbittick.persister.domain.orderbook.OrderBookService;
import com.rabbittick.persister.domain.trade.TradeService;
import com.rabbittick.persister.domain.ticker.TickerService;
import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.OrderBookPayload;
import com.rabbittick.persister.global.dto.TickerPayload;
import com.rabbittick.persister.global.dto.TradePayload;

import io.micrometer.core.instrument.Counter;
import io.micrometer.core.instrument.DistributionSummary;
import io.micrometer.core.instrument.MeterRegistry;
import io.micrometer.core.instrument.Timer;

/**
 * RabbitMQ에서 시장 데이터 메시지를 소비하는 리스너.
 *
 * 주요 책임:
 *
 * 수신 메시지 역직렬화
 * 데이터 타입 분기 처리 (ticker/trade/orderbook)
 * DB 저장 처리 및 Ack/Nack 정책 적용
 * 예외 및 멱등성 처리 로그 기록
 */
@Component
public class MarketDataConsumer {

	private static final Logger log = LoggerFactory.getLogger(MarketDataConsumer.class);
	private static final String METRIC_MESSAGES = "market_data.messages";
	private static final String METRIC_PROCESS_LATENCY = "market_data.process.latency";
	private static final String METRIC_PERSIST_LATENCY = "market_data.persist.latency";
	private static final String METRIC_INGEST_LAG = "market_data.ingest.lag";
	private static final String METRIC_ACK = "market_data.ack";
	private static final String METRIC_NACK = "market_data.nack";

	private final ObjectMapper objectMapper;
	private final TickerService tickerService;
	private final TradeService tradeService;
	private final OrderBookService orderBookService;
	private final boolean nackRequeue;
	private final MeterRegistry meterRegistry;

	/**
	 * MarketDataConsumer 생성자.
	 *
	 * @param objectMapper JSON 변환기
	 * @param tickerService 티커 저장 서비스
	 * @param tradeService 거래 체결 저장 서비스
	 * @param orderBookService 호가 저장 서비스
	 * @param nackRequeue Nack 시 재큐잉 여부
	 */
	public MarketDataConsumer(
		ObjectMapper objectMapper,
		TickerService tickerService,
		TradeService tradeService,
		OrderBookService orderBookService,
		MeterRegistry meterRegistry,
		@Value("${app.rabbitmq.nack-requeue:true}") boolean nackRequeue
	) {
		this.objectMapper = objectMapper;
		this.tickerService = tickerService;
		this.tradeService = tradeService;
		this.orderBookService = orderBookService;
		this.meterRegistry = meterRegistry;
		this.nackRequeue = nackRequeue;
	}

	/**
	 * RabbitMQ 메시지를 수신하여 처리한다.
	 *
	 * @param message 수신 메시지
	 * @param channel RabbitMQ 채널
	 * @throws IOException 채널 Ack/Nack 실패 시
	 */
	@RabbitListener(
		queues = "${app.rabbitmq.queue}",
		containerFactory = "rabbitListenerContainerFactory",
		concurrency = "${app.rabbitmq.listener-concurrency:2-4}"
	)
	public void handleMarketDataMessage(Message message, Channel channel) throws IOException {
		long deliveryTag = message.getMessageProperties().getDeliveryTag();
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		Timer.Sample totalSample = Timer.start(meterRegistry);
		String messageType = "unknown";
		String outcome = "success";
		boolean acked = false;
		boolean nacked = false;
		Long ingestLagMs = null;

		try {
			String normalizedJson = normalizeBody(body);
			JsonNode rootNode = objectMapper.readTree(normalizedJson);
			messageType = extractDataType(rootNode);
			String messageTypeTag = normalizeDataType(messageType);
			ingestLagMs = extractIngestLagMs(rootNode);
			if (messageType == null) {
				outcome = "missing_type";
				log.warn("metadata.dataType이 누락되었습니다. messageBody={}", body);
				channel.basicAck(deliveryTag, false);
				acked = true;
				return;
			}

			if (isTickerType(messageType)) {
				MarketDataMessage<TickerPayload> marketDataMessage = objectMapper.readValue(
					normalizedJson,
					new TypeReference<MarketDataMessage<TickerPayload>>() {}
				);
				recordPersistLatency(messageTypeTag, () -> tickerService.saveTicker(marketDataMessage));
			} else if (isTradeType(messageType)) {
				MarketDataMessage<TradePayload> marketDataMessage = objectMapper.readValue(
					normalizedJson,
					new TypeReference<MarketDataMessage<TradePayload>>() {}
				);
				recordPersistLatency(messageTypeTag, () -> tradeService.saveTrade(marketDataMessage));
			} else if (isOrderBookType(messageType)) {
				MarketDataMessage<OrderBookPayload> marketDataMessage = objectMapper.readValue(
					normalizedJson,
					new TypeReference<MarketDataMessage<OrderBookPayload>>() {}
				);
				recordPersistLatency(messageTypeTag, () -> orderBookService.saveOrderBook(marketDataMessage));
			} else {
				outcome = "unsupported_type";
				log.warn("지원하지 않는 dataType 입니다. dataType={}, messageBody={}", messageType, body);
				channel.basicAck(deliveryTag, false);
				acked = true;
				return;
			}

			channel.basicAck(deliveryTag, false);
			acked = true;
		} catch (DataIntegrityViolationException ex) {
			outcome = "duplicate";
			log.warn("중복 데이터로 판단되어 저장을 생략합니다. messageBody={}", body, ex);
			channel.basicAck(deliveryTag, false);
			acked = true;
		} catch (Exception ex) {
			outcome = "error";
			log.error("메시지 처리에 실패했습니다. messageBody={}", body, ex);
			channel.basicNack(deliveryTag, false, nackRequeue);
			nacked = true;
		} finally {
			String messageTypeTag = normalizeDataType(messageType);
			recordProcessingMetrics(messageTypeTag, outcome, totalSample, acked, nacked);
			recordIngestLag(messageTypeTag, ingestLagMs);
		}
	}

	private boolean isTickerType(String dataType) {
		return dataType != null && dataType.equalsIgnoreCase("TICKER");
	}

	/**
	 * trade 타입 여부를 확인한다.
	 *
	 * @param dataType 데이터 타입
	 * @return trade 타입 여부
	 */
	private boolean isTradeType(String dataType) {
		return dataType != null && dataType.equalsIgnoreCase("TRADE");
	}

	/**
	 * orderbook 타입 여부를 확인한다.
	 *
	 * @param dataType 데이터 타입
	 * @return orderbook 타입 여부
	 */
	private boolean isOrderBookType(String dataType) {
		return dataType != null && dataType.equalsIgnoreCase("ORDERBOOK");
	}

	/**
	 * metadata에서 dataType을 추출한다.
	 *
	 * @param rootNode 메시지 루트 노드
	 * @return dataType 문자열 (없으면 null)
	 */
	private String extractDataType(JsonNode rootNode) {
		JsonNode metadataNode = rootNode.get("metadata");
		if (metadataNode == null) {
			return null;
		}
		JsonNode dataTypeNode = metadataNode.get("dataType");
		if (dataTypeNode == null || dataTypeNode.isNull()) {
			return null;
		}
		return dataTypeNode.asText();
	}

	private String normalizeDataType(String dataType) {
		if (dataType == null || dataType.isBlank()) {
			return "unknown";
		}
		return dataType.trim().toLowerCase();
	}

	private Long extractIngestLagMs(JsonNode rootNode) {
		if (rootNode == null) {
			return null;
		}
		JsonNode metadataNode = rootNode.get("metadata");
		if (metadataNode == null) {
			return null;
		}
		JsonNode collectedAtNode = metadataNode.get("collectedAt");
		if (collectedAtNode == null || collectedAtNode.isNull()) {
			return null;
		}
		String collectedAt = collectedAtNode.asText();
		if (collectedAt == null || collectedAt.isBlank()) {
			return null;
		}
		try {
			Instant collectedAtInstant = Instant.parse(collectedAt);
			long lagMs = Duration.between(collectedAtInstant, Instant.now()).toMillis();
			return Math.max(lagMs, 0);
		} catch (DateTimeParseException ex) {
			return null;
		}
	}

	private void recordPersistLatency(String messageTypeTag, Runnable persistence) {
		Timer.Sample persistSample = Timer.start(meterRegistry);
		try {
			persistence.run();
			persistSample.stop(persistTimer(messageTypeTag, "success"));
		} catch (DataIntegrityViolationException ex) {
			persistSample.stop(persistTimer(messageTypeTag, "duplicate"));
			throw ex;
		} catch (RuntimeException ex) {
			persistSample.stop(persistTimer(messageTypeTag, "error"));
			throw ex;
		}
	}

	private void recordProcessingMetrics(
		String messageTypeTag,
		String outcome,
		Timer.Sample totalSample,
		boolean acked,
		boolean nacked
	) {
		totalSample.stop(processTimer(messageTypeTag, outcome));
		Counter.builder(METRIC_MESSAGES)
			.description("Messages processed by consumer")
			.tags("dataType", messageTypeTag, "outcome", outcome)
			.register(meterRegistry)
			.increment();
		if (acked) {
			Counter.builder(METRIC_ACK)
				.description("Messages acked by consumer")
				.tags("dataType", messageTypeTag, "outcome", outcome)
				.register(meterRegistry)
				.increment();
		}
		if (nacked) {
			Counter.builder(METRIC_NACK)
				.description("Messages nacked by consumer")
				.tags("dataType", messageTypeTag, "outcome", outcome)
				.register(meterRegistry)
				.increment();
		}
	}

	private void recordIngestLag(String messageTypeTag, Long ingestLagMs) {
		if (ingestLagMs == null) {
			return;
		}
		DistributionSummary.builder(METRIC_INGEST_LAG)
			.baseUnit("milliseconds")
			.description("Lag between collection time and consumer time")
			.tags("dataType", messageTypeTag)
			.register(meterRegistry)
			.record(ingestLagMs);
	}

	private Timer processTimer(String messageTypeTag, String outcome) {
		return Timer.builder(METRIC_PROCESS_LATENCY)
			.description("End-to-end processing latency in consumer")
			.tags("dataType", messageTypeTag, "outcome", outcome)
			.register(meterRegistry);
	}

	private Timer persistTimer(String messageTypeTag, String outcome) {
		return Timer.builder(METRIC_PERSIST_LATENCY)
			.description("DB persistence latency in consumer")
			.tags("dataType", messageTypeTag, "outcome", outcome)
			.register(meterRegistry);
	}

	/**
	 * 메시지 본문을 정규화한다.
	 *
	 * JSON 문자열이 따옴표로 감싸져 들어온 경우 한 번 더 역직렬화하여 실제 JSON을 얻는다.
	 *
	 * @param body 원본 본문
	 * @return 정규화된 JSON 문자열
	 * @throws IOException 역직렬화 실패 시
	 */
	private String normalizeBody(String body) throws IOException {
		String trimmed = body.trim();
		if (trimmed.startsWith("\"") && trimmed.endsWith("\"")) {
			return objectMapper.readValue(trimmed, String.class);
		}
		return body;
	}
}
