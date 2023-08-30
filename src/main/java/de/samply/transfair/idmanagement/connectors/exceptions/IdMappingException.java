package de.samply.transfair.idmanagement.connectors.exceptions;

/** ID Mapping Exception. */
public class IdMappingException extends Exception {

  private static final long serialVersionUID = 1L;

  public IdMappingException(String message) {
    super(message);
  }
  
  public IdMappingException(String message, Exception e) {
    super(message, e);
  }

}
