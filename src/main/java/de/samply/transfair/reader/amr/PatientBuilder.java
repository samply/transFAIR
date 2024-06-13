package de.samply.transfair.reader.amr;

import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR Patient resources and extensions
 * from data extracted from an AMR CSV file.
 */
public class PatientBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Patient resource using attributes extracted from the record.
     *
     * @param record A map containing patient data, where keys represent data attributes.
     * @return A constructed Patient resource with populated properties and extensions.
     */
    public static Patient build(Map<String, String> record) {
        Patient patient = new Patient();

        // Extract patient data from the map and set properties
        String patientCounter = record.get("PatientCounter");
        String gender = record.get("Gender");
        String age = record.get("Age");
        String laboratoryCode = record.get("LaboratoryCode");
        String hospitalId = record.get("HospitalId");
        String hospitalUnitType = record.get("HospitalUnitType");

        // Set the patient's ID
        IdType patientId = new IdType(patientCounter);
        patient.setId(patientId);

        // Set other properties (e.g., gender, extensions)
        patient.setGender(mapStringToAdministrativeGender(gender));
        addAgeExtension(patient, age);
        addLaboratoryCodeExtension(patient, laboratoryCode);
        addHospitalIdExtension(patient, hospitalId);
        addHospitalUnitTypeExtension(patient, hospitalUnitType);

        return patient;
    }

    /**
     * Maps a string representation of gender to the corresponding HAPI FHIR AdministrativeGender enum.
     *
     * @param genderValue The string representation of gender.
     * @return The corresponding AdministrativeGender enum value.
     */
    private static AdministrativeGender mapStringToAdministrativeGender(String genderValue) {
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
                return AdministrativeGender.UNKNOWN; // Handle unknown values
        }
    }

    /**
     * Adds an age extension to the given Patient resource.
     *
     * @param patient The Patient resource to which the extension will be added.
     * @param age     The age value to be added to the extension.
     */
    private static void addAgeExtension(Patient patient, String age) {
        patient.addExtension(createStringExtension(age, "https://ecdc.amr/fhir/StructureDefinition/PatientAge"));
    }

    /**
     * Adds a laboratory code extension to the given Patient resource.
     *
     * @param patient        The Patient resource to which the extension will be added.
     * @param laboratoryCode The laboratory code value to be added to the extension.
     */
    private static void addLaboratoryCodeExtension(Patient patient, String laboratoryCode) {
        patient.addExtension(createStringExtension(laboratoryCode, "https://ecdc.amr/fhir/StructureDefinition/PatientLaboratoryCode"));
    }

    /**
     * Adds a hospital ID extension to the given Patient resource.
     *
     * @param patient  The Patient resource to which the extension will be added.
     * @param hospitalId The hospital ID value to be added to the extension.
     */
    private static void addHospitalIdExtension(Patient patient, String hospitalId) {
        patient.addExtension(createStringExtension(hospitalId, "https://ecdc.amr/fhir/StructureDefinition/PatientHospitalId"));
    }

    /**
     * Adds a hospital unit type extension to the given Patient resource.
     *
     * @param patient        The Patient resource to which the extension will be added.
     * @param hospitalUnitType The hospital unit type value to be added to the extension.
     */
    private static void addHospitalUnitTypeExtension(Patient patient, String hospitalUnitType) {
        patient.addExtension(createStringExtension(hospitalUnitType, "https://ecdc.amr/fhir/StructureDefinition/PatientHospitalUnitType"));
    }
}
