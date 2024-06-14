package de.samply.transfair.reader.amr;

import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;

import java.util.HashMap;
import java.util.Map;

@Slf4j
public abstract class ResourceBuilder {
    protected Map<String, Resource> resourceMap = new HashMap<String, Resource>();
    protected String recordName;


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
}
