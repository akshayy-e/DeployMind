package com.deploymind.service;

import com.deploymind.model.Incident;
import org.springframework.stereotype.Service;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.Optional;
import java.util.concurrent.atomic.AtomicInteger;
import com.deploymind.model.CreateIncidentRequest;

@Service
public class IncidentService {
    private final List<Incident> incidents = new ArrayList<>();
    private final AtomicInteger counter = new AtomicInteger(5);

    public IncidentService() {
        incidents.add(new Incident("INC-001", "Payment API", "Database connection timeout", "CRITICAL", "RESOLVED",
                "v2.7.1", "psycopg2.OperationalError: connection pool timeout",
                List.of("API latency spikes", "Database connections exhausted", "5xx responses increased"),
                List.of("Connection-pool configuration changed"), "2026-06-14T10:15:00Z",
                "Connection leak introduced by a pool configuration change",
                "Rolled back the connection-pool configuration and restarted the affected service"));

        incidents.add(new Incident("INC-002", "Payment API", "Repeated DB connection exhaustion", "HIGH", "RESOLVED",
                "v2.7.8", "Timeout waiting for database connection",
                List.of("Connection wait time increased", "Intermittent 502s"),
                List.of("Pool max-size increased"), "2026-07-02T16:20:00Z",
                "Pool size increase masked an application connection leak",
                "Reverted pool-size change and patched connection lifecycle"));

        incidents.add(new Incident("INC-003", "Orders API", "Redis timeout after deployment", "HIGH", "RESOLVED",
                "v4.2.0", "Redis timeout while fetching order session",
                List.of("Session reads fail", "Latency spikes"),
                List.of("Redis client retry policy changed"), "2026-08-19T08:42:00Z",
                "Retry policy caused request amplification",
                "Reverted retry policy and deployed bounded backoff"));

        incidents.add(new Incident("INC-004", "Payment API", "Database connection timeout after release", "CRITICAL", "OPEN",
                "v2.8.4", "Timeout waiting for database connection",
                List.of("Payment requests failing", "Connection pool saturated", "5xx responses increased"),
                List.of("Connection-pool configuration modified"), Instant.now().toString(), null, null));
    }


    private final AtomicInteger challenge = new AtomicInteger(0);

    public Incident createChallenge() {
        int n = challenge.getAndIncrement() % 5;
        CreateIncidentRequest r = switch (n) {
            case 0 -> request("Checkout API", "Redis timeout after deployment", "HIGH", "v3.4.2", "Timeout waiting for Redis connection", List.of("Checkout sessions failing", "Latency spikes", "5xx responses increased"), List.of("Redis client retry policy changed"));
            case 1 -> request("Order Processor", "Kafka consumer lag increasing", "HIGH", "v4.8.1", "Consumer processing timeout", List.of("Lag increasing by partition", "Messages aging", "Throughput dropped"), List.of("Consumer concurrency changed"));
            case 2 -> request("Identity API", "Users receiving unauthorized errors", "CRITICAL", "v6.1.0", "401 invalid token", List.of("Login failures increased", "Only new tokens fail", "Authorization errors spike"), List.of("JWT issuer configuration changed"));
            case 3 -> request("Analytics API", "Out of memory after release", "CRITICAL", "v5.2.0", "java.lang.OutOfMemoryError: Java heap space", List.of("Heap usage reaches 98%", "GC pauses increase", "Pods restart"), List.of("New report processing module deployed"));
            default -> request("Orders API", "API latency spike", "HIGH", "v7.3.4", "p99 response time above 4s", List.of("p95 and p99 latency increased", "Database calls slower", "Traffic is normal"), List.of("ORM dependency upgraded"));
        };
        return create(r);
    }

    private CreateIncidentRequest request(String service, String title, String severity, String deployment, String error, List<String> symptoms, List<String> changes) {
        CreateIncidentRequest r = new CreateIncidentRequest();
        r.setService(service); r.setTitle(title); r.setSeverity(severity); r.setDeployment(deployment); r.setError(error); r.setSymptoms(symptoms); r.setRecentChanges(changes);
        return r;
    }

    public List<Incident> all() { return incidents; }

    public Incident create(CreateIncidentRequest r) {
        String id = String.format("INC-%03d", counter.getAndIncrement());
        Incident i = new Incident(id, r.getService().trim(), r.getTitle().trim(), r.getSeverity().trim().toUpperCase(), "OPEN",
                r.getDeployment().trim(), r.getError().trim(), new ArrayList<>(r.getSymptoms()), new ArrayList<>(r.getRecentChanges()),
                java.time.Instant.now().toString(), null, null);
        incidents.add(0, i);
        return i;
    }

    public Optional<Incident> find(String id) {
        return incidents.stream().filter(i -> i.getId().equals(id)).findFirst();
    }
}
