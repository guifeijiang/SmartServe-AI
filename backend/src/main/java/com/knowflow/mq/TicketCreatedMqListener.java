package com.knowflow.mq;

import com.knowflow.common.UnrecoverableMessageException;
import com.knowflow.service.TicketCreatedEventHandler;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyContext;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently;
import org.apache.rocketmq.common.message.MessageExt;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.nio.charset.StandardCharsets;
import java.util.List;

public class TicketCreatedMqListener implements MessageListenerConcurrently {

    private static final Logger log = LoggerFactory.getLogger(TicketCreatedMqListener.class);

    private final TicketCreatedEventHandler ticketCreatedEventHandler;

    public TicketCreatedMqListener(TicketCreatedEventHandler ticketCreatedEventHandler) {
        this.ticketCreatedEventHandler = ticketCreatedEventHandler;
    }

    @Override
    public ConsumeConcurrentlyStatus consumeMessage(List<MessageExt> messages, ConsumeConcurrentlyContext context) {
        for (MessageExt message : messages) {
            try {
                String payload = new String(message.getBody(), StandardCharsets.UTF_8);
                ticketCreatedEventHandler.handle(payload);
            } catch (UnrecoverableMessageException ex) {
                log.warn("Drop unrecoverable ticket-created message, msgId={}", message.getMsgId(), ex);
            } catch (Exception ex) {
                log.warn("Consume ticket-created message failed, msgId={}", message.getMsgId(), ex);
                return ConsumeConcurrentlyStatus.RECONSUME_LATER;
            }
        }
        return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
    }
}
