package de.samply.transfair.idmanagement.connectors.exceptions;

/** Pseudo csv file domain exception. */
public class DomainNotFoundInCsvFileException extends Exception {

  private static final long serialVersionUID = 1L;

  /** Throws when there is an issue with the domains found in the csv file.
   *
   * @param message passed down message
   */
  public DomainNotFoundInCsvFileException(String message) {
    super(message);
  }

}
