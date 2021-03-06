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

import com.addthis.bundle.core.Bundle;
import com.addthis.bundle.core.list.ListBundle;
import com.addthis.bundle.core.list.ListBundleFormat;
import com.addthis.bundle.io.DataChannelCodec;
import com.addthis.bundle.util.AutoField;
import com.addthis.bundle.value.ValueFactory;
import com.addthis.hydra.kafka.bundle.BenignKafkaException;
import com.addthis.hydra.task.source.bundleizer.Bundleizer;
import com.addthis.hydra.task.source.bundleizer.BundleizerFactory;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

import java.io.ByteArrayInputStream;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.CountDownLatch;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicBoolean;

import static com.addthis.hydra.kafka.consumer.KafkaSource.putWhileRunning;
import static com.addthis.hydra.kafka.consumer.MessageWrapper.messageQueueEndMarker;

class DecodeTask implements Runnable {

    private static final Logger log = LoggerFactory.getLogger(DecodeTask.class);

    private final CountDownLatch decodeLatch;
    private final BundleizerFactory bundleizerFactory;
    private final ListBundleFormat format;
    private final AutoField injectSourceName;
    private final AtomicBoolean running;
    private final BlockingQueue<MessageWrapper> messageQueue;
    private final BlockingQueue<BundleWrapper> bundleQueue;

    public DecodeTask(CountDownLatch decodeLatch, BundleizerFactory bundleizerFactory,
                      ListBundleFormat format, AutoField injectSourceName, AtomicBoolean running,
                      BlockingQueue<MessageWrapper> messageQueue, BlockingQueue<BundleWrapper> bundleQueue) {
        this.running = running;
        this.bundleizerFactory = bundleizerFactory;
        this.injectSourceName = injectSourceName;
        this.messageQueue = messageQueue;
        this.bundleQueue = bundleQueue;
        this.decodeLatch = decodeLatch;
        this.format = format;
    }

    @Override
    public void run() {
        try {
            //noinspection StatementWithEmptyBody
            while (running.get() && decodeUnlessEnded()) {}
            log.info("finished decoding");
        } catch (BenignKafkaException ignored) {
        } catch (Exception e) {
            log.error("kafka decode thread failed: ", e);
            throw e;
        } finally {
            decodeLatch.countDown();
        }
    }

    @SuppressWarnings("BooleanMethodNameMustStartWithQuestion")
    private boolean decodeUnlessEnded() {
        MessageWrapper messageWrapper = null;
        try {
            messageWrapper = messageQueue.poll(1, TimeUnit.SECONDS);
        } catch (InterruptedException e) {
            // ignored
        }
        if (messageWrapper == messageQueueEndMarker) {
            messageQueue.add(messageQueueEndMarker);
            return false;
        }
        if (messageWrapper != null) {
            Bundle bundle = null;
            try {
                byte[] messageBytes = messageWrapper.message;
                // temporary hack to avoid unnecessary array copies to input streams and back (caused by the bundleizer interface)
                // if no bundleizer is specified, then default to calling DataChannelCodec.decodeBundle directly.
                if(bundleizerFactory == null) {
                    bundle = DataChannelCodec.decodeBundle(new ListBundle(format), messageBytes);
                } else {
                    Bundleizer bundleizer = bundleizerFactory.createBundleizer(new ByteArrayInputStream(messageBytes), format);
                    bundle = bundleizer.next();
                }
            } catch (Exception e) {
                log.error("failed to decode bundle from topic: {}, partition: {}",
                          messageWrapper.topic, messageWrapper.partition);
                log.error("decode exception: ", e);
            }
            if (bundle != null) {
                if(injectSourceName != null) {
                    injectSourceName.setValue(bundle, ValueFactory.create(messageWrapper.sourceIdentifier));
                }
                putWhileRunning(bundleQueue, new BundleWrapper(bundle, messageWrapper.sourceIdentifier,
                        messageWrapper.offset+1), running);
            }
        }
        return true;
    }

}
