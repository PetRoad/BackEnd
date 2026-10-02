package com.petroad.backend.api;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RestController;
import javax.sql.DataSource;
import java.sql.Connection;
import java.sql.SQLException;
import java.util.Map;

@RestController
public class HealthController {
    private final DataSource dataSource;
    private final String commit;

    public HealthController(DataSource dataSource, @Value("${app.commit:local}") String commit) {
        this.dataSource = dataSource;
        this.commit = commit;
    }

    @GetMapping("/api/health")
    public ResponseEntity<Map<String, String>> health() {
        try (Connection connection = dataSource.getConnection()) {
            if (connection.isValid(2)) return ResponseEntity.ok(Map.of("status", "ok", "commit", commit));
        } catch (SQLException ignored) { }
        return ResponseEntity.status(503).body(Map.of("status", "unavailable", "commit", commit));
    }
}
