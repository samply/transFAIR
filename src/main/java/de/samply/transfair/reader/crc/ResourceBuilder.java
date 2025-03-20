package de.samply.transfair.reader.crc;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.instance.model.api.IBaseResource;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Condition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

/**
 * Abstract base class for building and managing FHIR resources.
 *
 * <p>This class provides common utility methods for handling FHIR resources,
 * including resource identification, extension creation, and adding resources to a bundle.
 */
@Slf4j
public abstract class ResourceBuilder {
    // Resource cache, to avoid generating resources repeatedly.
    protected Map<String, Resource> resourceMap = new HashMap<String, Resource>();

    /**
     * Retrieves the number of resources currently stored in the resource map.
     *
     * @return The number of resources in {@code resourceMap}.
     */
    protected int getResourceMapSize() {
        return resourceMap.size();
    }

    /**
     * Returns the resource map containing all cached resources.
     *
     * @return A map where keys are resource identifiers and values are {@link Resource} objects.
     */
    public Map<String, Resource> getResourceMap() {
        return resourceMap;
    }

    /**
     * Retrieves the ID of a resource from the resource map based on a given key
     * (e.g. resource ID).
     *
     * @param key The key associated with the resource.
     * @return The resource ID if found, otherwise {@code null}.
     */
    public String getResourceId(String key) {
        if (key == null) {
            log.warn("getResourceId: Key is null");
            return null;
        }
        if (!resourceMap.containsKey(key))
            return null;
        return resourceMap.get(key).getIdElement().getIdPart();
    }

    /**
     * Adds all stored resources to the supplied FHIR {@link Bundle}.
     *
     * @param bundle The {@link Bundle} to which resources should be added.
     */
    public void addResourcesToBundle(Bundle bundle) {
        for (Resource resource : resourceMap.values()) {
            Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
            entry.setResource(resource);
            bundle.addEntry(entry);
        }
        log.info("Bundle size: " + bundle.getEntry().size());
    }

    /**
     * Creates a FHIR {@link Extension} with a string value.
     *
     * @param value The string value for the extension.
     * @param url   The URL defining the extension.
     * @return A newly created {@link Extension} with the given value and URL.
     */
    protected Extension createStringExtension(String value, String url) {
        Extension extension = new Extension();
        extension.setUrl(url);
        extension.setValue(new StringType(value));

        return extension;
    }

    /**
     * Assigns a random UUID as the ID of the given resource.
     *
     * @param resource The {@link Resource} to assign an ID.
     */
    protected void setId(Resource resource) {
        resource.setId(UUID.randomUUID().toString());
    }

    /**
     * Sets an identifier for a given {@link DomainResource}.
     *
     * <p>The identifier is assigned based on the BBMRI-ERIC system.
     * This method only supports specific resource types such as {@link Patient},
     * {@link Observation}, {@link Condition}, {@link Organization}, and {@link Specimen}.
     * If an unsupported resource type is provided, a warning is logged.
     *
     * @param resource         The {@link DomainResource} to assign an identifier.
     * @param identifierString The identifier value to set.
     */
    protected void setIdentifier(DomainResource resource, String identifierString) {
        Identifier identifier = new Identifier()
                .setSystem("http://www.bbmri-eric.eu/")
                .setValue(identifierString);

        // Check if the resource actually supports identifiers
        if (resource instanceof Patient)
            ((Patient) resource).getIdentifier().add(identifier);
        else if (resource instanceof Observation)
            ((Observation) resource).getIdentifier().add(identifier);
        else if (resource instanceof Condition)
            ((Condition) resource).getIdentifier().add(identifier);
        else if (resource instanceof Organization)
            ((Organization) resource).getIdentifier().add(identifier);
        else if (resource instanceof Specimen)
            ((org.hl7.fhir.r4.model.Specimen) resource).getIdentifier().add(identifier);
        else {
            log.warn("Resource type does not support identifiers: " + resource.getClass().getSimpleName());
        }
    }

    /**
     * Generates a resource identifier from a given record.
     *
     * <p>This method looks for a key named "id" in the record and calls
     * {@link #generateResourceIdentifier(String)} if found.
     *
     * @param record A map containing key-value pairs representing resource data.
     * @return A generated resource identifier, or {@code null} if the key is missing.
     */
    protected String generateResourceIdentifier(Map<String, String> record) {
        return generateResourceIdentifier(record, "id");
    }

    /**
     * Generates a resource identifier from a given record using a specific key.
     *
     * <p>If the key does not exist in the record, a warning is logged.
     *
     * @param record A map containing key-value pairs representing resource data.
     * @param key    The key whose value will be used as the resource identifier.
     * @return A generated resource identifier, or {@code null} if the key is missing.
     */
    protected String generateResourceIdentifier(Map<String, String> record, String key) {
        if (!record.containsKey(key)) {
            log.warn("generateResourceIdentifier: " + key + " not found in record. Skipping.");
            return null;
        }
        return generateResourceIdentifier(record.get(key));
    }

    /**
     * Generates a resource identifier from an input string.
     *
     * This default implementation simply returns the input string.
     *
     * <p>This method can be overridden by subclasses to implement custom identifier generation logic.
     *
     * @param input The input string from which the identifier will be generated.
     * @return The generated identifier.
     */
    protected String generateResourceIdentifier(String input) {
        return input;
    }
}
