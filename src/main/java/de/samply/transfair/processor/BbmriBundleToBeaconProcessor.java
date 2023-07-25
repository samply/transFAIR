package de.samply.transfair.processor;

import de.samply.transfair.mapper.FhirResourceMapper;
import de.samply.transfair.mapper.bbmri2beacon.Bbmri2BeaconIndividual;
import de.samply.transfair.models.beacon.BeaconIndividuals;
import lombok.extern.slf4j.Slf4j;
import org.hl7.fhir.r4.model.Bundle;
import org.hl7.fhir.r4.model.Bundle.BundleEntryComponent;
import org.hl7.fhir.r4.model.Bundle.BundleType;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.ResourceType;
import org.hl7.fhir.r4.model.Reference;
import org.hl7.fhir.r4.model.Patient;

import org.springframework.batch.item.ItemProcessor;

import java.util.Collection;
import java.util.List;

@Slf4j
public class BbmriBundleToBeaconProcessor implements ItemProcessor<Bundle, BeaconIndividuals> {
  // FHIR to Beacon conversion does not need a mapper, but without it, invisible Spring magic stops working.
  FhirResourceMapper mapper;

  public BbmriBundleToBeaconProcessor(FhirResourceMapper mapper) {
    this.mapper = mapper;
  }

  public BeaconIndividuals process(final Bundle bundle) {
    System.out.println("BbmriBundleToBeaconProcessor.process; entered");

//    List<Resource> resources = bundle.getEntry().stream().map(entry -> entry.getResource()).toList();
//
//    Bundle outputBundle = new Bundle().setType(BundleType.TRANSACTION);
//    outputBundle.setId(UUID.randomUUID().toString());
//    List<Resource> outputResources = new LinkedList<>();
//    add(resources, outputResources);
//    build(outputBundle, outputResources);

    System.out.println("BbmriBundleToBeaconProcessor.process; extract Beacon individuals");
    Bbmri2BeaconIndividual bbmri2BeaconIndividual = new Bbmri2BeaconIndividual();
    BeaconIndividuals beaconIndividuals =null;
    try {
      beaconIndividuals = bbmri2BeaconIndividual.transfer(bundle);
    } catch (Exception e) {
    }
    System.out.println("BbmriBundleToBeaconProcessor.process; extracted Beacon individuals");

    return beaconIndividuals;
  }

  private void add(Collection<? extends Resource> resources, List<Resource> outputResources) {
    for (Resource resource: resources)
      add(resource, outputResources);
  }

  private void add(Resource resource, List<Resource> outputResources) {
    if (resource != null) {
      Resource referenceFreeResource = stripReferences(resource);
      outputResources.add(referenceFreeResource);
    }
  }

  private  Resource stripReferences(Resource resource) {
    System.out.println("stripReferences; entered");
    Resource newResource = resource.copy();
    ResourceType resourceType = resource.getResourceType();
    if (resourceType == null)
      System.out.println("Resource type is null");
    else if (resourceType == ResourceType.Patient) {
      System.out.println("We have a patient");
      Patient patient = (Patient) newResource;
      Reference organization = patient.getManagingOrganization();
      System.out.println("organization: " + organization);
      organization.setReference(null);
    } else
      System.out.println("resourceType: " + resourceType.toString());
    return newResource;
  }

  private void build(Bundle outputBundle, List<Resource> outputResources) {
    if (!outputBundle.hasId()) {
      throw new IllegalStateException("Attempt creating bundle without an ID");
    }

    for (var resource : outputResources) {
      String url = resource.getResourceType().name() + "/" + resource.getIdElement().getIdPart();

      outputBundle.addEntry(
              new BundleEntryComponent()
                      .setFullUrl(new Reference(url).getReference())
                      .setResource(resource)
                      .setRequest(
                              new Bundle.BundleEntryRequestComponent()
                                      .setMethod(Bundle.HTTPVerb.PUT)
                                      .setUrl(url)));
    }
  }
}
