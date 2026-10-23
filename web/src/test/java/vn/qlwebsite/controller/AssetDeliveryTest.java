package vn.qlwebsite.controller;

import static org.hamcrest.Matchers.containsString;
import static org.junit.jupiter.api.Assertions.*;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.*;

import java.net.URI;
import java.util.*;
import java.util.regex.Pattern;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

@SpringBootTest
@AutoConfigureMockMvc
class AssetDeliveryTest {
  @Autowired MockMvc mvc;

  @Test
  void pageLoadsEveryModuleUnderTheSameReleasePath() throws Exception {
    String html =
        mvc.perform(get("/login"))
            .andExpect(status().isOk())
            .andExpect(header().string("Cache-Control", "no-store"))
            .andReturn()
            .getResponse()
            .getContentAsString();
    var matcher = Pattern.compile("src=\"(/assets/v[0-9a-f]{16}/js/app\\.js)\"").matcher(html);
    assertTrue(matcher.find(), "The entry script must bypass old unversioned browser cache");
    String entry = matcher.group(1), base = entry.substring(0, entry.lastIndexOf("/js/"));
    assertTrue(html.contains("href=\"" + base + "/css/app.css\""));
    Set<String> visited = new HashSet<>();
    var pending = new ArrayDeque<String>();
    pending.add(entry);
    var imports = Pattern.compile("from\\s+\"(\\./[^\"]+\\.js)\"");
    while (!pending.isEmpty()) {
      String path = pending.remove();
      if (!visited.add(path)) continue;
      assertTrue(path.startsWith(base + "/js/"));
      String js =
          mvc.perform(get(path))
              .andExpect(status().isOk())
              .andExpect(content().contentTypeCompatibleWith("text/javascript"))
              .andExpect(header().string("Cache-Control", containsString("immutable")))
              .andReturn()
              .getResponse()
              .getContentAsString();
      var dependencies = imports.matcher(js);
      while (dependencies.find())
        pending.add(URI.create(path).resolve(dependencies.group(1)).toString());
    }
    for (String module :
        List.of("app.js", "data.js", "backend.js", "management.js", "chart.js", "checks.js"))
      assertTrue(visited.contains(base + "/js/" + module), "Missing module: " + module);
    mvc.perform(get(base + "/css/app.css")).andExpect(status().isOk());
    mvc.perform(get("/assets/obsolete/js/data.js")).andExpect(status().isNotFound());
  }

  @Test
  void oldAssetUrlsAndAllEntryPagesDoNotRetainStaleCode() throws Exception {
    for (String path : List.of("/js/app.js", "/js/data.js", "/css/app.css", "/", "/login"))
      mvc.perform(get(path))
          .andExpect(status().isOk())
          .andExpect(header().string("Cache-Control", "no-store"));
  }
}
