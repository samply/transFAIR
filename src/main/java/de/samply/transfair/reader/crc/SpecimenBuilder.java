package de.samply.transfair.reader.crc;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.DateTimeType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR Specimen resources and extensions
 * from data extracted from an CRC cohort CSV file.
 */
@Slf4j
public class SpecimenBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Specimen resource using attributes extracted from the record.
     *
     * @param patient        The associated Patient for the Specimen.
     * @param biobankBuilder
     * @param patientBuilder
     * @param collection
     * @param record         A map containing Specimen data, where keys represent data attributes.
     * @return A constructed Specimen resource with populated properties and extensions.
     */
    public Specimen build(PatientBuilder patientBuilder, Organization collection, Map<String, String> record) {
        String patientId = patientBuilder.getResourceId(record.get("case_id"));
        if (patientId == null)
            return null;

        Specimen resource = new Specimen();
        String identifier = generateResourceIdentifier(record, "sample_num");
        if (resourceMap.containsKey(identifier))
            return (Specimen) resourceMap.get(identifier);
        resourceMap.put(identifier, resource);

        // Extract Specimen data from the map
        String patientIdentifier = record.get("case_id");
        String specimenType = record.get("sample_material");
        String specimenYear = record.get("sample_year_num");

        setId(resource);
        setIdentifier(resource, identifier);

        // Set properties of the Specimen
        resource.getSubject().setReference("Patient/" + patientId);
        resource.getType().setText(specimenType);
        resource.getCollection().setCollected(new DateTimeType(specimenYear)); // when collected

        // Create the extension for the custodian
        Extension custodianExtension = new Extension();
        custodianExtension.setUrl("https://fhir.bbmri.de/StructureDefinition/Custodian");

        // Set the valueReference to the collection organization
        Reference collectionReference = new Reference("Organization/" + collection.getIdElement().getValue());
        custodianExtension.setValue(collectionReference);

        // Add the extension to the Specimen
        resource.addExtension(custodianExtension);

        return resource;
    }
}
