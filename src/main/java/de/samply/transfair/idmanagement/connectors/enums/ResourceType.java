package de.samply.transfair.idmanagement.connectors.enums;

import de.samply.transfair.idmanagement.IdMapper;

/**
 * Used in class {@link this.set_mapping} to define the FHIR resource type that an id belong to. See
 * {@link IdMapper}.toBbmri and {@link IdMapper}.toMii methods
 *
 * @author jdoerenberg
 */
public enum ResourceType {
  PATIENT,
  SPECIMEN //TODO: Required?
}
