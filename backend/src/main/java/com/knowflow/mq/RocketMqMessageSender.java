package com.knowflow.mq;

public interface RocketMqMessageSender {

    void send(String topic, String tag, String keys, String body);
}
