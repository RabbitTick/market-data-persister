package com.rabbittick.persister.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.time.Duration;
import java.time.Instant;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
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
 * 데이터타입별 큐 분리에 따른 전용 컨슈머 메서드 제공
 *   ticker.queue    → handleTickerMessage    (tickerContainerFactory)
 *   trade.queue     → handleTradeMessage     (tradeContainerFactory)
 *   orderbook.queue → handleOrderBookMessage (orderBookContainerFactory)
 * 수신 메시지 역직렬화
 * DB 저장 처리 및 Ack/Nack 정책 적용
 * 예외 및 멱등성 처리 로그 기록
 */
@Component
public class MarketDataConsumer {

	private static final Logger log = LoggerFactory.getLogger(MarketDataConsumer.class);
	private static final String METRIC_MESSAGES = "market_data.messages";
	private static final String METRIC_PROCESS_LATENCY = "market_data.process.latency";
	private static final String METRIC_PERSIST_LATENCY = "market_data.persist.latency";
	private static final String METRIC_PARSE_LATENCY = "market_data.segment.parse.latency";
	private static final String METRIC_COMMIT_LATENCY = "market_data.segment.commit.latency";
	private static final String METRIC_INGEST_LAG = "market_data.ingest.lag";
	private static final String METRIC_ACK = "market_data.ack";
	private static final String METRIC_NACK = "market_data.nack";

	private final ObjectMapper objectMapper;
	private final TickerService tickerService;
	private final TradeService tradeService;
	private final OrderBookService orderBookService;
	private final MeterRegistry meterRegistry;
    private final RabbitTemplate rabbitTemplate;
    private final String dlqExchangeName;
    private final String dlqRoutingKey;

	/**
	 * MarketDataConsumer 생성자.
	 *
	 * @param objectMapper JSON 변환기
	 * @param tickerService 티커 저장 서비스
	 * @param tradeService 거래 체결 저장 서비스
	 * @param orderBookService 호가 저장 서비스
	 * @param meterRegistry 메트릭 레지스트리
	 */
	public MarketDataConsumer(
		ObjectMapper objectMapper,
		TickerService tickerService,
		TradeService tradeService,
		OrderBookService orderBookService,
		MeterRegistry meterRegistry,
        RabbitTemplate rabbitTemplate,
        @Value("${app.rabbitmq.dlq-exchange}") String dlqExchangeName,
        @Value("${app.rabbitmq.dlq-routing-key}") String dlqRoutingKey
	) {
		this.objectMapper = objectMapper;
		this.tickerService = tickerService;
		this.tradeService = tradeService;
		this.orderBookService = orderBookService;
		this.meterRegistry = meterRegistry;
        this.rabbitTemplate = rabbitTemplate;
        this.dlqExchangeName = dlqExchangeName;
        this.dlqRoutingKey = dlqRoutingKey;
	}

	/**
	 * ticker.queue 전용 컨슈머.
	 *
	 * @param message 수신 메시지
	 * @param channel RabbitMQ 채널
	 * @throws IOException 채널 Ack/Nack 실패 시
	 */
	@RabbitListener(
		queues = "ticker.queue",
		containerFactory = "tickerContainerFactory"
	)
	public void handleTickerMessage(Message message, Channel channel) throws IOException {
		processMessage(message, channel, "ticker", body -> {
			MarketDataMessage<TickerPayload> msg = objectMapper.readValue(
				body,
				new TypeReference<MarketDataMessage<TickerPayload>>() {}
			);
			tickerService.saveTicker(msg);
		});
	}

	/**
	 * trade.queue 전용 컨슈머.
	 *
	 * @param message 수신 메시지
	 * @param channel RabbitMQ 채널
	 * @throws IOException 채널 Ack/Nack 실패 시
	 */
	@RabbitListener(
		queues = "trade.queue",
		containerFactory = "tradeContainerFactory"
	)
	public void handleTradeMessage(Message message, Channel channel) throws IOException {
		processMessage(message, channel, "trade", body -> {
			MarketDataMessage<TradePayload> msg = objectMapper.readValue(
				body,
				new TypeReference<MarketDataMessage<TradePayload>>() {}
			);
			tradeService.saveTrade(msg);
		});
	}

