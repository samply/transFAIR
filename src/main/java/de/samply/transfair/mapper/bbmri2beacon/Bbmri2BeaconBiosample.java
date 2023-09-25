package de.samply.transfair.mapper.bbmri2beacon;

import de.samply.transfair.mapper.bbmri2beacon.BbmriBeaconTypeConverter;
import de.samply.transfair.models.beacon.BeaconBiosample;
import de.samply.transfair.models.beacon.BeaconBiosamples;
import de.samply.transfair.models.beacon.BeaconSampleInfo;
import de.samply.transfair.models.beacon.BeaconSampleOriginType;
import de.samply.transfair.writer.BeaconMongoSaver;
import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.util.List;
import java.util.Objects;
import java.util.Set;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;

/**
 * This mapping transfers sample-related data from one blaze with bbmri to a biosamples.json file.
 */
@Slf4j
public class Bbmri2BeaconBiosample {
//  private FhirComponent fhirComponent;
//  private BeaconBiosamples beaconBiosamples;
//
//  public Bbmri2BeaconBiosample(FhirComponent fhirComponent) {
//    this.fhirComponent = fhirComponent;
//  }
//
  /**
   * Transfer data relating to samples from FHIR to Beacon.
   */
  public BeaconBiosamples transfer(Bundle bundle) {
    BeaconBiosamples beaconBiosamples = new BeaconBiosamples();

    int specimenCount = 0;
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      Resource resource = entry.getResource();
      if (resource instanceof Specimen) {
        Specimen specimen = (Specimen) resource;
        beaconBiosamples.add(transferBiosample(specimen));
        specimenCount++;
      }
    }

    log.info("Transferred: " + beaconBiosamples.biosamples.size() + " biosamples from "
            + specimenCount + " specimens");

    return beaconBiosamples;
  }

//  /**
//   * Create an object encapsulating a Beacon biosample, based on a Specimen in the FHIR store.
//   *
//   * @param sid ID of a specimen in FHIR store.
//   * @return Beacon biosample.
//   */
//  public BeaconBiosample transferBiosample(String sid) {
//    log.info("Loading data for sample " + sid);
//
//    Specimen specimen = fhirComponent.transferController.fetchResource(
//            fhirComponent.getSourceFhirServer(), Specimen.class, sid);
//
//    return transferBiosample(specimen);
//  }

  /**
   * Create an object encapsulating a Beacon biosample, based on a Specimen in the FHIR store.
   *
   * @param specimen ID of a specimen in FHIR store.
   * @return Beacon biosample.
   */
  public BeaconBiosample transferBiosample(Specimen specimen) {
    BeaconBiosample beaconBiosample = new BeaconBiosample();
    beaconBiosample.id = transferId(specimen);
    beaconBiosample.individualId = transferPatientId(specimen);
    beaconBiosample.collectionDate = transferCollectionDate(specimen);
    beaconBiosample.info = transferInfo();
    beaconBiosample.sampleOriginType = transferType(specimen);

    return beaconBiosample;
  }

  /**
   * Pulls an ID from the BBMRI Specimen.
   *
   * @param specimen BBMRI Specimen.
   * @return ID.
   */
  private String transferId(Specimen specimen) {
    String id = specimen.getIdPart();
    if (Objects.isNull(id)) {
      id = specimen.getId();
    }

    return id;
  }

  /**
   * Returns the ID of the patient from whom the specimen was taken.
   *
   * @param specimen BBMRI Specimen.
   * @return Patient ID.
   */
  private String transferPatientId(Specimen specimen) {
    Reference subject = specimen.getSubject();
    String patientId = subject.getReference();

    return patientId.substring(8);
  }

  /**
   * Returns the date at which the specimen was collected.
   *
   * @param specimen BBMRI Specimen.
   * @return Collectin date.
   */
  private String transferCollectionDate(Specimen specimen) {

    return specimen.getCollection().getCollectedDateTimeType().getValueAsString();
  }

  /**
   * Returns the "info" object required by Beacon. This contains mainly information
   * about the species of the subject. I.e. H. sapiens.
   *
   * @param specimen BBMRI Specimen.
   * @return Beacin info object.
   */
  private BeaconSampleInfo transferInfo() {

    return BeaconSampleInfo.createHumanBeaconSampleInfo();
  }

  /**
   * Returns the type of the sample, e.g. blood.
   *
   * @param specimen BBMRI Specimen.
   * @return Sample type.
   */
  private BeaconSampleOriginType transferType(Specimen specimen) {
    CodeableConcept type = specimen.getType();
    if (Objects.isNull(type)) {
      log.warn("No sample type available for: " + specimen.getId());
      return null;
    }
    List<Coding> codings = type.getCoding();
    if (Objects.isNull(codings) || codings.isEmpty()) {
      log.warn("No sample codings available for: " + specimen.getId());
      return null;
    }
    String code = codings.get(0).getCode();

    return BbmriBeaconTypeConverter.fromBbmriToBeacon(code);
  }
}
