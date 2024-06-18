package de.samply.transfair.reader.amr;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.CareTeam;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Reference;

import java.util.HashMap;
import java.util.Map;

/**
 * This is a utility class for constructing FHIR CareTeam resources and extensions
 * from data extracted from an AMR CSV file.
 *
 * CareTeam resources are used for population estimation for a number of different
 * data types, e.g. Laboratory, Refguide, etc.
 */
@Slf4j
public class CareTeamBuilder extends ResourceBuilder {
    protected String nameStartingString;

    /**
     * Builds a FHIR CareTeam resource using attributes extracted from the record.
     * We are using this resource because it posesses a "subject" attribute and this
     * is needed for population estimation.
     *
     * @param patient
     * @param record A map containing data, where keys represent data attributes.
     * @return A constructed CareTeam resource with populated properties and extensions.
     */
    public void build(Patient patient, Map<String, String> record) {
        String id = generateResourceId(patient, record);
        if (resourceMap.containsKey(id))
            return;

        CareTeam resource = new CareTeam();
        resource.setId(id);
        resource.setName(nameStartingString + " " + record.get(recordName));

        // Set the patient as the subject. Needed for population estimation.
        resource.setSubject(new Reference("Patient/" + patient.getIdElement().getIdPart()));

        resourceMap.put(id, resource);
    }

    /**
     * For each Patient, there will be one CareTeam resource for each Laboratory, Refguide, etc.
     * This method creates a unique resource ID based on the Patient ID and the Lab/Refguide
     * CareTeam ID.
     *
     * @param patient
     * @param record
     * @return new ID
     */
    public String generateResourceId(Patient patient, Map<String, String> record) {
        return patient.getIdElement().getIdPart() + "-" + record.get(recordName);
    }
}
