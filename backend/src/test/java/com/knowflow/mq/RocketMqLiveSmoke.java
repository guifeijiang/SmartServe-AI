package com.knowflow.mq;

import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.consumer.listener.ConsumeConcurrentlyStatus;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.message.Message;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;

import java.nio.charset.StandardCharsets;
import java.util.UUID;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicReference;

/**
 * Manual live check against local RocketMQ 4.4.0. Not a Surefire test.
 */
public final class RocketMqLiveSmoke {

    public static void main(String[] args) throws Exception {
        String nameServer = "127.0.0.1:9876";
        String topic = "TICKET_CREATED";
        String tag = "CREATED";
        String eventId = "live-" + UUID.randomUUID();
        String body = "{\"eventId\":\"" + eventId + "\",\"ticketId\":1,\"ticketNo\":\"TKT-LIVE\",\"customerUserId\":1,\"title\":\"live\"}";

        CountDownLatch latch = new CountDownLatch(1);
        AtomicReference<String> consumed = new AtomicReference<>();

        DefaultMQProducer producer = new DefaultMQProducer("knowflow-ticket-live-smoke-producer");
        producer.setNamesrvAddr(nameServer);
        producer.setVipChannelEnabled(false);
        producer.start();
        SendResult sendResult = producer.send(new Message(topic, tag, eventId, body.getBytes(StandardCharsets.UTF_8)), 5000);

        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer("knowflow-ticket-live-smoke-" + UUID.randomUUID());
        consumer.setNamesrvAddr(nameServer);
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_FIRST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.subscribe(topic, tag);
        consumer.registerMessageListener((MessageListenerConcurrentlyAdapter) (messages, context) -> {
            String payload = new String(messages.get(0).getBody(), StandardCharsets.UTF_8);
            if (payload.contains(eventId)) {
                consumed.set(payload);
                latch.countDown();
            }
            return ConsumeConcurrentlyStatus.CONSUME_SUCCESS;
        });
        consumer.start();
        boolean received = latch.await(20, TimeUnit.SECONDS);
        producer.shutdown();
        consumer.shutdown();

        if (!received || consumed.get() == null || !consumed.get().contains(eventId)) {
            System.err.println("LIVE_FAIL send=" + sendResult + " consumed=" + consumed.get());
            System.exit(1);
        }
        System.out.println("LIVE_OK sendStatus=" + sendResult.getSendStatus() + " msgId=" + sendResult.getMsgId());
    }

    @FunctionalInterface
    private interface MessageListenerConcurrentlyAdapter
            extends org.apache.rocketmq.client.consumer.listener.MessageListenerConcurrently {
    }

    private RocketMqLiveSmoke() {
    }
}
