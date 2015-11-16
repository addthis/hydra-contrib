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
package com.addthis.hydra.kafka.consumer;

import java.io.File;

import java.util.Map;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.ConcurrentHashMap;
import java.util.concurrent.ConcurrentMap;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.ThreadPoolExecutor;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import com.addthis.basis.util.LessFiles;

import com.addthis.bark.ZkUtil;
import com.addthis.bundle.channel.DataChannelError;
import com.addthis.bundle.core.Bundle;
import com.addthis.bundle.core.list.ListBundleFormat;
import com.addthis.hydra.data.util.DateUtil;
import com.addthis.hydra.store.db.DBKey;
import com.addthis.hydra.store.db.PageDB;
import com.addthis.hydra.task.run.TaskRunConfig;
import com.addthis.hydra.task.source.SimpleMark;
import com.addthis.hydra.task.source.TaskDataSource;
import com.addthis.hydra.task.source.bundleizer.BundleizerFactory;

import com.google.common.util.concurrent.ThreadFactoryBuilder;
import com.google.common.util.concurrent.Uninterruptibles;

import com.fasterxml.jackson.annotation.JsonProperty;

import org.apache.commons.io.FileUtils;
import org.apache.curator.framework.CuratorFramework;

import org.joda.time.DateTime;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import static com.addthis.hydra.kafka.consumer.BundleWrapper.bundleQueueEndMarker;
import kafka.javaapi.PartitionMetadata;
import kafka.javaapi.TopicMetadata;

public class KafkaSource extends TaskDataSource {

    private static final Logger log = LoggerFactory.getLogger(KafkaSource.class);

    @JsonProperty(required = true)
    private String zookeeper;
    @JsonProperty(required = true)
    private String topic;
    @JsonProperty
    private String startDate;
    @JsonProperty
    private String dateFormat = "YYMMdd";
    @JsonProperty
    private String markDir = "marks";
    /** Ignore the mark directory */
    @JsonProperty
    private boolean ignoreMarkDir;
    /** Specifies conversion to bundles.  If null, then uses DataChannelCodec.decodeBundle */
    @JsonProperty
    private BundleizerFactory format;

    @JsonProperty
    private int fetchThreads = 1;
    @JsonProperty
    private int decodeThreads = 1;
    @JsonProperty
    private int queueSize = 10000;
    @JsonProperty
    private int seedBrokers = 3;

    @JsonProperty
    private TaskRunConfig config;
    @JsonProperty
    private int pollRetries = 180;

    PageDB<SimpleMark> markDb;
    AtomicBoolean running;
    LinkedBlockingQueue<BundleWrapper> bundleQueue;
    CuratorFramework zkClient;
    final ConcurrentMap<String, Long> sourceOffsets = new ConcurrentHashMap<>();

    private ExecutorService fetchExecutor;
    private ExecutorService decodeExecutor;

