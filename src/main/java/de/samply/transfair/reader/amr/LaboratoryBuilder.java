package de.samply.transfair.reader.amr;

import lombok.extern.slf4j.Slf4j;

/**
 * This is a utility class for constructing FHIR CareTeam (Laboratory) resources and extensions
 * from data extracted from an AMR CSV file.
 */
@Slf4j
public class LaboratoryBuilder extends CareTeamBuilder {
    public LaboratoryBuilder() {
        nameStartingString = "Laboratory";
        recordName = "LaboratoryCode";
    }
}
