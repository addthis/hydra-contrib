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

import java.util.ArrayList;
import java.util.Comparator;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import java.util.Properties;
import java.util.Collections;

import org.apache.kafka.clients.consumer.KafkaConsumer;
import org.apache.kafka.common.PartitionInfo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

public final class ConsumerUtils {

    private static final Logger log = LoggerFactory.getLogger(ConsumerUtils.class);

    public static Map<String, List<PartitionInfo>> getTopicsMetadata(Properties consumerProperties, List<String> topics) {
        KafkaConsumer consumer = null;
        Map<String, List<PartitionInfo>> topicsMetadata = null;
        try {
            consumer = new KafkaConsumer(consumerProperties);
            topicsMetadata = new HashMap<>();
            for(String topic : topics){
                List<PartitionInfo> toBeSortedPartitionInfo = consumer.partitionsFor(topic);
                List<PartitionInfo> sortedList = new ArrayList<>();
                for(PartitionInfo partitionInfo : toBeSortedPartitionInfo){
                    sortedList.add(partitionInfo);
                }
                Collections.sort(sortedList, (PartitionInfo p1, PartitionInfo p2)->(p1.partition()-p2.partition()));
                topicsMetadata.put(topic, sortedList);
            }
        } catch (Exception e) {
            log.error("getTopicsMetadata failed: ", e);
        } finally {
            if(consumer != null) {
                consumer.close();
            }
        }
        return topicsMetadata;
    }
}
