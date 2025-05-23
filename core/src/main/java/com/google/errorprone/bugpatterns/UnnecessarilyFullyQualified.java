/*
 * Copyright 2020 The Error Prone Authors.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package com.google.errorprone.bugpatterns;

import static com.google.common.collect.Iterables.getOnlyElement;
import static com.google.errorprone.matchers.Description.NO_MATCH;
import static com.google.errorprone.util.ASTHelpers.getGeneratedBy;
import static com.google.errorprone.util.ASTHelpers.getSymbol;
import static com.google.errorprone.util.ASTHelpers.getType;
import static com.google.errorprone.util.ASTHelpers.isGeneratedConstructor;
import static com.google.errorprone.util.FindIdentifiers.findIdent;
import static com.sun.tools.javac.code.Kinds.KindSelector.VAL_TYP;
import static java.util.stream.Collectors.toCollection;

import com.google.common.base.Ascii;
import com.google.common.collect.HashBasedTable;
import com.google.common.collect.HashMultimap;
import com.google.common.collect.ImmutableSet;
import com.google.common.collect.SetMultimap;
import com.google.common.collect.Table;
import com.google.errorprone.BugPattern;
import com.google.errorprone.BugPattern.SeverityLevel;
import com.google.errorprone.ErrorProneFlags;
import com.google.errorprone.VisitorState;
import com.google.errorprone.bugpatterns.BugChecker.CompilationUnitTreeMatcher;
import com.google.errorprone.fixes.SuggestedFix;
import com.google.errorprone.matchers.Description;
import com.google.errorprone.util.ASTHelpers;
import com.sun.source.tree.ClassTree;
import com.sun.source.tree.CompilationUnitTree;
import com.sun.source.tree.ExpressionTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.ImportTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.MethodTree;
import com.sun.source.tree.Tree;
import com.sun.source.util.SimpleTreeVisitor;
import com.sun.source.util.TreePath;
import com.sun.tools.javac.code.Symbol;
import com.sun.tools.javac.code.Symbol.ClassSymbol;
import com.sun.tools.javac.code.Symbol.PackageSymbol;
import com.sun.tools.javac.code.Symbol.TypeSymbol;
import com.sun.tools.javac.code.Type;
import com.sun.tools.javac.util.Position;
import java.util.ArrayList;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Objects;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;
import javax.inject.Inject;
import javax.lang.model.element.Name;

/** Flags uses of fully qualified names which are not ambiguous if imported. */
@BugPattern(
    severity = SeverityLevel.WARNING,
    summary = "This fully qualified name is unambiguous to the compiler if imported.")
