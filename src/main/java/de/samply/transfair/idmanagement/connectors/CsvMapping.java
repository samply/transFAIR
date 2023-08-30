package de.samply.transfair.idmanagement.connectors;

import com.opencsv.CSVReader;
import com.opencsv.exceptions.CsvException;
import de.samply.transfair.idmanagement.connectors.exceptions.DomainNotFoundInCsvFileException;
import de.samply.transfair.idmanagement.connectors.exceptions.NoMappingFoundException;
import java.io.FileReader;
import java.io.IOException;
import org.jetbrains.annotations.NotNull;

/**
 * Reads ID-Mappings from csv file. See read_mappings method for details.
 * Access to the mappings is provided by using the methods from the parent class.
 *
 * @see this.read_mappings
 * @see IdMapping
 * @author jdoerenberg
 */
public class CsvMapping extends IdMapping {

  private String filepath;

  /** Standard constructor. */
  public CsvMapping() {
    super();
  }

  /**
   * Constructor which stores filepath.
   *
   * @param s filepath to csv file where mappings are loaded from
   */
  @SuppressWarnings("unused")
  public CsvMapping(@NotNull String s) {
    super();
    this.filepath = s;
  }

  /**
   * Standard setter for field this.filepath
   *
   * @param s filepath to csv file where mappings are loaded from
   */
  public void setFilepath(@NotNull String s) {
    this.filepath = s;
  }

  /**
   * Standard getter for field this.filepath
   *
   * @return filepath to csv file where mappings are loaded from
   */
  @SuppressWarnings("unused")
  public String getFilepath() {
    return this.filepath;
  }

  /**
   * Reads ID mappings from a csv file and stores them in the data structure provided in parental
   * class. The column headlines (first row) contain the names of the domains mapped to each other.
   * After reading these, mappings are processed row by row. If two domains contain a non-empty
   * value, these values are stored in the mapping. This is done for each pair of non-empty values
   * in the row.
   *
   * @throws IOException in case file at this.filepath cannot be read (e.g. opened by different
   *     program, does not exist,...)
   * @throws CsvException in case file at this.filepath is not a proper csv file
   */
  @Override
  public String fetchMapping(
      @NotNull String id, @NotNull String srcDomain, @NotNull String tarDomain) throws Exception {
    try (CSVReader reader = new CSVReader(new FileReader(filepath))) {
      // Get indices of rows containing source domain and target domain
      String[] domains = reader.readNext();
      int srcIdx = -1;
      int tarIdx = -1;
      for (int i = 0; i < domains.length; i++) {
        if (domains[i].equals(srcDomain)) {
          srcIdx = i;
        }
        if (domains[i].equals(tarDomain)) {
          tarIdx = i;
        }
      }
      // If one of the domains is not found, throw an exception
      if (srcIdx == -1) {
        throw new DomainNotFoundInCsvFileException(
            "Domain " + srcDomain + " not found in csv file " + this.filepath);
      }
      if (tarIdx == -1) {
        throw new DomainNotFoundInCsvFileException(
            "Domain " + tarDomain + " not found in csv file " + this.filepath);
      }

      // Iterate over whole csv file and search for mapping between the domains where value of
      // src_domain is argumentid
      String[] row;
      while ((row = reader.readNext()) != null) {
        if (row[srcIdx].equals(id) && !row[tarIdx].equals("")) {
          return row[tarIdx];
        }
      }
      throw new NoMappingFoundException(
          "No mapping from domain "
              + srcDomain
              + " to domain "
              + tarDomain
              + " found for id "
              + id);
    }
  }
}
