package com.addthis.hydra.kafka.consumer;


import java.io.IOException;
import java.io.InputStream;

import com.addthis.basis.util.LessBytes;

import com.addthis.bundle.core.Bundle;
import com.addthis.bundle.core.BundleFactory;
import com.addthis.bundle.io.DataChannelCodec;
import com.addthis.hydra.task.source.bundleizer.Bundleizer;
import com.addthis.hydra.task.source.bundleizer.BundleizerFactory;

public class KafkaChannelBundleizer extends BundleizerFactory {

    @Override
    public Bundleizer createBundleizer(final InputStream input, final BundleFactory factory) {
        return new Bundleizer() {
            @Override
            public Bundle next() throws IOException {
                return DataChannelCodec.decodeBundle(factory.createBundle(), LessBytes.readFully(input));
            }
        };
    }

}
