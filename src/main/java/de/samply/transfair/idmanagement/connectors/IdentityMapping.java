package de.samply.transfair.idmanagement.connectors;

import java.util.List;
import org.jetbrains.annotations.NotNull;

/**
 * Mapper that maps every ID to itself and ignores any domain arguments.
 * It is also impossible to add any mappings. Respective methods just do nothing.
 *
 * @author jdoerenberg
 */
public class IdentityMapping extends IdMapping {
  /** Overrides parental class method to do return the id itself. */
  @Override
  public String fetchMapping(
      @NotNull String id, @NotNull String srcDomain, @NotNull String tarDomain) {
    return id;
  }

  /**
   * Returns the id itself.
   *
   * @param id id that is returned
   * @param srcDomain has no effect
   * @param tarDomain has no effect
   * @return id itself
   */
  @Override
  public String mapId(@NotNull String id, @NotNull String srcDomain, @NotNull String tarDomain) {
    return id;
  }

  /** Overrides parental class method to do nothing. */
  @Override
  public void setMapping(
      @NotNull String domA, @NotNull String domB, @NotNull String idA, @NotNull String idB) {}

  /** Overrides parental class method to do nothing. */
  @Override
  public void setMappings(
      @NotNull String domA, @NotNull String domB, @NotNull String[] idsA, @NotNull String[] idsB) {}

  /** Overrides parental class method to do nothing. */
  @SuppressWarnings("unused")
  public void setMappings(
      @NotNull String domA,
      @NotNull String domB,
      @NotNull List<String> idsA,
      @NotNull List<String> idsB) {}
}
