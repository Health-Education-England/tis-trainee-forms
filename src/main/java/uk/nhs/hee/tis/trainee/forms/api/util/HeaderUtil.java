package uk.nhs.hee.tis.trainee.forms.api.util;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.http.HttpHeaders;

/**
 * Utility class for HTTP headers creation.
 */
public final class HeaderUtil {

  private static final Logger log = LoggerFactory.getLogger(HeaderUtil.class);

  private HeaderUtil() {
  }

  /**
   * Create an entity creation failure header alert.
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

  /**
   * Create an form relocation failure header alert.
   *
   * @param errorKey       The message key for the error.
   * @param defaultMessage The default message.
   * @return The created {@link HttpHeaders}.
   */
  public static HttpHeaders moveFailureAlert(String errorKey,
                                             String defaultMessage) {
    log.error("Form relocation failed, {}", defaultMessage);
    HttpHeaders headers = new HttpHeaders();
    headers.add("X-traineeForms-error", "error." + errorKey);
    return headers;
  }
}
