package com.rabbittick.persister.config;

import java.io.IOException;
import java.util.Map;

import com.rabbittick.persister.messaging.AcknowledgingRepublishMessageRecoverer;
import org.aopalliance.aop.Advice;
import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.DirectExchange;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.amqp.rabbit.core.RabbitTemplate;
import org.springframework.amqp.rabbit.config.RetryInterceptorBuilder;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.dao.DataAccessException;
import org.springframework.retry.policy.SimpleRetryPolicy;

import java.time.format.DateTimeParseException;

import com.fasterxml.jackson.core.JsonParseException;
import com.fasterxml.jackson.databind.JsonMappingException;

/**
 * RabbitMQ 토폴로지 및 리스너 설정.
 *
 * 주요 책임:
 *
 * Exchange/Queue/Binding 선언 및 데이터타입별 큐 분리
 * 데이터타입별 컨슈머 수 독립 설정
 * 수동 Ack 모드 컨테이너 팩토리 설정
 */
@Configuration
public class RabbitMqConfig {

	@Value("${app.rabbitmq.exchange}")
	private String exchangeName;

	@Value("${app.rabbitmq.routing-key-ticker:*.ticker.#}")
	private String tickerRoutingKey;

	@Value("${app.rabbitmq.routing-key-trade:*.trade.#}")
	private String tradeRoutingKey;

	@Value("${app.rabbitmq.routing-key-orderbook:*.orderbook.#}")
	private String orderBookRoutingKey;

	@Value("${app.rabbitmq.ticker-concurrent-consumers:2}")
	private int tickerConcurrentConsumers;

	@Value("${app.rabbitmq.ticker-max-concurrent-consumers:4}")
	private int tickerMaxConcurrentConsumers;

	@Value("${app.rabbitmq.trade-concurrent-consumers:2}")
	private int tradeConcurrentConsumers;

	@Value("${app.rabbitmq.trade-max-concurrent-consumers:4}")
	private int tradeMaxConcurrentConsumers;

	@Value("${app.rabbitmq.orderbook-concurrent-consumers:4}")
	private int orderBookConcurrentConsumers;

	@Value("${app.rabbitmq.orderbook-max-concurrent-consumers:8}")
	private int orderBookMaxConcurrentConsumers;

	@Value("${app.rabbitmq.prefetch-count:50}")
	private int prefetchCount;

	@Value("${app.rabbitmq.dlq-exchange:market-data.dlx}")
	private String dlqExchangeName;

	@Value("${app.rabbitmq.dlq-queue:market-data.persist.dlq}")
	private String dlqQueueName;

	@Value("${app.rabbitmq.dlq-routing-key:market-data.persist.dlq}")
	private String dlqRoutingKey;

	@Value("${app.rabbitmq.retry-max-attempts:3}")
	private int retryMaxAttempts;

	/**
	 * Exchange/Queue/Binding 토폴로지를 생성한다.
	 *
	 * 데이터타입별 큐 분리:
	 * - ticker.queue    ← *.ticker.#
	 * - trade.queue     ← *.trade.#
	 * - orderbook.queue ← *.orderbook.#
	 *
	 * orderbook이 폭증해도 ticker/trade 처리에 영향 없음.
	 * 거래소가 추가되더라도 Binding Key의 * 와일드카드가 커버하므로 코드 수정 불필요.
	 * DLQ(Dead Letter Queue) 및 DLX(Dead Letter Exchange) 포함.
	 *
	 * @return RabbitMQ 선언 객체 묶음
	 */
	@Bean
	public Declarables marketDataTopology() {
		TopicExchange exchange = new TopicExchange(exchangeName, true, false);

		Queue tickerQueue    = new Queue("ticker.queue", true);
		Queue tradeQueue     = new Queue("trade.queue", true);
		Queue orderBookQueue = new Queue("orderbook.queue", true);

		Binding tickerBinding    = BindingBuilder.bind(tickerQueue).to(exchange).with(tickerRoutingKey);
		Binding tradeBinding     = BindingBuilder.bind(tradeQueue).to(exchange).with(tradeRoutingKey);
		Binding orderBookBinding = BindingBuilder.bind(orderBookQueue).to(exchange).with(orderBookRoutingKey);

		DirectExchange dlx = new DirectExchange(dlqExchangeName, true, false);
		Queue dlq = new Queue(dlqQueueName, true);
		Binding dlqBinding = BindingBuilder.bind(dlq).to(dlx).with(dlqRoutingKey);

		return new Declarables(
			exchange,
			tickerQueue, tradeQueue, orderBookQueue,
			tickerBinding, tradeBinding, orderBookBinding,
			dlx, dlq, dlqBinding
		);
	}

