package com.intellij.psi.scope.conflictResolvers;

import com.intellij.pom.java.LanguageLevel;
import com.intellij.psi.*;
import com.intellij.psi.infos.CandidateInfo;
import com.intellij.psi.infos.MethodCandidateInfo;
import com.intellij.psi.scope.PsiConflictResolver;
import com.intellij.psi.util.*;

import java.util.HashMap;
import java.util.Iterator;
import java.util.List;
import java.util.Map;

/**
 * Created by IntelliJ IDEA.
 * User: ik
 * Date: 10.06.2003
 * Time: 19:41:51
 * To change this template use Options | File Templates.
 */
public class JavaMethodsConflictResolver implements PsiConflictResolver{
  private final PsiExpressionList myArgumentsList;

  public JavaMethodsConflictResolver(PsiExpressionList list){
    myArgumentsList = list;
  }

  public CandidateInfo resolveConflict(List<CandidateInfo> conflicts){
    int conflictsCount = conflicts.size();
    if (conflictsCount <= 0) return null;
    if (conflictsCount == 1) return conflicts.get(0);
    checkSameSignatures(conflicts);

    conflictsCount = conflicts.size();
    if (conflictsCount == 1) return conflicts.get(0);

    int maxCheckLevel = -1;
    int[] checkLevels = new int[conflictsCount];
    int index = 0;
    for (final CandidateInfo conflict : conflicts) {
      final MethodCandidateInfo method = (MethodCandidateInfo)conflict;
      final int level = getCheckLevel(method);
      checkLevels[index++] = level;
      maxCheckLevel = Math.max(maxCheckLevel, level);
    }

    for (int i = conflictsCount - 1; i >= 0; i--) {
      // check for level
      if (checkLevels[i] < maxCheckLevel) {
        conflicts.remove(i);
      }
    }

    conflictsCount = conflicts.size();
    if (conflictsCount == 1) return conflicts.get(0);

    checkParametersNumber(conflicts, myArgumentsList.getExpressions().length);
    conflictsCount = conflicts.size();
    if (conflictsCount == 1) return conflicts.get(0);

    final int applicabilityLevel = checkApplicability(conflicts);
    final boolean applicable = applicabilityLevel > MethodCandidateInfo.ApplicabilityLevel.NOT_APPLICABLE;

    conflictsCount = conflicts.size();
    if (conflictsCount == 1) return conflicts.get(0);

    // Specifics
    if (applicable) {
      final CandidateInfo[] newConflictsArray = conflicts.toArray(new CandidateInfo[conflicts.size()]);
      for (int i = 1; i < conflictsCount; i++) {
        final CandidateInfo method = newConflictsArray[i];
        for (int j = 0; j < i; j++) {
          final CandidateInfo conflict = newConflictsArray[j];
          assert conflict != method;
          switch (isMoreSpecific(method, conflict, applicabilityLevel)) {
            case TRUE:
              conflicts.remove(conflict);
              break;
            case FALSE:
              conflicts.remove(method);
              break;
            case CONFLICT:
              break;
          }
        }
      }
    }
    if (conflicts.size() == 1){
      return conflicts.get(0);
    }

    return null;
  }

