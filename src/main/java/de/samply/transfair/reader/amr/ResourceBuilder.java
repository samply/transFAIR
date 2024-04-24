package de.samply.transfair.reader.amr;

import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.StringType;

public abstract class ResourceBuilder {
    protected static Extension createStringExtension(String value, String url) {
        Extension extension = new Extension();
        extension.setUrl(url);
        extension.setValue(new StringType(value));

        return extension;
    }
}
