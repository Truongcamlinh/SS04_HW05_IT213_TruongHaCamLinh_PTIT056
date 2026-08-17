# Bài 5: Workflow sự cố khẩn cấp - Bản khác

## 1. Thiết kế tổng quan

Workflow xử lý tin nhắn tài xế theo mô hình hai pha:

```text
Phase 1: ETL va luu IncidentReport vao DB
Phase 2: Phat Console Alert va cap nhat notificationStatus
```

Mục tiêu quan trọng nhất là cô lập lỗi cảnh báo. Nếu `ConsoleAlertService` lỗi, dữ liệu sự cố không bị rollback vì đã được commit ở phase 1.

## 2. ASCII Flow Diagram

```text
+-----------------------------+
| Raw driver message          |
| ORDER, PLATE, urgency, desc |
+--------------+--------------+
               |
               v
+-----------------------------+
| IncidentETLService          |
| processDriverMessage(...)   |
+--------------+--------------+
               |
               v
+-----------------------------+
| Extract                     |
| - orderCode                 |
| - vehiclePlate              |
| - urgencyLevel              |
| - description               |
| - incidentTime              |
+--------------+--------------+
               |
               v
+-----------------------------+
| Validate                    |
| - orderCode                 |
| - vehiclePlate              |
| - urgencyLevel              |
+--------------+--------------+
               |
               v
+-----------------------------+
| Save DB Phase 1             |
| transaction #1              |
| status = PENDING            |
| if HIGH/CRITICAL            |
+--------------+--------------+
               |
               v
       +----------------+
       | Emergency ?    |
       +-------+--------+
               |
      +--------+---------+
      |                  |
      v                  v
+------------+   +-----------------------+
| No alert   |   | ConsoleAlertService   |
| status     |   | publishEmergencyAlert |
| NOT_REQUIRED|  +-----------+-----------+
+------------+               |
                       +-----+------+
                       |            |
                       v            v
              +-------------+  +----------------+
              | Alert OK    |  | Alert failed   |
              | status      |  | catch error    |
              | SUCCESS     |  | log detail     |
              +------+------+  | status FAILED  |
                     |         +-------+--------+
                     |                 |
                     v                 v
              +-------------------------------+
              | Update DB Phase 2             |
              | transaction #2                |
              | SUCCESS or FAILED persisted   |
              +-------------------------------+
```

## 3. Giải pháp chịu lỗi

### Tách hai pha transaction

Phase 1 lưu `IncidentReport` trước:

```java
IncidentReport saved = repository.save(report);
```

Nếu sự cố là `HIGH` hoặc `CRITICAL`, trạng thái ban đầu:

```text
PENDING
```

Nếu không cần alert:

```text
NOT_REQUIRED
```

Sau khi phase 1 commit, hệ thống mới gọi `ConsoleAlertService`.

### Cô lập lỗi bằng try-catch

Alert là side effect bên ngoài DB. Nó có thể lỗi vì console channel bận, thiết bị phát tín hiệu lỗi hoặc hệ thống cảnh báo quá tải. Vì vậy alert được đặt trong `try-catch`:

```java
try {
    consoleAlertService.publishEmergencyAlert(savedReport);
    return updateAlertStatusPhaseTwo(savedReport.getId(), NotificationStatus.SUCCESS, null);
} catch (RuntimeException exception) {
    log.error("ALERT ISOLATED FAILURE: ...", exception);
    return updateAlertStatusPhaseTwo(savedReport.getId(), NotificationStatus.FAILED, exception.getMessage());
}
```

Nếu alert lỗi:

- Không ném exception ra ngoài làm hỏng workflow chính.
- Không rollback dữ liệu sự cố đã lưu.
- Ghi log stacktrace chi tiết.
- Cập nhật `notificationStatus = FAILED`.
- Lưu `notificationErrorMessage` để nhân viên kỹ thuật tra cứu.

## 4. Mã nguồn chính

```text
src/main/java/vn/rikkei/logistics/workflow/alert/ConsoleAlertService.java
src/main/java/vn/rikkei/logistics/workflow/domain/IncidentReport.java
src/main/java/vn/rikkei/logistics/workflow/domain/NotificationStatus.java
src/main/java/vn/rikkei/logistics/workflow/repository/IncidentReportRepository.java
src/main/java/vn/rikkei/logistics/workflow/service/IncidentETLService.java
src/main/java/vn/rikkei/logistics/workflow/demo/DemoRunner.java
```

