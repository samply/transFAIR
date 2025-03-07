package de.samply.transfair.reader.crc;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Patient;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR Specimen resources and extensions
 * from data extracted from an CRC cohort CSV file.
 */
@Slf4j
public class SpecimenBuilder extends ResourceBuilder {
    public SpecimenBuilder() {
        recordName = "IsolateId";
    }

    /**
     * Builds a FHIR Specimen resource using attributes extracted from the record.
     *
     * @param patient The associated Patient for the Specimen.
     * @param record A map containing Specimen data, where keys represent data attributes.
     * @return A constructed Specimen resource with populated properties and extensions.
     */
    public void build(Patient patient, Map<String, String> record) {
        Specimen specimen = new Specimen();

        // Extract Specimen data from the map
        String isolateId = record.get(recordName);

        specimen.setId(isolateId);

        // Set properties of the Specimen
        specimen.getSubject().setReference("Patient/" + patient.getIdElement().getIdPart());

        resourceMap.put(isolateId, specimen);
    }
}
