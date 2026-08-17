package vn.rikkei.logistics.workflow.demo;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.boot.CommandLineRunner;
import org.springframework.stereotype.Component;
import vn.rikkei.logistics.workflow.domain.IncidentReport;
import vn.rikkei.logistics.workflow.service.IncidentETLService;

@Component
public class DemoRunner implements CommandLineRunner {

    private static final Logger log = LoggerFactory.getLogger(DemoRunner.class);

    private final IncidentETLService incidentETLService;

    public DemoRunner(IncidentETLService incidentETLService) {
        this.incidentETLService = incidentETLService;
    }

    @Override
    public void run(String... args) {
        log.info("========== CASE A: ALERT SUCCESS ==========");
        IncidentReport success = incidentETLService.processDriverMessage(
                "ORDER:RK5001 PLATE:30F99999 HIGH - Xe tai giao hang gap tai nan nhe, " +
                        "can dieu phoi xe thay the trong 60 phut."
        );
        log.info("CASE A RESULT: incidentId={}, orderCode={}, status={}",
                success.getId(), success.getOrderCode(), success.getNotificationStatus());

        log.info("========== CASE B: ALERT FAILED BUT INCIDENT SAVED ==========");
        IncidentReport failed = incidentETLService.processDriverMessage(
                "ORDER:RK5002 PLATE:51D77777 CRITICAL - Khoang lanh container mat dien, " +
                        "alert-busy, can xu ly khan cap."
        );
        log.info("CASE B RESULT: incidentId={}, orderCode={}, status={}, error={}",
                failed.getId(), failed.getOrderCode(), failed.getNotificationStatus(),
                failed.getNotificationErrorMessage());
    }
}
