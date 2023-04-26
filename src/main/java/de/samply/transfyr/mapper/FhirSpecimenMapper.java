package de.samply.transfyr.mapper;

import java.util.HashMap;
import java.util.List;
import org.hl7.fhir.r4.model.Coding;
import org.hl7.fhir.r4.model.Resource;
import org.hl7.fhir.r4.model.Specimen;
import lombok.extern.slf4j.Slf4j;

@Slf4j
public class FhirSpecimenMapper extends FhirMapper {


  public FhirSpecimenMapper(
      HashMap<String, String> icd10Snomed, HashMap<String, String> sampleTypeSnomed) {
    super(icd10Snomed, sampleTypeSnomed);
  }
  
  private Specimen convertSampleTypeBySystem(Specimen in, Specimen out, String inSystem, String outSystem){


    Coding inCoding = in.getType().getCoding()
        .stream()
        .filter(coding -> coding.getSystem()
            .equals(inSystem))
        .findFirst().get();


    if (inCoding != null){
      String newCode = this.sampleTypeSnomed.get(inCoding.getCode());

      if (newCode != null) {
        Coding outCoding = new Coding().setCode(newCode).setSystem(outSystem);
        out.getType().setCoding(List.of(outCoding));

      }
    }

    return out;
  }

  public Resource map(Resource resource){

    Specimen in = (Specimen) resource;
    Specimen out = in.copy();
    
    this.convertSampleTypeBySystem(in, out, "https://fhir.bbmri.de/CodeSystem/SampleMaterialType", "http://snomed.info/sct");
    
    

    return out;
  }
}
