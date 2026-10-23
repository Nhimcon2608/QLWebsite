package vn.qlwebsite.config;

import java.io.IOException;
import java.security.MessageDigest;
import java.security.NoSuchAlgorithmException;
import java.time.Duration;
import java.util.Arrays;
import java.util.Comparator;
import java.util.HexFormat;
import java.util.stream.Stream;
import org.springframework.context.annotation.Configuration;
import org.springframework.core.io.Resource;
import org.springframework.core.io.support.PathMatchingResourcePatternResolver;
import org.springframework.http.CacheControl;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import org.springframework.web.servlet.resource.VersionResourceResolver;

@Configuration
public class AssetConfig implements WebMvcConfigurer {
  private final String version;

  public AssetConfig() throws IOException, NoSuchAlgorithmException {
    var resolver = new PathMatchingResourcePatternResolver();
    Resource[] resources =
        Stream.concat(
                Arrays.stream(resolver.getResources("classpath:/static/js/*.js")),
                Arrays.stream(resolver.getResources("classpath:/static/css/*.css")))
            .sorted(Comparator.comparing(Resource::getFilename))
            .toArray(Resource[]::new);
    if (resources.length == 0) throw new IOException("Không tìm thấy tài nguyên giao diện.");
    var digest = MessageDigest.getInstance("SHA-256");
    for (var resource : resources) {
      digest.update(resource.getFilename().getBytes(java.nio.charset.StandardCharsets.UTF_8));
      digest.update((byte) 0);
      try (var input = resource.getInputStream()) {
        digest.update(input.readAllBytes());
      }
    }
    version = "v" + HexFormat.of().formatHex(digest.digest()).substring(0, 16);
  }

  public String basePath() {
    return "/assets/" + version;
  }

  @Override
  public void addResourceHandlers(ResourceHandlerRegistry registry) {
    // A directory version preserves relative ES module imports throughout the dependency graph.
    registry
        .addResourceHandler("/assets/**")
        .addResourceLocations("classpath:/static/")
        .setCacheControl(CacheControl.maxAge(Duration.ofDays(365)).cachePublic().immutable())
        .resourceChain(true)
        .addResolver(new VersionResourceResolver().addFixedVersionStrategy(version, "/**"));
    // Legacy URLs remain usable, but must not be cached across releases.
    registry
        .addResourceHandler("/js/**")
        .addResourceLocations("classpath:/static/js/")
        .setCacheControl(CacheControl.noStore());
    registry
        .addResourceHandler("/css/**")
        .addResourceLocations("classpath:/static/css/")
        .setCacheControl(CacheControl.noStore());
  }
}
