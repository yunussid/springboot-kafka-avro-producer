package com.kafkaPrac.kafka.controller;

import com.kafkaPrac.kafka.avro.Employee;
import com.kafkaPrac.kafka.dto.EmployeeRequest;
import com.kafkaPrac.kafka.service.KafkaAvroMessagePublisher;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.*;

/**
 * REST Controller for publishing Employee events using Avro
 * 
 * ENDPOINTS:
 * POST /avro-producer/employee          - Publish employee (Kafka decides partition)
 * POST /avro-producer/employee/partition/{partition} - Publish to specific partition
 */
@RestController
@RequestMapping("/avro-producer")
public class EmployeeController {

    private final KafkaAvroMessagePublisher publisher;

    public EmployeeController(KafkaAvroMessagePublisher publisher) {
        this.publisher = publisher;
    }

    /**
     * Publish Employee using Avro serialization
     * 
     * Example:
     * POST /avro-producer/employee
     * {
     *   "id": 1,
     *   "name": "John Doe",
     *   "email": "john@company.com",
     *   "department": "Engineering",
     *   "salary": 75000.00,
     *   "phoneNumber": "+1-555-0123",
     *   "address": "123 Main St"
     * }
     */
    @PostMapping("/employee")
    public ResponseEntity<String> publishEmployee(@RequestBody EmployeeRequest request) {
        // Convert DTO to Avro-generated Employee class
        Employee employee = Employee.newBuilder()
                .setId(request.getId())
                .setName(request.getName())
                .setEmail(request.getEmail())
                .setDepartment(request.getDepartment())
                .setSalary(request.getSalary())
                .setPhoneNumber(request.getPhoneNumber())  // Can be null
                .setAddress(request.getAddress())          // Can be null
                .build();

        publisher.sendEmployee(employee);

        return ResponseEntity.ok("Employee published with Avro serialization - ID: " + employee.getId());
    }

    /**
     * Publish Employee to specific partition
     */
    @PostMapping("/employee/partition/{partition}")
    public ResponseEntity<String> publishEmployeeToPartition(
            @RequestBody EmployeeRequest request,
            @PathVariable int partition) {

        Employee employee = Employee.newBuilder()
                .setId(request.getId())
                .setName(request.getName())
                .setEmail(request.getEmail())
                .setDepartment(request.getDepartment())
                .setSalary(request.getSalary())
                .setPhoneNumber(request.getPhoneNumber())
                .setAddress(request.getAddress())
                .build();

        publisher.sendEmployeeToPartition(employee, partition);

        return ResponseEntity.ok("Employee published to partition " + partition + " - ID: " + employee.getId());
    }

    /**
     * Demo endpoint showing schema evolution
     * Publishes employee with only required fields (phoneNumber and address are null)
     */
    @PostMapping("/employee/minimal")
    public ResponseEntity<String> publishMinimalEmployee(@RequestBody EmployeeRequest request) {
        // Only set required fields - optional fields default to null
        Employee employee = Employee.newBuilder()
                .setId(request.getId())
                .setName(request.getName())
                .setEmail(request.getEmail())
                .setDepartment(request.getDepartment())
                .setSalary(request.getSalary())
                // phoneNumber and address are optional (nullable)
                .build();

        publisher.sendEmployee(employee);

        return ResponseEntity.ok("Minimal Employee published (without optional fields) - ID: " + employee.getId());
    }
}
