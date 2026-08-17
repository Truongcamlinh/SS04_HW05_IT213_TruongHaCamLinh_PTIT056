package vn.rikkei.logistics.workflow.repository;

import org.springframework.data.jpa.repository.JpaRepository;
import vn.rikkei.logistics.workflow.domain.IncidentReport;

public interface IncidentReportRepository extends JpaRepository<IncidentReport, Long> {
}
