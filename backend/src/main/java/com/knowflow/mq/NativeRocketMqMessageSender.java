package com.knowflow.mq;

import com.knowflow.config.RocketMqProperties;
import org.apache.rocketmq.client.producer.DefaultMQProducer;
import org.apache.rocketmq.client.producer.SendResult;
import org.apache.rocketmq.client.producer.SendStatus;
import org.apache.rocketmq.common.message.Message;

import java.nio.charset.StandardCharsets;

public class NativeRocketMqMessageSender implements RocketMqMessageSender {

    private final DefaultMQProducer producer;
    private final RocketMqProperties properties;

    public NativeRocketMqMessageSender(DefaultMQProducer producer, RocketMqProperties properties) {
        this.producer = producer;
        this.properties = properties;
    }

    @Override
    public void send(String topic, String tag, String keys, String body) {
        try {
            Message message = new Message(
                    topic,
                    tag,
                    keys,
                    body.getBytes(StandardCharsets.UTF_8)
            );
            SendResult result = producer.send(message, properties.getSendTimeoutMs());
            if (result == null || result.getSendStatus() != SendStatus.SEND_OK) {
                throw new IllegalStateException("RocketMQ 发送未成功: " + (result == null ? "null" : result.getSendStatus()));
            }
        } catch (RuntimeException ex) {
            throw ex;
        } catch (Exception ex) {
            throw new IllegalStateException("RocketMQ 发送失败: " + ex.getMessage(), ex);
        }
    }
}
