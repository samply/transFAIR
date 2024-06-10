package de.samply.transfair.reader.amr;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR CareTeam (Laboratory) resources and extensions
 * from data extracted from an AMR CSV file.
 */
@Slf4j
public class LaboratoryBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR CareTeam resource using attributes extracted from the record.
     * We are using this resource because it posesses a "subject" attribute and this
     * is needed for population estimation.
     *
     * @param patient
     * @param record A map containing data, where keys represent data attributes.
     * @return A constructed CareTeam resource with populated properties and extensions.
     */
    public static CareTeam buildLaboratory(Patient patient, Map<String, String> record) {
        CareTeam laboratory = new CareTeam();

        // Extract laboratory data from the map
        String laboratoryCode = record.get("LaboratoryCode");

        laboratory.setId(laboratoryCode);
        laboratory.setName("Laboratory " + laboratoryCode);

        // Set the patient as the subject. Needed for population estimation.
        laboratory.setSubject(new Reference("Patient/" + patient.getIdElement().getIdPart()));

        return laboratory;
    }
}
