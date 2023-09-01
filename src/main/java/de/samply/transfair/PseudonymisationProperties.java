package de.samply.transfair;

import lombok.Data;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.context.annotation.Configuration;

@Configuration
@ConfigurationProperties(prefix = "id")
@Data
public class PseudonymisationProperties {

  String bbmridomain;
  String miidomain;
  String setting;
  String csvpath;

}


