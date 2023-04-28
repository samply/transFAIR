package de.samply.transfyr.mapper;

import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.CanonicalType;
import org.hl7.fhir.r4.model.Extension;
import org.hl7.fhir.r4.model.Meta;
import org.hl7.fhir.r4.model.Organization;
import org.hl7.fhir.r4.model.Resource;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirOrganizationMapper extends FhirMapper {

  public static final String MII_BIOBANK_DESCRIPTION =
      "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/BeschreibungSammlung";
  public static final String BBMRI_BIOBANK_DESCRIPTION =
      "https://fhir.bbmri.de/StructureDefinition/OrganizationDescription";
  public static final String MII_CANONICAL_TYPE = 
      "https://www.medizininformatik-initiative.de/fhir/ext/modul-biobank/StructureDefinition/Organization";


  public FhirOrganizationMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleType2Snomed) {
    super(icd10Snomed, sampleType2Snomed);
  }


  public List<Resource> map(Resource resource){

    Organization in = (Organization) resource;
    Organization out = in.copy();

    out.setMeta(new Meta().setProfile(List.of(new CanonicalType(MII_CANONICAL_TYPE))));

    for (Extension extension : out.getExtension()) {
      if (extension.getUrl().equals(BBMRI_BIOBANK_DESCRIPTION)) {
        extension.setUrl(MII_BIOBANK_DESCRIPTION);
      }
    }

    return List.of(resource);
  }
}
