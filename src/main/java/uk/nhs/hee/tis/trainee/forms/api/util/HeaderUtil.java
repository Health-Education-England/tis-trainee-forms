package uk.nhs.hee.tis.trainee.forms.api.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * Utility class for HTTP headers creation.
 */
public final class HeaderUtil {

  private static final Logger log = LoggerFactory.getLogger(HeaderUtil.class);

  private static final String APPLICATION_NAME = "traineeReference";

  private HeaderUtil() {
  }

  /**
   * Create a generic header alert.
   *
   * @param message The message to include in the header.
   * @param param   The params to include in the header.
   * @return The created {@link HttpHeaders}.
   */
  public static HttpHeaders createAlert(String message, String param) {
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-traineeReference-alert", message);
    headers.add("X-traineeReference-params", param);
    return headers;
  }

  /**
   * Create an entity creation header alert.
   *
   * @param entityName The name of the created entity.
   * @param param      The params to include in the header.
   * @return The created {@link HttpHeaders}.
   */
  public static HttpHeaders createEntityCreationAlert(String entityName, String param) {
    return createAlert(APPLICATION_NAME + "." + entityName + ".created", param);
  }

  /**
   * Create an entity update header alert.
   *
   * @param entityName The name of the updated entity.
   * @param param      The params to include in the header.
   * @return The created {@link HttpHeaders}.
   */
  public static HttpHeaders createEntityUpdateAlert(String entityName, String param) {
    return createAlert(APPLICATION_NAME + "." + entityName + ".updated", param);
  }

  /**
   * Create an entity deletion header alert.
   *
   * @param entityName The name of the deleted entity.
   * @param param      The params to include in the header.
   * @return The created {@link HttpHeaders}.
   */
  public static HttpHeaders createEntityDeletionAlert(String entityName, String param) {
    return createAlert(APPLICATION_NAME + "." + entityName + ".deleted", param);
  }

  /**
   * Create a generic failure header alert.
   *
   * @param entityName     The name of the entity.
   * @param errorKey       The message key for the error.
   * @param defaultMessage The default message.
   * @return The created {@link HttpHeaders}.
   */
  public static HttpHeaders createFailureAlert(String entityName, String errorKey,
      String defaultMessage) {
    log.error("Entity creation failed, {}", defaultMessage);
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-traineeReference-error", "error." + errorKey);
    headers.add("X-traineeReference-params", entityName);
    return headers;
  }
}
