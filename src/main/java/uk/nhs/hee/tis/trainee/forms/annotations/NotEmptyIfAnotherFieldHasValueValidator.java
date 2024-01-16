package uk.nhs.hee.tis.trainee.forms.annotations;

import java.lang.reflect.InvocationTargetException;
import javax.validation.ConstraintValidator;
import javax.validation.ConstraintValidatorContext;
import org.apache.commons.beanutils.BeanUtils;

/**
 * Implementation of {@link NotEmptyIfAnotherFieldHasValueValidation} validator.
 **/
public class NotEmptyIfAnotherFieldHasValueValidator
    implements ConstraintValidator<NotEmptyIfAnotherFieldHasValueValidation, Object> {

  private String fieldName;
  private String expectedFieldValue;
  private String dependFieldName;
  private String message;

  @Override
  public void initialize(NotEmptyIfAnotherFieldHasValueValidation annotation) {
    fieldName = annotation.fieldName();
    expectedFieldValue = annotation.fieldValue();
    dependFieldName = annotation.dependFieldName();
    message = annotation.message();
  }

  /**
   * Is the value of the dependent field valid, i.e. is it a string of length at least 1 if the
   * fieldName field has expectedFieldValue value; otherwise it can be null or empty.
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
      String fieldValue = BeanUtils.getProperty(value, fieldName);
      String dependFieldValue = BeanUtils.getProperty(value, dependFieldName);

      if (expectedFieldValue.equals(fieldValue)
          && (dependFieldValue == null || dependFieldValue.isEmpty())) {
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