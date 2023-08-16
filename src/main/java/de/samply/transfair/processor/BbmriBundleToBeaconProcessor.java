package de.samply.transfair.processor;

import de.samply.transfair.mapper.FhirResourceMapper;
import de.samply.transfair.mapper.bbmri2beacon.Bbmri2BeaconIndividual;
import de.samply.transfair.models.beacon.BeaconIndividuals;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.springframework.batch.item.ItemProcessor;

@Slf4j
/**
 * BbmriBundleToBeaconProcessor is an implementation of ItemProcessor that converts a FHIR Bundle to BeaconIndividuals.
 */
public class BbmriBundleToBeaconProcessor implements ItemProcessor<Bundle, BeaconIndividuals> {
  /**
   * Constructor.
   *
   * @param mapper Not used, set this argument to null.
   */
  public BbmriBundleToBeaconProcessor(FhirResourceMapper mapper) {
    // FHIR to Beacon conversion does not need a mapper, but without it, invisible Spring magic stops working.
  }

  /**
   * Processes a FHIR Bundle and converts it to BeaconIndividuals.
   *
   * @param bundle The FHIR Bundle object to be processed.
   * @return The converted BeaconIndividuals object.
   */
  public BeaconIndividuals process(final Bundle bundle) {
    BeaconIndividuals beaconIndividuals = null;
    try {
      beaconIndividuals = (new Bbmri2BeaconIndividual()).transfer(bundle);
    } catch (Exception e) {
      log.warn("An error occurred while converting a FHIR Bundle to BFF");
      e.printStackTrace();
    }

    return beaconIndividuals;
  }
}
