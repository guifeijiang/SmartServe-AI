package com.knowflow.config;

import com.knowflow.service.OutboxDispatchService;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

@Component
@ConditionalOnProperty(prefix = "knowflow.rocketmq", name = "outbox-dispatch-enabled", havingValue = "true")
public class OutboxDispatchScheduler {

    private final OutboxDispatchService outboxDispatchService;

    public OutboxDispatchScheduler(OutboxDispatchService outboxDispatchService) {
        this.outboxDispatchService = outboxDispatchService;
    }

    @Scheduled(fixedDelayString = "${knowflow.rocketmq.dispatch-interval-ms:2000}")
    public void dispatch() {
        outboxDispatchService.dispatchDueMessages();
    }
}
