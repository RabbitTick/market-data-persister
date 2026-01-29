package com.rabbittick.persister.messaging;

import java.io.IOException;
import java.nio.charset.StandardCharsets;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.annotation.RabbitListener;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.dao.DataIntegrityViolationException;
import org.springframework.stereotype.Component;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.rabbitmq.client.Channel;
import com.rabbittick.persister.domain.ticker.TickerService;
import com.rabbittick.persister.global.dto.MarketDataMessage;
import com.rabbittick.persister.global.dto.TickerPayload;

/**
 * RabbitMQ에서 시장 데이터 메시지를 소비하는 리스너.
 *
 * 주요 책임:
 *
 * 수신 메시지 역직렬화
 * 데이터 타입 분기 처리 (ticker만 허용)
 * DB 저장 처리 및 Ack/Nack 정책 적용
 * 예외 및 멱등성 처리 로그 기록
 */
@Component
public class MarketDataConsumer {
	private static final Logger log = LoggerFactory.getLogger(MarketDataConsumer.class);

	private final ObjectMapper objectMapper;
	private final TickerService tickerService;
	private final boolean nackRequeue;

	/**
	 * MarketDataConsumer 생성자.
	 *
	 * @param objectMapper JSON 변환기
	 * @param tickerService 티커 저장 서비스
	 * @param nackRequeue Nack 시 재큐잉 여부
	 */
	public MarketDataConsumer(
		ObjectMapper objectMapper,
		TickerService tickerService,
		@Value("${app.rabbitmq.nack-requeue:true}") boolean nackRequeue
	) {
		this.objectMapper = objectMapper;
		this.tickerService = tickerService;
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
		containerFactory = "rabbitListenerContainerFactory"
	)
	public void handleMessage(Message message, Channel channel) throws IOException {
		long deliveryTag = message.getMessageProperties().getDeliveryTag();
		String body = new String(message.getBody(), StandardCharsets.UTF_8);

		try {
			String normalizedBody = normalizeBody(body);
			MarketDataMessage<TickerPayload> marketDataMessage = objectMapper.readValue(
				normalizedBody,
				new TypeReference<MarketDataMessage<TickerPayload>>() {}
			);

			if (marketDataMessage.getMetadata() == null
				|| !isTickerType(marketDataMessage.getMetadata().getDataType())) {
				log.warn("지원하지 않는 dataType 입니다. messageBody={}", body);
				channel.basicAck(deliveryTag, false);
				return;
			}

			tickerService.saveTicker(marketDataMessage);
			channel.basicAck(deliveryTag, false);
		} catch (DataIntegrityViolationException ex) {
			log.warn("중복 티커 데이터로 판단되어 저장을 생략합니다. messageBody={}", body, ex);
			channel.basicAck(deliveryTag, false);
		} catch (Exception ex) {
			log.error("메시지 처리에 실패했습니다. messageBody={}", body, ex);
			channel.basicNack(deliveryTag, false, nackRequeue);
		}
	}

	private boolean isTickerType(String dataType) {
		return dataType != null && dataType.equalsIgnoreCase("TICKER");
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
