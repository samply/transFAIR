package de.samply.transfair.idmanagement.connectors.exceptions;

/** Configuration error where no mapping is found. */
public class NoMappingFoundException extends Exception {

  private static final long serialVersionUID = 1L;
 
  /** Throws an exception when there is no mapping matching the configuration.
   *
   * @param message passed down message
   */
  public NoMappingFoundException(String message) {
    super(message);
  }

}
