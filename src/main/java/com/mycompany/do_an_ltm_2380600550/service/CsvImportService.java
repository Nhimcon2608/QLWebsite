package com.mycompany.do_an_ltm_2380600550.service;

import com.mycompany.do_an_ltm_2380600550.entity.Website;
import com.mycompany.do_an_ltm_2380600550.repository.WebsiteRepository;
import java.io.*;
import java.util.*;
import org.apache.commons.csv.*;
import org.springframework.http.HttpStatus;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.server.ResponseStatusException;

@Service
public class CsvImportService {
  private final WebsiteService service;
  private final WebsiteRepository websites;

  public CsvImportService(WebsiteService service, WebsiteRepository websites) {
    this.service = service;
    this.websites = websites;
  }

  public record Preview(List<Website> websites, List<String> errors) {}

  public Preview preview(String csv) {
    if (csv == null || csv.getBytes(java.nio.charset.StandardCharsets.UTF_8).length > 1000000)
      throw new ResponseStatusException(HttpStatus.BAD_REQUEST, "CSV trống hoặc lớn hơn 1 MB.");
    List<Website> rows = new ArrayList<>();
    List<String> errors = new ArrayList<>();
    Set<String> urls = new HashSet<>();
    try (var parser =
        CSVFormat.DEFAULT
            .builder()
            .setDuplicateHeaderMode(DuplicateHeaderMode.DISALLOW)
            .setHeader()
            .setSkipHeaderRecord(true)
            .setIgnoreEmptyLines(true)
            .setTrim(true)
            .get()
            .parse(new StringReader(csv.replaceFirst("^\\uFEFF", "")))) {
      if (!parser.getHeaderMap().containsKey("url"))
        throw new IllegalArgumentException(
            "Cần cột url; có thể thêm name, groupName, environment, tags.");
      for (var row : parser) {
        if (rows.size() + errors.size() >= 500) {
          errors.add("Tối đa 500 dòng mỗi lần nhập.");
          break;
        }
        try {
          var w = new Website();
          w.setUrl(row.get("url"));
          w.setName(value(row, "name", w.getUrl()));
          w.setGroupName(value(row, "groupName", ""));
          w.setEnvironment(value(row, "environment", ""));
          w.setTags(value(row, "tags", ""));
          w.setFailureThreshold(3);
          service.validate(w);
          if (!urls.add(w.getUrl()) || websites.existsByUrlAndArchivedAtIsNull(w.getUrl()))
            throw new IllegalArgumentException("URL bị trùng.");
          rows.add(w);
        } catch (Exception ex) {
          errors.add(
              "Dòng "
                  + (row.getRecordNumber() + 1)
                  + ": "
                  + (ex instanceof ResponseStatusException r
                      ? r.getReason()
                      : "Dữ liệu thiếu, sai định dạng hoặc URL bị trùng."));
        }
      }
    } catch (Exception ex) {
      errors.add(
          ex instanceof IllegalArgumentException
              ? ex.getMessage()
              : "Không đọc được CSV. Kiểm tra dấu ngoặc kép và tên cột.");
    }
    if (rows.isEmpty() && errors.isEmpty()) errors.add("CSV không có dữ liệu.");
    return new Preview(rows, errors);
  }

  private String value(CSVRecord row, String name, String fallback) {
    return row.isMapped(name) && row.isSet(name) && !row.get(name).isBlank()
        ? row.get(name)
        : fallback;
  }

  @Transactional
  public synchronized Map<String, Object> apply(String csv) {
    var preview = preview(csv);
    if (!preview.errors().isEmpty())
      throw new ResponseStatusException(
          HttpStatus.UNPROCESSABLE_ENTITY, String.join(" ", preview.errors()));
    websites.saveAll(preview.websites());
    return Map.of("imported", preview.websites().size());
  }
}
