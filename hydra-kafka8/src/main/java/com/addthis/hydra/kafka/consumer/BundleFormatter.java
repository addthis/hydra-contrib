package com.addthis.hydra.kafka.consumer;

import java.io.IOException;
import java.io.PrintStream;
import java.io.UncheckedIOException;

import java.util.Properties;

import com.addthis.bundle.core.Bundle;

import kafka.tools.MessageFormatter;

// utility class, primarily intended for use with the kafka-console-consumer
public class BundleFormatter implements MessageFormatter {

    private BundleDecoder decoder = new BundleDecoder();

    @Override
    public void writeTo(byte[] key, byte[] value, PrintStream output) {
        Bundle bundle = decoder.deserialize(null, value);
        try {
            output.write(bundle.toString().getBytes());
            output.write("\n".getBytes());
        } catch (IOException e) {
            throw new UncheckedIOException(e);
        }
    }

    @Override
    public void init(Properties props) {
        // not needed
    }

    @Override
    public void close() {
        // not needed
    }
}
