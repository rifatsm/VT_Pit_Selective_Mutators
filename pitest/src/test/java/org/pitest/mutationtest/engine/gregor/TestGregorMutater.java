/*
 * Copyright 2010 Henry Coles
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing,
 * software distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and limitations under the License.
 */
package org.pitest.mutationtest.engine.gregor;

import org.junit.Test;
import org.pitest.mutationtest.engine.MutationDetails;
import org.pitest.mutationtest.engine.gregor.config.Mutator;
import org.pitest.mutationtest.engine.gregor.mutators.IncrementsMutator;
import org.pitest.mutationtest.engine.gregor.mutators.InvertNegsMutator;
import org.pitest.mutationtest.engine.gregor.mutators.MathMutator;
import org.pitest.mutationtest.engine.gregor.mutators.ReturnValsMutator;
import org.pitest.util.ResourceFolderByteArraySource;

import java.util.Collection;
import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;
import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertTrue;

public class TestGregorMutater extends MutatorTestBase {

  public static class HasMultipleMutations {
    public int mutable() {
      int j = 10;
      for (int i = 0; i != 10; i++) {
        j = j << 1;
      }

      return -j;
    }

  }

  @Test
  public void shouldFindMutationsFromAllSuppliedMutators() throws Exception {

    createTesteeWith(MathMutator.MATH,
        ReturnValsMutator.RETURN_VALS,
        InvertNegsMutator.INVERT_NEGS,
        IncrementsMutator.INCREMENTS);

    final List<MutationDetails> actualDetails = findMutationsFor(HasMultipleMutations.class);

    assertTrue(actualDetails.stream()
        .anyMatch(descriptionContaining("Replaced Shift Left with Shift Right")));
    assertTrue(actualDetails.stream()
        .anyMatch(descriptionContaining("replaced return of integer")));
    assertTrue(actualDetails.stream()
        .anyMatch(descriptionContaining("Changed increment")));
    assertTrue(actualDetails.stream()
        .anyMatch(descriptionContaining("removed negation")));
  }

  @Test
  public void shouldFindNoMutationsWhenNoMutationOperatorsSupplied()
      throws Exception {
    class VeryMutable {
      @SuppressWarnings("unused")
      public int f(final int i) {
        switch (i) {
        case 0:
          return 1;
        }
        return 0;
      }
    }
    createTesteeWith();
    final List<MutationDetails> actualDetails = findMutationsFor(VeryMutable.class);
    assertTrue(actualDetails.isEmpty());

  }

  static enum AnEnum {
    Foo, Bar;
  }

  @Test
  public void shouldNotMutateCodeGeneratedByCompilerToImplementEnums() {
    createTesteeWith(Mutator.all());
    final Collection<MutationDetails> actualDetails = findMutationsFor(AnEnum.class);
    assertTrue(actualDetails.isEmpty());
  }

  static enum EnumWithCustomConstructor {
    Foo, Bar;

    int i;

    EnumWithCustomConstructor() {
      this.i++;
    }

  }

  @Test
  public void shouldMutateCustomConstructorsAddedToEnums() {
    createTesteeWith(Mutator.all());
    final Collection<MutationDetails> actualDetails = findMutationsFor(EnumWithCustomConstructor.class);
    assertThat(actualDetails).isNotEmpty();
  }



  public static class HasAssertStatement {
    public void foo(final int i) {
      assert ((i + 20) > 10);
    }
  }

  @Test
  public void shouldNotMutateAssertStatments() {
    createTesteeWith(Mutator.byName("NEGATE_CONDITIONALS"));
    final Collection<MutationDetails> actualDetails = findMutationsFor(HasAssertStatement.class);
    assertEquals(0, actualDetails.size());
  }

  public static class HasAssertStatementAndOtherStatements {
    public int state;

    public void foo(final int i) {
      assert ((i + 20) > 10);
      if (i > 1) {
        this.state = 1;
      }
    }
  }

  @Test
  public void shouldMutateOtherStatementsWhenAssertIsPresent() {
    createTesteeWith(Mutator.byName("NEGATE_CONDITIONALS"));
    final Collection<MutationDetails> actualDetails = findMutationsFor(HasAssertStatementAndOtherStatements.class);
    assertEquals(1, actualDetails.size());
  }

  @Test
  public void shouldNotMutateGroovyClasses() {
    createTesteeWith(new ResourceFolderByteArraySource(),
        i -> true, Mutator.all());
    final Collection<MutationDetails> actualDetails = findMutationsFor("groovy/SomeGroovyCode");
    assertTrue(actualDetails.isEmpty());
  }

  @Test
  public void shouldNotMutateGroovyClosures() {
    createTesteeWith(new ResourceFolderByteArraySource(),
        i -> true, Mutator.all());
    final Collection<MutationDetails> actualDetails = findMutationsFor("groovy/SomeGroovyCode$_mapToString_closure2");
    assertTrue(actualDetails.isEmpty());
  }

