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
package com.addthis.hydra.kafka.producer;


import java.util.Collection;
import java.util.Collections;
import java.util.Map;
import java.util.Properties;

import com.addthis.bundle.core.Bundle;
import com.addthis.hydra.kafka.KafkaUtils;

import org.apache.curator.framework.CuratorFramework;
import org.apache.kafka.clients.producer.KafkaProducer;
import org.apache.kafka.clients.producer.Producer;
import org.apache.kafka.common.Node;

public class ProducerUtils {
    public static String brokerListString(Collection<Node> brokers) {
        StringBuilder stringBuilder = new StringBuilder();
        boolean first = true;
        for(Node broker : brokers) {
            if(!first) {
                stringBuilder.append(",");
            }
            stringBuilder.append(broker.host()).append(":").append(broker.port());
            first = false;
        }
        return stringBuilder.toString();
    }

    public static Properties defaultConfig(String zookeeper, Map<String,String> overrides) {
        CuratorFramework zkClient = null;
        try {
            zkClient = KafkaUtils.newZkClient(zookeeper);
            Collection<Node> brokers = KafkaUtils.getSeedKafkaBrokers(zkClient, 3).values();
            if (brokers.isEmpty()) {
                throw new RuntimeException("no kafka brokers available from zookeeper: " + zookeeper);
            }
            return defaultBootstrapConfig(brokerListString(brokers), overrides);
        } finally {
            if(zkClient != null) {
                zkClient.close();
            }
        }
    }

    public static Properties defaultBootstrapConfig(String bootstrapServers, Map<String,String> overrides) {
        Properties properties = new Properties();
        properties.put("bootstrap.servers", bootstrapServers);
        properties.put("acks", "all");
        properties.put("compression.type", "gzip");
        properties.put("linger.ms", "3000");
        properties.put("retries", "3");
        properties.put("block.on.buffer.full", "false");
        properties.putAll(overrides);
        return properties;
    }

    public static Properties defaultConfig(String zookeeper) {
        return defaultConfig(zookeeper, Collections.emptyMap());
    }

    public static Properties defaultBootstrapConfig(String bootstrapServers) {
        return defaultBootstrapConfig(bootstrapServers, Collections.emptyMap());
    }

    public static Producer<Bundle,Bundle> newBundleProducer(String zookeeper, Map<String,String> overrides) {
        BundleEncoder encoder = new BundleEncoder();
        return new KafkaProducer<>(defaultConfig(zookeeper, overrides), encoder, encoder);
    }

    public static Producer<Bundle,Bundle> newBundleProducer(String zookeeper) {
        return newBundleProducer(zookeeper, Collections.emptyMap());
    }

    public static Producer<Bundle,Bundle> newBundleProducer(String zookeeper, String topicSuffix, Map<String,String> overrides) {
        BundleEncoder encoder = new BundleEncoder();
        return new TopicSuffixProducer<>(new KafkaProducer<>(defaultConfig(zookeeper, overrides), encoder, encoder), topicSuffix);
    }

    public static Producer<Bundle,Bundle> newBundleProducer(String zookeeper, String topicSuffix) {
        return newBundleProducer(zookeeper, topicSuffix, Collections.emptyMap());
    }
}
