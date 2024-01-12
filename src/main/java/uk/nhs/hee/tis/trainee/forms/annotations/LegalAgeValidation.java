package uk.nhs.hee.tis.trainee.forms.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

@Target( { FIELD, PARAMETER })
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = LegalAgeValidator.class)
public @interface LegalAgeValidation {
  int adultAgeYears() default 18;
  public String message() default "You must be at least 18 years old";

  //represents group of constraints
  public Class<?>[] groups() default {};
  //represents additional information about annotation
  public Class<? extends Payload>[] payload() default {};
}
