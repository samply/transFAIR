package de.samply.transfair.reader;

import de.samply.transfair.reader.crc.BiobankBuilder;
import de.samply.transfair.reader.crc.CollectionBuilder;
import de.samply.transfair.reader.crc.ConditionBuilder;
import de.samply.transfair.reader.crc.CsvReader;
import de.samply.transfair.reader.crc.PatientBuilder;
import de.samply.transfair.reader.crc.SpecimenBuilder;
import de.samply.transfair.util.JsonSerializer;
import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;

import ca.uhn.fhir.rest.client.api.IGenericClient;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Type;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.StringType;

import lombok.extern.slf4j.Slf4j;

import java.util.Calendar;
import java.util.Date;
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
  @Value("${crc.specimenFilePath}")
  private String specimenFilePath;
  @Value("${crc.patientMaxCount}")
  private String patientMaxCount;

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
    log.info("read: entered");
    if (bundle == null)
      bundle = new Bundle();
    else
      return null; // Terminate reading

    PatientBuilder patientBuilder = new PatientBuilder();
    SpecimenBuilder specimenBuilder = new SpecimenBuilder();
    CollectionBuilder collectionBuilder = new CollectionBuilder();
    BiobankBuilder biobankBuilder = new BiobankBuilder();
    ConditionBuilder conditionBuilder = new ConditionBuilder();
    log.info("read: reading patient data from: " + patientFilePath);
    for (Map<String, String> record : CsvReader.readCsvFilesInDirectory(patientFilePath)) {
      log.info("read: patient record: " + JsonSerializer.toJsonString(record));
      Patient patient = patientBuilder.build(record, patientMaxCount);
      if (patient == null)
        break;
      conditionBuilder.build(patient, record);
    }
    log.info("read: reading specimen data from: " + specimenFilePath);
    for (Map<String, String> record : CsvReader.readCsvFilesInDirectory(specimenFilePath)) {
      Organization biobank = biobankBuilder.build(record);
      Organization collection = collectionBuilder.build(biobank, record);
      specimenBuilder.build(patientBuilder, collection, record);
    }

    calculatePatientDatesOfBirth(patientBuilder, specimenBuilder);

    log.info("read: adding resources to bundle");
    patientBuilder.addResourcesToBundle(bundle);
    specimenBuilder.addResourcesToBundle(bundle);
    collectionBuilder.addResourcesToBundle(bundle);
    biobankBuilder.addResourcesToBundle(bundle);
    conditionBuilder.addResourcesToBundle(bundle);

    log.info("read: done, bundle size: " + bundle.getEntry().size());
    return bundle;
  }

  /**
   * Calculates the date of birth of each Patient based on the data in the Specimen table.
   *
   * The Patient objects contain an extension with patient age at first diagnosis, in years.
   *
   * Specimen objects contain a collection year.
   *
   * This method finds the earliest collection year in the Specimen table for each Patient
   * and subtracts the patient age to get the year of birth.
   *
   *
   * @param patientBuilder
   * @param specimenBuilder
   */
  private void calculatePatientDatesOfBirth(PatientBuilder patientBuilder, SpecimenBuilder specimenBuilder) {
    Map<String, Resource> patientMap = patientBuilder.getResourceMap();
    Map<String, Resource> specimenMap = specimenBuilder.getResourceMap();
    int patientCount = 0;
    int ageExtensionCount = 0;
    int patientAgeCount = 0;
    int specimenAvailableCount = 0;
    int specimenDateTimeCount = 0;
    for (Resource patient : patientMap.values()) {
      patientCount++;
      Patient patientObj = (Patient) patient;

      // Get the extension
      Extension ageExtension = patientObj.getExtensionByUrl("https://bbmri.crc/fhir/StructureDefinition/PatientAge");
      if (ageExtension == null) {
        log.warn("calculatePatientDatesOfBirth: Extension https://bbmri.crc/fhir/StructureDefinition/PatientAge not found");
        continue;
      }
      ageExtensionCount++;
      // Get the value from the extension
      Type ageValue = ageExtension.getValue();
      if (ageValue == null) {
        log.warn("calculatePatientDatesOfBirth: Value not found in extension https://bbmri.crc/fhir/StructureDefinition/PatientAge");
        continue;
      }
      // Cast to IntegerType
      int patientAge = (-1);
      if (ageValue instanceof IntegerType)
        patientAge = ((IntegerType) ageValue).getValue();
      else {
        String stringAgeValue = ageValue.toString();
        if (stringAgeValue == null) {
          log.warn("calculatePatientDatesOfBirth: stringAgeValue == null");
          continue;
        } else if (stringAgeValue.isEmpty()) {
          log.warn("calculatePatientDatesOfBirth: stringAgeValue.isEmpty()");
          continue;
        } else if (!stringAgeValue.matches("\\d+")) {
          log.warn("calculatePatientDatesOfBirth: stringAgeValue does not not look like an integer: " + stringAgeValue);
          continue;
        }
        patientAge = Integer.parseInt(stringAgeValue);
      }
      patientAgeCount++;
      int minYear = 10000;
      boolean specimenAvailable = false;
      boolean specimenHasCollectedDateTime = false;
      for (Resource specimen : specimenMap.values()) {
        if (((Specimen)specimen).hasSubject() && ((Specimen)specimen).getSubject().getReference().equals("Patient/" + patientObj.getIdElement().getIdPart())) {
          specimenAvailable = true;
          if (((Specimen) specimen).hasCollection() && ((Specimen) specimen).getCollection().hasCollectedDateTimeType()) {
            specimenHasCollectedDateTime = true;
            Date collectedDate = ((Specimen) specimen).getCollection().getCollectedDateTimeType().getValue();
            Calendar calendar = Calendar.getInstance();
            calendar.setTime(collectedDate);
            int year = calendar.get(Calendar.YEAR);
            if (year < minYear)
              minYear = year;
          }
        }
      }
      if (!specimenAvailable) {
        log.warn("calculatePatientDatesOfBirth: no specimens available for patient: " + patientObj.getIdElement().getIdPart());
        log.warn("calculatePatientDatesOfBirth: patient identifier: " + patientObj.getIdentifier().get(0).getValue());
        continue;
      } else
        specimenAvailableCount++;
      if (!specimenHasCollectedDateTime) {
        log.warn("calculatePatientDatesOfBirth: no specimens with collection date for patient: " + patientObj.getIdElement().getIdPart());
        continue;
      } else
        specimenDateTimeCount++;

      if (minYear < 10000) {
        int yearOfBirth = minYear - patientAge;
        // Set the date to January 1st of the calculated birth year
        Calendar calendar = Calendar.getInstance();
        calendar.clear(); // Clear all fields to avoid unwanted time parts
        calendar.set(Calendar.YEAR, yearOfBirth);
        calendar.set(Calendar.MONTH, Calendar.JANUARY);
        calendar.set(Calendar.DAY_OF_MONTH, 1);

        // Set the birth date in the Patient object
        patientObj.getBirthDateElement().setValue(calendar.getTime());
      }
    }
    log.info("calculatePatientDatesOfBirth: patientCount: " + patientCount);
    log.info("calculatePatientDatesOfBirth: ageExtensionCount: " + ageExtensionCount);
    log.info("calculatePatientDatesOfBirth: patientAgeCount: " + patientAgeCount);
    log.info("calculatePatientDatesOfBirth: specimenAvailableCount: " + specimenAvailableCount);
    log.info("calculatePatientDatesOfBirth: specimenDateTimeCount: " + specimenDateTimeCount);
  }
}
