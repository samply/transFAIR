package de.samply.transfair.mapper.bbmri2beacon;

import de.samply.transfair.mapper.bbmri2beacon.BbmriBeaconAddressConverter;
import de.samply.transfair.mapper.bbmri2beacon.BbmriBeaconSexConverter;
import de.samply.transfair.models.beacon.BeaconGeographicOrigin;
import de.samply.transfair.models.beacon.BeaconIndividual;
import de.samply.transfair.models.beacon.BeaconIndividuals;
import de.samply.transfair.models.beacon.BeaconMeasure;
import de.samply.transfair.writer.BeaconMongoSaver;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.Address;
import org.hl7.fhir.r4.model.BaseDateTimeType;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.StringType;

import java.security.KeyManagementException;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.time.LocalDate;
import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

/**
 * This mapping transfers patient-related data from one blaze with bbmri to an
 * individuals.json file.
 */
@Slf4j
public class Bbmri2BeaconIndividual {
  private static final String COLLECTION_NAME = "individuals";
  private BeaconIndividuals beaconIndividuals;

  /**
   * Transfer data relating to patients from FHIR to Beacon.
   */
  public BeaconIndividuals transfer(Bundle bundle) {
    BeaconIndividuals beaconIndividuals = new BeaconIndividuals();

    int patientCount = 0;
    for (Bundle.BundleEntryComponent entry : bundle.getEntry()) {
      Resource resource = entry.getResource();
      if (resource instanceof Patient) {
        Patient patient = (Patient) resource;
        String patientId = transferId(patient);
        beaconIndividuals.add(transferIndividual(patient));
        patientCount++;
      }
    }

    log.info("Transferred: " + beaconIndividuals.individuals.size() + " individuals from "
            + patientCount + " patients");

    return beaconIndividuals;
  }

  /**
   * Create an object encapsulating a Beacon individual, based on a patient in the FHIR store.
   *
   * @param pid ID of a patient in FHIR store.
   * @return Beacon individual.
   */
  public BeaconIndividual transferIndividual(Patient patient) {
    return transferIndividual(patient, new ArrayList<IBaseResource>());
  }

  /**
   * Create an object encapsulating a Beacon individual, based on a patient in the FHIR store.
   *
   * @param patient Patient in FHIR store.
   * @return Beacon individual.
   */
  public BeaconIndividual transferIndividual(Patient patient, List<IBaseResource> observations) {
    String pid = transferId(patient);
    BeaconIndividual beaconIndividual = new BeaconIndividual();
    beaconIndividual.id = pid;
    String bbmriGender = patient.getGender().getDisplay();
    beaconIndividual.sex = BbmriBeaconSexConverter.fromBbmriToBeacon(bbmriGender);
    beaconIndividual.geographicOrigin = transferAddress(patient);
    beaconIndividual.measures = transferObservations(observations);

    return beaconIndividual;
  }

  /**
   * Pulls an ID from the BBMRI Patient.
   *
   * @param patient BBMRI Patient.
   * @return ID.
   */
  private String transferId(Patient patient) {
    String id = patient.getIdElement().getIdPart();
    if (Objects.isNull(id)) {
      id = patient.getId();
    }

    return id;
  }

  /**
   * Takes the country name from a Patient resource and creates a geographic origin
   * object suitable for use in Beacon.
   *
   * @param patient FHIR Patient resource.
   * @return Beacon geographic origin.
   */
  private BeaconGeographicOrigin transferAddress(Patient patient) {
    List<Address> addresses = patient.getAddress();
    if (!Objects.isNull(addresses)) {
      String country = null;
      for (int j = 0; j < addresses.size(); j++) {
        Address address = addresses.get(0);
        country = address.getCountry(); // ideally, this should return a non-null value
        if (Objects.isNull(country)) {
          List<StringType> addressLines = address.getLine();
          if (!Objects.isNull(addressLines)) {
            for (int i = addressLines.size() - 1; i >= 0; i--) {
              country = addressLines.get(i).asStringValue();
              if (BbmriBeaconAddressConverter.isCountry(country)) {
                break;
              }
            }
          }
        }
        if (!Objects.isNull(country) && !country.isEmpty()) {
          break;
        }
      }
      log.info("For patient " + patient.getId() + ", country=" + country);

      return BbmriBeaconAddressConverter.fromBbmriToBeacon(country);
    }

    return null;
  }

  /**
   * In Beacon, "measures" (such as body weight) are considered to be part of an individual,
   * whereas in FHIR, they are contracted out to the Observation resource. In this method,
   * relevant information is extracted from the Observations associated with
   * a patient and transferred to a set of Beacon measures.
   *
   * @param observations List of Observations for the patient.
   * @return A set of Beacon measures for the patient.
   */
  private List<BeaconMeasure> transferObservations(List<IBaseResource> observations) {
    List<BeaconMeasure> measures = new ArrayList<>();
    for (IBaseResource o : observations) {
      BeaconMeasure measure = transferObservation((Observation) o);
      if (!Objects.isNull(measure)) {
        measures.add(measure);
      }
    }

    return measures;
  }

  /**
   * Transfer the information in the given FHIR Observation to a Beacon measure.
   *
   * @param observation FHIR Observation resource.
   * @return Beacon measure.
   */
  private BeaconMeasure transferObservation(Observation observation) {
    String id = observation.getId();
    String effectiveDateTime = null;
    if (Objects.isNull(observation.getEffective())) {
      log.warn("getEffective returns null for observation " + id);
    } else {
      BaseDateTimeType dataTimeType = observation.getEffectiveDateTimeType().dateTimeValue();
      int y = dataTimeType.getYear().intValue();
      int m = dataTimeType.getMonth().intValue() + 1; // FHIR months start at 0!
      int d = dataTimeType.getDay().intValue();
      effectiveDateTime = LocalDate.of(y, m, d).toString();
    }
    List<Coding> codings = observation.getCode().getCoding();
    if (Objects.isNull(codings) || codings.isEmpty()) {
      log.warn("No codings in Observation " + id);
      return null;
    }
    String code = codings.get(0).getCode();
    if (Objects.isNull(code)) {
      log.warn("Null coding in Observation: " + id);
      return null;
    }
    if (code.equals("39156-5")) {
      log.info("Observation is BMI");
      double value = observation.getValueQuantity().getValue().doubleValue();
      return BeaconMeasure.makeBmiMeasure(value, effectiveDateTime);
    } else if (code.equals("29463-7")) {
      log.info("Observation is Weight");
      double value = observation.getValueQuantity().getValue().doubleValue();
      return BeaconMeasure.makeWeightMeasure(value, effectiveDateTime);
    } else if (code.equals("8302-2")) {
      log.info("Observation is Height");
      double value = observation.getValueQuantity().getValue().doubleValue();
      return BeaconMeasure.makeHeightMeasure(value, effectiveDateTime);
    } else {
      log.warn("Unknown code " + code + " in Observation " + id);
    }

    return null;
  }
}
