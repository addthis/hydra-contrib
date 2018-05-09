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

class MessageWrapper {

    static final MessageWrapper messageQueueEndMarker = new MessageWrapper(0, null, null, 0, null);

    public final long offset;
    public final byte[] message;
    public final String topic;
    public final int partition;
    public final String sourceIdentifier;

    MessageWrapper(long offset, byte[] message, String topic, int partition, String sourceIdentifier) {
        this.offset = offset;
        this.message = message;
        this.topic = topic;
        this.partition = partition;
        this.sourceIdentifier = sourceIdentifier;
    }
}