  private static void checkSameSignatures(final List<CandidateInfo> conflicts) {
    // candidates should go in order of class hierarchy traversal
    // in order for this to work
    Map<MethodSignature, CandidateInfo> signatures = new HashMap<MethodSignature, CandidateInfo>();
    for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext();) {
      CandidateInfo info = iterator.next();
      PsiMethod method = (PsiMethod)info.getElement();
      assert method != null;
      PsiClass class1 = method.getContainingClass();
      MethodSignature signature = method.getSignature(info.getSubstitutor());
      CandidateInfo existing = signatures.get(signature);
      if (existing != null) {
        PsiMethod existingMethod = (PsiMethod)existing.getElement();
        assert existingMethod != null;
        PsiClass class2 = existingMethod.getContainingClass();
        if (class1.isInterface() && "java.lang.Object".equals(class2.getQualifiedName())) { //prefer interface methods to methods from Object
          signatures.put(signature, info);
          continue;
        }
        if (method == existingMethod) {
          PsiElement scope1 = info.getCurrentFileResolveScope();
          PsiElement scope2 = existing.getCurrentFileResolveScope();
          if (scope1 instanceof PsiClass && scope2 instanceof PsiClass &&
          PsiTreeUtil.isAncestor(scope1, scope2, true) && !existing.isAccessible()) { //prefer methods from outer class to inaccessible base class methods
            signatures.put(signature, info);
            continue;
          }
        }
        PsiType returnType1 = method.getReturnType();
        PsiType returnType2 = existingMethod.getReturnType();
        if (returnType1 != null && returnType2 != null) {
          returnType1 = info.getSubstitutor().substitute(returnType1);
          returnType2 = existing.getSubstitutor().substitute(returnType2);
          if (returnType1.isAssignableFrom(returnType2)
              && (class1.isInheritor(class2, true) || class2.isInheritor(class1, true))
              
              ) iterator.remove();
        }
        continue;
      }
      signatures.put(signature, info);
    }
  }

  private static void checkParametersNumber(final List<CandidateInfo> conflicts, final int argumentsCount) {
    boolean parametersNumberMatch = false;
    for (CandidateInfo info : conflicts) {
      if (info instanceof MethodCandidateInfo) {
        final PsiMethod method = ((MethodCandidateInfo)info).getElement();
        if (method.isVarArgs()) return;
        if (method.getParameterList().getParametersCount() == argumentsCount) {
          parametersNumberMatch = true;
        }
      }
    }

    if (parametersNumberMatch) {
      for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext();) {
        CandidateInfo info = iterator.next();
        if (info instanceof MethodCandidateInfo) {
          final PsiMethod method = ((MethodCandidateInfo)info).getElement();
          if (method.getParameterList().getParametersCount() != argumentsCount) {
            iterator.remove();
          }
        }
      }
    }
  }

  private static int checkApplicability(List<CandidateInfo> conflicts) {
    int maxApplicabilityLevel = 0;
    boolean toFilter = false;
    for (CandidateInfo conflict : conflicts) {
      final int level = ((MethodCandidateInfo)conflict).getApplicabilityLevel();
      if (maxApplicabilityLevel > 0 && maxApplicabilityLevel != level) {
        toFilter = true;
      }
      if (level > maxApplicabilityLevel) {
        maxApplicabilityLevel = level;
      }
    }

    if (toFilter) {
      for (Iterator<CandidateInfo> iterator = conflicts.iterator(); iterator.hasNext();) {
        CandidateInfo info = iterator.next();
        final int level = ((MethodCandidateInfo)info).getApplicabilityLevel();  //cached
        if (level < maxApplicabilityLevel) {
          iterator.remove();
        }
      }
    }

    return maxApplicabilityLevel;
  }

  private static int getCheckLevel(MethodCandidateInfo method){
    boolean visible = method.isAccessible();// && !method.myStaticProblem;
    boolean available = method.isStaticsScopeCorrect();
    return (visible ? 1 : 0) << 2 |
           (available ? 1 : 0) << 1 |
           (method.getCurrentFileResolveScope() instanceof PsiImportStaticStatement ? 0 : 1);
  }

  private enum Specifics {
    FALSE,
    TRUE,
    CONFLICT
  }

  private static Specifics checkSubtyping(PsiType type1, PsiType type2) {
    final boolean assignable2From1 = TypeConversionUtil.isAssignable(type2, type1, false);
    final boolean assignable1From2 = TypeConversionUtil.isAssignable(type1, type2, false);
    if (assignable1From2 || assignable2From1) {
      if (assignable1From2 && assignable2From1) {
        return null;
      }

      return assignable1From2 ? Specifics.FALSE : Specifics.TRUE;
    }

    return Specifics.CONFLICT;
  }

  private boolean isBoxingHappened(PsiType argType, PsiType parameterType) {
    if (argType == null) return parameterType instanceof PsiPrimitiveType;
    final LanguageLevel languageLevel = PsiUtil.getLanguageLevel(myArgumentsList);
    if (parameterType instanceof PsiClassType) {
      parameterType = ((PsiClassType)parameterType).setLanguageLevel(languageLevel);
    }

    return TypeConversionUtil.boxingConversionApplicable(parameterType, argType);
  }

  private Specifics isMoreSpecific(final CandidateInfo info1, final CandidateInfo info2, final int applicabilityLevel) {
    PsiMethod method1 = (PsiMethod)info1.getElement();
    PsiMethod method2 = (PsiMethod)info2.getElement();
    final PsiClass class1 = method1.getContainingClass();
    final PsiClass class2 = method2.getContainingClass();

    final PsiParameter[] params1 = method1.getParameterList().getParameters();
    final PsiParameter[] params2 = method2.getParameterList().getParameters();

    PsiExpression[] args = myArgumentsList.getExpressions();

    final PsiTypeParameter[] typeParameters1 = method1.getTypeParameters();
    final PsiTypeParameter[] typeParameters2 = method2.getTypeParameters();
    final PsiSubstitutor classSubstitutor1 = info1.getSubstitutor(); //substitutions for method type parameters will be ignored
    final PsiSubstitutor classSubstitutor2 = info2.getSubstitutor();
    PsiSubstitutor methodSubstitutor1 = PsiSubstitutor.EMPTY;
    PsiSubstitutor methodSubstitutor2 = PsiSubstitutor.EMPTY;

    final int max = Math.max(params1.length, params2.length);
    PsiType[] types1 = new PsiType[max];
    PsiType[] types2 = new PsiType[max];
    for (int i = 0; i < max; i++) {
      PsiType type1 = params1[Math.min(i, params1.length - 1)].getType();
      PsiType type2 = params2[Math.min(i, params2.length - 1)].getType();
      if (applicabilityLevel == MethodCandidateInfo.ApplicabilityLevel.VARARGS) {
        if (type1 instanceof PsiEllipsisType && type2 instanceof PsiEllipsisType) {
          type1 = ((PsiEllipsisType)type1).toArrayType();
          type2 = ((PsiEllipsisType)type2).toArrayType();
        }
        else {
          type1 = type1 instanceof PsiEllipsisType ? ((PsiArrayType)type1).getComponentType() : type1;
          type2 = type2 instanceof PsiEllipsisType ? ((PsiArrayType)type2).getComponentType() : type2;
        }
      }

      types1[i] = type1;
      types2[i] = type2;
    }

    if (typeParameters1.length == 0 || typeParameters2.length == 0) {
      if (typeParameters1.length > 0) {
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myArgumentsList.getProject()).getResolveHelper();
        methodSubstitutor1 = calculateMethodSubstitutor(typeParameters1, types1, types2, resolveHelper);
      }
      else if (typeParameters2.length > 0) {
        final PsiResolveHelper resolveHelper = JavaPsiFacade.getInstance(myArgumentsList.getProject()).getResolveHelper();
        methodSubstitutor2 = calculateMethodSubstitutor(typeParameters2, types2, types1, resolveHelper);
      }
    }
    else {
      methodSubstitutor1 = createRawSubstitutor(typeParameters1);
      methodSubstitutor2 = createRawSubstitutor(typeParameters2);
    }

    int[] boxingHappened = new int[2];
    for (int i = 0; i < types1.length; i++) {
      PsiType type1 = classSubstitutor1.substitute(methodSubstitutor1.substitute(types1[i]));
      PsiType type2 = classSubstitutor2.substitute(methodSubstitutor2.substitute(types2[i]));
      PsiType argType = i < args.length ? args[i].getType() : null;

      boxingHappened[0] += isBoxingHappened(argType, type1) ? 1 : 0;
      boxingHappened[1] += isBoxingHappened(argType, type2) ? 1 : 0;
    }
    if (boxingHappened[0] == 0 && boxingHappened[1] > 0) return Specifics.TRUE;
    if (boxingHappened[0] > 0 && boxingHappened[1] == 0) return Specifics.FALSE;

    Specifics isMoreSpecific = null;
    for (int i = 0; i < types1.length; i++) {
      PsiType type1 = classSubstitutor1.substitute(methodSubstitutor1.substitute(types1[i]));
      PsiType type2 = classSubstitutor2.substitute(methodSubstitutor2.substitute(types2[i]));

      final Specifics specifics = checkSubtyping(type1, type2);
      if (specifics == null) continue;
      switch (specifics) {
        case TRUE:
          if (isMoreSpecific == Specifics.FALSE) return Specifics.CONFLICT;
          isMoreSpecific = specifics;
          break;
        case FALSE:
          if (isMoreSpecific == Specifics.TRUE) return Specifics.CONFLICT;
          isMoreSpecific = specifics;
          break;
        case CONFLICT:
          return Specifics.CONFLICT;
      }
    }

    if (isMoreSpecific == null && class1 != class2) {
      if (class2.isInheritor(class1, true) || class1.isInterface() && !class2.isInterface()) {
        if (MethodSignatureUtil.isSubsignature(method1.getSignature(info1.getSubstitutor()), method2.getSignature(info2.getSubstitutor()))) {
          isMoreSpecific = Specifics.FALSE;
        }
      }
      else if (class1.isInheritor(class2, true) || class2.isInterface()) {
        if (MethodSignatureUtil.isSubsignature(method2.getSignature(info2.getSubstitutor()), method1.getSignature(info1.getSubstitutor()))) {
          isMoreSpecific = Specifics.TRUE;
        }
      }
    }
    if (isMoreSpecific == null) {
      if (typeParameters1.length < typeParameters2.length) return Specifics.TRUE;
      if (typeParameters1.length > typeParameters2.length) return Specifics.FALSE;
      return Specifics.CONFLICT;
    }

    return isMoreSpecific;
  }

  private PsiSubstitutor calculateMethodSubstitutor(final PsiTypeParameter[] typeParameters,
                                                    final PsiType[] types1,
                                                    final PsiType[] types2,
                                                    final PsiResolveHelper resolveHelper) {
    PsiSubstitutor substitutor = resolveHelper.inferTypeArguments(typeParameters, types1, types2, PsiUtil.getLanguageLevel(myArgumentsList));
    for (PsiTypeParameter typeParameter : typeParameters) {
      if (!substitutor.getSubstitutionMap().containsKey(typeParameter)) {
        substitutor = substitutor.put(typeParameter, TypeConversionUtil.typeParameterErasure(typeParameter));
      }
    }
    return substitutor;
  }

  private static PsiSubstitutor createRawSubstitutor(final PsiTypeParameter[] typeParameters) {
    PsiSubstitutor substitutor = PsiSubstitutor.EMPTY;
    for (final PsiTypeParameter typeParameter : typeParameters) {
      substitutor = substitutor.put(typeParameter, null);
    }

    return substitutor;
  }
}
