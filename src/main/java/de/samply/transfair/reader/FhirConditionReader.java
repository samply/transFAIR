package de.samply.transfair.reader;

import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.batch.item.ItemReader;

@Slf4j
public class FhirConditionReader implements ItemReader<Bundle> {

  private final IGenericClient client;
  private Bundle bundle;

  public FhirConditionReader(IGenericClient client){
    this.client = client;
  }

  @Override
  public Bundle read() {

    if (bundle == null){
      bundle = client.search()
          .forResource("Condition")
          .include(new Include("Condition:subject"))
          .count(10)
          .returnBundle(Bundle.class)
          .execute();

    } else if (bundle.getLink(IBaseBundle.LINK_NEXT) != null) {
      bundle = client
          .loadPage()
          .next(bundle)
          .execute();
    } else {
      return null;
    }

    return bundle;
  }
}
