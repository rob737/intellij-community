/*
 * @author max
 */
package com.intellij.psi.search.searches;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.PsiClass;
import com.intellij.psi.search.SearchScope;
import com.intellij.util.Query;

public class AllClassesSearch extends ExtensibleQueryFactory<PsiClass, AllClassesSearch.SearchParameters> {
  public static final AllClassesSearch INSTANCE = new AllClassesSearch();

  public static class SearchParameters {
    private final SearchScope myScope;
    private final Project myProject;
    private final Condition<String> myShortNameCondition;

    public SearchParameters(final SearchScope scope, final Project project) {
      this(scope, project, Condition.TRUE);
    }

    public SearchParameters(final SearchScope scope, final Project project, final Condition<String> shortNameCondition) {
      myScope = scope;
      myProject = project;
      myShortNameCondition = shortNameCondition;
    }

    public SearchScope getScope() {
      return myScope;
    }

    public Project getProject() {
      return myProject;
    }

    public boolean nameMatches(String name) {
      return myShortNameCondition.value(name);
    }
  }

  public static Query<PsiClass> search(SearchScope scope, Project project) {
    return INSTANCE.createQuery(new SearchParameters(scope, project));
  }

  public static Query<PsiClass> search(SearchScope scope, Project project, Condition<String> shortNameCondition) {
    return INSTANCE.createQuery(new SearchParameters(scope, project, shortNameCondition));
  }
}