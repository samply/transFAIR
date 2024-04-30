package de.samply.transfair.writer;

import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import io.micrometer.core.instrument.Metrics;
import java.io.FileWriter;
import java.io.IOException;
import java.util.concurrent.atomic.AtomicInteger;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.batch.item.Chunk;
import org.springframework.batch.item.ItemWriter;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.retry.RetryCallback;
import org.springframework.retry.RetryContext;
import org.springframework.retry.backoff.FixedBackOffPolicy;
import org.springframework.retry.listener.RetryListenerSupport;
import org.springframework.retry.policy.SimpleRetryPolicy;
import org.springframework.retry.support.RetryTemplate;

@Slf4j
public class FhirBundleWriter implements ItemWriter<Bundle> {

  private static final AtomicInteger resourcesWrittenTotal =
      Metrics.globalRegistry.gauge("batch.fhir.resources.written.total", new AtomicInteger(0));
  private static final AtomicInteger bundlesWrittenTotal =
      Metrics.globalRegistry.gauge("batch.fhir.bundles.written.total", new AtomicInteger(0));
  private final FhirContext ctx = FhirContext.forR4();
  private final IGenericClient client;
  private final RetryTemplate retryTemplate = new RetryTemplate();

  @Value("${data.outputFileDirectory}")
  private String outFileDir;
  @Value("${data.writeBundlesToFile}")
  private boolean writeToFile;

  public FhirBundleWriter(IGenericClient client) {
    this.client = client;

    var backOffPolicy = new FixedBackOffPolicy();
    backOffPolicy.setBackOffPeriod(10000);
    var retryPolicy = new SimpleRetryPolicy();
    retryPolicy.setMaxAttempts(10);

    retryTemplate.setBackOffPolicy(backOffPolicy);
    retryTemplate.setRetryPolicy(retryPolicy);

    retryTemplate.registerListener(new RetryListenerSupport() {
      @Override
      public <T, E extends Throwable> void onError(RetryContext context, RetryCallback<T, E> callback, Throwable throwable) {
        log.warn("Trying to send bundle caused error. {} attempt.", context.getRetryCount(), throwable);
      }
    });
  }

  @Override
//  public void write(Chunk<? extends Bundle> chunk) throws Exception {
//
//    var writtenBundles = bundlesWrittenTotal.incrementAndGet();
//    Bundle bundle = chunk.getItems().get(0);
//
//    if (writeToFile){
//      this.writeToFile(bundle, writtenBundles);
//      return;
//    }
//
//    var response = retryTemplate.execute((RetryCallback<Bundle, Exception>) context ->
//    client.transaction().withBundle(bundle).execute());
//  }

  public void write(Chunk<? extends Bundle> chunk) throws Exception {
    var writtenBundles = bundlesWrittenTotal.incrementAndGet();
    Bundle bundle = chunk.getItems().get(0);

    if (bundle != null) {
      log.info("write: bundle size: " + bundle.getEntry().size());
    }

    if (writeToFile){
      this.writeToFile(bundle, writtenBundles);
      return;
    }

    // Extract the RetryCallback into a separate method
    RetryCallback<Bundle, Exception> retryCallback = context -> {
      log.info("write.retryCallback: executing transaction with bundle");
      return client.transaction().withBundle(bundle).execute();
    };

    log.info("write: start retrying the execute");

    // Use a try-catch block to handle exceptions
    try {
      var response = retryTemplate.execute(retryCallback);
      log.info("write: response: " + response);
    } catch (Exception e) {
      // Handle the exception
      System.err.println("An error occurred during the retry operation: " + e.getMessage());
      throw e; // Rethrow the exception if necessary
    }

    log.info("write: done");
  }

  private void writeToFile(Bundle fhirBundle, int chunk) {

    String outputFile = outFileDir + "/bundle_" + chunk + ".json";
    String stringToWrite = ctx.newJsonParser().setPrettyPrint(true).encodeResourceToString(fhirBundle);

    try (FileWriter file = new FileWriter(outputFile)) {

      file.write(stringToWrite);
      file.flush();

    } catch (IOException e) {
      e.printStackTrace();
    }
  }

}