  public static class OneStraightThroughMethod {
    public void straightThrough(int i) {
      i++;
      i++;
    }
  }

  @Test
  public void shouldRecordMutationsAsInSameBlockWhenForAStraightThroughMethod() {
    createTesteeWith(Mutator.byName("INCREMENTS"));
    final List<MutationDetails> actualDetails = findMutationsFor(OneStraightThroughMethod.class);
    assertEquals(2, actualDetails.size());
    final int firstMutationBlock = actualDetails.get(0).getBlock();
    assertEquals(firstMutationBlock, actualDetails.get(1).getBlock());
  }

  public static class SimpleBranch {
    public void straightThrough(int i, final boolean b) {
      if (b) {
        i++;
      } else {
        i++;
      }
    }
  }

  @Test
  public void shouldRecordMutationsAsInDifferentBlocksWhenInDifferentBranchesOfIfStatement() {
    createTesteeWith(Mutator.byName("INCREMENTS"));
    final List<MutationDetails> actualDetails = findMutationsFor(SimpleBranch.class);
    assertTwoMutationsInDifferentBlocks(actualDetails);
  }

  public static class TwoMethods {
    public void a(int i) {
      i++;
    }

    public void b(int i) {
      i++;
    }
  }

  public static class SwitchStatement {
    public void a(int i, final int b) {
      switch (b) {
      case 0:
        i++;
        break;
      case 1:
        i++;
        break;
      default:
        i++;
      }
    }

  }

  @Test
  public void shouldRecordMutationsAsInDifferentBlocksWhenInDifferentBranchesOfSwitchStatement() {
    createTesteeWith(Mutator.byName("INCREMENTS"));
    final List<MutationDetails> actualDetails = findMutationsFor(SwitchStatement.class);
    assertEquals(3, actualDetails.size());
    final int firstMutationBlock = actualDetails.get(0).getBlock();
    assertEquals(firstMutationBlock + 1, actualDetails.get(1).getBlock());
    assertEquals(firstMutationBlock + 2, actualDetails.get(2).getBlock());
  }

  public static class FallThroughSwitch {
    public void a(int i, final int b) {
      switch (b) {
      case 0:
        i++;
      case 1:
        i++;
      }
    }
  }

  @Test
  public void shouldNotRecordMutationsAsInSameBlockWhenSwitchStatementFallsThrough() {
    createTesteeWith(Mutator.byName("INCREMENTS"));
    final List<MutationDetails> actualDetails = findMutationsFor(FallThroughSwitch.class);
    assertEquals(2, actualDetails.size());
    final int firstMutationBlock = actualDetails.get(0).getBlock();
    assertEquals(firstMutationBlock+1, actualDetails.get(1).getBlock());
  }

  public static class HasExceptionBlock {
    public void foo(int i) {
      try {
        i++;
      } catch (final Exception ex) {
        i++;
      }
    }
  }

  @Test
  public void shouldRecordMutationsAsInDifferentBlocksWhenInExceptionHandler() {
    createTesteeWith(Mutator.byName("INCREMENTS"));
    final List<MutationDetails> actualDetails = findMutationsFor(HasExceptionBlock.class);
    assertTwoMutationsInDifferentBlocks(actualDetails);
  }

  public static class HasTwoMutableMethods {
    public int a() {
      return 1;
    }

    public int a(int i) {
      if (i > 2) {
        System.out.println(i);
      }
      return 1;
    }
  }

  @Test
  public void shouldScopeMutationIndexesByInstructionCounter() {
    createTesteeWith(Mutator.byName("RETURN_VALS"));
    final List<MutationDetails> actualDetails = findMutationsFor(HasTwoMutableMethods.class);
    assertEquals(2, actualDetails.size());
    assertEquals(4, actualDetails.get(0).getId().getFirstIndex());
    assertEquals(15, actualDetails.get(1).getId().getFirstIndex()); // differs
                                                                    // by
                                                                    // target?
  }

  @Test
  public void shouldNotMutateCompilerGeneratedConditionalsInStringSwitch() {
    createTesteeWith(new ResourceFolderByteArraySource(),
        i -> true, Mutator.byName("REMOVE_CONDITIONALS"));
    final Collection<MutationDetails> actualDetails = findMutationsFor("Java7SwitchOnString");
    assertThat(actualDetails).isEmpty();
  }


  private void assertTwoMutationsInDifferentBlocks(
      final List<MutationDetails> actualDetails) {
    assertEquals(2, actualDetails.size());
    final int firstMutationBlock = actualDetails.get(0).getBlock();
    assertEquals(firstMutationBlock + 1, actualDetails.get(1).getBlock());
  }
}
