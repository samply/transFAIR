package de.samply.transfair.reader;

import de.samply.transfair.reader.amr.CsvReader;
import de.samply.transfair.reader.amr.PatientBuilder;
import de.samply.transfair.reader.amr.ObservationBuilder;
import de.samply.transfair.reader.amr.LaboratoryBuilder;
import de.samply.transfair.reader.amr.SpecimenBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;
import java.util.HashMap;

/**
 * FhirAmrReader reads data from an AMR source and generates FHIR resources.
 * Depending on the settings in application.yml, it processes CSV files from the specified directory.
 * FHIR objects, such as Patients and Observations, are created based on the data from the CSV files.
 * One Observation is created for every row in the table, and one Patient is created for every unique PatientCounter value.
 *
 * Usage:
 * - Configure the 'amr.filePath' property in application.yml to specify the path to a file or directory containing .csv files.
 * - Call the 'read' method to generate FHIR resources from the CSV data.
 *
 * Returns a Bundle containing the generated FHIR resources if data has been read from the file(s).
 * Returns null once no more data is left to read.
 */
@Slf4j
public class FhirAmrReader implements ItemReader<Bundle> {
  private Bundle bundle;
  @Value("${amr.filePath}")
  private String amrFilePath;

  public FhirAmrReader(IGenericClient client){
  }

  /**
   * Reads data from an AMR source. Depending on the settings of the relevant
   * parameters in application.yml, different sources will be used.
   *
   * amr.filePath will be treated as the path
   * to a file or directory containíng .csv files. The data from all of these
   * files will be extracted.
   *
   * FHIR objects will be generated, based on the data from the CSV files.
   * One Observation will be created for every row in the table.
   * One Patient will be created for every value of the PatientCounter.
   *
   * Returns a Bundle if something has been read from a file.
   *
   * Returns null once nothing more is left to read.
   *
   * @return
   */
  public Bundle read() {
    if (bundle == null)
      bundle = new Bundle();
    else
      return null; // Terminate reading

    Map<String, Patient> patientMap = new HashMap<String, Patient>();
    Map<String, Specimen> specimenMap = new HashMap<String, Specimen>();
    Map<String,String> observationIdMap = new HashMap<String, String>();
    Map<String, Organization> laboratoryMap = new HashMap<String, Organization>();
    int recordCounter = 0;
    for (Map<String, String> record : CsvReader.readCsvFilesInDirectory(amrFilePath)) {
      // Create or retrieve the patient
      Patient patient = patientMap.computeIfAbsent(record.get("PatientCounter"),
              key -> PatientBuilder.buildPatient(record));

      // Create or retrieve the isolate
      Specimen specimen = specimenMap.computeIfAbsent(record.get("IsolateId"),
              key -> SpecimenBuilder.buildSpecimen(patient, record));

      // Create the observation
      Observation observation = ObservationBuilder.buildObservation(recordCounter, patient, record, observationIdMap);
      recordCounter++;

      if (observation == null)
        continue;

      // Create or retrieve the laboratory
      Organization laboratory = laboratoryMap.computeIfAbsent(record.get("LaboratoryCode"),
              key -> LaboratoryBuilder.buildLaboratory(observation, record));

      // Pack the Observation into the Bundle
      Bundle.BundleEntryComponent observationEntry = new Bundle.BundleEntryComponent();
      observationEntry.setResource(observation);
      bundle.addEntry(observationEntry);
    }

    // Pack the Patient resources into the Bundle
    for (Patient patient: patientMap.values()) {
      Bundle.BundleEntryComponent patientEntry = new Bundle.BundleEntryComponent();
      patientEntry.setResource(patient);
      bundle.addEntry(patientEntry);
    }

    // Pack the Specimen resources into the Bundle
    for (Specimen specimen: specimenMap.values()) {
      Bundle.BundleEntryComponent specimenEntry = new Bundle.BundleEntryComponent();
      specimenEntry.setResource(specimen);
      bundle.addEntry(specimenEntry);
    }

    // Pack the laboratory resources into the Bundle
    for (Organization laboratory: laboratoryMap.values()) {
      log.info("laboratory: " + laboratory.getIdElement().getIdPart());
      Bundle.BundleEntryComponent laboratoryEntry = new Bundle.BundleEntryComponent();
      laboratoryEntry.setResource(laboratory);
      bundle.addEntry(laboratoryEntry);
    }

    return bundle;
  }
}