    @Override
    public Bundle next() {
        if (!running.get()) {
            return null;
        }
        BundleWrapper bundle;
        try {
            bundle = bundleQueue.poll(pollRetries, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // reset interrupt status
            Thread.currentThread().interrupt();
            // fine to leave bundles on the queue
            return null;
        }
        if (bundle == null) {
            throw new DataChannelError(
                    "giving up on kafka source next() after waiting: " + pollRetries + " seconds");
        }
        if (bundle == bundleQueueEndMarker) {
            // add back end-marker in case someone continues calling peek/next on the source
            bundleQueue.add(bundleQueueEndMarker);
            return null;
        }
        // Although the bundle queue is guaranteed to be ordered by offsets, concurrent calls to
        // next() can complete (and update offsets) in arbitrary order.  Assuming the threads calling
        // next() will not be interrupted, we can just always take the max offset per partition since all
        // earlier bundles will eventually be returned to some next() call.
        sourceOffsets.compute(bundle.sourceIdentifier, (partition, previous) ->
                previous == null ? bundle.offset : Math.max(previous, bundle.offset));
        return bundle.bundle;
    }

    @Override
    public Bundle peek() {
        BundleWrapper bundle = null;
        int retries = 0;
        while (bundle == null && retries < pollRetries) {
            bundle = bundleQueue.peek();
            // seemingly no better option than sleeping here - blocking queue doesn't have a
            // blocking peek, but
            // still want to maintain source.peek() == source.next() guarantees
            if (bundle == null) {
                Uninterruptibles.sleepUninterruptibly(1000, TimeUnit.MILLISECONDS);
            }
            retries++;
        }
        if (bundle == null) {
            throw new DataChannelError(
                    "giving up on kafka source peek() after retrying: " + retries);
        }
        if (bundle == bundleQueueEndMarker) {
            return null;
        }
        return bundle.bundle;
    }

    @Override
    public void close() {
        running.set(false);
        fetchExecutor.shutdown();
        decodeExecutor.shutdown();
        for (Map.Entry<String, Long> sourceOffset : sourceOffsets.entrySet()) {
            SimpleMark mark = new SimpleMark();
            mark.set(String.valueOf(sourceOffset.getValue()), sourceOffset.getValue());
            mark.setEnd(false);
            this.markDb.put(new DBKey(0, sourceOffset.getKey()), mark);
            log.info("updating mark db, source: {}, index: {}", sourceOffset.getKey(), sourceOffset.getValue());
        }
        this.markDb.close();
        this.zkClient.close();
    }

    @Override
    public void init() {
        try {
            if (ignoreMarkDir) {
                File md = new File(markDir);
                if (md.exists()) {
                    FileUtils.deleteDirectory(md);
                    log.info("Deleted marks directory : {}", md);
                }
            }
            this.bundleQueue = new LinkedBlockingQueue<>(queueSize);
            this.markDb = new PageDB<>(LessFiles.initDirectory(markDir), SimpleMark.class, 100, 100);
            // move to init method
            this.fetchExecutor = new ThreadPoolExecutor(fetchThreads, fetchThreads,
                            0L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(),
                            new ThreadFactoryBuilder()
                                    .setNameFormat("source-kafka-fetch-%d")
                                    .setDaemon(true)
                                    .build());
            this.decodeExecutor = new ThreadPoolExecutor(decodeThreads, decodeThreads,
                            0L, TimeUnit.SECONDS,
                            new LinkedBlockingQueue<>(),
                            new ThreadFactoryBuilder()
                                    .setNameFormat("source-kafka-decode-%d")
                                    .setDaemon(true)
                                    .build());
            this.running = new AtomicBoolean(true);
            final DateTime startTime = (startDate != null) ? DateUtil.getDateTime(dateFormat, startDate) : null;

            zkClient = ZkUtil.makeStandardClient(zookeeper, false);
            TopicMetadata metadata = ConsumerUtils.getTopicMetadata(zkClient, seedBrokers, topic);

            final Integer[] shards = config.calcShardList(metadata.partitionsMetadata().size());
            final ListBundleFormat bundleFormat = new ListBundleFormat();
            final CountDownLatch decodeLatch = new CountDownLatch(shards.length);
            for (final int shard : shards) {
                LinkedBlockingQueue<MessageWrapper> messageQueue = new LinkedBlockingQueue<>(this.queueSize);
                final PartitionMetadata partition = metadata.partitionsMetadata().get(shard);
                FetchTask fetcher = new FetchTask(this, topic, partition, startTime, messageQueue);
                fetchExecutor.execute(fetcher);
                Runnable decoder = new DecodeTask(decodeLatch, format, bundleFormat, running, messageQueue, bundleQueue);
                decodeExecutor.execute(decoder);
            }
            decodeExecutor.submit(new MarkEndTask<>(decodeLatch, running, bundleQueue, bundleQueueEndMarker));
        } catch (Exception ex) {
            log.error("Error initializing kafka source: ", ex);
            throw new RuntimeException(ex);
        }
    }

    // Put onto linked blocking queue, giving up (via exception) if running becomes false (interrupts are ignored in favor of the running flag).
    // Uses an exception rather than returning boolean since checking for return status was a huge mess (e.g. had to keep track of how far
    // your iterator got in the message set when retrying).
    static <E> void putWhileRunning(BlockingQueue<E> queue, E value, AtomicBoolean running) {
        boolean offered = false;
        while (!offered) {
            if (!running.get()) {
                throw BenignKafkaException.INSTANCE;
            }
            try {
                offered = queue.offer(value, 1, TimeUnit.SECONDS);
            } catch (InterruptedException e) {
                // ignored
            }
        }
    }
}
