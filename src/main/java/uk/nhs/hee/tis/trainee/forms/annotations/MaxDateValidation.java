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
@Constraint(validatedBy = MaxDateValidator.class)
public @interface MaxDateValidation {
  int maxYearsInFuture() default 50;
  public String message() default "The date cannot be 50 years or more in the future";

  //represents group of constraints
  public Class<?>[] groups() default {};
  //represents additional information about annotation
  public Class<? extends Payload>[] payload() default {};
}
