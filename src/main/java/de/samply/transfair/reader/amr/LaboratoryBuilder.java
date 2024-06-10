package de.samply.transfair.reader.amr;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR Organization (Laboratory) resources and extensions
 * from data extracted from an AMR CSV file.
 */
@Slf4j
public class LaboratoryBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Organization resource using attributes extracted from the record.
     *
     * @param patient
     * @param observation The associated Observation for the laboratory.
     * @param record A map containing data, where keys represent data attributes.
     * @return A constructed Organization resource with populated properties and extensions.
     */
    public static Organization buildLaboratory(Patient patient, Observation observation, Map<String, String> record) {
        Organization organization = new Organization();

        // Extract laboratory data from the map
        String laboratoryCode = record.get("LaboratoryCode");

        organization.setId(laboratoryCode);
        organization.setName("Laboratory " + laboratoryCode);

        // Create a CodeableConcept for the type
        CodeableConcept type = new CodeableConcept();

        // Add coding for HL7 organization type
        type.addCoding(new Coding()
                .setSystem("http://terminology.hl7.org/CodeSystem/organization-type")
                .setCode("prov")
                .setDisplay("Healthcare Provider"));

        // Set the text
        type.setText("Laboratory");

        // Add the type to the organization
        organization.addType(type);

        // Set the performer attribute of the Observation resource
        observation.addPerformer(new Reference("Organization/" + laboratoryCode));

        // Create a Reference to the Patient resource
        Reference patientReference = new Reference("Patient/" + patient.getIdElement().getIdPart());
        // Add the reference to a custom extension in the Organization resource
        organization.addExtension()
                .setUrl("http://ecdc.eu/fhir/StructureDefinition/related-patient")
                .setValue(patientReference);
        // Repurposing "partOf" to link the organization to the patient resource:
        organization.getPartOf().setReference("Patient/" + patient.getIdElement().getIdPart());

        return organization;
    }
}
