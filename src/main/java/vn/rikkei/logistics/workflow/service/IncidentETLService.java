package vn.rikkei.logistics.workflow.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.support.TransactionTemplate;
import vn.rikkei.logistics.workflow.alert.ConsoleAlertService;
import vn.rikkei.logistics.workflow.domain.IncidentData;
import vn.rikkei.logistics.workflow.domain.IncidentReport;
import vn.rikkei.logistics.workflow.domain.NotificationStatus;
import vn.rikkei.logistics.workflow.domain.UrgencyLevel;
import vn.rikkei.logistics.workflow.repository.IncidentReportRepository;

import java.time.LocalDateTime;
import java.util.regex.Matcher;
import java.util.regex.Pattern;

@Service
public class IncidentETLService {

    private static final Logger log = LoggerFactory.getLogger(IncidentETLService.class);
    private static final Pattern ORDER_CODE_PATTERN = Pattern.compile("ORDER[:\\- ]([A-Z0-9]+)", Pattern.CASE_INSENSITIVE);
    private static final Pattern VEHICLE_PLATE_PATTERN = Pattern.compile("PLATE[:\\- ]([A-Z0-9.-]+)", Pattern.CASE_INSENSITIVE);

    private final IncidentReportRepository repository;
    private final ConsoleAlertService consoleAlertService;
    private final TransactionTemplate transactionTemplate;

    public IncidentETLService(
            IncidentReportRepository repository,
            ConsoleAlertService consoleAlertService,
            TransactionTemplate transactionTemplate
    ) {
        this.repository = repository;
        this.consoleAlertService = consoleAlertService;
        this.transactionTemplate = transactionTemplate;
    }

    public IncidentReport processDriverMessage(String rawMessage) {
        IncidentData incidentData = extract(rawMessage);
        validate(incidentData);

        IncidentReport savedReport = saveIncidentPhaseOne(incidentData);

        if (!savedReport.isEmergency()) {
            log.info("WORKFLOW DONE: incidentId={}, alertStatus={}",
                    savedReport.getId(), savedReport.getNotificationStatus());
            return savedReport;
        }

        try {
            consoleAlertService.publishEmergencyAlert(savedReport);
            return updateAlertStatusPhaseTwo(savedReport.getId(), NotificationStatus.SUCCESS, null);
        } catch (RuntimeException exception) {
            log.error("ALERT ISOLATED FAILURE: incidentId={}, orderCode={}, error={}",
                    savedReport.getId(), savedReport.getOrderCode(), exception.getMessage(), exception);
            return updateAlertStatusPhaseTwo(savedReport.getId(), NotificationStatus.FAILED, exception.getMessage());
        }
    }

    private IncidentReport saveIncidentPhaseOne(IncidentData incidentData) {
        return transactionTemplate.execute(status -> {
            NotificationStatus initialStatus = isEmergency(incidentData.urgencyLevel())
                    ? NotificationStatus.PENDING
                    : NotificationStatus.NOT_REQUIRED;

            IncidentReport report = new IncidentReport(
                    incidentData.orderCode(),
                    incidentData.vehiclePlate(),
                    incidentData.urgencyLevel(),
                    incidentData.description(),
                    incidentData.incidentTime(),
                    initialStatus
            );

            IncidentReport saved = repository.save(report);
            log.info("PHASE 1 COMMITTED: incidentId={}, orderCode={}, urgency={}, notificationStatus={}",
                    saved.getId(), saved.getOrderCode(), saved.getUrgencyLevel(), saved.getNotificationStatus());
            return saved;
        });
    }

    private IncidentReport updateAlertStatusPhaseTwo(
            Long incidentId,
            NotificationStatus status,
            String errorMessage
    ) {
        return transactionTemplate.execute(transactionStatus -> {
            IncidentReport report = repository.findById(incidentId)
                    .orElseThrow(() -> new IllegalStateException("Incident report not found: " + incidentId));

            if (status == NotificationStatus.SUCCESS) {
                report.markAlertSuccess();
            } else if (status == NotificationStatus.FAILED) {
                report.markAlertFailed(errorMessage);
            }

            IncidentReport updated = repository.save(report);
            log.info("PHASE 2 COMMITTED: incidentId={}, notificationStatus={}, error={}",
                    updated.getId(), updated.getNotificationStatus(), updated.getNotificationErrorMessage());
            return updated;
        });
    }

    private IncidentData extract(String rawMessage) {
        if (rawMessage == null || rawMessage.isBlank()) {
            throw new IllegalArgumentException("Raw driver message must not be blank");
        }

        return new IncidentData(
                matchOrDefault(ORDER_CODE_PATTERN, rawMessage, "UNKNOWN"),
                matchOrDefault(VEHICLE_PLATE_PATTERN, rawMessage, "UNKNOWN"),
                parseUrgency(rawMessage),
                rawMessage.trim(),
                LocalDateTime.now()
        );
    }

    private void validate(IncidentData incidentData) {
        if (incidentData.orderCode().isBlank()) {
            throw new IllegalArgumentException("orderCode must not be blank");
        }

        if (incidentData.vehiclePlate().isBlank()) {
            throw new IllegalArgumentException("vehiclePlate must not be blank");
        }

        if (incidentData.urgencyLevel() == null) {
            throw new IllegalArgumentException("urgencyLevel must not be null");
        }
    }

    private boolean isEmergency(UrgencyLevel urgencyLevel) {
        return urgencyLevel == UrgencyLevel.HIGH || urgencyLevel == UrgencyLevel.CRITICAL;
    }

    private UrgencyLevel parseUrgency(String rawMessage) {
        String upper = rawMessage.toUpperCase();

        if (upper.contains("CRITICAL")) {
            return UrgencyLevel.CRITICAL;
        }
        if (upper.contains("HIGH")) {
            return UrgencyLevel.HIGH;
        }
        if (upper.contains("MEDIUM")) {
            return UrgencyLevel.MEDIUM;
        }
        return UrgencyLevel.LOW;
    }

    private String matchOrDefault(Pattern pattern, String text, String defaultValue) {
        Matcher matcher = pattern.matcher(text);
        return matcher.find() ? matcher.group(1).toUpperCase() : defaultValue;
    }
}
