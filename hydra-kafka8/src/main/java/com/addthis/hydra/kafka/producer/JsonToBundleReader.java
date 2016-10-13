package com.addthis.hydra.kafka.producer;

import com.addthis.bundle.core.Bundle;
import com.addthis.bundle.core.Bundles;
import com.addthis.bundle.io.DataChannelCodec;
import kafka.producer.KeyedMessage;
import kafka.tools.ConsoleProducer;
import org.eclipse.jetty.io.RuntimeIOException;

import java.io.IOException;
import java.io.InputStream;

import java.util.Properties;
import java.util.Scanner;

public class JsonToBundleReader implements ConsoleProducer.MessageReader {

    String topic;
    Scanner scanner;

    @Override
    public void init(InputStream inputStream, Properties props) {
        this.topic = props.getProperty("topic");
        this.scanner = new Scanner(inputStream);
    }

    @Override
    public KeyedMessage<byte[], byte[]> readMessage() {
        try {
            String line = this.scanner.nextLine();
            Bundle bundle = Bundles.decode(line);
            return new KeyedMessage<>(topic, DataChannelCodec.encodeBundle(bundle));
        } catch (IOException e) {
            throw new RuntimeIOException(e);
        }
    }

    @Override
    public void close() {
        this.scanner.close();
    }
}
