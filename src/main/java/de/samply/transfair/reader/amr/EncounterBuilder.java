package de.samply.transfair.reader.amr;

import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Location;
import org.hl7.fhir.r4.model.Encounter;
import org.hl7.fhir.r4.model.Patient;

/**
 * This is a utility class for constructing FHIR Encounter resources
 * from data extracted from an AMR CSV file.
 */
public class EncounterBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Observation resource using attributes extracted from the record.
     *
     * @param record        A map containing observation data, where keys represent data attributes.
     * @param recordCounter A counter for the record, used in generating the Observation ID.
     * @param patient       The associated Patient for the Observation.
     * @return A constructed Observation resource with populated properties and extensions.
     */
    public static Encounter buildEncounter(int recordCounter, Patient patient, Location location) {
        Encounter encounter = new Encounter();

        String patientId = patient.getIdElement().getValueAsString();
        encounter.setId(patientId + "." + recordCounter);

        encounter.setStatus(Encounter.EncounterStatus.FINISHED);
        encounter.addLocation().setLocation(new Reference(location));

        return encounter;
    }
}
