package de.samply.transfair.reader.crc;

import de.samply.transfair.util.JsonSerializer;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Enumerations.AdministrativeGender;
import org.hl7.fhir.r4.model.IdType;
import org.hl7.fhir.r4.model.Condition;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Meta;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR Condition resources and extensions
 * from data extracted from an CRC cohort CSV file.
 */
@Slf4j
public class ConditionBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Condition resource using attributes extracted from the record.
     *
     * @param record A map containing patient data, where keys represent data attributes.
     * @return A constructed Patient resource with populated properties and extensions.
     */
    public Condition build(Patient patient, Map<String, String> record) {
        Condition resource = new Condition();
        String identifier = generateResourceIdentifier(record);
        if (resourceMap.containsKey(identifier))
            return (Condition) resourceMap.get(identifier);
        resourceMap.put(identifier, resource);

        setId(resource);
        setIdentifier(resource, identifier);

        // Extract condition data from the map and set properties
        String histLoc = record.get("hist_loc");

        // Create the coding for the condition code
        Coding coding = new Coding();
        coding.setSystem("http://hl7.org/fhir/sid/icd-10");
        coding.setCode(extractIcd10FromHistLoc(histLoc));
        coding.setVersion("2016");

        // Wrap the coding in a CodeableConcept
        CodeableConcept codeableConcept = new CodeableConcept();
        codeableConcept.addCoding(coding);

        // Set the code on the Condition
        resource.setCode(codeableConcept);

        // Create a Meta object and set the profile
        Meta meta = new Meta();
        meta.addProfile("https://fhir.bbmri.de/StructureDefinition/Condition");

        // Add the Meta object to the Organization
        resource.setMeta(meta);

        // Create a reference to the Patient and set it as the subject of the Condition
        Reference patientReference = new Reference();
        patientReference.setReference("Patient/" + patient.getId());
        resource.setSubject(patientReference);

        return resource;
    }

    private String extractIcd10FromHistLoc(String histLoc) {
        String[] histLocParts = histLoc.split(":");
        return histLocParts[histLocParts.length - 1];
    }
}
