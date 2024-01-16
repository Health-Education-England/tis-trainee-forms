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
 * Validates that field {@code dependFieldName} is not null or empty if
 * field {@code fieldName} has value {@code fieldValue}.
 **/
@Target({TYPE, ANNOTATION_TYPE})
@Retention(RUNTIME)
@Repeatable(NotEmptyIfAnotherFieldHasValueValidation.List.class) // only with hibernate-validator >= 6.x
@Constraint(validatedBy = NotEmptyIfAnotherFieldHasValueValidator.class)
@Documented
public @interface NotEmptyIfAnotherFieldHasValueValidation {

  /**
   * The constraining field.
   *
   * @return The constraining field.
   */
  String fieldName();

  /**
   * The constraining field value.
   *
   * @return the constraining field value.
   */
  String fieldValue();

  /**
   * The dependent field, that cannot have a null or empty value if the constraining field has the
   * constraining value.
   *
   * @return the dependent field name.
   */
  String dependFieldName();

  /**
   *
   * @return
   */
  String message() default "This field must have a value";

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
    NotEmptyIfAnotherFieldHasValueValidation[] value();
  }

}