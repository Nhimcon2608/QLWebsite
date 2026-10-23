package com.mycompany.do_an_ltm_2380600550.entity;

import jakarta.persistence.*;
import java.time.LocalDateTime;

@Entity
@Table(name = "websites")
public class Website {

  @Id
  @GeneratedValue(strategy = GenerationType.IDENTITY)
  private Long id;

  @Column(nullable = false)
  private String name;

  @Column(nullable = false, length = 500)
  private String url;

  // Tần suất kiểm tra (tính bằng giây), mặc định 60s
  private Integer checkIntervalSeconds = 60;

  // Trạng thái cho phép theo dõi hay không
  private Boolean isActive = true;

  private LocalDateTime createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);

  public Website() {}

  public Website(String name, String url, Integer checkIntervalSeconds, Boolean isActive) {
    this.name = name;
    this.url = url;
    if (checkIntervalSeconds != null) {
      this.checkIntervalSeconds = checkIntervalSeconds;
    }
    if (isActive != null) {
      this.isActive = isActive;
    }
    this.createdAt = LocalDateTime.now(java.time.ZoneOffset.UTC);
  }

  // Getters & Setters
  public Long getId() {
    return id;
  }

  public void setId(Long id) {
    this.id = id;
  }

  public String getName() {
    return name;
  }

  public void setName(String name) {
    this.name = name;
  }

  public String getUrl() {
    return url;
  }

  public void setUrl(String url) {
    this.url = url;
  }

  public Integer getCheckIntervalSeconds() {
    return checkIntervalSeconds;
  }

  public void setCheckIntervalSeconds(Integer checkIntervalSeconds) {
    this.checkIntervalSeconds = checkIntervalSeconds;
  }

  public Boolean getIsActive() {
    return isActive;
  }

  public void setIsActive(Boolean isActive) {
    this.isActive = isActive;
  }

  public LocalDateTime getCreatedAt() {
    return createdAt;
  }

  public void setCreatedAt(LocalDateTime createdAt) {
    this.createdAt = createdAt;
  }

  @Transient private boolean maintenanceActive;

  public boolean getMaintenanceActive() {
    return maintenanceActive;
  }

  public void setMaintenanceActive(boolean value) {
    maintenanceActive = value;
  }

  private Integer timeoutMs = 10000;
  private Integer slowThresholdMs = 2000;
  private Integer failureThreshold = 1;
  private String expectedCodes = "200-399";

  @Column(length = 1000)
  private String description = "";

  private String groupName = "";
  private String environment = "";

  @Column(length = 1000)
  private String tags = "";

  private String monitoringStatus = "UNKNOWN";
  private Integer consecutiveFailures = 0;
  private LocalDateTime firstFailureAt = null;
  private LocalDateTime lastCheckedAt = null;
  private LocalDateTime nextCheckAt = null;
  private LocalDateTime archivedAt = null;
  private LocalDateTime sslExpiresAt = null;
  private LocalDateTime sslCheckedAt = null;

  @Column(length = 1000)
  private String sslError = null;

  private Integer sslWarnDays = 30;
  private String sslAlertKey = null;

  public Integer getTimeoutMs() {
    return timeoutMs == null ? 10000 : timeoutMs;
  }

  public void setTimeoutMs(Integer value) {
    this.timeoutMs = value;
  }

  public Integer getSlowThresholdMs() {
    return slowThresholdMs == null ? 2000 : slowThresholdMs;
  }

  public void setSlowThresholdMs(Integer value) {
    this.slowThresholdMs = value;
  }

  public Integer getFailureThreshold() {
    return failureThreshold == null ? 1 : failureThreshold;
  }

  public void setFailureThreshold(Integer value) {
    this.failureThreshold = value;
  }

  public String getExpectedCodes() {
    return expectedCodes == null ? "200-399" : expectedCodes;
  }

  public void setExpectedCodes(String value) {
    this.expectedCodes = value;
  }

  public String getDescription() {
    return description == null ? "" : description;
  }

  public void setDescription(String value) {
    this.description = value;
  }

  public String getGroupName() {
    return groupName == null ? "" : groupName;
  }

  public void setGroupName(String value) {
    this.groupName = value;
  }

  public String getEnvironment() {
    return environment == null ? "" : environment;
  }

  public void setEnvironment(String value) {
    this.environment = value;
  }

  public String getTags() {
    return tags == null ? "" : tags;
  }

  public void setTags(String value) {
    this.tags = value;
  }

  public String getMonitoringStatus() {
    return monitoringStatus == null ? "UNKNOWN" : monitoringStatus;
  }

  public void setMonitoringStatus(String value) {
    this.monitoringStatus = value;
  }

  public Integer getConsecutiveFailures() {
    return consecutiveFailures == null ? 0 : consecutiveFailures;
  }

  public void setConsecutiveFailures(Integer value) {
    this.consecutiveFailures = value;
  }

  public LocalDateTime getFirstFailureAt() {
    return firstFailureAt;
  }

  public void setFirstFailureAt(LocalDateTime value) {
    this.firstFailureAt = value;
  }

  public LocalDateTime getLastCheckedAt() {
    return lastCheckedAt;
  }

  public void setLastCheckedAt(LocalDateTime value) {
    this.lastCheckedAt = value;
  }

  public LocalDateTime getNextCheckAt() {
    return nextCheckAt;
  }

  public void setNextCheckAt(LocalDateTime value) {
    this.nextCheckAt = value;
  }

  public LocalDateTime getArchivedAt() {
    return archivedAt;
  }

  public void setArchivedAt(LocalDateTime value) {
    this.archivedAt = value;
  }

  public LocalDateTime getSslExpiresAt() {
    return sslExpiresAt;
  }

  public void setSslExpiresAt(LocalDateTime value) {
    this.sslExpiresAt = value;
  }

  public LocalDateTime getSslCheckedAt() {
    return sslCheckedAt;
  }

  public void setSslCheckedAt(LocalDateTime value) {
    this.sslCheckedAt = value;
  }

  public String getSslError() {
    return sslError;
  }

  public void setSslError(String value) {
    this.sslError = value;
  }

  public Integer getSslWarnDays() {
    return sslWarnDays == null ? 30 : sslWarnDays;
  }

  public void setSslWarnDays(Integer value) {
    this.sslWarnDays = value;
  }

  public String getSslAlertKey() {
    return sslAlertKey;
  }

  public void setSslAlertKey(String value) {
    this.sslAlertKey = value;
  }

  // Optional checks default to the legacy HTTP monitor for existing rows.
  private String checkType = "HTTP";

  public String getCheckType() {
    return checkType == null ? "HTTP" : checkType;
  }

  public void setCheckType(String value) {
    checkType = value;
  }

  private String keywordMode = "OFF";

  public String getKeywordMode() {
    return keywordMode == null ? "OFF" : keywordMode;
  }

  public void setKeywordMode(String value) {
    keywordMode = value;
  }

  @Column(length = 1000)
  private String keyword = "";

  public String getKeyword() {
    return keyword == null ? "" : keyword;
  }

  public void setKeyword(String value) {
    keyword = value;
  }

  private Boolean keywordCaseSensitive = false;

  public Boolean getKeywordCaseSensitive() {
    return keywordCaseSensitive == null ? false : keywordCaseSensitive;
  }

  public void setKeywordCaseSensitive(Boolean value) {
    keywordCaseSensitive = value;
  }

  private Boolean jsonEnabled = false;

  public Boolean getJsonEnabled() {
    return jsonEnabled == null ? false : jsonEnabled;
  }

  public void setJsonEnabled(Boolean value) {
    jsonEnabled = value;
  }

  @Column(length = 500)
  private String jsonPointer = "";

  public String getJsonPointer() {
    return jsonPointer == null ? "" : jsonPointer;
  }

  public void setJsonPointer(String value) {
    jsonPointer = value;
  }

  private String jsonComparison = "EQUALS";

  public String getJsonComparison() {
    return jsonComparison == null ? "EQUALS" : jsonComparison;
  }

  public void setJsonComparison(String value) {
    jsonComparison = value;
  }

  @Column(length = 2000)
  private String jsonExpected = "";

  public String getJsonExpected() {
    return jsonExpected == null ? "" : jsonExpected;
  }

  public void setJsonExpected(String value) {
    jsonExpected = value;
  }

  private String dnsRecordType = "A";

  public String getDnsRecordType() {
    return dnsRecordType == null ? "A" : dnsRecordType;
  }

  public void setDnsRecordType(String value) {
    dnsRecordType = value;
  }

  @Column(length = 1000)
  private String dnsExpected = "";

  public String getDnsExpected() {
    return dnsExpected == null ? "" : dnsExpected;
  }

  public void setDnsExpected(String value) {
    dnsExpected = value;
  }

  private Integer tcpPort = 443;

  public Integer getTcpPort() {
    return tcpPort == null ? 443 : tcpPort;
  }

  public void setTcpPort(Integer value) {
    tcpPort = value;
  }
}
