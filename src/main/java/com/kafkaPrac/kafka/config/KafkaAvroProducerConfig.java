package com.kafkaPrac.kafka.config;

import io.confluent.kafka.serializers.KafkaAvroSerializer;
import io.confluent.kafka.serializers.KafkaAvroSerializerConfig;
import org.apache.kafka.clients.producer.ProducerConfig;
import org.apache.kafka.common.serialization.StringSerializer;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.kafka.core.DefaultKafkaProducerFactory;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.core.ProducerFactory;

import java.util.HashMap;
import java.util.Map;

/**
 * Kafka Producer Configuration with Avro Serialization
 * 
 * KEY CONCEPTS:
 * 1. KafkaAvroSerializer - Serializes Java objects to Avro binary format
 * 2. Schema Registry URL - Where schemas are stored and validated
 * 3. AUTO_REGISTER_SCHEMAS - Automatically register new schemas
 * 
 * FLOW:
 * Employee.java → KafkaAvroSerializer → Schema Registry (stores schema) → Kafka (stores data)
 */
@Configuration
public class KafkaAvroProducerConfig {

    @Value("${spring.kafka.bootstrap-servers}")
    private String bootstrapServers;

    @Value("${schema.registry.url}")
    private String schemaRegistryUrl;

    /**
     * Producer configuration for Avro serialization
     */
    @Bean
    public Map<String, Object> producerConfigs() {
        Map<String, Object> props = new HashMap<>();
        
        // Kafka broker address
        props.put(ProducerConfig.BOOTSTRAP_SERVERS_CONFIG, bootstrapServers);
        
        // Key serializer - String
        props.put(ProducerConfig.KEY_SERIALIZER_CLASS_CONFIG, StringSerializer.class);
        
        // Value serializer - Avro (converts Java objects to Avro binary)
        props.put(ProducerConfig.VALUE_SERIALIZER_CLASS_CONFIG, KafkaAvroSerializer.class);
        
        // Schema Registry URL - where schemas are stored
        props.put(KafkaAvroSerializerConfig.SCHEMA_REGISTRY_URL_CONFIG, schemaRegistryUrl);
        
        // Auto-register schemas when producing messages
        // When true: New schema versions are automatically registered
        // When false: Must manually register schemas
        props.put(KafkaAvroSerializerConfig.AUTO_REGISTER_SCHEMAS, true);
        
        // Reliability settings
        props.put(ProducerConfig.ACKS_CONFIG, "all");
        props.put(ProducerConfig.RETRIES_CONFIG, 3);
        
        return props;
    }

    @Bean
    public ProducerFactory<String, Object> producerFactory() {
        return new DefaultKafkaProducerFactory<>(producerConfigs());
    }

    @Bean
    public KafkaTemplate<String, Object> kafkaTemplate() {
        return new KafkaTemplate<>(producerFactory());
    }
}
