package com.mycompany.do_an_ltm_2380600550.service;

import java.io.IOException;
import java.net.http.HttpResponse;
import java.nio.ByteBuffer;
import java.util.List;
import java.util.concurrent.CompletionStage;
import java.util.concurrent.Flow;

/** Counts streamed bytes before buffering; never retains an unbounded response. */
final class LimitedBodyHandler implements HttpResponse.BodyHandler<byte[]> {
  static final int MAX_BYTES = 2 * 1024 * 1024;

  @Override
  public HttpResponse.BodySubscriber<byte[]> apply(HttpResponse.ResponseInfo info) {
    return new HttpResponse.BodySubscriber<>() {
      final HttpResponse.BodySubscriber<byte[]> delegate =
          HttpResponse.BodySubscribers.ofByteArray();
      Flow.Subscription subscription;
      long received;
      boolean done;

      public CompletionStage<byte[]> getBody() {
        return delegate.getBody();
      }

      public void onSubscribe(Flow.Subscription value) {
        subscription = value;
        delegate.onSubscribe(value);
      }

      public void onNext(List<ByteBuffer> buffers) {
        if (done) return;
        for (var buffer : buffers) received += buffer.remaining();
        if (received > MAX_BYTES) {
          done = true;
          subscription.cancel();
          delegate.onError(new IOException("BODY_TOO_LARGE: Nội dung vượt giới hạn 2 MiB."));
        } else delegate.onNext(buffers);
      }

      public void onError(Throwable error) {
        if (!done) {
          done = true;
          delegate.onError(error);
        }
      }

      public void onComplete() {
        if (!done) {
          done = true;
          delegate.onComplete();
        }
      }
    };
  }
}
