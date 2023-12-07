package de.samply.transfair.processor;

import de.samply.transfair.mapper.FhirResourceMapper;
import de.samply.transfair.mapper.bbmri2beacon.Bbmri2BeaconBiosample;
import de.samply.transfair.models.beacon.BeaconBiosamples;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.batch.item.ItemProcessor;

@Slf4j
/**
 * BbmriBundleToBeaconBiosampleProcessor is an implementation of ItemProcessor that converts a FHIR Bundle to BeaconBiosamples.
 */
public class BbmriBundleToBeaconBiosampleProcessor implements ItemProcessor<Bundle, BeaconBiosamples> {
  /**
   * Constructor.
   *
   * @param mapper Not used, set this argument to null.
   */
  public BbmriBundleToBeaconBiosampleProcessor(FhirResourceMapper mapper) {
    // FHIR to Beacon conversion does not need a mapper, but without it, invisible Spring magic stops working.
  }

  /**
   * Processes a FHIR Bundle and converts it to BeaconBiosamples.
   *
   * @param bundle The FHIR Bundle object to be processed.
   * @return The converted BeaconBiosamples object.
   */
  public BeaconBiosamples process(final Bundle bundle) {
    BeaconBiosamples beaconBiosamples = null;
    try {
      beaconBiosamples = (new Bbmri2BeaconBiosample()).transfer(bundle);
    } catch (Exception e) {
      log.warn("An error occurred while converting a FHIR Bundle to BFF");
      e.printStackTrace();
    }

    return beaconBiosamples;
  }
}
