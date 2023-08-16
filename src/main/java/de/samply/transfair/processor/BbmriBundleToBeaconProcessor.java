package de.samply.transfair.processor;

import de.samply.transfair.mapper.FhirResourceMapper;
import de.samply.transfair.mapper.bbmri2beacon.Bbmri2BeaconIndividual;
import de.samply.transfair.models.beacon.BeaconIndividuals;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Patient;

import org.springframework.batch.item.ItemProcessor;

import java.util.Collection;
import java.util.List;

@Slf4j
public class BbmriBundleToBeaconProcessor implements ItemProcessor<Bundle, BeaconIndividuals> {
  public BbmriBundleToBeaconProcessor(FhirResourceMapper mapper) {
  }

  public BeaconIndividuals process(final Bundle bundle) {
    Bbmri2BeaconIndividual bbmri2BeaconIndividual = new Bbmri2BeaconIndividual();
    BeaconIndividuals beaconIndividuals =null;
    try {
      beaconIndividuals = bbmri2BeaconIndividual.transfer(bundle);
    } catch (Exception e) {
    }

    return beaconIndividuals;
  }
}