    /**
     * orderbook.queue 배치 컨슈머.
     *
     * <p>컨테이너가 prefetch로 미리 가져온 메시지를 최대 batchSize건 모아서 호출한다.
     * burst 구간에서는 batchSize에 빠르게 도달하고, 평상시에는 receiveTimeout(1초)
     * 이후 그 시점까지 수신된 건수로 호출된다.
     *
     * <p>재시도 어드바이스를 사용하지 않으므로 파싱 실패·저장 실패를 메서드 내에서 처리한다:
     * <ul>
     *   <li>파싱 실패한 건: DLQ로 개별 발행 후 Ack (배치 전체를 막지 않음)</li>
     *   <li>저장 실패(DataIntegrityViolation): 중복으로 간주, Ack</li>
     *   <li>저장 실패(그 외): 전체 메시지를 DLQ로 발행 후 Ack (무한 Nack 루프 방지)</li>
     * </ul>
     *
     * @param messages 컨테이너가 모은 메시지 묶음
     * @param channel RabbitMQ 채널
     * @throws IOException basicAck 실패 시
     */
    @RabbitListener(
            queues = "orderbook.queue",
            containerFactory = "orderBookContainerFactory"
    )
    public void handleOrderBookMessage(List<Message> messages, Channel channel) throws IOException {
        if (messages.isEmpty()) {
            return;
        }

        List<OrderBookPayload> payloads = new ArrayList<>();
        long lastDeliveryTag = 0;
        String exchange = "UPBIT"; // 파싱 실패 시 fallback용 기본값

        for (Message message : messages) {
            lastDeliveryTag = message.getMessageProperties().getDeliveryTag();
            String body = new String(message.getBody(), StandardCharsets.UTF_8);

            try {
                String normalizedJson = normalizeBody(body);
                MarketDataMessage<OrderBookPayload> msg = objectMapper.readValue(
                        normalizedJson,
                        new TypeReference<MarketDataMessage<OrderBookPayload>>() {}
                );
                if (payloads.isEmpty()) {
                    exchange = msg.getMetadata().getExchange();
                }
                payloads.add(msg.getPayload());
            } catch (Exception e) {
                log.warn("[배치] OrderBook 파싱 실패, DLQ 발행 후 계속 처리. body={}", body, e);
                publishToDlq(message, e);
                recordBatchOutcome("orderbook", "parse_error");
            }
        }

        if (!payloads.isEmpty()) {
            Timer.Sample persistSample = Timer.start(meterRegistry);
            try {
                orderBookService.saveOrderBookBatch(exchange, payloads);
                persistSample.stop(Timer.builder(METRIC_PERSIST_LATENCY)
                        .tags("dataType", "orderbook", "outcome", "success")
                        .register(meterRegistry));
                recordBatchOutcome("orderbook", "success");
                log.debug("[배치] OrderBook {}건 저장 완료", payloads.size());
            } catch (DataIntegrityViolationException e) {
                persistSample.stop(Timer.builder(METRIC_PERSIST_LATENCY)
                        .tags("dataType", "orderbook", "outcome", "duplicate")
                        .register(meterRegistry));
                log.warn("[배치] OrderBook 배치 내 중복 데이터. size={}", payloads.size(), e);
                recordBatchOutcome("orderbook", "duplicate");
            } catch (Exception e) {
                persistSample.stop(Timer.builder(METRIC_PERSIST_LATENCY)
                        .tags("dataType", "orderbook", "outcome", "error")
                        .register(meterRegistry));
                log.error("[배치] OrderBook 배치 저장 실패. size={}. DLQ로 발행.", payloads.size(), e);
                for (Message message : messages) {
                    publishToDlq(message, e);
                }
                recordBatchOutcome("orderbook", "batch_error");
            }
        }

        channel.basicAck(lastDeliveryTag, true);
    }

    /**
     * 처리 불가 메시지를 DLQ로 발행한다.
     * 배치 리스너는 RetryInterceptor를 사용하지 않으므로 직접 발행한다.
     *
     * @param message 원본 메시지
     * @param cause 실패 원인
     */
    private void publishToDlq(Message message, Throwable cause) {
        try {
            rabbitTemplate.send(dlqExchangeName, dlqRoutingKey, message);
        } catch (Exception e) {
            log.error("[배치] DLQ 발행 실패. deliveryTag={}",
                    message.getMessageProperties().getDeliveryTag(), e);
        }
    }

    /**
     * 배치 처리 결과를 메트릭으로 기록한다.
     */
    private void recordBatchOutcome(String dataType, String outcome) {
        Counter.builder(METRIC_MESSAGES)
                .description("Messages processed by consumer")
                .tags("dataType", dataType, "outcome", outcome)
                .register(meterRegistry)
                .increment();
    }

