package com.rabbittick.persister.messaging;

import org.springframework.amqp.ImmediateAcknowledgeAmqpException;
import org.springframework.amqp.core.AmqpTemplate;
import org.springframework.amqp.core.Message;
import org.springframework.amqp.rabbit.retry.RepublishMessageRecoverer;

/**
 * DLQ 발행 후 원본 메시지를 Ack 처리하는 MessageRecoverer.
 *
 * <p>{@link RepublishMessageRecoverer}는 DLQ 발행 후 {@link ImmediateAcknowledgeAmqpException}을
 * 던지지 않아 Manual Ack 모드에서 원본 메시지가 Unacked 상태로 남을 수 있다.
 * 이 클래스는 DLQ 발행 후 해당 예외를 던져 컨테이너가 Ack을 처리하도록 한다.
 */
public class AcknowledgingRepublishMessageRecoverer extends RepublishMessageRecoverer {

    /**
     * AcknowledgingRepublishMessageRecoverer를 생성한다.
     *
     * @param errorTemplate DLQ 발행에 사용할 AmqpTemplate
     * @param errorExchange DLQ가 바인딩된 Exchange 이름
     * @param errorRoutingKey DLQ 라우팅 키
     */
    public AcknowledgingRepublishMessageRecoverer(
            AmqpTemplate errorTemplate,
            String errorExchange,
            String errorRoutingKey
    ) {
        super(errorTemplate, errorExchange, errorRoutingKey);
    }

    /**
     * 실패한 메시지를 DLQ로 발행하고 원본 메시지를 Ack 처리한다.
     *
     * @param message 처리 실패한 메시지
     * @param cause 실패 원인 예외
     * @throws ImmediateAcknowledgeAmqpException 컨테이너가 원본 메시지를 Ack 처리하도록 유도
     */
    @Override
    public void recover(Message message, Throwable cause) {
        super.recover(message, cause);
        throw new ImmediateAcknowledgeAmqpException(
                "Republished failed message to DLQ", cause);
    }
}