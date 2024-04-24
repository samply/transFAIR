package de.samply.transfair.reader.amr;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

import java.time.ZonedDateTime;
import java.util.Map;

/**
 * This is a utility class for constructing FHIR Observation resources and extensions
 * from data extracted from an AMR CSV file.
 */
@Slf4j
public class ObservationBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Observation resource using attributes extracted from the record.
     *
     * @param recordCounter   A counter for the record, used in generating the Observation ID.
     * @param patient         The associated Patient for the Observation.
     * @param record A map containing observation data, where keys represent data attributes.
     * @return A constructed Observation resource with populated properties and extensions.
     */
    public static Observation buildObservation(int recordCounter, Patient patient, Map<String, String> record) {
        Observation observation = new Observation();

        String patientId = patient.getIdElement().getValueAsString();
        observation.setId(patientId + "." + recordCounter);

        // Extract observation data from the map
        String pathogen = record.get("Pathogen");
        String antibiotic = record.get("Antibiotic");
        String sir = record.get("SIR");
        String isolateId = record.get("IsolateId");
        String dataSource = record.get("DataSource");
        String patientType = record.get("PatientType");
        String reportingCountry = record.get("ReportingCountry");
        String referenceGuidelinesSir = record.get("ReferenceGuidelinesSIR");
        String dateUsedForStatistics = record.get("DateUsedForStatistics");

        // Set properties of the Observation
        observation.getSubject().setReference("Patient/" + patient.getIdElement().getIdPart());
        observation.setCode(new CodeableConcept().setText("Antibiotic Resistance"));
        observation.setValue(constructObjectValueCodeableConcept(pathogen, antibiotic, sir));

        // Set the effective date to the current date and time
        ZonedDateTime currentTime = ZonedDateTime.now();
        observation.setEffective(new DateTimeType(currentTime.toInstant().toString()));

        // Add extensions
        addIsolateIdExtension(observation, isolateId);
        addDataSourceExtension(observation, dataSource);
        addPatientTypeExtension(observation, patientType);
        addReportingCountryExtension(observation, reportingCountry);
        addReferenceGuidelinesSirExtension(observation, referenceGuidelinesSir);
        addDateUsedForStatistics(observation, dateUsedForStatistics);

        return observation;
    }

    /**
     * Adds an isolate ID extension to the given Observation resource.
     *
     * @param observation The Observation resource to which the extension will be added.
     * @param isolateId   The isolate ID value to be added to the extension.
     */
    private static void addIsolateIdExtension(Observation observation, String isolateId) {
        observation.addExtension(createStringExtension(isolateId, "https://ecdc.amr/fhir/StructureDefinition/ObservationIsolateId"));
    }

    /**
     * Adds a data source extension to the given Observation resource.
     *
     * @param observation The Observation resource to which the extension will be added.
     * @param dataSource  The data source value to be added to the extension.
     */
    private static void addDataSourceExtension(Observation observation, String dataSource) {
        observation.addExtension(createStringExtension(dataSource, "https://ecdc.amr/fhir/StructureDefinition/ObservationDataSource"));
    }

    /**
     * Adds a patient type extension to the given Observation resource.
     *
     * @param observation The Observation resource to which the extension will be added.
     * @param patientType The patient type value to be added to the extension.
     */
    private static void addPatientTypeExtension(Observation observation, String patientType) {
        observation.addExtension(createStringExtension(patientType, "https://ecdc.amr/fhir/StructureDefinition/ObservationPatientType"));
    }

    /**
     * Adds a reporting country extension to the given Observation resource.
     *
     * @param observation      The Observation resource to which the extension will be added.
     * @param reportingCountry The reporting country value to be added to the extension.
     */
    private static void addReportingCountryExtension(Observation observation, String reportingCountry) {
        observation.addExtension(createStringExtension(reportingCountry, "https://ecdc.amr/fhir/StructureDefinition/ObservationReportingCountry"));
    }

    /**
     * Adds a reference guidelines extension to the given Observation resource.
     *
     * @param observation      The Observation resource to which the extension will be added.
     * @param referenceGuidelinesSir The reference guidelines value to be added to the extension.
     */
    private static void addReferenceGuidelinesSirExtension(Observation observation, String referenceGuidelinesSir) {
        observation.addExtension(createStringExtension(referenceGuidelinesSir, "https://ecdc.amr/fhir/StructureDefinition/ObservationReferenceGuidelinesSIR"));
    }

    private static void addDateUsedForStatistics(Observation observation, String dateUsedForStatistics) {
        observation.addExtension(createStringExtension(dateUsedForStatistics, "https://ecdc.amr/fhir/StructureDefinition/ObservationDateUsedForStatistics"));
    }

    /**
     * Constructs a CodeableConcept for the value of the Observation using provided pathogen, antibiotic, and SIR values.
     *
     * @param pathogen   The pathogen value.
     * @param antibiotic The antibiotic value.
     * @param sir        The SIR (degree of antibiotic resistance) value.
     * @return A CodeableConcept representing the value of the Observation.
     */
    private static CodeableConcept constructObjectValueCodeableConcept(String pathogen, String antibiotic, String sir) {
        CodeableConcept codeableConcept = new CodeableConcept();
        // Add coding for pathogen
        Coding pathogenCoding = new Coding();
        pathogenCoding.setSystem("https://ecdc.amr/pathogen-codes"); // Replace with the actual system URI
        pathogenCoding.setCode(pathogen);
        codeableConcept.addCoding(pathogenCoding);
        // Add coding for antibiotic
        Coding antibioticCoding = new Coding();
        antibioticCoding.setSystem("https://ecdc.amr/antibiotic-codes"); // Replace with the actual system URI
        antibioticCoding.setCode(antibiotic);
        codeableConcept.addCoding(antibioticCoding);
        // Add coding for SIR
        Coding sirCoding = new Coding();
        sirCoding.setSystem("https://ecdc.amr/sir-codes"); // Replace with the actual system URI
        sirCoding.setCode(sir);
        codeableConcept.addCoding(sirCoding);

        return codeableConcept;
    }
}
