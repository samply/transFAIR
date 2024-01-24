package de.samply.transfair.reader;

import org.springframework.batch.item.ItemReader;
import org.springframework.beans.factory.annotation.Value;

import ca.uhn.fhir.model.api.Include;
import ca.uhn.fhir.rest.client.api.IGenericClient;
//import org.dcm4che3.data.Attributes;
import org.hl7.fhir.instance.model.api.IBaseBundle;
import ca.uhn.fhir.context.FhirContext;
import ca.uhn.fhir.model.api.TemporalPrecisionEnum;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.IntegerType;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Bundle.HTTPVerb;
import org.hl7.fhir.r4.model.IdType;

import org.apache.commons.csv.CSVFormat;
import org.apache.commons.csv.CSVParser;
import org.apache.commons.csv.CSVRecord;

import lombok.extern.slf4j.Slf4j;

import java.io.FileReader;
import java.io.IOException;
import java.io.Reader;
import java.util.Map;
import java.util.HashMap;

@Slf4j
public class FhirAmtReader implements ItemReader<Bundle> {

  private final IGenericClient client;
  private Bundle bundle;
  @Value("${amt.filePath}")
  private String amtFilePath;

  public FhirAmtReader(IGenericClient client){
    this.client = client;
  }

  /**
   * Reads data from an AMT source. Depending on the settings of the relevant
   * parameters in application.yml, different sources will be used.
   *
   * amt.filePath will be treated as the path
   * to a file or directory containíng .dcm files. The metadata from all of these
   * files will be extracted.
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
      // Terminate reading
      return null;

    // Replace with your file path
    String filePath = amtFilePath + "/amrtest_fake_2023_11_27.csv";

    FhirContext ctx = FhirContext.forR4();

    try (Reader reader = new FileReader(filePath);
         CSVParser csvParser = CSVFormat.DEFAULT.withHeader().parse(reader)) {

      Map<String, Patient> patientMap = new HashMap<String, Patient>();

      int recordCounter = 0;
      for (CSVRecord record : csvParser) {
        // Extract Patient data from the CSV record
        String patientCounter = record.get("PatientCounter");
        String gender = record.get("Gender");
        String age = record.get("Age");
        String laboratoryCode = record.get("LaboratoryCode");
        String hospitalId = record.get("HospitalId");
        String hospitalUnitType = record.get("HospitalUnitType");

        // Create or retrieve the patient
        Patient patient = patientMap.computeIfAbsent(patientCounter,
                          key -> createPatient(patientCounter, gender, age, laboratoryCode, hospitalId, hospitalUnitType));

        // Extract Observation data from the CSV record
        String pathogen = record.get("Pathogen");
        String antibiotic = record.get("Antibiotic");
        String sir = record.get("SIR");
        String isolateId = record.get("IsolateId");
        String dataSource = record.get("DataSource");
        String patientType = record.get("PatientType");
        String reportingCountry = record.get("ReportingCountry");

        // Create the observation
        Observation observation = createObservation(recordCounter, patient, pathogen, antibiotic, sir, isolateId, dataSource, patientType, reportingCountry);

        Bundle.BundleEntryComponent observationEntry = new Bundle.BundleEntryComponent();
        observationEntry.setResource(observation);


        bundle.addEntry(observationEntry);

        recordCounter++;
      }

      for (Patient patient: patientMap.values()) {
        Bundle.BundleEntryComponent patientEntry = new Bundle.BundleEntryComponent();
        patientEntry.setResource(patient);

        bundle.addEntry(patientEntry);
      }
    } catch (IOException e) {
      e.printStackTrace();
    }

    return bundle;
  }

  private Patient createPatient(String patientCounter, String gender, String age, String laboratoryCode, String hospitalId, String hospitalUnitType) {
    Patient patient = new Patient();

    // Set the patient's ID
    IdType patientId = new IdType(patientCounter);
    patient.setId(patientId);

    patient.setGender(mapStringToAdministrativeGender(gender));

    // Add extensions
    addAgeExtensionToPatient(patient, age);
    addLaboratoryCodeExtensionToPatient(patient, laboratoryCode);
    addHospitalIdExtensionToPatient(patient, hospitalId);
    addHospitalUnitTypeExtensionToPatient(patient, hospitalUnitType);

    return patient;
  }

  private AdministrativeGender mapStringToAdministrativeGender(String genderValue) {
    switch (genderValue.toUpperCase()) {
      case "M":
        return AdministrativeGender.MALE;
      case "F":
        return AdministrativeGender.FEMALE;
      case "UNK":
        return AdministrativeGender.UNKNOWN;
      case "O":
        return AdministrativeGender.OTHER;
      default:
        return null; // Handle unknown values
    }
  }

  private void addAgeExtensionToPatient(Patient patient, String age) {
    patient.addExtension(createIntegerExtension(age, "https://ecdc.amt/fhir/StructureDefinition/PatientAge"));
  }

  private void addLaboratoryCodeExtensionToPatient(Patient patient, String laboratoryCode) {
    patient.addExtension(createStringExtension(laboratoryCode, "https://ecdc.amt/fhir/StructureDefinition/PatientLaboratoryCode"));
  }

  private void addHospitalIdExtensionToPatient(Patient patient, String hospitalId) {
    patient.addExtension(createStringExtension(hospitalId, "https://ecdc.amt/fhir/StructureDefinition/PatientHospitalId"));
  }

  private void addHospitalUnitTypeExtensionToPatient(Patient patient, String hospitalUnitType) {
    patient.addExtension(createStringExtension(hospitalUnitType, "https://ecdc.amt/fhir/StructureDefinition/PatientHospitalUnitType"));
  }

  private Observation createObservation(int recordCounter, Patient patient, String pathogen, String antibiotic, String sir, String isolateId, String dataSource, String patientType, String reportingCountry) {
    Observation observation = new Observation();

    String patientId = patient.getIdElement().getValueAsString();
    observation.setId(patientId + "." + recordCounter);
    observation.getSubject().setReference("Patient/" + patientId);
    observation.getCode().setText("Antibiotic Resistance");
    observation.setValue(constructObjectValueCodeableConcept(pathogen, antibiotic, sir));

    // Add extensions
    addIsolateIdExtensionToObservation(observation, isolateId);
    addDataSourceExtensionToObservation(observation, dataSource);
    addPatientTypeExtensionToObservation(observation, patientType);
    addReportingCountryExtensionToObservation(observation, reportingCountry);

    return observation;
  }

  private void addIsolateIdExtensionToObservation(Observation observation, String isolateId) {
    observation.addExtension(createStringExtension(isolateId, "https://ecdc.amt/fhir/StructureDefinition/ObservationIsolateId"));
  }

  private void addDataSourceExtensionToObservation(Observation observation, String dataSource) {
    observation.addExtension(createStringExtension(dataSource, "https://ecdc.amt/fhir/StructureDefinition/ObservationDataSource"));
  }

  private void addPatientTypeExtensionToObservation(Observation observation, String patientType) {
    observation.addExtension(createStringExtension(patientType, "https://ecdc.amt/fhir/StructureDefinition/ObservationPatientType"));
  }

  private void addReportingCountryExtensionToObservation(Observation observation, String reportingCountry) {
    observation.addExtension(createStringExtension(reportingCountry, "https://ecdc.amt/fhir/StructureDefinition/ObservationReportingCountry"));
  }

  private CodeableConcept constructObjectValueCodeableConcept(String pathogen, String antibiotic, String sir) {
    CodeableConcept codeableConcept = new CodeableConcept();
    // Add coding for pathogen
    Coding pathogenCoding = new Coding();
    pathogenCoding.setSystem("https://ecdc.amt/pathogen-codes"); // Replace with the actual system URI
    pathogenCoding.setCode(pathogen);
    codeableConcept.addCoding(pathogenCoding);
    // Add coding for antibiotic
    Coding antibioticCoding = new Coding();
    antibioticCoding.setSystem("https://ecdc.amt/antibiotic-codes"); // Replace with the actual system URI
    antibioticCoding.setCode(antibiotic);
    codeableConcept.addCoding(antibioticCoding);
    // Add coding for SIR
    Coding sirCoding = new Coding();
    sirCoding.setSystem("https://ecdc.amt/sir-codes"); // Replace with the actual system URI
    sirCoding.setCode(sir);
    codeableConcept.addCoding(sirCoding);

    return codeableConcept;
  }

  private Extension createStringExtension(String value, String url) {
    Extension extension = new Extension();
    extension.setUrl(url);
    extension.setValue(new StringType(value));

    return extension;
  }

  private Extension createIntegerExtension(String value, String url) {
    Extension extension = new Extension();
    extension.setUrl(url);
    extension.setValue(new IntegerType(Integer.parseInt(value)));

    return extension;
  }
}
