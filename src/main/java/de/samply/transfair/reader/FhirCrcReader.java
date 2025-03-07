package de.samply.transfair.reader;

import de.samply.transfair.reader.crc.CsvReader;
import de.samply.transfair.reader.crc.PatientBuilder;
import de.samply.transfair.reader.crc.ObservationBuilder;
import de.samply.transfair.reader.crc.SpecimenBuilder;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Observation;

import lombok.extern.slf4j.Slf4j;

import java.util.Map;

/**
 * FhirCrcReader reads data from an CRC cohort source and generates FHIR resources.
 * Depending on the settings in application.yml, it processes CSV files from the specified directory.
 * FHIR objects, such as Patients and Observations, are created based on the data from the CSV files.
 * One Observation is created for every row in the table, and one Patient is created for every unique PatientCounter value.
 *
 * Usage:
 * - Configure the 'crc.filePath' property in application.yml to specify the path to a file or directory containing .csv files.
 * - Call the 'read' method to generate FHIR resources from the CSV data.
 *
 * Returns a Bundle containing the generated FHIR resources if data has been read from the file(s).
 * Returns null once no more data is left to read.
 */
@Slf4j
public class FhirCrcReader implements ItemReader<Bundle> {
  private Bundle bundle;
  @Value("${crc.patientFilePath}")
  private String patientFilePath;

  public FhirCrcReader(IGenericClient client){
  }

  /**
   * Reads data from an CRC cohort source. Depending on the settings of the relevant
   * parameters in application.yml, different sources will be used.
   *
   * crc.patientFilePath will be treated as the path
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

    PatientBuilder patientBuilder = new PatientBuilder();
    SpecimenBuilder specimenBuilder = new SpecimenBuilder();
    ObservationBuilder observationBuilder = new ObservationBuilder();
    int recordCounter = 0;
    for (Map<String, String> record : CsvReader.readCsvFilesInDirectory(patientFilePath)) {
      Patient patient = patientBuilder.build(record);
      specimenBuilder.build(patient, record);

      // Create the observation
      Observation observation = observationBuilder.build(recordCounter, patient, record);

      recordCounter++;
    }

    observationBuilder.addResourcesToBundle(bundle);
    patientBuilder.addResourcesToBundle(bundle);
    specimenBuilder.addResourcesToBundle(bundle);

    return bundle;
  }
}
