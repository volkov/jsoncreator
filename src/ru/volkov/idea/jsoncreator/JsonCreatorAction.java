package ru.volkov.idea.jsoncreator;

import com.intellij.codeInsight.generation.actions.BaseGenerateAction;
import com.intellij.psi.PsiAnonymousClass;
import com.intellij.psi.PsiClass;

/**
 * User: serg-v
 * Date: 12/15/14
 * Time: 3:07 AM
 */
public class JsonCreatorAction extends BaseGenerateAction {

  public JsonCreatorAction() {
    super(new JsonCreatorHandler());
  }

  @Override
  protected boolean isValidForClass(final PsiClass targetClass) {
    return super.isValidForClass(targetClass) && !(targetClass instanceof PsiAnonymousClass);
  }

}
