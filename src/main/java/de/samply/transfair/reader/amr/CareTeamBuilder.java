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
 * This is a utility class for constructing FHIR CareTeam resources and extensions
 * from data extracted from an AMR CSV file.
 *
 * CareTeam resources are used for population estimation for a number of different
 * data types, e.g. Laboratory, Refguide, etc.
 */
@Slf4j
public class CareTeamBuilder extends ResourceBuilder {
    public static CareTeam build(Patient patient, String id, String nameStartingString) {
        CareTeam careTeam = new CareTeam();

        careTeam.setId(id);
        careTeam.setName(nameStartingString + " " + id);

        // Set the patient as the subject. Needed for population estimation.
        careTeam.setSubject(new Reference("Patient/" + patient.getIdElement().getIdPart()));

        return careTeam;
    }
}
