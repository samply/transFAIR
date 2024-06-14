package de.samply.transfair.reader.amr;

import lombok.extern.slf4j.Slf4j;

/**
 * This is a utility class for constructing FHIR CareTeam (Refguide) resources and extensions
 * from data extracted from an AMR CSV file.
 */
@Slf4j
public class RefguideBuilder extends CareTeamBuilder {
    public RefguideBuilder() {
        nameStartingString = "Refguide";
        recordName = "ReferenceGuidelinesSIR";
    }
}
