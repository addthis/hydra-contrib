package com.addthis.basis.kafka.bundle;


import com.addthis.bundle.core.Bundle;
import com.addthis.bundle.core.Bundles;
import com.addthis.bundle.io.DataChannelCodec;
import org.apache.kafka.common.serialization.Serializer;

import java.io.IOException;
import java.util.Map;


public class BundleJsonSerializer implements Serializer<Bundle> {

    @Override
    public void configure(Map<String, ?> configs, boolean b) {
        // not needed
    }

    @Override
    public byte[] serialize(String topic, Bundle bundle) {
        return Bundles.toJsonString(bundle).getBytes();
    }

    @Override
    public void close() {
        // not needed
    }
}
