package com.knowflow.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

@ConfigurationProperties(prefix = "knowflow.rocketmq")
public class RocketMqProperties {

    private boolean enabled = false;
    private boolean consumerEnabled = false;
    private boolean outboxDispatchEnabled = false;
    private String nameServer = "127.0.0.1:9876";
    private String producerGroup = "knowflow-ticket-producer";
    private String consumerGroup = "knowflow-ticket-consumer";
    private String topic = "TICKET_CREATED";
    private String tag = "CREATED";
    private long sendTimeoutMs = 3000L;
    private int maxRetry = 5;
    private int dispatchBatchSize = 20;
    private long dispatchIntervalMs = 2000L;

    public boolean isEnabled() {
        return enabled;
    }

    public void setEnabled(boolean enabled) {
        this.enabled = enabled;
    }

    public boolean isConsumerEnabled() {
        return consumerEnabled;
    }

    public void setConsumerEnabled(boolean consumerEnabled) {
        this.consumerEnabled = consumerEnabled;
    }

    public boolean isOutboxDispatchEnabled() {
        return outboxDispatchEnabled;
    }

    public void setOutboxDispatchEnabled(boolean outboxDispatchEnabled) {
        this.outboxDispatchEnabled = outboxDispatchEnabled;
    }

    public String getNameServer() {
        return nameServer;
    }

    public void setNameServer(String nameServer) {
        this.nameServer = nameServer;
    }

    public String getProducerGroup() {
        return producerGroup;
    }

    public void setProducerGroup(String producerGroup) {
        this.producerGroup = producerGroup;
    }

    public String getConsumerGroup() {
        return consumerGroup;
    }

    public void setConsumerGroup(String consumerGroup) {
        this.consumerGroup = consumerGroup;
    }

    public String getTopic() {
        return topic;
    }

    public void setTopic(String topic) {
        this.topic = topic;
    }

    public String getTag() {
        return tag;
    }

    public void setTag(String tag) {
        this.tag = tag;
    }

    public long getSendTimeoutMs() {
        return sendTimeoutMs;
    }

    public void setSendTimeoutMs(long sendTimeoutMs) {
        this.sendTimeoutMs = sendTimeoutMs;
    }

    public int getMaxRetry() {
        return maxRetry;
    }

    public void setMaxRetry(int maxRetry) {
        this.maxRetry = maxRetry;
    }

    public int getDispatchBatchSize() {
        return dispatchBatchSize;
    }

    public void setDispatchBatchSize(int dispatchBatchSize) {
        this.dispatchBatchSize = dispatchBatchSize;
    }

    public long getDispatchIntervalMs() {
        return dispatchIntervalMs;
    }

    public void setDispatchIntervalMs(long dispatchIntervalMs) {
        this.dispatchIntervalMs = dispatchIntervalMs;
    }
}
