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

  @Override
  public void initialize(NotEmptyIfAnotherFieldHasValueValidation annotation) {
    fieldName          = annotation.fieldName();
    expectedFieldValue = annotation.fieldValue();
    dependFieldName    = annotation.dependFieldName();
  }

  @Override
  public boolean isValid(Object value, ConstraintValidatorContext ctx) {

    if (value == null) {
      return true;
    }

    try {
      String fieldValue       = BeanUtils.getProperty(value, fieldName);
      String dependFieldValue = BeanUtils.getProperty(value, dependFieldName);


      if (expectedFieldValue.equals(fieldValue)
          && (dependFieldValue == null || dependFieldValue.isEmpty())) {
        ctx.disableDefaultConstraintViolation();
        ctx.buildConstraintViolationWithTemplate(ctx.getDefaultConstraintMessageTemplate())
            .addNode(dependFieldName)
            .addConstraintViolation();
        return false;
      }

    } catch (NoSuchMethodException | InvocationTargetException | IllegalAccessException ex) {
      throw new RuntimeException(ex);
    }

    return true;
  }
}