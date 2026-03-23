package com.kafkaPrac.kafka.service;

import com.kafkaPrac.kafka.avro.Employee;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.kafka.core.KafkaTemplate;
import org.springframework.kafka.support.SendResult;
import org.springframework.stereotype.Service;

import java.util.concurrent.CompletableFuture;

/**
 * Kafka Avro Message Publisher
 * 
 * Publishes Employee objects serialized using Avro format.
 * Schema is automatically registered in Schema Registry on first publish.
 * 
 * BENEFITS OF AVRO:
 * 1. Schema stored separately from data (smaller message size)
 * 2. Schema evolution support (add/remove fields without breaking consumers)
 * 3. Strong typing with generated Java classes
 * 4. Binary format (faster serialization/deserialization)
 */
@Service
public class KafkaAvroMessagePublisher {

    private static final Logger log = LoggerFactory.getLogger(KafkaAvroMessagePublisher.class);

    private final KafkaTemplate<String, Object> kafkaTemplate;

    @Value("${app.kafka.topics.employee}")
    private String employeeTopic;

    public KafkaAvroMessagePublisher(KafkaTemplate<String, Object> kafkaTemplate) {
        this.kafkaTemplate = kafkaTemplate;
    }

    /**
     * Publish Employee to Kafka using Avro serialization
     * 
     * FLOW:
     * 1. Employee object passed to KafkaTemplate
     * 2. KafkaAvroSerializer converts to Avro binary
     * 3. Schema registered/validated in Schema Registry
     * 4. Binary data sent to Kafka topic
     */
    public void sendEmployee(Employee employee) {
        log.info("Publishing Employee with Avro: {}", employee);

        CompletableFuture<SendResult<String, Object>> future = 
                kafkaTemplate.send(employeeTopic, String.valueOf(employee.getId()), employee);

        future.whenComplete((result, ex) -> {
            if (ex == null) {
                log.info("Employee sent successfully - Topic: {}, Partition: {}, Offset: {}",
                        result.getRecordMetadata().topic(),
                        result.getRecordMetadata().partition(),
                        result.getRecordMetadata().offset());
            } else {
                log.error("Failed to send Employee: {}", ex.getMessage());
            }
        });
    }

    /**
     * Publish Employee to specific partition
     */
    public void sendEmployeeToPartition(Employee employee, int partition) {
        log.info("Publishing Employee to partition {}: {}", partition, employee);

        kafkaTemplate.send(employeeTopic, partition, String.valueOf(employee.getId()), employee)
                .whenComplete((result, ex) -> {
                    if (ex == null) {
                        log.info("Employee sent to partition {} - Offset: {}",
                                result.getRecordMetadata().partition(),
                                result.getRecordMetadata().offset());
                    } else {
                        log.error("Failed to send Employee: {}", ex.getMessage());
                    }
                });
    }
}
