package de.samply.transfair;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "fhir")
@Data
public class FhirProperties {

  FhirInput input;
  FhirOutput output;

  @Data
  public static class FhirInput {
    private String url;
  }
  @Data
  public static class FhirOutput {
    private String url;
  }

}


