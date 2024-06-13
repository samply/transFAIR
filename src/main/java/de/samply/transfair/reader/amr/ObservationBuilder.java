package de.samply.transfair.reader.amr;

import de.samply.transfair.util.HashUtils;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Reference;

import java.text.ParseException;
import java.text.SimpleDateFormat;
import java.util.ArrayList;
import java.util.Date;

import java.time.ZonedDateTime;
import java.util.List;
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
     * @param recordCounter A counter for the record, used in generating the Observation ID.
     * @param patient The associated Patient for the Observation.
     * @param record A map containing observation data, where keys represent data attributes.
     * @return A constructed Observation resource with populated properties and extensions.
     */
    public static Observation build(int recordCounter, Patient patient, Map<String, String> record, Map<String,String> observationIdMap) {
        Observation observation = new Observation();

        // Extract observation data from the map
        List<String> attributes = new ArrayList<String>();
        String pathogen = record.get("Pathogen"); attributes.add(pathogen);
        String antibiotic = record.get("Antibiotic"); attributes.add(antibiotic);
        String sir = record.get("SIR"); attributes.add(sir);
        String isolateId = record.get("IsolateId"); attributes.add(isolateId);
        String dataSource = record.get("DataSource"); attributes.add(dataSource);
        String patientType = record.get("PatientType"); attributes.add(patientType);
        String reportingCountry = record.get("ReportingCountry"); attributes.add(reportingCountry);
        String referenceGuidelinesSir = record.get("ReferenceGuidelinesSIR"); attributes.add(referenceGuidelinesSir);
        String dateUsedForStatistics = record.get("DateUsedForStatistics"); attributes.add(dateUsedForStatistics);

        // Create an ID for the Observation as a hash of all the attributes
        String patientId = patient.getIdElement().getValueAsString();
        String id = patientId + "." + HashUtils.generateHashFromStringList(attributes);
        log.info("buildObservation: id: " + id);
        // Don't store duplicated observations
        if (observationIdMap.containsKey(id))
            return null;
        else
            observationIdMap.put(id, id);
        observation.setId(id);

        // Set properties of the Observation
        observation.getSubject().setReference("Patient/" + patient.getIdElement().getIdPart());
        observation.setCode(new CodeableConcept().setText("Antibiotic Resistance"));
        observation.setValue(constructObjectValueCodeableConcept(pathogen, antibiotic, sir));

        // Set the effective date to the current date and time
        ZonedDateTime currentTime = ZonedDateTime.now();
        observation.setEffective(new DateTimeType(currentTime.toInstant().toString()));

        // Use "issued" for statistics date
        SimpleDateFormat formatter = new SimpleDateFormat("yyyy-MM-dd");
        try {
            Date issuedDate = formatter.parse(dateUsedForStatistics);
            observation.setIssued(issuedDate);
        } catch (ParseException e) {
            log.warn(e.toString());
        }

        // Add extensions
        addIsolateIdExtension(observation, isolateId);
        addDataSourceExtension(observation, dataSource);
        addPatientTypeExtension(observation, patientType);
        addReportingCountryExtension(observation, reportingCountry);
        addReferenceGuidelinesSirExtension(observation, referenceGuidelinesSir);

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
