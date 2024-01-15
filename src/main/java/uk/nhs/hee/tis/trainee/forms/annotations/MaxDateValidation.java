package uk.nhs.hee.tis.trainee.forms.annotations;

import static java.lang.annotation.ElementType.FIELD;
import static java.lang.annotation.ElementType.PARAMETER;

import java.lang.annotation.Documented;
import java.lang.annotation.Retention;
import java.lang.annotation.RetentionPolicy;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

@Target({FIELD, PARAMETER})
@Retention(RetentionPolicy.RUNTIME)
@Documented
@Constraint(validatedBy = MaxDateValidator.class)
public @interface MaxDateValidation {

  /**
   * The maximum number of years in the future a date may be.
   *
   * @return The maximum years, by default 50.
   */
  int maxYearsInFuture() default 50;

  /**
   * The error message if the date is too far in the future.
   *
   * @return The error message.
   */
  public String message() default "The date cannot be 50 years or more in the future";

  /**
   * The group of constraints.
   *
   * @return The array of constraint classes.
   */
  public Class<?>[] groups() default {};

  /**
   * Additional information about the annotation.
   *
   * @return The payload extension.
   */
  public Class<? extends Payload>[] payload() default {};
}
