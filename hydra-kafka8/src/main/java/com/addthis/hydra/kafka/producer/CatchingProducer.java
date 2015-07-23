package com.addthis.hydra.kafka.producer;

import java.util.List;
import java.util.Map;
import java.util.concurrent.CompletableFuture;
import java.util.concurrent.Future;

import org.apache.kafka.clients.producer.Callback;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.clients.producer.ProducerRecord;
import org.apache.kafka.clients.producer.RecordMetadata;
import org.apache.kafka.common.Metric;
import org.apache.kafka.common.MetricName;
import org.apache.kafka.common.PartitionInfo;

public class CatchingProducer<K,V> implements Producer<K,V> {

    private Producer<K, V> producer;

    private static Future<RecordMetadata> wrapException(final Exception e) {
        CompletableFuture<RecordMetadata> result = new CompletableFuture<>();
        result.completeExceptionally(e);
        return result;
    }

    public CatchingProducer(Producer<K, V> producer) {
        this.producer = producer;
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record) {
        try {
            return this.producer.send(record);
        } catch (Exception e) {
            return wrapException(e);
        }
    }

    @Override
    public Future<RecordMetadata> send(ProducerRecord<K, V> record, Callback callback) {
        try {
            return this.producer.send(record, callback);
        } catch (Exception e) {
            callback.onCompletion(null, e);
            return wrapException(e);
        }
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
}
