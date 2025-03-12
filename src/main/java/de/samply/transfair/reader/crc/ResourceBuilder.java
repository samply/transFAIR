package de.samply.transfair.reader.crc;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import org.hl7.fhir.r4.model.Identifier;
import org.hl7.fhir.instance.model.api.IBaseResource;
//import org.hl7.fhir.instance.model.api.IBaseHasIdentifier;
import org.hl7.fhir.r4.model.DomainResource;
import org.hl7.fhir.r4.model.Patient;
import org.hl7.fhir.r4.model.Observation;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Specimen;
import org.hl7.fhir.r4.model.Condition;

import java.util.HashMap;
import java.util.Map;
import java.util.UUID;

@Slf4j
public abstract class ResourceBuilder {
    protected Map<String, Resource> resourceMap = new HashMap<String, Resource>();

    protected int getResourceMapSize() {
        return resourceMap.size();
    }

    public String getResourceId(String key) {
        if (key == null) {
            log.warn("getResourceId: Key is null");
            return null;
        }
        if (!resourceMap.containsKey(key))
            return null;
        return resourceMap.get(key).getIdElement().getIdPart();
    }

    public void addResourcesToBundle(Bundle bundle) {
        for (Resource resource : resourceMap.values()) {
            log.info("Adding to bundle: " + resource.getIdElement().getIdPart());
            Bundle.BundleEntryComponent entry = new Bundle.BundleEntryComponent();
            entry.setResource(resource);
            bundle.addEntry(entry);
        }
    }

    protected Extension createStringExtension(String value, String url) {
        Extension extension = new Extension();
        extension.setUrl(url);
        extension.setValue(new StringType(value));

        return extension;
    }

    protected void setId(Resource resource) {
        resource.setId(UUID.randomUUID().toString());
    }

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

    protected String generateResourceIdentifier(Map<String, String> record) {
        return generateResourceIdentifier(record, "id");
    }

    protected String generateResourceIdentifier(Map<String, String> record, String key) {
        if (!record.containsKey(key)) {
            log.warn("generateResourceIdentifier: " + key + " not found in record. Skipping.");
            return null;
        }
        return generateResourceIdentifier(record.get(key));
    }

    protected String generateResourceIdentifier(String input) {
        return input;
    }
}
