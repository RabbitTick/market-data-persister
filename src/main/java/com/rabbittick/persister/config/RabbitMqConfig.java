package com.rabbittick.persister.config;

import org.springframework.amqp.core.AcknowledgeMode;
import org.springframework.amqp.core.Binding;
import org.springframework.amqp.core.BindingBuilder;
import org.springframework.amqp.core.Declarables;
import org.springframework.amqp.core.Queue;
import org.springframework.amqp.core.TopicExchange;
import org.springframework.amqp.rabbit.config.SimpleRabbitListenerContainerFactory;
import org.springframework.amqp.rabbit.connection.ConnectionFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * RabbitMQ 토폴로지 및 리스너 설정.
 *
 * 주요 책임:
 *
 * Exchange/Queue/Binding 선언
 * 수동 Ack 모드 컨테이너 팩토리 설정
 */
@Configuration
public class RabbitMqConfig {

	@Value("${app.rabbitmq.exchange}")
	private String exchangeName;

	@Value("${app.rabbitmq.queue}")
	private String queueName;

	@Value("${app.rabbitmq.routing-key-ticker:*.ticker.#}")
	private String tickerRoutingKey;

	@Value("${app.rabbitmq.routing-key-trade:*.trade.#}")
	private String tradeRoutingKey;

	@Value("${app.rabbitmq.routing-key-orderbook:*.orderbook.#}")
	private String orderBookRoutingKey;

	/**
	 * Exchange/Queue/Binding 토폴로지를 생성한다.
	 *
	 * @return RabbitMQ 선언 객체 묶음
	 */
	@Bean
	public Declarables marketDataTopology() {
		Queue queue = new Queue(queueName, true);
		TopicExchange exchange = new TopicExchange(exchangeName, true, false);
		Binding tickerBinding = BindingBuilder.bind(queue).to(exchange).with(tickerRoutingKey);
		Binding tradeBinding = BindingBuilder.bind(queue).to(exchange).with(tradeRoutingKey);
		Binding orderBookBinding = BindingBuilder.bind(queue).to(exchange).with(orderBookRoutingKey);
		return new Declarables(exchange, queue, tickerBinding, tradeBinding, orderBookBinding);
	}

	/**
	 * 수동 Ack 모드 컨테이너 팩토리를 생성한다.
	 *
	 * @param connectionFactory RabbitMQ 커넥션 팩토리
	 * @return 리스너 컨테이너 팩토리
	 */
	@Bean
	public SimpleRabbitListenerContainerFactory rabbitListenerContainerFactory(ConnectionFactory connectionFactory) {
		SimpleRabbitListenerContainerFactory factory = new SimpleRabbitListenerContainerFactory();
		factory.setConnectionFactory(connectionFactory);
		factory.setAcknowledgeMode(AcknowledgeMode.MANUAL);
		return factory;
	}
}
