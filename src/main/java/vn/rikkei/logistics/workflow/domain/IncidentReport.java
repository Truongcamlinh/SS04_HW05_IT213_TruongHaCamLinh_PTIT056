package vn.rikkei.logistics.workflow.domain;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.EnumType;
import jakarta.persistence.Enumerated;
import jakarta.persistence.GeneratedValue;
import jakarta.persistence.GenerationType;
import jakarta.persistence.Id;
import jakarta.persistence.Table;

import java.time.LocalDateTime;

@Entity
@Table(name = "incident_reports")
public class IncidentReport {

    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    private Long id;

    @Column(nullable = false)
    private String orderCode;

    @Column(nullable = false)
    private String vehiclePlate;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private UrgencyLevel urgencyLevel;

    @Column(nullable = false, length = 1200)
    private String description;

    @Column(nullable = false)
    private LocalDateTime incidentTime;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private NotificationStatus notificationStatus;

    @Column(length = 1200)
    private String notificationErrorMessage;

    protected IncidentReport() {
    }

    public IncidentReport(
            String orderCode,
            String vehiclePlate,
            UrgencyLevel urgencyLevel,
            String description,
            LocalDateTime incidentTime,
            NotificationStatus notificationStatus
    ) {
        this.orderCode = orderCode;
        this.vehiclePlate = vehiclePlate;
        this.urgencyLevel = urgencyLevel;
        this.description = description;
        this.incidentTime = incidentTime;
        this.notificationStatus = notificationStatus;
    }

    public Long getId() {
        return id;
    }

    public String getOrderCode() {
        return orderCode;
    }

    public String getVehiclePlate() {
        return vehiclePlate;
    }

    public UrgencyLevel getUrgencyLevel() {
        return urgencyLevel;
    }

    public String getDescription() {
        return description;
    }

    public LocalDateTime getIncidentTime() {
        return incidentTime;
    }

    public NotificationStatus getNotificationStatus() {
        return notificationStatus;
    }

    public String getNotificationErrorMessage() {
        return notificationErrorMessage;
    }

    public boolean isEmergency() {
        return urgencyLevel == UrgencyLevel.HIGH || urgencyLevel == UrgencyLevel.CRITICAL;
    }

    public void markAlertSuccess() {
        this.notificationStatus = NotificationStatus.SUCCESS;
        this.notificationErrorMessage = null;
    }

    public void markAlertFailed(String errorMessage) {
        this.notificationStatus = NotificationStatus.FAILED;
        this.notificationErrorMessage = errorMessage;
    }
}
