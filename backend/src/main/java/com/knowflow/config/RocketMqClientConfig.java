package com.knowflow.config;

import com.knowflow.mq.NativeRocketMqMessageSender;
import com.knowflow.mq.RocketMqMessageSender;
import com.knowflow.mq.TicketCreatedMqListener;
import com.knowflow.service.TicketCreatedEventHandler;
import org.apache.rocketmq.client.consumer.DefaultMQPushConsumer;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.common.consumer.ConsumeFromWhere;
import org.apache.rocketmq.common.protocol.heartbeat.MessageModel;
import org.springframework.boot.autoconfigure.condition.ConditionalOnMissingBean;
import org.springframework.boot.autoconfigure.condition.ConditionalOnProperty;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.scheduling.annotation.EnableScheduling;

@Configuration
@EnableScheduling
@EnableConfigurationProperties(RocketMqProperties.class)
public class RocketMqClientConfig {

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "knowflow.rocketmq", name = "enabled", havingValue = "true")
    public DefaultMQProducer ticketMqProducer(RocketMqProperties properties) throws Exception {
        DefaultMQProducer producer = new DefaultMQProducer(properties.getProducerGroup());
        producer.setNamesrvAddr(properties.getNameServer());
        producer.setVipChannelEnabled(false);
        producer.setSendMsgTimeout((int) properties.getSendTimeoutMs());
        producer.setRetryTimesWhenSendFailed(0);
        producer.start();
        return producer;
    }

    @Bean
    @ConditionalOnProperty(prefix = "knowflow.rocketmq", name = "enabled", havingValue = "true")
    public RocketMqMessageSender rocketMqMessageSender(DefaultMQProducer ticketMqProducer, RocketMqProperties properties) {
        return new NativeRocketMqMessageSender(ticketMqProducer, properties);
    }

    @Bean
    @ConditionalOnMissingBean(RocketMqMessageSender.class)
    public RocketMqMessageSender disabledRocketMqMessageSender() {
        return (topic, tag, keys, body) -> {
            throw new IllegalStateException("RocketMQ 未启用");
        };
    }

    @Bean(destroyMethod = "shutdown")
    @ConditionalOnProperty(prefix = "knowflow.rocketmq", name = "consumer-enabled", havingValue = "true")
    public DefaultMQPushConsumer ticketCreatedConsumer(
            RocketMqProperties properties,
            TicketCreatedEventHandler ticketCreatedEventHandler
    ) throws Exception {
        DefaultMQPushConsumer consumer = new DefaultMQPushConsumer(properties.getConsumerGroup());
        consumer.setNamesrvAddr(properties.getNameServer());
        consumer.setVipChannelEnabled(false);
        consumer.setConsumeFromWhere(ConsumeFromWhere.CONSUME_FROM_LAST_OFFSET);
        consumer.setMessageModel(MessageModel.CLUSTERING);
        consumer.setConsumeMessageBatchMaxSize(1);
        consumer.subscribe(properties.getTopic(), properties.getTag());
        consumer.registerMessageListener(new TicketCreatedMqListener(ticketCreatedEventHandler));
        consumer.start();
        return consumer;
    }
}
