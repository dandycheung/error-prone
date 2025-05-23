/*
 * Copyright 2011 The Error Prone Authors.
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

package com.google.errorprone.matchers;

import com.google.errorprone.VisitorState;
import com.sun.source.tree.AnnotationTree;
import com.sun.source.tree.IdentifierTree;
import com.sun.source.tree.MemberSelectTree;
import com.sun.source.tree.Tree;
import com.sun.tools.javac.tree.JCTree;

/**
 * @author eaftan@google.com (Eddie Aftandilian)
 * @author pepstein@google.com (Peter Epstein)
 */
public class AnnotationType implements Matcher<AnnotationTree> {

  private final String annotationClassName;

  public AnnotationType(String annotationClassName) {
    this.annotationClassName = annotationClassName;
  }

  @Override
  public boolean matches(AnnotationTree annotationTree, VisitorState state) {
    Tree type = annotationTree.getAnnotationType();
    if (type instanceof IdentifierTree && type instanceof JCTree.JCIdent jcIdent) {
      return jcIdent.sym.getQualifiedName().contentEquals(annotationClassName);
    } else if (type instanceof MemberSelectTree
        && type instanceof JCTree.JCFieldAccess jcFieldAccess) {
      return jcFieldAccess.sym.getQualifiedName().contentEquals(annotationClassName);
    } else {
      return false;
    }
  }
}
