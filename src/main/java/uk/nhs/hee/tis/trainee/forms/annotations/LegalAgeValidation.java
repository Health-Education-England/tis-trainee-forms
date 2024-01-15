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
@Constraint(validatedBy = LegalAgeValidator.class)
public @interface LegalAgeValidation {

  /**
   * The age at adulthood.
   *
   * @return The age in years, by default 18.
   */
  int adultAgeYears() default 18;

  /**
   * The error message if the proposed birthdate means the person is currently not an adult.
   *
   * @return The error message.
   */
  public String message() default "You must be at least 18 years old";

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
