package com.mycompany.do_an_ltm_2380600550.service;

import com.fasterxml.jackson.core.JsonPointer;
import com.fasterxml.jackson.databind.*;
import com.mycompany.do_an_ltm_2380600550.entity.Website;
import java.util.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Component;
import org.springframework.web.server.ResponseStatusException;

@Component
public class ContentChecks {
  private final ObjectMapper json;

  public ContentChecks(ObjectMapper json) {
    this.json = json;
  }

  public record Result(String errorType, String message) {}

  public boolean enabled(Website w) {
    return !w.getKeywordMode().equals("OFF") || w.getJsonEnabled();
  }

  public void validate(Website w) {
    if (!Set.of("HTTP", "DNS", "TCP").contains(w.getCheckType()))
      bad("Kiểu kiểm tra phải là HTTP, DNS hoặc TCP.");
    if (!Set.of("OFF", "CONTAINS", "NOT_CONTAINS").contains(w.getKeywordMode())
        || w.getKeyword().length() > 1000
        || (!w.getKeywordMode().equals("OFF") && w.getKeyword().isBlank()))
      bad("Nhập từ khóa từ 1 đến 1000 ký tự hoặc tắt kiểm tra từ khóa.");
    if (!w.getCheckType().equals("HTTP") && enabled(w))
      bad("Từ khóa và JSON chỉ dùng cho kiểu HTTP.");
    if (!Set.of("EXISTS", "EQUALS").contains(w.getJsonComparison())
        || w.getJsonPointer().length() > 500
        || w.getJsonExpected().length() > 2000) bad("Cấu hình JSON không hợp lệ hoặc quá dài.");
    if (w.getJsonEnabled()) {
      try {
        // RFC 6901 only permits ~0 and ~1 escapes; Jackson otherwise accepts malformed escapes.
        if (java.util.regex.Pattern.compile("~(?![01])").matcher(w.getJsonPointer()).find())
          throw new IllegalArgumentException();
        JsonPointer.compile(w.getJsonPointer());
        if (w.getJsonComparison().equals("EQUALS")) parse(w.getJsonExpected());
      } catch (Exception ex) {
        bad(
            "JSON Pointer phải có dạng /data/status; giá trị mong đợi phải là JSON hợp lệ (ví dụ:"
                + " \"ok\", true, 200).");
      }
    }
    if (!Set.of("A", "AAAA").contains(w.getDnsRecordType()) || w.getDnsExpected().length() > 1000)
      bad("DNS hỗ trợ bản ghi A hoặc AAAA, giá trị mong đợi tối đa 1000 ký tự.");
    w.setDnsExpected(w.getDnsExpected().trim());
    if (!w.getDnsExpected().isBlank()) {
      for (String ip : w.getDnsExpected().split("[,\\s]+")) {
        if (!NetworkChecks.validAddress(ip, w.getDnsRecordType()))
          bad("IP DNS mong đợi không đúng loại A/AAAA.");
      }
    }
    if (w.getTcpPort() < 1 || w.getTcpPort() > 65535) bad("Cổng TCP phải từ 1 đến 65535.");
  }

  private JsonNode parse(String value) throws Exception {
    if (value.startsWith("\uFEFF")) value = value.substring(1);
    var result = json.reader().with(DeserializationFeature.FAIL_ON_TRAILING_TOKENS).readTree(value);
    if (result == null || result.isMissingNode()) throw new IllegalArgumentException();
    return result;
  }

  public Result check(Website w, String body) {
    if (!w.getKeywordMode().equals("OFF")) {
      String text = w.getKeywordCaseSensitive() ? body : body.toLowerCase(Locale.ROOT);
      String keyword =
          w.getKeywordCaseSensitive() ? w.getKeyword() : w.getKeyword().toLowerCase(Locale.ROOT);
      boolean found = text.contains(keyword);
      if (w.getKeywordMode().equals("CONTAINS") && !found)
        return new Result(
            "KEYWORD_MISSING", "Không tìm thấy từ khóa bắt buộc trong nội dung phản hồi.");
      if (w.getKeywordMode().equals("NOT_CONTAINS") && found)
        return new Result("KEYWORD_FORBIDDEN", "Phát hiện từ khóa bị cấm trong nội dung phản hồi.");
    }
    if (w.getJsonEnabled()) {
      try {
        var node = parse(body).at(w.getJsonPointer());
        if (node.isMissingNode())
          return new Result(
              "JSON_PATH_MISSING", "Không tìm thấy JSON Pointer: " + w.getJsonPointer());
        if (w.getJsonComparison().equals("EQUALS") && !node.equals(parse(w.getJsonExpected())))
          return new Result(
              "JSON_VALUE_MISMATCH", "Giá trị JSON không khớp tại: " + w.getJsonPointer());
      } catch (Exception ex) {
        return new Result("INVALID_JSON", "Phản hồi không phải JSON hợp lệ.");
      }
    }
    return new Result(null, "Đã đạt các điều kiện từ khóa/JSON đã cấu hình.");
  }

  private void bad(String message) {
    throw new ResponseStatusException(HttpStatus.BAD_REQUEST, message);
  }
}
