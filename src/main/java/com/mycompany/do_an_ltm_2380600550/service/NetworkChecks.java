package com.mycompany.do_an_ltm_2380600550.service;

import com.mycompany.do_an_ltm_2380600550.entity.Website;
import jakarta.annotation.PreDestroy;
import java.net.*;
import java.util.*;
import java.util.concurrent.*;
import org.springframework.stereotype.Component;

@Component
public class NetworkChecks {
  // OS DNS calls may ignore interrupts: bound both workers and the queue.
  private final ExecutorService resolver =
      new ThreadPoolExecutor(
          2,
          2,
          0,
          TimeUnit.SECONDS,
          new ArrayBlockingQueue<>(8),
          r -> {
            var t = new Thread(r, "dns-resolver");
            t.setDaemon(true);
            return t;
          },
          new ThreadPoolExecutor.AbortPolicy());

  public record Result(boolean success, String errorType, String message, String details) {}

  public List<String> resolve(String host, String type, int timeoutMs) throws Exception {
    Future<List<String>> future =
        resolver.submit(
            () ->
                Arrays.stream(InetAddress.getAllByName(host))
                    .filter(
                        a ->
                            type.equals("ANY")
                                || type.equals("A") && a instanceof Inet4Address
                                || type.equals("AAAA") && a instanceof Inet6Address)
                    .map(InetAddress::getHostAddress)
                    .distinct()
                    .sorted()
                    .toList());
    try {
      return future.get(timeoutMs, TimeUnit.MILLISECONDS);
    } finally {
      future.cancel(true);
    }
  }

  public Result check(Website w) throws InterruptedException {
    String host = URI.create(w.getUrl()).getHost();
    if (host.startsWith("[")) host = host.substring(1, host.length() - 1);
    long start = System.nanoTime();
    boolean resolved = false;
    try {
      var addresses =
          resolve(
              host,
              w.getCheckType().equals("DNS") ? w.getDnsRecordType() : "ANY",
              w.getTimeoutMs());
      resolved = true;
      if (addresses.isEmpty())
        return new Result(
            false,
            "DNS_NO_RECORDS",
            "Không có bản ghi " + w.getDnsRecordType() + " cho " + host,
            null);
      String detail = String.join(", ", addresses);
      if (w.getCheckType().equals("DNS")) {
        if (!w.getDnsExpected().isBlank()) {
          var expected =
              Arrays.stream(w.getDnsExpected().split("[,\\s]+"))
                  .map(NetworkChecks::canonicalAddress)
                  .toList();
          if (!addresses.containsAll(expected))
            return new Result(
                false, "DNS_MISMATCH", "DNS thiếu IP mong đợi. Kết quả: " + detail, detail);
        }
        return new Result(true, null, null, w.getDnsRecordType() + " " + host + ": " + detail);
      }
      Exception last = null;
      for (int index = 0; index < addresses.size(); index++) {
        String address = addresses.get(index);
        long remaining =
            w.getTimeoutMs() - TimeUnit.NANOSECONDS.toMillis(System.nanoTime() - start);
        if (remaining <= 0) throw new TimeoutException();
        try (var socket = new Socket()) {
          // Try every resolved address within a shared budget.
          socket.connect(
              new InetSocketAddress(InetAddress.getByName(address), w.getTcpPort()),
              (int) Math.max(1, remaining / (addresses.size() - index)));
          return new Result(
              true, null, null, "Kết nối TCP thành công: " + address + ":" + w.getTcpPort());
        } catch (java.io.IOException ex) {
          last = ex;
        }
      }
      return new Result(
          false,
          "TCP_CONNECTION_FAILED",
          "Không kết nối được TCP "
              + host
              + ":"
              + w.getTcpPort()
              + " ("
              + (last == null ? "timeout" : last.getClass().getSimpleName())
              + ")",
          detail);
    } catch (InterruptedException ex) {
      throw ex;
    } catch (Exception ex) {
      String type =
          resolved ? "TCP_TIMEOUT" : ex instanceof TimeoutException ? "DNS_TIMEOUT" : "DNS_ERROR";
      return new Result(
          false,
          type,
          (resolved ? "Hết thời gian kết nối TCP tới " : "Không phân giải được DNS cho ")
              + host
              + " ("
              + ex.getClass().getSimpleName()
              + ")",
          null);
    }
  }

  static boolean validAddress(String ip, String type) {
    try {
      if (!ip.matches(type.equals("A") ? "[0-9]+(?:\\.[0-9]+){3}" : "[0-9a-fA-F:]+")
          || type.equals("AAAA") && !ip.contains(":")) return false;
      var address = InetAddress.getByName(ip);
      return type.equals("A") ? address instanceof Inet4Address : address instanceof Inet6Address;
    } catch (Exception ex) {
      return false;
    }
  }

  static String canonicalAddress(String ip) {
    try {
      return InetAddress.getByName(ip).getHostAddress();
    } catch (Exception ex) {
      throw new IllegalArgumentException("IP không hợp lệ", ex);
    }
  }

  @PreDestroy
  public void close() {
    resolver.shutdownNow();
  }
}
