package com.addthis.hydra.kafka.producer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;

import com.yammer.metrics.Metrics;
import com.yammer.metrics.core.Meter;

import org.apache.kafka.clients.producer.BufferExhaustedException;
import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public class UncheckedProducer<K,V> implements Producer<K,V> {

    public static final Logger log = LoggerFactory.getLogger(UncheckedProducer.class);
    public static final Meter kafkaBufferFull = Metrics.newMeter(UncheckedProducer.class, "kafkaBufferFull", "buffer-full", TimeUnit.SECONDS);
    public static final Meter kafkaSendError = Metrics.newMeter(UncheckedProducer.class, "kafkaSendError", "send-error", TimeUnit.SECONDS);
    private static final ErrorLoggingCallback defaultCallback = new ErrorLoggingCallback(null);

    private Producer<K, V> producer;

    public UncheckedProducer(Producer<K, V> producer) {
        this.producer = new CatchingProducer<>(producer);
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
        return this.producer.send(record, defaultCallback);
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {
        return this.producer.send(record, new ErrorLoggingCallback(callback));
    }

    @Override
    public List<PartitionInfo> partitionsFor(String topic) {
        return this.producer.partitionsFor(topic);
    }

    @Override
    public Map<MetricName, ? extends Metric> metrics() {
        return this.producer.metrics();
    }

    @Override
    public void close() {
        this.producer.close();
    }

    public static class ErrorLoggingCallback implements Callback {

        private Callback customCallback;

        public ErrorLoggingCallback(Callback customCallback) {
            this.customCallback = customCallback;
        }

        @Override
        public void onCompletion(RecordMetadata metadata, Exception exception) {
            if(exception instanceof BufferExhaustedException) {
                kafkaBufferFull.mark();
                log.error("kafka buffer full: ", exception);
            } else if(exception != null) {
                kafkaSendError.mark();
                log.error("kafka send failed for topic: " + metadata.topic() +
                        ", partition: " + metadata.partition() + ", offset: " + metadata.offset() + "," +
                        " verify that the topic has been created and the partition has an available leader: ",
                        exception);
            }
            if(customCallback != null) {
                customCallback.onCompletion(metadata, exception);
            }
        }
    }
}
