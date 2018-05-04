/*
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.addthis.hydra.kafka.bundle;

import java.util.Arrays;
import java.util.List;
import java.util.Properties;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.atomic.AtomicBoolean;

import com.addthis.basis.util.Parameter;

import com.addthis.hydra.store.db.DBKey;
import com.addthis.hydra.store.db.PageDB;
import com.addthis.hydra.task.source.SimpleMark;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.addthis.hydra.kafka.bundle.KafkaSource.putWhileRunning;
import kafka.api.FetchRequest;
import kafka.api.FetchRequestBuilder;
import kafka.cluster.BrokerEndPoint;
import kafka.common.ErrorMapping;
import kafka.javaapi.FetchResponse;
import kafka.javaapi.PartitionMetadata;
import org.apache.kafka.clients.consumer.KafkaConsumer;
import kafka.javaapi.message.ByteBufferMessageSet;
import kafka.message.MessageAndOffset;
import org.apache.kafka.clients.consumer.ConsumerRecord;
import org.apache.kafka.clients.consumer.ConsumerRecords;
import org.apache.kafka.common.TopicPartition;
import org.apache.kafka.common.PartitionInfo;

class FetchTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(FetchTask.class);

    //private static final int fetchSize = Parameter.intValue(FetchTask.class + ".fetchSize", 1048576);
    private static final int timeout = Parameter.intValue(FetchTask.class + ".timeout", 10000);
    //private static final int offsetAttempts = Parameter.intValue(FetchTask.class + ".offsetAttempts", 3);

    private KafkaSource kafkaSource;
    private final String topic;
    private final PartitionInfo partition;
    private final String startTime;
    private final LinkedBlockingQueue<MessageWrapper> messageQueue;

    public FetchTask(KafkaSource kafkaSource, String topic, PartitionInfo partition, String startTime,
            LinkedBlockingQueue<MessageWrapper> messageQueue) {
        this.kafkaSource = kafkaSource;
        this.topic = topic;
        this.partition = partition;
        this.startTime = startTime;
        this.messageQueue = messageQueue;
    }

    @Override
    public void run() {
        consume(this.kafkaSource.running, this.topic, this.partition, this.kafkaSource.markDb, this.startTime,
                this.messageQueue, this.kafkaSource.consumerProperties);
    }

    private static void consume(AtomicBoolean running, String topic, PartitionInfo partition,
            PageDB<SimpleMark> markDb, String startTime, LinkedBlockingQueue<MessageWrapper> messageQueue,
            Properties consumerProperties) {
        KafkaConsumer consumer = null;
        try {
            if (!running.get()) {
                return;
            }
            // initialize consumer and offsets
            int partitionId = partition.partition();
            consumer = new KafkaConsumer(consumerProperties);
            String sourceIdentifier = topic + "-" + partitionId;
            TopicPartition topicPartition = new TopicPartition(topic, partitionId);
            consumer.assign(Arrays.asList(topicPartition));
            // lazy operation, only evaluates when poll() or position() is called
            consumer.seekToEnd(Arrays.asList(topicPartition));
            final long endOffset = consumer.position(topicPartition);
            final SimpleMark previousMark = markDb.get(new DBKey(0, sourceIdentifier));
            long startOffset = -1;
            if (previousMark != null) {
                startOffset = previousMark.getIndex();
                consumer.seek(topicPartition, startOffset);
            } else if (startTime.equals("earliest")) {
                consumer.seekToBeginning(Arrays.asList(topicPartition));
                startOffset = consumer.position(topicPartition);
                log.info("no previous mark for partition: {}, starting from offset: {}, closest to: {}",
                         partitionId, startOffset, startTime);
            } else if (startTime.equals("latest")) {
                startOffset = endOffset;
                log.info("no previous mark for partition: {}, starting from offset: {}, closest to: {}",
                        partitionId, startOffset, startTime);
            }
            if (startOffset == -1) {
                log.info("no previous mark for topic: {}, partition: {}, no offsets available for " +
                         "startTime: {}, starting from earliest",
                         topic, partitionId, startTime);
                consumer.seekToBeginning(Arrays.asList(topicPartition));
                startOffset = consumer.position(topicPartition);
            }
            log.info("started consuming topic: {}, partition: {}, at offset: {}, until offset: {}",
                    topic, partitionId, startOffset, endOffset);
            // fetch from broker, add to queue (decoder threads will process queue in parallel)
            long offset = startOffset;
            while (running.get() && (offset <= endOffset)) {
                ConsumerRecords<byte[],byte[]> records = consumer.poll(timeout);
                if(!records.isEmpty()) {
                    for (ConsumerRecord<byte[], byte[]> record : records) {
                        putWhileRunning(messageQueue, new MessageWrapper(record.offset(), record.value(), topic,
                                partitionId, sourceIdentifier), running);
                        offset = record.offset() + 1;
                    }
                }
            }
            log.info("finished consuming topic: {}, partition: {}", topic, partitionId);
        } catch (BenignKafkaException ignored) {
        } catch (Exception e) {
            log.error("kafka consume thread failed: ", e);
        } finally {
            putWhileRunning(messageQueue, MessageWrapper.messageQueueEndMarker, running);
            if(consumer != null) {
                consumer.close();
            }
        }
    }
}
