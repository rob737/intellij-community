package com.intellij.psi.filters.getters;

import com.intellij.codeInsight.CodeInsightUtil;
import com.intellij.codeInsight.completion.CompletionContext;
import com.intellij.codeInsight.completion.PrefixMatcher;
import com.intellij.codeInsight.lookup.LookupElementFactory;
import com.intellij.codeInsight.lookup.MutableLookupElement;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.openapi.util.Condition;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.filters.ContextGetter;
import com.intellij.psi.util.PsiUtil;
import com.intellij.util.IncorrectOperationException;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

public class ClassLiteralGetter {
  private static final Logger LOG = Logger.getInstance("com.intellij.psi.filters.getters.ClassLiteralGetter");
  private final ContextGetter myBaseGetter;
  @NonNls private static final String DOT_CLASS = ".class";

  public ClassLiteralGetter(ContextGetter baseGetter) {
    myBaseGetter = baseGetter;
  }

  public MutableLookupElement<PsiExpression>[] getClassLiterals(PsiElement context, CompletionContext completionContext, final PrefixMatcher matcher) {
    final Condition<String> shortNameCondition = new Condition<String>() {
      public boolean value(String s) {
        return matcher.prefixMatches(s);
      }
    };

    final List<MutableLookupElement<PsiExpression>> result = new ArrayList<MutableLookupElement<PsiExpression>>();
    for (final Object element : myBaseGetter.get(context, completionContext)) {
      if (element instanceof PsiClassType) {
        PsiClassType.ClassResolveResult resolveResult = ((PsiClassType)element).resolveGenerics();
        PsiClass psiClass = resolveResult.getElement();
        if (psiClass != null && CommonClassNames.JAVA_LANG_CLASS.equals(psiClass.getQualifiedName())) {
          final PsiTypeParameter[] typeParameters = psiClass.getTypeParameters();
          if (typeParameters.length == 1) {
            PsiType substitution = resolveResult.getSubstitutor().substitute(typeParameters[0]);
            boolean addInheritors = false;
            if (substitution instanceof PsiWildcardType) {
              final PsiWildcardType wildcardType = (PsiWildcardType)substitution;
              substitution = wildcardType.getBound();
              addInheritors = wildcardType.isExtends();
            }

            final PsiClass aClass = PsiUtil.resolveClassInType(substitution);
            if (aClass == null) continue;

            createLookupElement(substitution, context, result);
            if (addInheritors && substitution != null && !CommonClassNames.JAVA_LANG_OBJECT.equals(substitution.getCanonicalText())) {
              for (final PsiType type : CodeInsightUtil.addSubtypes(substitution, context, true, shortNameCondition)) {
                createLookupElement(type, context, result);
              }
            }

          }
        }
      }
    }

    return result.toArray(new MutableLookupElement[result.size()]);
  }

  private static void createLookupElement(@Nullable final PsiType type, final PsiElement context, final List<MutableLookupElement<PsiExpression>> list) {
    if (type instanceof PsiClassType && !((PsiClassType)type).hasParameters() && !(((PsiClassType) type).resolve() instanceof PsiTypeParameter)) {
      try {
        final PsiManager manager = context.getManager();
        PsiExpression expr =
          JavaPsiFacade.getInstance(manager.getProject()).getElementFactory().createExpressionFromText(type.getCanonicalText() + DOT_CLASS, context);
        expr = (PsiExpression)JavaCodeStyleManager.getInstance(context.getProject()).shortenClassReferences(expr);
        list.add(LookupElementFactory.getInstance().createLookupElement(expr, expr.getText()));
      }
      catch (IncorrectOperationException e) {
        LOG.error(e);
      }
    }
  }
}
