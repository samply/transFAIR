package de.samply.transfair.reader.crc;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.CodeableConcept;
import org.hl7.fhir.r4.model.Coding;

import java.util.Map;

/**
 * This is a utility class for constructing FHIR Organization resources and extensions
 * from data extracted from an CRC cohort CSV file.
 *
 * Collection resources are used for storing information about sample collections.
 */
@Slf4j
public class CollectionBuilder extends ResourceBuilder {
    /**
     * Builds a FHIR Organiztion resource using attributes extracted from the record.
     *
     * @param biobank
     * @param record  A map containing Specimen data, where keys represent data attributes.
     * @return A constructed CareTeam resource with populated properties and extensions.
     */
    public Organization build(Organization biobank, Map<String, String> record) {
        Organization resource = new Organization();
        String identifier = generateResourceIdentifier(record, "case_id");
        if (resourceMap.containsKey(identifier))
            return (Organization) resourceMap.get(identifier);
        resourceMap.put(identifier, resource);

        setId(resource);
        setIdentifier(resource, identifier);

        // Create a Meta object and set the profile
        Meta meta = new Meta();
        meta.addProfile("https://fhir.bbmri.de/StructureDefinition/Collection");

        // Add the Meta object to the Organization
        resource.setMeta(meta);

        // CollectionType extension
        Extension collectionTypeExtension = new Extension()
                .setUrl("https://fhir.bbmri.de/StructureDefinition/CollectionType")
                .setValue(new CodeableConcept().addCoding(new Coding()
                        .setSystem("https://fhir.bbmri.de/CodeSystem/CollectionType")
                        .setCode("SAMPLE")));

        // DataCategory extension
        Extension dataCategoryExtension = new Extension()
                .setUrl("https://fhir.bbmri.de/StructureDefinition/DataCategory")
                .setValue(new CodeableConcept().addCoding(new Coding()
                        .setSystem("https://fhir.bbmri.de/CodeSystem/DataCategory")
                        .setCode("BIOLOGICAL_SAMPLES")));

        // Add extensions to the organization
        resource.addExtension(collectionTypeExtension);
        resource.addExtension(dataCategoryExtension);

        // Create a reference to the biobank organization
        Reference biobankReference = new Reference("Organization/" + biobank.getIdElement().getValue());

        // Set the partOf relationship
        resource.setPartOf(biobankReference);

        return resource;
    }

    protected String generateResourceIdentifier(String input) {
        String collectionId = "bbmri-eric:ID:EU_BBMRI-ERIC:collection:" + input;
        String[] caseIdParts = input.split(":");
        if (caseIdParts.length == 6)
            collectionId = "bbmri-eric:ID:" + caseIdParts[2] + ":" + caseIdParts[3] + ":" + caseIdParts[4];
        return collectionId;
    }
}
