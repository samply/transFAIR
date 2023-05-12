package de.samply.transfyr.processor;

import java.util.List;
import java.util.UUID;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.batch.item.ItemProcessor;
import de.samply.transfyr.mapper.FhirResourceMapper;
import de.samply.transfyr.mapper.bbmri2mii.FhirBbmriToMiiResourceMapper;
import de.samply.transfyr.util.FhirBundleBuilder;

@Slf4j
public class FhirBundleProcessor implements ItemProcessor<Bundle, Bundle> {

  FhirResourceMapper mapper;

  public FhirBundleProcessor(FhirResourceMapper mapper) {
    this.mapper = mapper;
  }


  @Override
    public Bundle process(final Bundle bundle) {

    List<Resource> resources = bundle.getEntry().stream().map(entry -> entry.getResource()).toList();
    List<Resource> mappedResources = mapper.map(resources);

    FhirBundleBuilder fhirBundleBuilder = new FhirBundleBuilder();
    fhirBundleBuilder
        .id(UUID.randomUUID().toString())
        .add(mappedResources);

    return fhirBundleBuilder.build();

  }
}
