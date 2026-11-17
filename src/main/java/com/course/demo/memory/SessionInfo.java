package com.course.demo.memory;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class SessionInfo {
    private String sessionId;
    private Instant creationTime;
    private Instant lastAccessedTime;
    private int maxInactiveIntervalSeconds;
}
