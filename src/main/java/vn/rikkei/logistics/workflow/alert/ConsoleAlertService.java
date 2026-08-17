package vn.rikkei.logistics.workflow.alert;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import vn.rikkei.logistics.workflow.domain.IncidentReport;

@Service
public class ConsoleAlertService {

    private static final Logger log = LoggerFactory.getLogger(ConsoleAlertService.class);

    public void publishEmergencyAlert(IncidentReport report) {
        if (report.getDescription().toLowerCase().contains("alert-busy")) {
            throw new IllegalStateException("Console alert channel is busy for incident " + report.getId());
        }

        log.warn("""

                +====================================================================+
                |                      EMERGENCY INCIDENT ALERT                       |
                +====================================================================+
                | Incident ID : {}
                | Order Code  : {}
                | Vehicle     : {}
                | Urgency     : {}
                | Time        : {}
                | Description : {}
                +====================================================================+
                """,
                report.getId(),
                report.getOrderCode(),
                report.getVehiclePlate(),
                report.getUrgencyLevel(),
                report.getIncidentTime(),
                report.getDescription()
        );
    }
}
