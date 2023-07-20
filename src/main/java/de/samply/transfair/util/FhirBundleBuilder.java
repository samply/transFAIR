package de.samply.transfair.util;

import java.util.Collection;
import java.util.LinkedList;
import java.util.List;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Resource;
import org.springframework.stereotype.Component;

@Component
public class FhirBundleBuilder {

    private final Bundle bundle;
    private final List<Resource> resources;

  public FhirBundleBuilder() {
    bundle = new Bundle().setType(BundleType.TRANSACTION);
    resources = new LinkedList<>();
  }


  public FhirBundleBuilder id(String id) {
      bundle.setId(id);
      return this;
    }

    public FhirBundleBuilder add(Resource resource) {
      if (resource != null) {
        resources.add(resource);
      }
      return this;
    }

    public FhirBundleBuilder add(Collection<? extends Resource> resources) {
      this.resources.addAll(resources);
      return this;
    }

    public Bundle build() {
      if (!bundle.hasId()) {
        throw new IllegalStateException("Attempt creating bundle without an ID");
      }

      for (var resource : resources) {

        String url = resource.getResourceType().name() + "/" + resource.getIdElement().getIdPart();

        bundle.addEntry(
            new BundleEntryComponent()
                .setFullUrl(new Reference(url).getReference())
                .setResource(resource)
                .setRequest(
                    new Bundle.BundleEntryRequestComponent()
                        .setMethod(Bundle.HTTPVerb.PUT)
                        .setUrl(url)));
      }
      return bundle;
    }

  }