## 5. Chạy thử

```bash
./gradlew clean build -x test
./gradlew bootRun
```

`DemoRunner` chạy tự động hai case:

```text
Case A: HIGH incident -> alert success -> SUCCESS
Case B: CRITICAL incident + alert-busy -> alert failed -> FAILED
```

## 6. Minh chứng chạy thực tế

Build:

```text
> Task :clean UP-TO-DATE
> Task :compileJava
> Task :processResources
> Task :classes
> Task :resolveMainClassName
> Task :bootJar
> Task :jar
> Task :assemble
> Task :check
> Task :build

BUILD SUCCESSFUL in 718ms
```

### Case A - Alert thành công

```text
2026-08-17T14:55:27.251+07:00  INFO  v.r.logistics.workflow.demo.DemoRunner
: ========== CASE A: ALERT SUCCESS ==========

2026-08-17T14:55:27.284+07:00  INFO  v.r.l.w.service.IncidentETLService
: PHASE 1 COMMITTED: incidentId=1, orderCode=RK5001, urgency=HIGH, notificationStatus=PENDING

2026-08-17T14:55:27.287+07:00  WARN  v.r.l.w.alert.ConsoleAlertService
:
+====================================================================+
|                      EMERGENCY INCIDENT ALERT                       |
+====================================================================+
| Incident ID : 1
| Order Code  : RK5001
| Vehicle     : 30F99999
| Urgency     : HIGH
| Time        : 2026-08-17T14:55:27.251889
| Description : ORDER:RK5001 PLATE:30F99999 HIGH - Xe tai giao hang gap tai nan nhe, can dieu phoi xe thay the trong 60 phut.
+====================================================================+

2026-08-17T14:55:27.295+07:00  INFO  v.r.l.w.service.IncidentETLService
: PHASE 2 COMMITTED: incidentId=1, notificationStatus=SUCCESS, error=null

2026-08-17T14:55:27.298+07:00  INFO  v.r.logistics.workflow.demo.DemoRunner
: CASE A RESULT: incidentId=1, orderCode=RK5001, status=SUCCESS
```

### Case B - Alert thất bại nhưng DB vẫn lưu

```text
2026-08-17T14:55:27.298+07:00  INFO  v.r.logistics.workflow.demo.DemoRunner
: ========== CASE B: ALERT FAILED BUT INCIDENT SAVED ==========

2026-08-17T14:55:27.299+07:00  INFO  v.r.l.w.service.IncidentETLService
: PHASE 1 COMMITTED: incidentId=2, orderCode=RK5002, urgency=CRITICAL, notificationStatus=PENDING

2026-08-17T14:55:27.299+07:00 ERROR  v.r.l.w.service.IncidentETLService
: ALERT ISOLATED FAILURE: incidentId=2, orderCode=RK5002, error=Console alert channel is busy for incident 2

java.lang.IllegalStateException: Console alert channel is busy for incident 2
    at vn.rikkei.logistics.workflow.alert.ConsoleAlertService.publishEmergencyAlert(ConsoleAlertService.java:15)
    at vn.rikkei.logistics.workflow.service.IncidentETLService.processDriverMessage(IncidentETLService.java:52)
    at vn.rikkei.logistics.workflow.demo.DemoRunner.run(DemoRunner.java:32)

2026-08-17T14:55:27.300+07:00  INFO  v.r.l.w.service.IncidentETLService
: PHASE 2 COMMITTED: incidentId=2, notificationStatus=FAILED, error=Console alert channel is busy for incident 2

2026-08-17T14:55:27.301+07:00  INFO  v.r.logistics.workflow.demo.DemoRunner
: CASE B RESULT: incidentId=2, orderCode=RK5002, status=FAILED, error=Console alert channel is busy for incident 2
```

Kết luận từ log:

```text
Case A: Incident saved -> alert success -> notificationStatus SUCCESS
Case B: Incident saved -> alert throws exception -> error logged -> notificationStatus FAILED
```
