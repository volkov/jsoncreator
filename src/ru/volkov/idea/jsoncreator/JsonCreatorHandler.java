/*
 * Copyright 2000-2009 JetBrains s.r.o.
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 * http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package ru.volkov.idea.jsoncreator;

import com.intellij.openapi.project.Project;
import com.intellij.util.containers.ContainerUtil;
import org.jetbrains.annotations.NonNls;
import org.jetbrains.annotations.NotNull;

import java.util.ArrayList;
import java.util.Collections;
import java.util.List;


import com.intellij.codeInsight.NullableNotNullManager;
import com.intellij.codeInsight.generation.*;
import com.intellij.openapi.diagnostic.Logger;
import com.intellij.psi.*;
import com.intellij.psi.codeStyle.CodeStyleManager;
import com.intellij.psi.codeStyle.JavaCodeStyleManager;
import com.intellij.psi.codeStyle.VariableKind;
import com.intellij.psi.javadoc.PsiDocComment;
import com.intellij.psi.util.TypeConversionUtil;
import com.intellij.util.IncorrectOperationException;

/**
 * Some static code copied from GenerateConstructorHandler.
 * User: serg-v
 * Date: 12/15/14
 * Time: 3:12 AM
 */
public class JsonCreatorHandler extends GenerateConstructorHandler {

  private static final Logger LOG = Logger.getInstance("#ru.volkov.idea.jsoncreator.JsonCreatorHandler");

  @Override
  @NotNull
  protected List<? extends GenerationInfo> generateMemberPrototypes(PsiClass aClass, ClassMember[] members) throws IncorrectOperationException {
    List<PsiMethod> baseConstructors = new ArrayList<>();
    List<PsiField> fieldsVector = new ArrayList<>();
    for (ClassMember member1 : members) {
      PsiElement member = ((PsiElementClassMember)member1).getElement();
      if (member instanceof PsiMethod) {
        baseConstructors.add((PsiMethod)member);
      }
      else {
        fieldsVector.add((PsiField)member);
      }
    }
    PsiField[] fields = fieldsVector.toArray(new PsiField[fieldsVector.size()]);

    if (!baseConstructors.isEmpty()) {
      List<GenerationInfo> constructors = new ArrayList<>(baseConstructors.size());
      final PsiClass superClass = aClass.getSuperClass();
      assert superClass != null;
      PsiSubstitutor substitutor = TypeConversionUtil.getSuperClassSubstitutor(superClass, aClass, PsiSubstitutor.EMPTY);
      for (PsiMethod baseConstructor : baseConstructors) {
        baseConstructor = GenerateMembersUtil.substituteGenericMethod(baseConstructor, substitutor, aClass);
        constructors.add(new PsiGenerationInfo<>(generateConstructorPrototype(aClass, baseConstructor, false, fields)));
      }
      return filterOutAlreadyInsertedConstructors(aClass, constructors);
    }
    final List<GenerationInfo> constructors =
            Collections.<GenerationInfo>singletonList(new PsiGenerationInfo<>(generateConstructorPrototype(aClass, null, false, fields)));
    return filterOutAlreadyInsertedConstructors(aClass, constructors);
  }


  private static List<? extends GenerationInfo> filterOutAlreadyInsertedConstructors(PsiClass aClass,
                                                                                     List<? extends GenerationInfo> constructors)
  {
    boolean alreadyExist = true;
    for (GenerationInfo constructor : constructors) {
      alreadyExist &= aClass.findMethodBySignature((PsiMethod) constructor.getPsiMember(), false) != null;
    }
    if (alreadyExist) {
      return Collections.emptyList();
    }
    return constructors;
  }