	/**
	 * 재시도 정책: 재시도할 예외와 재시도하지 않을 예외를 구분한다.
	 * - 재시도 O: DB 타임아웃·일시 오류(DataAccessException), 네트워크(IOException) → N회 재시도 후 DLQ
	 * - 재시도 X: JSON 파싱/매핑 실패, 잘못된 형식, 비즈니스 검증 실패 → 즉시 recoverer(DLQ) 호출
	 * - 분류되지 않은 예외(defaultValue=false): NPE, IllegalStateException, 기타 미분류 → 재시도 없이 DLQ (버그/알 수 없는 오류는 반복 재시도보다 보존·검토가 우선)
	 * cause 체인을 탐색하므로 리스너에서 RuntimeException(cause)로 던져도 원인 예외 기준으로 분류된다.
	 */
	@Bean
	public SimpleRetryPolicy messageRetryPolicy() {
		Map<Class<? extends Throwable>, Boolean> retryableExceptions = Map.of(
			// 재시도 O: 일시적 장애
			DataAccessException.class, true,
			IOException.class, true,
			// 재시도 X: 재시도해도 성공 불가
			JsonParseException.class, false,
			JsonMappingException.class, false,
			IllegalArgumentException.class, false,
			DateTimeParseException.class, false,
			NumberFormatException.class, false
		);
		return new SimpleRetryPolicy(
			retryMaxAttempts,
			retryableExceptions,
			true,  // traverseCauses: RuntimeException(cause)에서 cause 기준 분류
			false  // defaultValue: 분류되지 않은 예외는 재시도 안 함 → DLQ로 보존
		);
	}

	/**
	 * 재시도 후 DLQ로 전달하는 인터셉터.
	 * 재시도 가능 예외만 N회 재시도하고, 그 외는 즉시 recoverer(DLQ)로 보낸다.
	 * 세 팩토리가 동일한 재시도 정책과 Recoverer를 공유한다.
	 *
	 * @param rabbitTemplate Rabbit 템플릿
	 * @param messageRetryPolicy 재시도 정책 빈
	 * @return 재시도 어드바이스
	 */
	@Bean
	public Advice retryAdvice(
		RabbitTemplate rabbitTemplate,
		SimpleRetryPolicy messageRetryPolicy
	) {
		AcknowledgingRepublishMessageRecoverer recoverer = new AcknowledgingRepublishMessageRecoverer(
			rabbitTemplate,
			dlqExchangeName,
			dlqRoutingKey
		);
		return RetryInterceptorBuilder.stateless()
			.retryPolicy(messageRetryPolicy)
			.recoverer(recoverer)
			.build();
	}

	/**
	 * ticker 전용 컨테이너 팩토리를 생성한다.
	 *
	 * @RabbitListener(queues = "ticker.queue", containerFactory = "tickerContainerFactory")
	 *
	 * @param connectionFactory RabbitMQ 커넥션 팩토리
	 * @param retryAdvice 재시도 어드바이스
	 * @return 리스너 컨테이너 팩토리
	 */
	@Bean
	public SimpleRabbitListenerContainerFactory tickerContainerFactory(
		ConnectionFactory connectionFactory,
		Advice retryAdvice
	) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		factory.setConcurrentConsumers(tickerConcurrentConsumers);
		factory.setMaxConcurrentConsumers(tickerMaxConcurrentConsumers);
		factory.setPrefetchCount(prefetchCount);
		factory.setEnforceImmediateAckForManual(true);
		factory.setAdviceChain(retryAdvice);
		factory.setContainerCustomizer(container -> container.setShutdownTimeout(5000L));
		return factory;
	}

	/**
	 * trade 전용 컨테이너 팩토리를 생성한다.
	 *
	 * @RabbitListener(queues = "trade.queue", containerFactory = "tradeContainerFactory")
	 *
	 * @param connectionFactory RabbitMQ 커넥션 팩토리
	 * @param retryAdvice 재시도 어드바이스
	 * @return 리스너 컨테이너 팩토리
	 */
	@Bean
	public SimpleRabbitListenerContainerFactory tradeContainerFactory(
		ConnectionFactory connectionFactory,
		Advice retryAdvice
	) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		factory.setConcurrentConsumers(tradeConcurrentConsumers);
		factory.setMaxConcurrentConsumers(tradeMaxConcurrentConsumers);
		factory.setPrefetchCount(prefetchCount);
		factory.setEnforceImmediateAckForManual(true);
		factory.setAdviceChain(retryAdvice);
		factory.setContainerCustomizer(container -> container.setShutdownTimeout(5000L));
		return factory;
	}

	/**
	 * orderbook 전용 컨테이너 팩토리를 생성한다.
	 *
	 * @RabbitListener(queues = "orderbook.queue", containerFactory = "orderBookContainerFactory")
	 *
	 * @param connectionFactory RabbitMQ 커넥션 팩토리
	 * @param retryAdvice 재시도 어드바이스
	 * @return 리스너 컨테이너 팩토리
	 */
	@Bean
	public SimpleRabbitListenerContainerFactory orderBookContainerFactory(
		ConnectionFactory connectionFactory,
		Advice retryAdvice
	) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		factory.setConcurrentConsumers(orderBookConcurrentConsumers);
		factory.setMaxConcurrentConsumers(orderBookMaxConcurrentConsumers);
		factory.setPrefetchCount(prefetchCount);
		factory.setEnforceImmediateAckForManual(true);
		factory.setAdviceChain(retryAdvice);
		factory.setContainerCustomizer(container -> container.setShutdownTimeout(5000L));
		return factory;
	}
}
