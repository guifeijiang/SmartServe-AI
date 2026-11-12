package com.knowflow.service;

import com.knowflow.config.RocketMqProperties;
import com.knowflow.entity.MessageOutbox;
import com.knowflow.mapper.MessageOutboxMapper;
import com.knowflow.mq.RocketMqMessageSender;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.util.List;

@Service
public class OutboxDispatchService {

    private static final Logger log = LoggerFactory.getLogger(OutboxDispatchService.class);
    private static final int ERROR_MAX_LENGTH = 1024;

    private final MessageOutboxMapper messageOutboxMapper;
    private final RocketMqMessageSender rocketMqMessageSender;
    private final RocketMqProperties properties;

    public OutboxDispatchService(
            MessageOutboxMapper messageOutboxMapper,
            RocketMqMessageSender rocketMqMessageSender,
            RocketMqProperties properties
    ) {
        this.messageOutboxMapper = messageOutboxMapper;
        this.rocketMqMessageSender = rocketMqMessageSender;
        this.properties = properties;
    }

    public int dispatchDueMessages() {
        List<MessageOutbox> due = messageOutboxMapper.findDuePending(properties.getDispatchBatchSize());
        int sent = 0;
        for (MessageOutbox outbox : due) {
            if (dispatchOne(outbox)) {
                sent++;
            }
        }
        return sent;
    }

    boolean dispatchOne(MessageOutbox outbox) {
        try {
            rocketMqMessageSender.send(outbox.getTopic(), outbox.getTag(), outbox.getEventId(), outbox.getPayload());
            messageOutboxMapper.markSent(outbox.getId());
            return true;
        } catch (Exception ex) {
            int retryCount = (outbox.getRetryCount() == null ? 0 : outbox.getRetryCount()) + 1;
            boolean exhausted = retryCount >= properties.getMaxRetry();
            String status = exhausted ? MessageOutbox.STATUS_FAILED : MessageOutbox.STATUS_PENDING;
            LocalDateTime nextRetryAt = exhausted ? null : LocalDateTime.now().plusSeconds((long) retryCount * 10L);
            messageOutboxMapper.markRetry(outbox.getId(), status, retryCount, truncate(ex.getMessage()), nextRetryAt);
            log.warn("Outbox {} send failed, retryCount={}, status={}", outbox.getId(), retryCount, status, ex);
            return false;
        }
    }

    private static String truncate(String message) {
        if (message == null) {
            return "RocketMQ 发送失败";
        }
        return message.length() > ERROR_MAX_LENGTH ? message.substring(0, ERROR_MAX_LENGTH) : message;
    }
}
