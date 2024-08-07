/*
 * The MIT License (MIT)
 *
 * Copyright 2024 Crown Copyright (Health Education England)
 *
 * Permission is hereby granted, free of charge, to any person obtaining a copy of this software and
 * associated documentation files (the "Software"), to deal in the Software without restriction,
 * including without limitation the rights to use, copy, modify, merge, publish, distribute,
 * sublicense, and/or sell copies of the Software, and to permit persons to whom the Software is
 * furnished to do so, subject to the following conditions:
 *
 * The above copyright notice and this permission notice shall be included in all copies or
 * substantial portions of the Software.
 *
 * THE SOFTWARE IS PROVIDED "AS IS", WITHOUT WARRANTY OF ANY KIND, EXPRESS OR IMPLIED, INCLUDING BUT
 * NOT LIMITED TO THE WARRANTIES OF MERCHANTABILITY, FITNESS FOR A PARTICULAR PURPOSE AND
 * NONINFRINGEMENT. IN NO EVENT SHALL THE AUTHORS OR COPYRIGHT HOLDERS BE LIABLE FOR ANY CLAIM,
 * DAMAGES OR OTHER LIABILITY, WHETHER IN AN ACTION OF CONTRACT, TORT OR OTHERWISE, ARISING FROM,
 * OUT OF OR IN CONNECTION WITH THE SOFTWARE OR THE USE OR OTHER DEALINGS IN THE SOFTWARE.
 */

package uk.nhs.hee.tis.trainee.forms.annotations;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import jakarta.validation.ConstraintValidatorContext;
import jakarta.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import java.time.LocalDate;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class LegalAgeValidatorTest {

  @Mock
  ConstraintValidatorContext constraintValidatorContext;

  LegalAgeValidator validator;

  @BeforeEach
  void setup() {
    constraintValidatorContext = mock(ConstraintValidatorContext.class);
    when(constraintValidatorContext.buildConstraintViolationWithTemplate(any()))
        .thenReturn(dummyConstraintViolationBuilder());
    validator = new LegalAgeValidator();
    validator.initWithValues(18);
  }

  @Test
  void isValidIfBirthDateIsNullTest() {
    assertThat("Unexpected invalid LegalAgeValidator.",
        validator.isValid(null, constraintValidatorContext), is(true));
  }

  @Test
  void isNotValidIfBirthDateIsLessThanAdultAgeAgo() {
    LocalDate birthDate = LocalDate.now().minusYears(17);

    assertThat("Unexpected valid LegalAgeValidator.",
        validator.isValid(birthDate, constraintValidatorContext), is(false));
  }

  @Test
  void isValidIfBirthDateIsMoreThanAdultAgeAgo() {
    LocalDate birthDate = LocalDate.now().minusYears(19);

    assertThat("Unexpected invalid LegalAgeValidator.",
        validator.isValid(birthDate, constraintValidatorContext), is(true));
  }

  /**
   * Helper function to return a dummy ConstraintViolationBuilder for the mocked
   * constraintValidatorContext.
   *
   * @return An empty ConstraintViolationBuilder.
   */
  ConstraintViolationBuilder dummyConstraintViolationBuilder() {
    return new ConstraintViolationBuilder() {
      @Override
      public NodeBuilderDefinedContext addNode(String name) {
        return null;
      }

      @Override
      public NodeBuilderCustomizableContext addPropertyNode(String name) {
        return new NodeBuilderCustomizableContext() {
          @Override
          public NodeContextBuilder inIterable() {
            return null;
          }

          @Override
          public NodeBuilderCustomizableContext inContainer(Class<?> containerClass,
              Integer typeArgumentIndex) {
            return null;
          }

          @Override
          public NodeBuilderCustomizableContext addNode(String name) {
            return null;
          }

          @Override
          public NodeBuilderCustomizableContext addPropertyNode(String name) {
            return null;
          }

          @Override
          public LeafNodeBuilderCustomizableContext addBeanNode() {
            return null;
          }

          @Override
          public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name,
              Class<?> containerType, Integer typeArgumentIndex) {
            return null;
          }

          @Override
          public ConstraintValidatorContext addConstraintViolation() {
            return null;
          }
        };
      }

      @Override
      public LeafNodeBuilderCustomizableContext addBeanNode() {
        return null;
      }

      @Override
      public ContainerElementNodeBuilderCustomizableContext addContainerElementNode(String name,
          Class<?> containerType, Integer typeArgumentIndex) {
        return null;
      }

      @Override
      public NodeBuilderDefinedContext addParameterNode(int index) {
        return null;
      }

      @Override
      public ConstraintValidatorContext addConstraintViolation() {
        return null;
      }
    };
  }
}