  public static PsiMethod generateConstructorPrototype(PsiClass aClass,
                                                       PsiMethod baseConstructor,
                                                       boolean copyJavaDoc,
                                                       PsiField[] fields) throws IncorrectOperationException {
    PsiManager manager = aClass.getManager();
    Project project = aClass.getProject();
    JVMElementFactory factory = JVMElementFactories.requireFactory(aClass.getLanguage(), project);
    CodeStyleManager codeStyleManager = CodeStyleManager.getInstance(manager.getProject());

    String className = aClass.getName();
    assert className != null : aClass;
    PsiMethod constructor = factory.createConstructor(className, aClass);
    constructor.getModifierList().addAnnotation("com.fasterxml.jackson.annotation.JsonCreator");

    GenerateMembersUtil.setVisibility(aClass, constructor);

    if (baseConstructor != null) {
      PsiJavaCodeReferenceElement[] throwRefs = baseConstructor.getThrowsList().getReferenceElements();
      for (PsiJavaCodeReferenceElement ref : throwRefs) {
        constructor.getThrowsList().add(ref);
      }

      if (copyJavaDoc) {
        final PsiDocComment docComment = ((PsiMethod)baseConstructor.getNavigationElement()).getDocComment();
        if (docComment != null) {
          constructor.addAfter(docComment, null);
        }
      }
    }

    boolean isNotEnum = false;
    if (baseConstructor != null) {
      PsiClass superClass = aClass.getSuperClass();
      LOG.assertTrue(superClass != null);
      if (!CommonClassNames.JAVA_LANG_ENUM.equals(superClass.getQualifiedName())) {
        isNotEnum = true;
        if (baseConstructor instanceof PsiCompiledElement) { // to get some parameter names
          PsiClass dummyClass = JVMElementFactories.requireFactory(baseConstructor.getLanguage(), project).createClass("Dummy");
          baseConstructor = (PsiMethod)dummyClass.add(baseConstructor);
        }
        PsiParameter[] params = baseConstructor.getParameterList().getParameters();
        for (PsiParameter param : params) {
          String name = param.getName();
          assert name != null : param;
          PsiParameter newParam = factory.createParameter(name, param.getType(), aClass);
          GenerateMembersUtil.copyOrReplaceModifierList(param, newParam);
          constructor.getParameterList().add(newParam);
        }
      }
    }

    JavaCodeStyleManager javaStyle = JavaCodeStyleManager.getInstance(project);

    final PsiMethod dummyConstructor = factory.createConstructor(className);
    dummyConstructor.getParameterList().replace(constructor.getParameterList().copy());
    List<PsiParameter> fieldParams = new ArrayList<>();
    for (PsiField field : fields) {
      String fieldName = field.getName();
      assert fieldName != null : field;
      String name = javaStyle.variableNameToPropertyName(fieldName, VariableKind.FIELD);
      String parmName = javaStyle.propertyNameToVariableName(name, VariableKind.PARAMETER);
      parmName = javaStyle.suggestUniqueVariableName(parmName, dummyConstructor, true);
      PsiParameter parm = factory.createParameter(parmName, field.getType(), aClass);

      NullableNotNullManager.getInstance(project).copyNotNullAnnotation(field, parm);

      final PsiAnnotation annotation = parm.getModifierList().addAnnotation("com.fasterxml.jackson.annotation.JsonProperty");
      PsiAnnotationSupport support = LanguageAnnotationSupport.INSTANCE.forLanguage(annotation.getLanguage());
      annotation.setDeclaredAttributeValue("value", support.createLiteralValue(name, annotation));


      if (constructor.isVarArgs()) {
        final PsiParameterList parameterList = constructor.getParameterList();
        parameterList.addBefore(parm, parameterList.getParameters()[parameterList.getParametersCount() - 1]);
        final PsiParameterList dummyParameterList = dummyConstructor.getParameterList();
        dummyParameterList.addBefore(parm.copy(), dummyParameterList.getParameters()[dummyParameterList.getParametersCount() - 1]);
      }
      else {
        constructor.getParameterList().add(parm);
        dummyConstructor.getParameterList().add(parm.copy());
      }

      fieldParams.add(parm);
    }

    ConstructorBodyGenerator generator = ConstructorBodyGenerator.INSTANCE.forLanguage(aClass.getLanguage());
    if (generator != null) {
      StringBuilder buffer = new StringBuilder();
      generator.start(buffer, constructor.getName(), PsiParameter.EMPTY_ARRAY);
      if (isNotEnum) {
        generator.generateSuperCallIfNeeded(buffer, baseConstructor.getParameterList().getParameters());
      }
      final PsiParameter[] parameters = fieldParams.toArray(new PsiParameter[fieldParams.size()]);
      final List<String> existingNames = ContainerUtil.map(dummyConstructor.getParameterList().getParameters(), parameter -> parameter.getName());
      if (generator instanceof ConstructorBodyGeneratorEx) {
        ((ConstructorBodyGeneratorEx)generator).generateFieldInitialization(buffer, fields, parameters, existingNames);
      }
      else {
        generator.generateFieldInitialization(buffer, fields, parameters);
      }
      generator.finish(buffer);
      PsiMethod stub = factory.createMethodFromText(buffer.toString(), aClass);
      PsiCodeBlock original = constructor.getBody(), replacement = stub.getBody();
      assert original != null : constructor;
      assert replacement != null : stub;
      original.replace(replacement);
    }

    constructor = (PsiMethod)codeStyleManager.reformat(constructor);
    return constructor;
  }


}
