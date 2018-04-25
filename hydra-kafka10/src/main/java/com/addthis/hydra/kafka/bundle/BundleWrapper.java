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

import com.addthis.bundle.core.Bundle;

class BundleWrapper {

    static final BundleWrapper bundleQueueEndMarker = new BundleWrapper(null, null, 0);

    public final Bundle bundle;
    public final String sourceIdentifier;
    public final long offset;

    public BundleWrapper(Bundle bundle, String sourceIdentifier, long offset) {
        this.bundle = bundle;
        this.sourceIdentifier = sourceIdentifier;
        this.offset = offset;
    }
}
