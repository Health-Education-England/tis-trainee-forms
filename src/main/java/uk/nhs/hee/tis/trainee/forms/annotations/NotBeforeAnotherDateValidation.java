package uk.nhs.hee.tis.trainee.forms.annotations;

import static java.lang.annotation.ElementType.ANNOTATION_TYPE;
import static java.lang.annotation.ElementType.TYPE;
import static java.lang.annotation.RetentionPolicy.RUNTIME;

import java.lang.annotation.Documented;
import java.lang.annotation.Repeatable;
import java.lang.annotation.Retention;
import java.lang.annotation.Target;
import javax.validation.Constraint;
import javax.validation.Payload;

/**
 * Validates that local date field {@code dependFieldName} is not before {@code fieldName}.
 **/
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Repeatable(NotBeforeAnotherDateValidation.List.class)
@Constraint(validatedBy = NotBeforeAnotherDateValidator.class)
@Documented
public @interface NotBeforeAnotherDateValidation {

  /**
   * The constraining field.
   *
   * @return The constraining field.
   */
  String fieldName();

  /**
   * The dependent field, that cannot have a date value before the constraining field date.
   *
   * @return The dependent field name.
   */
  String dependFieldName();

  /**
   * The error message if the dependent value is not valid.
   *
   * @return The error message.
   */
  String message() default "This date is not in the required range";

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

  @Target({TYPE, ANNOTATION_TYPE})
  @Retention(RUNTIME)
  @Documented
  @interface List {

    NotBeforeAnotherDateValidation[] value();
  }

}