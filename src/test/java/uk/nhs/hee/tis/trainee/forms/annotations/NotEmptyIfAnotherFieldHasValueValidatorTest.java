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

import javax.validation.ConstraintValidatorContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.params.ParameterizedTest;
import org.junit.jupiter.params.provider.NullAndEmptySource;
import org.mockito.Mock;

class NotEmptyIfAnotherFieldHasValueValidatorTest {

  public static class SimpleDto {

    private String field;
    private String dependentField;

    public String getField() {
      return field;
    }

    public void setField(String field) {
      this.field = field;
    }

    public String getDependentField() {
      return dependentField;
    }

    public void setDependentField(String dependentField) {
      this.dependentField = dependentField;
    }

    public SimpleDto() {
    }
  }

  @Mock
  ConstraintValidatorContext constraintValidatorContext;

  NotEmptyIfAnotherFieldHasValueValidator validator;

  @BeforeEach
  void setup() {
    constraintValidatorContext = mock(ConstraintValidatorContext.class);
    when(constraintValidatorContext.buildConstraintViolationWithTemplate(any()))
        .thenReturn(dummyConstraintViolationBuilder());
    validator = new NotEmptyIfAnotherFieldHasValueValidator();
    validator.initWithValues("field", "value", false,
        "dependentField", "error");
  }

  @Test
  void isValidIfObjectIsNullEqualTest() {
    validator.initWithValues("field", "value", false,
        "dependentField", "error");
    assertThat("Unexpected invalid NotEmptyIfAnotherFieldHasValueValidator.",
        validator.isValid(null, constraintValidatorContext), is(true));
  }

  @Test
  void isNotValidIfObjectIsNullNotEqualTest() {
    validator.initWithValues("field", "value", true,
        "dependentField", "error");
    assertThat("Unexpected valid NotEmptyIfAnotherFieldHasValueValidator.",
        validator.isValid(null, constraintValidatorContext), is(false));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void isNotValidIfDependentFieldIsEmptyTest(String str) {
    SimpleDto dto = new SimpleDto();
    dto.setField("value");
    dto.setDependentField(str);

    assertThat("Unexpected valid NotBeforeAnotherDateValidator.",
        validator.isValid(dto, constraintValidatorContext), is(false));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void isNotValidIfDependentFieldIsEmptyNotEqualTest(String str) {
    validator.initWithValues("field", "value", true,
        "dependentField", "error");
    SimpleDto dto = new SimpleDto();
    dto.setField("not value");
    dto.setDependentField(str);

    assertThat("Unexpected valid NotBeforeAnotherDateValidator.",
        validator.isValid(dto, constraintValidatorContext), is(false));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void isValidIfDependentFieldIsNotConstrainedTest(String str) {
    SimpleDto dto = new SimpleDto();
    dto.setField("not value");
    dto.setDependentField(str);

    assertThat("Unexpected invalid NotBeforeAnotherDateValidator.",
        validator.isValid(dto, constraintValidatorContext), is(true));
  }

  @ParameterizedTest
  @NullAndEmptySource
  void isValidIfDependentFieldIsNotConstrainedNotEqualTest(String str) {
    validator.initWithValues("field", "value", true,
        "dependentField", "error");
    SimpleDto dto = new SimpleDto();
    dto.setField("value");
    dto.setDependentField(str);

    assertThat("Unexpected invalid NotBeforeAnotherDateValidator.",
        validator.isValid(dto, constraintValidatorContext), is(true));
  }

  @Test
  void isValidIfDependentFieldIsPopulatedTest() {
    SimpleDto dto = new SimpleDto();
    dto.setField("value");
    dto.setDependentField("some value");

    assertThat("Unexpected invalid NotBeforeAnotherDateValidator.",
        validator.isValid(dto, constraintValidatorContext), is(true));
  }

  @Test
  void isValidIfDependentFieldIsPopulatedNotEqualTest() {
    validator.initWithValues("field", "value", true,
        "dependentField", "error");
    SimpleDto dto = new SimpleDto();
    dto.setField("not value");
    dto.setDependentField("some value");

    assertThat("Unexpected invalid NotBeforeAnotherDateValidator.",
        validator.isValid(dto, constraintValidatorContext), is(true));
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
