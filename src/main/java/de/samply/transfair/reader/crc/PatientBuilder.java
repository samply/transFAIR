package de.samply.transfair.reader.crc;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.springframework.beans.factory.annotation.Value;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR Patient resources and extensions
 * from data extracted from an CRC cohort CSV file.
 */
@Slf4j
public class PatientBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Patient resource using attributes extracted from the record.
     *
     * @param record          A map containing patient data, where keys represent data attributes.
     * @param patientMaxCount
     * @return A constructed Patient resource with populated properties and extensions.
     */
    public Patient build(Map<String, String> record, String patientMaxCount) {
        // Limit the number of patients to be processed. Intended to be used for testing purposes.
        if (patientMaxCount != null && !patientMaxCount.isEmpty() && getResourceMapSize() >= Integer.parseInt(patientMaxCount))
            return null;

        Patient resource = new Patient();
        String identifier = generateResourceIdentifier(record);
        if (resourceMap.containsKey(identifier))
            return (Patient) resourceMap.get(identifier);
        resourceMap.put(identifier, resource);

        setId(resource);
        setIdentifier(resource, identifier);

        // Extract patient data from the map and set properties
        String gender = record.get("sex");
        String age = record.get("age_at_primary_diagnosis");

        // Set other properties (e.g., gender, extensions)
        resource.setGender(mapStringToAdministrativeGender(gender));
        addAgeExtension(resource, age);

        return resource;
    }

    /**
     * Maps a string representation of gender to the corresponding HAPI FHIR AdministrativeGender enum.
     *
     * @param genderValue The string representation of gender.
     * @return The corresponding AdministrativeGender enum value.
     */
    private AdministrativeGender mapStringToAdministrativeGender(String genderValue) {
        switch (genderValue.toUpperCase()) {
            case "MALE":
                return AdministrativeGender.MALE;
            case "FEMALE":
                return AdministrativeGender.FEMALE;
            case "UNKOWN":
                return AdministrativeGender.UNKNOWN;
            case "OTHER":
                return AdministrativeGender.OTHER;
            default:
                return AdministrativeGender.UNKNOWN; // Handle values that don't fit any of the above
        }
    }

    /**
     * Adds an age extension to the given Patient resource.
     *
     * @param patient The Patient resource to which the extension will be added.
     * @param age     The age value to be added to the extension.
     */
    private void addAgeExtension(Patient patient, String age) {
        patient.addExtension(createStringExtension(age, "https://bbmri.crc/fhir/StructureDefinition/PatientAge"));
    }

    /**
     * Adds a laboratory code extension to the given Patient resource.
     *
     * @param patient        The Patient resource to which the extension will be added.
     * @param laboratoryCode The laboratory code value to be added to the extension.
     */
    private void addLaboratoryCodeExtension(Patient patient, String laboratoryCode) {
        patient.addExtension(createStringExtension(laboratoryCode, "https://bbmri.crc/fhir/StructureDefinition/PatientLaboratoryCode"));
    }

    /**
     * Adds a hospital ID extension to the given Patient resource.
     *
     * @param patient  The Patient resource to which the extension will be added.
     * @param hospitalId The hospital ID value to be added to the extension.
     */
    private void addHospitalIdExtension(Patient patient, String hospitalId) {
        patient.addExtension(createStringExtension(hospitalId, "https://bbmri.crc/fhir/StructureDefinition/PatientHospitalId"));
    }

    /**
     * Adds a hospital unit type extension to the given Patient resource.
     *
     * @param patient        The Patient resource to which the extension will be added.
     * @param hospitalUnitType The hospital unit type value to be added to the extension.
     */
    private void addHospitalUnitTypeExtension(Patient patient, String hospitalUnitType) {
        patient.addExtension(createStringExtension(hospitalUnitType, "https://bbmri.crc/fhir/StructureDefinition/PatientHospitalUnitType"));
    }
}
