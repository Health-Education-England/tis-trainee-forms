package uk.nhs.hee.tis.trainee.forms.annotations;

import java.lang.reflect.InvocationTargetException;
import java.time.LocalDate;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.beanutils.BeanUtils;

/**
 * Implementation of {@link NotBeforeAnotherDateValidation} validator.
 **/
public class NotBeforeAnotherDateValidator
    implements ConstraintValidator<NotBeforeAnotherDateValidation, Object> {

  private String fieldName;
  private String dependFieldName;
  private String message;

  @Override
  public void initialize(NotBeforeAnotherDateValidation annotation) {
    fieldName = annotation.fieldName();
    dependFieldName = annotation.dependFieldName();
    message = annotation.message();
  }

  /**
   * Is the value of the dependent field valid, i.e. is it not before fieldName value.
   *
   * @param value The object to validate.
   * @param ctx   The context in which the constraint is evaluated.
   *
   * @return True if the dependent field is valid, otherwise false.
   */
  @Override
  public boolean isValid(Object value, ConstraintValidatorContext ctx) {

    if (value == null) {
      return true;
    }

    try {
      boolean isOk = true;
      String fieldValue = BeanUtils.getProperty(value, fieldName);
      String dependFieldValue = BeanUtils.getProperty(value, dependFieldName);
      if (fieldValue == null) return true;
      if (dependFieldValue == null) isOk = false;
      if (isOk) {
        LocalDate fieldDate = LocalDate.parse(fieldValue);
        LocalDate dependFieldDate = LocalDate.parse(dependFieldValue);

        if (dependFieldDate.isBefore(fieldDate))
          isOk = false;
      }
      if (!isOk) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(message)
            .addPropertyNode(dependFieldName)
            .addConstraintViolation();
        return false;
      }

    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
      throw new RuntimeException(ex);
    }

    return true;
  }
}