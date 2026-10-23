package com.mycompany.do_an_ltm_2380600550.service;

import java.io.IOException;
import java.net.http.*;
import java.util.concurrent.*;

final class BoundedHttp {
  private BoundedHttp() {}

  static HttpResponse<byte[]> read(HttpClient http, HttpRequest request, long timeoutMs)
      throws IOException, InterruptedException {
    return send(http, request, new LimitedBodyHandler(), timeoutMs);
  }

  static <T> HttpResponse<T> send(
      HttpClient http, HttpRequest request, HttpResponse.BodyHandler<T> handler, long timeoutMs)
      throws IOException, InterruptedException {
    var pending = http.sendAsync(request, handler);
    try {
      return pending.get(timeoutMs, TimeUnit.MILLISECONDS);
    } catch (TimeoutException ex) {
      pending.cancel(true);
      throw new HttpTimeoutException("Hết thời gian chờ phản hồi đầy đủ (" + timeoutMs + " ms).");
    } catch (InterruptedException ex) {
      pending.cancel(true);
      throw ex;
    } catch (ExecutionException ex) {
      if (ex.getCause() instanceof IOException io) throw io;
      if (ex.getCause() instanceof InterruptedException interrupted) throw interrupted;
      throw new IOException(ex.getCause());
    }
  }
}