public final class UnnecessarilyFullyQualified extends BugChecker
    implements CompilationUnitTreeMatcher {

  private static final ImmutableSet<String> EXEMPTED_NAMES = ImmutableSet.of("Annotation");

  /**
   * Exempted types that fully qualified name usages are acceptable for their nested types when
   * importing the enclosing type is ambiguous.
   *
   * <p>Some types are meant to provide a namespace; therefore, imports for their nested types can
   * be confusing.
   *
   * <p>For instance, unlike its name suggests, {@code org.immutables.value.Value.Immutable} is used
   * to generate immutable value types, and its import can be misleading. So, importing {@code
   * org.immutables.value.Value} and using {@code @Value.Immutable} is more favorable than importing
   * {@code org.immutables.value.Value.Immutable} and using {@code @Immutable}.
   */
  private final ImmutableSet<String> exemptedEnclosingTypes;

  private final boolean batchFindings;

  @Inject
  UnnecessarilyFullyQualified(ErrorProneFlags errorProneFlags) {
    this.exemptedEnclosingTypes = errorProneFlags.getSetOrEmpty("BadImport:BadEnclosingTypes");
    this.batchFindings =
        errorProneFlags.getBoolean("UnnecessarilyFullyQualified:BatchFindings").orElse(false);
  }

  @Override
  public Description matchCompilationUnit(CompilationUnitTree tree, VisitorState state) {
    if (tree.getTypeDecls().stream()
        .anyMatch(t -> getSymbol(tree) != null && !getGeneratedBy(getSymbol(tree)).isEmpty())) {
      return NO_MATCH;
    }
    if (isPackageInfo(tree)) {
      return NO_MATCH;
    }
    Table<Name, TypeSymbol, List<TreePath>> table = HashBasedTable.create();
    SetMultimap<Name, TypeSymbol> identifiersSeen = HashMultimap.create();
    new SuppressibleTreePathScanner<Void, Void>(state) {
      @Override
      public Void visitImport(ImportTree importTree, Void unused) {
        return null;
      }

      @Override
      public Void visitClass(ClassTree tree, Void unused) {
        scan(tree.getModifiers(), null);
        scan(tree.getTypeParameters(), null);
        // Some anonymous classes have an extends clause which is fully qualified. Just ignore it.
        if (!getSymbol(tree).isAnonymous()) {
          scan(tree.getExtendsClause(), null);
          scan(tree.getImplementsClause(), null);
        }
        scan(tree.getMembers(), null);
        return null;
      }

      @Override
      public Void visitMethod(MethodTree tree, Void unused) {
        // Some generated constructors sneak in a fully qualified argument.
        return isGeneratedConstructor(tree) ? null : super.visitMethod(tree, null);
      }

      @Override
      public Void visitMemberSelect(MemberSelectTree memberSelectTree, Void unused) {
        if (!shouldIgnore()) {
          handle(getCurrentPath());
        }
        return super.visitMemberSelect(memberSelectTree, null);
      }

      @Override
      public Void visitIdentifier(IdentifierTree identifierTree, Void unused) {
        Type type = getType(identifierTree);
        if (type != null) {
          identifiersSeen.put(identifierTree.getName(), type.tsym);
        }
        return null;
      }

      private boolean shouldIgnore() {
        // Don't report duplicate hits if we're not at the tail of a series of member selects on
        // classes.
        Tree parentTree = getCurrentPath().getParentPath().getLeaf();
        return parentTree instanceof MemberSelectTree
            && getSymbol(parentTree) instanceof ClassSymbol;
      }

      private void handle(TreePath path) {
        MemberSelectTree tree = (MemberSelectTree) path.getLeaf();
        if (!isFullyQualified(tree)) {
          return;
        }
        if (isBadNestedClass(tree) || isExemptedEnclosingType(tree)) {
          handle(new TreePath(path, tree.getExpression()));
          return;
        }
        Symbol symbol = getSymbol(tree);
        if (!(symbol instanceof ClassSymbol)) {
          return;
        }
        if (state.getEndPosition(tree) == Position.NOPOS) {
          return;
        }
        List<TreePath> treePaths = table.get(tree.getIdentifier(), symbol.type.tsym);
        if (treePaths == null) {
          treePaths = new ArrayList<>();
          table.put(tree.getIdentifier(), symbol.type.tsym, treePaths);
        }
        treePaths.add(path);
      }

      private boolean isBadNestedClass(MemberSelectTree tree) {
        return BadImport.BAD_NESTED_CLASSES.contains(tree.getIdentifier().toString());
      }

      private boolean isExemptedEnclosingType(MemberSelectTree tree) {
        ExpressionTree expression = tree.getExpression();
        if (!(expression instanceof MemberSelectTree)) {
          return false;
        }
        Symbol symbol = getSymbol(expression);
        if (!(symbol instanceof ClassSymbol)) {
          return false;
        }

        return exemptedEnclosingTypes.contains(symbol.getQualifiedName().toString());
      }

      private boolean isFullyQualified(MemberSelectTree tree) {
        AtomicBoolean isFullyQualified = new AtomicBoolean();
        new SimpleTreeVisitor<Void, Void>() {
          @Override
          public Void visitMemberSelect(MemberSelectTree memberSelectTree, Void unused) {
            return visit(memberSelectTree.getExpression(), null);
          }

          @Override
          public Void visitIdentifier(IdentifierTree identifierTree, Void aVoid) {
            if (getSymbol(identifierTree) instanceof PackageSymbol) {
              isFullyQualified.set(true);
            }
            return null;
          }
        }.visit(tree, null);
        return isFullyQualified.get();
      }
    }.scan(state.getPath(), null);

    for (Map.Entry<Name, Map<TypeSymbol, List<TreePath>>> rows : table.rowMap().entrySet()) {
      Name name = rows.getKey();
      Map<TypeSymbol, List<TreePath>> types = rows.getValue();
      // Skip places where the same simple name refers to multiple types.
      if (types.size() > 1) {
        continue;
      }
      TypeSymbol type = getOnlyElement(types.keySet());
      // Skip weird Android classes which don't look like classes.
      if (Ascii.isLowerCase(name.charAt(0))) {
        continue;
      }
      // Don't replace if this name is already used in the file to refer to a _different_ type.
      if (identifiersSeen.get(name).stream().anyMatch(s -> !s.equals(type))) {
        continue;
      }
      String nameString = name.toString();
      if (EXEMPTED_NAMES.contains(nameString)) {
        continue;
      }
      List<TreePath> pathsToFix = getOnlyElement(types.values());
      Set<Symbol> meaningAtUsageSites =
          pathsToFix.stream()
              .map(path -> findIdent(nameString, state.withPath(path), VAL_TYP))
              .collect(toCollection(HashSet::new));
      // Don't report a finding if the simple name has a different meaning elsewhere.
      if (meaningAtUsageSites.stream().anyMatch(s -> s != null && !s.equals(type))) {
        continue;
      }
      SuggestedFix.Builder fixBuilder = SuggestedFix.builder();
      // Only add the import if any of the usage sites don't already resolve to this type.
      if (meaningAtUsageSites.stream().anyMatch(Objects::isNull)) {
        fixBuilder.addImport(getOnlyElement(types.keySet()).getQualifiedName().toString());
      }
      for (TreePath path : pathsToFix) {
        fixBuilder.replace(path.getLeaf(), nameString);
      }
      SuggestedFix fix = fixBuilder.build();
      for (TreePath path : pathsToFix) {
        state.reportMatch(describeMatch(path.getLeaf(), fix));
        if (this.batchFindings) {
          break;
        }
      }
    }
    return NO_MATCH;
  }

  private static boolean isPackageInfo(CompilationUnitTree tree) {
    String name = ASTHelpers.getFileName(tree);
    int idx = name.lastIndexOf('/');
    if (idx != -1) {
      name = name.substring(idx + 1);
    }
    return name.equals("package-info.java");
  }
}
