package de.samply.transfair.idmanagement.connectors;

import de.samply.transfair.idmanagement.connectors.exceptions.IdMappingException;
import java.util.HashMap;
import java.util.List;
import java.util.Map;
import org.jetbrains.annotations.NotNull;

/**
 * Stores mappings between IDs from multiple domains in nested hashmaps. A mapping is always
 * possible in both directions from domain A to domain B and vice versa.
 *
 * @author jdoerenberg
 */
public abstract class IdMapping {
  // First key: src domain, second key tar domain, third key identifier
  // Used as cache data structure for fetched mappings
  private final HashMap<String, HashMap<String, HashMap<String, String>>> mappingsCache;

  /** Standard constructor. Prepares nested hashmaps to store mappings in. */
  protected IdMapping() {
    this.mappingsCache = new HashMap<>();
  }

  /**
   * Standard getter for field {this.mappings}
   *
   * @return nested Hashmaps which store mappings
   */
  public Map<String, HashMap<String, HashMap<String, String>>> getMappingsCache() {
    return this.mappingsCache;
  }

  /**
   * Reads a single mapping from different sources depending on inherited class.
   *
   * @throws Exception Exceptions depend on implementation in inherited class. E.g. when reading
   *     from a file it can be IOException.
   */
  public abstract String fetchMapping(
      @NotNull String id, @NotNull String srcDomain, @NotNull String tarDomain) throws Exception;

  /**
   * Stores a single mapping between two IDs from two domains.
   *
   * @param domA name of the first domain
   * @param domB name of the second domain
   * @param idA id from the first domain
   * @param idB id from the second domain
   * @throws IllegalArgumentException is thrown if
   */
  public void setMapping(
      @NotNull String domA, @NotNull String domB, @NotNull String idA, @NotNull String idB)
      throws IllegalArgumentException {

    // check whether domain names are different
    if (domA.equals(domB)) {
      throw new IllegalArgumentException(
          "Equal domain names not allowed! Both domain names are '" + domA + "'");
    }

    if (domA.equals("") || domB.equals("")) {
      throw new IllegalArgumentException("Empty domain name is not allowed!");
    }

    // Check whether both ids are not empty
    if (idA.equals("") || idB.equals("")) {
      throw new IllegalArgumentException("Empty ID is not allowed!");
    }

    this.mappingsCache.computeIfAbsent(domA, k -> new HashMap<>());

    this.mappingsCache.computeIfAbsent(domB, k -> new HashMap<>());

    if (!this.mappingsCache.get(domA).containsKey(domB)) {
      this.mappingsCache.get(domA).put(domB, new HashMap<>());
    }
    if (!this.mappingsCache.get(domB).containsKey(domA)) {
      this.mappingsCache.get(domB).put(domA, new HashMap<>());
    }

    this.mappingsCache.get(domA).get(domB).put(idA, idB);
    this.mappingsCache.get(domB).get(domA).put(idB, idA);
  }

  /**
   * Stores multiple mappings between two input domains. While iterating over both id arrays at a
   * time, pairs of IDs are fed to the {@link this.set_mapping} method.
   *
   * @param domA name of first domain
   * @param domB name of second domain
   * @param idsA array of IDs from domain a
   * @param idsB array od IDs from domain b
   * @throws IllegalArgumentException If length of idsA and idsB is not equal, no mappings are
   *     stored. If an exception is thrown by {@link this.set_mapping} method while adding a single
   *     mapping, it is escalated and the respective mapping is skipped.
   */
  public void setMappings(
      @NotNull String domA, @NotNull String domB, @NotNull String[] idsA, @NotNull String[] idsB)
      throws IllegalArgumentException {

    if (idsA.length != idsB.length) {
      throw new IllegalArgumentException(
          "ID lists must have same length! First list has length "
              + idsA.length
              + " and second list has length "
              + idsB.length);
    }

    for (int i = 0; i < idsA.length; i++) {
      this.setMapping(domA, domB, idsA[i], idsB[i]);
    }
  }

  /**
   * Overloads {@link this.set_mappings} by adding the option to pass ArrayLists instead of arrays.
   */
  @SuppressWarnings("unused")
  public void setMappings(
      @NotNull String domA,
      @NotNull String domB,
      @NotNull List<String> idsA,
      @NotNull List<String> idsB)
      throws IllegalArgumentException {
    this.setMappings(domA, domB, idsA.toArray(new String[0]), idsB.toArray(new String[0]));
  }

  /**
   * Checks whether mapping from srcDomain to tarDomain is set up in the cache.
   *
   * @param srcDomain name of srcDomain
   * @param tarDomain name of tarDomain
   * @return Returns a boolean whether srcDomain and tarDomain exist and mapping from srcDomain to
   *     tarDomain is set up in the cache.
   */
  public boolean existDomains(@NotNull String srcDomain, @NotNull String tarDomain) {
    return mappingsCache.containsKey(srcDomain)
        && mappingsCache.get(srcDomain).containsKey(tarDomain);
  }

  /**
   * Checks whether a mapping of the id from the source domain to the target domain exists in the
   * cache.
   *
   * @param id The id to be checked fo existing mapping from srcDomain to tarDomain
   * @param srcDomain The domain tht should contain the id
   * @param tarDomain The domain where it should be able to map id to
   * @return Whether a mapping
   * @throws IllegalArgumentException Mapping from source to target domain is not within the cache
   */
  public boolean existsMapping(
      @NotNull String id, @NotNull String srcDomain, @NotNull String tarDomain) {
    if (!existDomains(srcDomain, tarDomain)) {
      return false;
    }
    return mappingsCache.get(srcDomain).get(tarDomain).containsKey(id);
  }

  /**
   * Maps a single id from its original source domain to a target domain and returns it.
   *
   * @param id id that shall be mapped from source domain to target domain
   * @param srcDomain domain that contains the id
   * @param tarDomain domain the id shall be mapped to
   * @return id from tarDomain which was mapped from input id
   * @throws IllegalArgumentException if mapping does not exist
   */
  public String mapId(@NotNull String id, @NotNull String srcDomain, @NotNull String tarDomain)
      throws IdMappingException {
    String tarId;
    if (!existsMapping(
        id, srcDomain,
        tarDomain)) { // If no mapping for the ud from srcDomain to tarDomain exists, fetch the
      // id and add it to the cache
      try {
        tarId = this.fetchMapping(id, srcDomain, tarDomain);
      } catch (Exception e) {
        throw new IdMappingException(
            "Unable to fetch id " + id + " from " + srcDomain + " to " + tarDomain + " !", e);
      }
      setMapping(srcDomain, tarDomain, id, tarId);
      return tarId;
    }

    return mappingsCache.get(srcDomain).get(tarDomain).get(id);
  }
}
