package de.samply.transfair.reader.crc;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Identifier;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR Organization resources and extensions
 * from data extracted from an CRC cohort CSV file.
 *
 */
@Slf4j
public class BiobankBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Organiztion resource using attributes extracted from the record.
     *
     * @param record A map containing Specimen data, where keys represent data attributes.
     * @return A constructed CareTeam resource with populated properties and extensions.
     */
    public Organization build(Map<String, String> record) {
        Organization resource = new Organization();
        String identifier = generateResourceIdentifier(record, "case_id");
        if (resourceMap.containsKey(identifier))
            return (Organization) resourceMap.get(identifier);
        resourceMap.put(identifier, resource);

        setId(resource);
        setIdentifier(resource, identifier);

        // Create a Meta object and set the profile
        Meta meta = new Meta();
        meta.addProfile("https://fhir.bbmri.de/StructureDefinition/Biobank");

        // Add the Meta object to the Organization
        resource.setMeta(meta);

        return resource;
    }

    protected String generateResourceIdentifier(String input) {
        String collectionId = "bbmri-eric:ID:EU_BBMRI-ERIC:collection:" + input;
        String[] caseIdParts = input.split(":");
        if (caseIdParts.length == 6)
            collectionId = "bbmri-eric:ID:" + caseIdParts[2];
        return collectionId;
    }
}
