package com.addthis.hydra.kafka.metrics;

import com.addthis.metrics.reporter.config.ReporterConfig;

import org.apache.kafka.common.metrics.KafkaMetric;
import org.apache.kafka.common.metrics.MetricsReporter;
import org.slf4j.LoggerFactory;

import java.util.List;
import java.util.Map;

public class MetricsReporterWrapper implements MetricsReporter {

    private static final org.slf4j.Logger log = LoggerFactory.getLogger(MetricsReporterWrapper.class);

    @Override
    public void configure(Map<String, ?> configs) {
        Object reporterConfigFile = configs.get("reporter.config.file");
        try {
            ReporterConfig.loadFromFileAndValidate((String)reporterConfigFile).enableAll();
        } catch (Exception e) {
            log.error("failed load and start reporter config using file: " + reporterConfigFile, e);
        }
    }

    @Override
    public void init(List<KafkaMetric> metrics) {

    }

    @Override
    public void metricChange(KafkaMetric metric) {

    }

    @Override
    public void metricRemoval(KafkaMetric metric) {

    }

    @Override
    public void close() {
    }
}
