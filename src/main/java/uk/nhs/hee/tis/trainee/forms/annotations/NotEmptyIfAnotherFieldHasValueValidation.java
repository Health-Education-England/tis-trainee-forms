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

  String fieldName();
  String fieldValue();
  String dependFieldName();

  String message() default "This field must have a value";
  Class<?>[] groups() default {};
  Class<? extends Payload>[] payload() default {};

  @Target({TYPE, ANNOTATION_TYPE})
  @Retention(RUNTIME)
  @Documented
  @interface List {
    NotEmptyIfAnotherFieldHasValueValidation[] value();
  }

}