	/**
	 * 세 컨슈머가 공유하는 공통 처리 흐름.
	 * 파싱 → 저장 → Ack / 예외 시 throw → RetryInterceptor → DLQ
	 *
	 * @param message 수신 메시지
	 * @param channel RabbitMQ 채널
	 * @param messageType 데이터 타입 문자열 (메트릭 태그용)
	 * @param persistence 실제 저장 로직 (람다)
	 * @throws IOException 채널 Ack/Nack 실패 시
	 */
	private void processMessage(
		Message message,
		Channel channel,
		String messageType,
		MessageProcessor persistence
	) throws IOException {
		long deliveryTag = message.getMessageProperties().getDeliveryTag();
		String body = new String(message.getBody(), StandardCharsets.UTF_8);
		Timer.Sample totalSample = Timer.start(meterRegistry);
		Timer.Sample parseSample = Timer.start(meterRegistry);
		String outcome = "success";
		boolean acked = false;
		boolean nacked = false;
		Long ingestLagMs = null;
		Timer.Sample commitSample = null;

		try {
			String normalizedJson = normalizeBody(body);
			JsonNode rootNode = objectMapper.readTree(normalizedJson);
			ingestLagMs = extractIngestLagMs(rootNode);
			parseSample.stop(parseTimer(messageType, outcome));
			recordPersistLatency(messageType, () -> {
				try {
					persistence.process(normalizedJson);
				} catch (IOException e) {
					throw new RuntimeException(e);
				}
			});
			commitSample = Timer.start(meterRegistry);

			channel.basicAck(deliveryTag, false);
			if (commitSample != null) {
				commitSample.stop(commitTimer(messageType, outcome));
			}
			acked = true;
		} catch (DataIntegrityViolationException ex) {
			outcome = "duplicate";
			log.warn("중복 데이터로 판단되어 저장을 생략합니다. dataType={}, messageBody={}", messageType, body, ex);
			channel.basicAck(deliveryTag, false);
			acked = true;
		} catch (Exception ex) {
			outcome = "error";
			log.error("메시지 처리에 실패했습니다. dataType={}, messageBody={}", messageType, body, ex);
			throw new RuntimeException(ex);
		} finally {
			recordProcessingMetrics(messageType, outcome, totalSample, acked, nacked);
			recordIngestLag(messageType, ingestLagMs);
		}
	}

	/**
	 * 저장 로직을 함수형 인터페이스로 추상화한다.
	 * 각 컨슈머의 람다가 이를 구현한다.
	 */
	@FunctionalInterface
	private interface MessageProcessor {
		void process(String normalizedJson) throws IOException;
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

	private void recordPersistLatency(String messageType, Runnable persistence) {
		Timer.Sample persistSample = Timer.start(meterRegistry);
		try {
			persistence.run();
			persistSample.stop(persistTimer(messageType, "success"));
		} catch (DataIntegrityViolationException ex) {
			persistSample.stop(persistTimer(messageType, "duplicate"));
			throw ex;
		} catch (RuntimeException ex) {
			persistSample.stop(persistTimer(messageType, "error"));
			throw ex;
		}
	}

	private void recordProcessingMetrics(
		String messageType,
		String outcome,
		Timer.Sample totalSample,
		boolean acked,
		boolean nacked
	) {
		totalSample.stop(processTimer(messageType, outcome));
		Counter.builder(METRIC_MESSAGES)
			.description("Messages processed by consumer")
			.tags("dataType", messageType, "outcome", outcome)
			.register(meterRegistry)
			.increment();
		if (acked) {
			Counter.builder(METRIC_ACK)
				.description("Messages acked by consumer")
				.tags("dataType", messageType, "outcome", outcome)
				.register(meterRegistry)
				.increment();
		}
		if (nacked) {
			Counter.builder(METRIC_NACK)
				.description("Messages nacked by consumer")
				.tags("dataType", messageType, "outcome", outcome)
				.register(meterRegistry)
				.increment();
		}
	}

	private void recordIngestLag(String messageType, Long ingestLagMs) {
		if (ingestLagMs == null) {
			return;
		}
		DistributionSummary.builder(METRIC_INGEST_LAG)
			.baseUnit("milliseconds")
			.description("Lag between collection time and consumer time")
			.tags("dataType", messageType)
			.register(meterRegistry)
			.record(ingestLagMs);
	}

	private Timer processTimer(String messageType, String outcome) {
		return Timer.builder(METRIC_PROCESS_LATENCY)
			.description("End-to-end processing latency in consumer")
			.tags("dataType", messageType, "outcome", outcome)
			.register(meterRegistry);
	}

	private Timer persistTimer(String messageType, String outcome) {
		return Timer.builder(METRIC_PERSIST_LATENCY)
			.description("DB persistence latency in consumer")
			.tags("dataType", messageType, "outcome", outcome)
			.register(meterRegistry);
	}

	private Timer parseTimer(String messageType, String outcome) {
		return Timer.builder(METRIC_PARSE_LATENCY)
			.description("Parse segment: receive to parse complete")
			.tags("dataType", messageType, "outcome", outcome)
			.register(meterRegistry);
	}

	private Timer commitTimer(String messageType, String outcome) {
		return Timer.builder(METRIC_COMMIT_LATENCY)
			.description("Commit segment: persist complete to broker ack")
			.tags("dataType", messageType, "outcome", outcome)
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
