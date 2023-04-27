package de.samply.transfyr.mapper;

import java.util.HashMap;
import java.util.List;
import java.util.Objects;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.StringType;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirOrganizationMapper extends FhirMapper {
  
  public static final String MII_BIOBANK_DESCRIPTION =
      "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/BeschreibungSammlung";
  public static final String BBMRI_BIOBANK_DESCRIPTION =
      "https://fhir.bbmri.de/StructureDefinition/OrganizationDescription";
  public static final String BBMRI_ID = "http://www.bbmri-eric.eu/";


  public FhirOrganizationMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
  }


  public Resource map(Resource resource){
    
    Organization in = (Organization) resource;
    Organization out = in.copy();
    
    out.setMeta(
        new Meta()
            .setProfile(
                List.of(
                    new CanonicalType(
                        "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Organization"))));
    
    for (Extension extension : in.getExtension()) {
      if (extension.getUrl().equals(BBMRI_BIOBANK_DESCRIPTION)) {
        out.addExtension(
            new Extension()
                .setUrl(MII_BIOBANK_DESCRIPTION)
                .setValue(new StringType(extension.getValue().primitiveValue())));
      }
    }
    
    return resource;
  }
}
