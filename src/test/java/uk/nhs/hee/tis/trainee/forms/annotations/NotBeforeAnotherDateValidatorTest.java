package uk.nhs.hee.tis.trainee.forms.annotations;

import static org.hamcrest.CoreMatchers.is;
import static org.hamcrest.MatcherAssert.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.when;

import java.time.LocalDate;
import javax.validation.ConstraintValidatorContext;
import javax.validation.ConstraintValidatorContext.ConstraintViolationBuilder;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.mockito.Mock;

class NotBeforeAnotherDateValidatorTest {

  public static class SimpleDto {

    private LocalDate startDate;
    private LocalDate endDate;

    public SimpleDto() {
    }

    public LocalDate getStartDate() {
      return startDate;
    }

    public void setStartDate(LocalDate startDate) {
      this.startDate = startDate;
    }

    public LocalDate getEndDate() {
      return endDate;
    }

    public void setEndDate(LocalDate endDate) {
      this.endDate = endDate;
    }
  }

  @Mock
  ConstraintValidatorContext constraintValidatorContext;

  NotBeforeAnotherDateValidator validator;

  @BeforeEach
  void setup() {
    constraintValidatorContext = mock(ConstraintValidatorContext.class);
    when(constraintValidatorContext.buildConstraintViolationWithTemplate(any()))
        .thenReturn(dummyConstraintViolationBuilder());
    validator = new NotBeforeAnotherDateValidator();
    validator.initWithValues("startDate", "endDate","error");
  }

  @Test
  void isValidIfObjectIsNullTest() {
    assertThat("Unexpected invalid NotBeforeAnotherDateValidator.",
        validator.isValid(null, constraintValidatorContext), is(true));
  }

  @Test
  void isValidIfFieldIsNullTest() {
    SimpleDto twoDates = new SimpleDto();
    twoDates.setStartDate(null);
    twoDates.setEndDate(LocalDate.now());

    assertThat("Unexpected invalid NotBeforeAnotherDateValidator.",
        validator.isValid(twoDates, constraintValidatorContext), is(true));
  }

  @Test
  void isNotValidIfDependentFieldIsNullTest() {
    SimpleDto twoDates = new SimpleDto();
    twoDates.setStartDate(LocalDate.now());
    twoDates.setEndDate(null);

    assertThat("Unexpected valid NotBeforeAnotherDateValidator.",
        validator.isValid(twoDates, constraintValidatorContext), is(false));
  }

  @Test
  void isNotValidIfDependentFieldIsBeforeFieldTest() {
    SimpleDto twoDates = new SimpleDto();
    twoDates.setStartDate(LocalDate.now());
    twoDates.setEndDate(LocalDate.now().minusMonths(1));

    assertThat("Unexpected valid NotBeforeAnotherDateValidator.",
        validator.isValid(twoDates, constraintValidatorContext), is(false));
  }

  @Test
  void isValidIfDependentFieldIsAfterFieldTest() {
    SimpleDto twoDates = new SimpleDto();
    twoDates.setStartDate(LocalDate.now());
    twoDates.setEndDate(LocalDate.now().plusMonths(1));

    assertThat("Unexpected invalid NotBeforeAnotherDateValidator.",
        validator.isValid(twoDates, constraintValidatorContext), is(true));
  }

  /**
   * Helper function to return a dummy ConstraintViolationBuilder for the mocked
   * constraintValidatorContext.
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
