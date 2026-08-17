package vn.rikkei.logistics.workflow.domain;

import java.time.LocalDateTime;

public record IncidentData(
        String orderCode,
        String vehiclePlate,
        UrgencyLevel urgencyLevel,
        String description,
        LocalDateTime incidentTime
) {
}
