package de.samply.transfyr.reader;

import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.client.api.IGenericClient;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.batch.item.ItemReader;

@Slf4j
public class FhirSpecimenReader implements ItemReader<Bundle> {

  private final IGenericClient client;
  private Bundle bundle;

  public FhirSpecimenReader(IGenericClient client){
    this.client = client;
  }

  @Override
  public Bundle read() {

    if (bundle == null){
      bundle = client.search()
          .forResource("Specimen")
          .include(new Include("Specimen:subject"))
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
