package de.espend.idea.shopware.navigation;

import com.intellij.codeInsight.navigation.actions.GotoDeclarationHandler;
import com.intellij.openapi.actionSystem.DataContext;
import com.intellij.openapi.editor.Editor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiManager;
import com.jetbrains.php.lang.psi.elements.Method;
import com.jetbrains.php.lang.psi.elements.PhpClass;
import com.jetbrains.smarty.SmartyFile;
import de.espend.idea.shopware.ShopwareProjectComponent;
import de.espend.idea.shopware.util.ShopwareUtil;
import de.espend.idea.shopware.util.SmartyPattern;
import de.espend.idea.shopware.util.TemplateUtil;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

/**
 * @author Daniel Espendiller <daniel@espendiller.net>
 */
public class SmartyFileGoToDeclarationHandler implements GotoDeclarationHandler {

    @Nullable
    @Override
    public PsiElement[] getGotoDeclarationTargets(PsiElement sourceElement, int offset, Editor editor) {

        if(!ShopwareProjectComponent.isValidForProject(sourceElement)) {
            return new PsiElement[0];
        }

        final List<PsiElement> targets = new ArrayList<PsiElement>();

        // {link file='frontend/_resources/styles/framework.css'}
        if(SmartyPattern.getLinkFilePattern().accepts(sourceElement)) {
            attachLinkFileTagGoto(sourceElement, targets);
        }

        // {extends file="frontend/register/index.tpl"}
        if(SmartyPattern.getFilePattern().accepts(sourceElement)) {
            attachExtendsFileGoto(sourceElement, targets);
        }

        // {url controller=Account
        if(SmartyPattern.getControllerPattern().accepts(sourceElement)) {
            attachControllerNameGoto(sourceElement, targets);
        }

        // {url controller=Account action=foobar
        if(SmartyPattern.getControllerActionPattern().accepts(sourceElement)) {
            attachControllerActionNameGoto(sourceElement, targets);
        }

        // {$foobar
        if(SmartyPattern.getVariableReference().accepts(sourceElement)) {
            attachControllerVariableGoto(sourceElement, targets);
        }

        // {s namespace=frontend/foo
        if(SmartyPattern.getNamespacePattern().accepts(sourceElement)) {
            attachNamespaceTagGoto(sourceElement, targets);
        }

        // {action controller=Account
        if(SmartyPattern.getControllerPattern("action").accepts(sourceElement)) {
            attachWidgetControllerNameGoto(sourceElement, targets);
        }

        // {action controller=Account action=foobar
        if(SmartyPattern.getControllerActionPattern("action").accepts(sourceElement)) {
            attachWidgetsControllerActionNameGoto(sourceElement, targets);
        }

        return targets.toArray(new PsiElement[targets.size()]);
    }

    private void attachControllerVariableGoto(PsiElement sourceElement, final List<PsiElement> psiElements) {

        final String finalText = normalizeFilename(sourceElement.getText());

        PsiFile psiFile = sourceElement.getContainingFile();
        if(!(psiFile instanceof SmartyFile)) {
            return;
        }

        Method method = ShopwareUtil.getControllerActionOnSmartyFile((SmartyFile) psiFile);
        if(method == null) {
            return;
        }

        ShopwareUtil.collectControllerViewVariable(method, new ShopwareUtil.ControllerViewVariableVisitor() {

            @Override
            public void visitVariable(String variableName, @NotNull PsiElement sourceType, @Nullable PsiElement typeElement) {
                if (variableName.equals(finalText)) {
                    psiElements.add(typeElement != null ? typeElement : sourceType);
                }
            }

        });

    }

    private void attachControllerNameGoto(PsiElement sourceElement, final List<PsiElement> psiElements) {
        final Project project = sourceElement.getProject();

        final String finalText = normalizeFilename(sourceElement.getText()).toLowerCase();
        ShopwareUtil.collectControllerClass(project, new ShopwareUtil.ControllerClassVisitor() {
            @Override
            public void visitClass(PhpClass phpClass, String moduleName, String controllerName) {
                if (controllerName.toLowerCase().equals(finalText)) {
                    psiElements.add(phpClass);
                }
            }
        });

    }

    private void attachWidgetControllerNameGoto(PsiElement sourceElement, final List<PsiElement> psiElements) {
        final Project project = sourceElement.getProject();

        final String finalText = normalizeFilename(sourceElement.getText()).toLowerCase();
        ShopwareUtil.collectControllerClass(project, new ShopwareUtil.ControllerClassVisitor() {
            @Override
            public void visitClass(PhpClass phpClass, String moduleName, String controllerName) {
                if (controllerName.toLowerCase().equals(finalText)) {
                    psiElements.add(phpClass);
                }
            }
        }, "Widgets");

    }

    private void attachControllerActionNameGoto(PsiElement sourceElement, final List<PsiElement> psiElements) {

        final String finalText = normalizeFilename(sourceElement.getText()).toLowerCase();
        ShopwareUtil.collectControllerActionSmartyWrapper(sourceElement, new ShopwareUtil.ControllerActionVisitor() {
            @Override
            public void visitMethod(Method method, String methodStripped, String moduleName, String controllerName) {
                if(methodStripped.toLowerCase().equals(finalText)) {
                    psiElements.add(method);
                }
            }
        });

    }

    private void attachWidgetsControllerActionNameGoto(PsiElement sourceElement, final List<PsiElement> psiElements) {

        final String finalText = normalizeFilename(sourceElement.getText()).toLowerCase();
        ShopwareUtil.collectControllerActionSmartyWrapper(sourceElement, new ShopwareUtil.ControllerActionVisitor() {
            @Override
            public void visitMethod(Method method, String methodStripped, String moduleName, String controllerName) {
                if(methodStripped.toLowerCase().equals(finalText)) {
                    psiElements.add(method);
                }
            }
        }, "Widgets");

    }

    private void attachExtendsFileGoto(PsiElement sourceElement, final List<PsiElement> psiElements) {

        final Project project = sourceElement.getProject();
        final VirtualFile currentFile = sourceElement.getContainingFile().getVirtualFile();

        final String finalText = normalizeFilename(sourceElement.getText());
        TemplateUtil.collectFiles(project, new TemplateUtil.SmartyTemplatePreventSelfVisitor(currentFile) {
            @Override
            public void visitNonSelfFile(VirtualFile virtualFile, String fileName) {

                if (!fileName.equals(finalText)) {
                    return;
                }

                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile != null) {
                    psiElements.add(psiFile);
                }

            }
        });

    }

    private void attachNamespaceTagGoto(PsiElement sourceElement, final List<PsiElement> psiElements) {

        final Project project = sourceElement.getProject();

        final String finalText = normalizeFilename(sourceElement.getText());
        TemplateUtil.collectFiles(sourceElement.getProject(), new TemplateUtil.SmartyTemplateVisitor() {
            @Override
            public void visitFile(VirtualFile virtualFile, String fileName) {

                if (!fileName.replaceFirst("[.][^.]+$", "").equals(finalText)) {
                    return;
                }

                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile != null) {
                    psiElements.add(psiFile);
                }
            }
        }, "tpl");

    }

    private void attachLinkFileTagGoto(PsiElement sourceElement, final List<PsiElement> psiElements) {

        final Project project = sourceElement.getProject();

        final String finalText = normalizeFilename(sourceElement.getText());
        TemplateUtil.collectFiles(sourceElement.getProject(), new TemplateUtil.SmartyTemplateVisitor() {
            @Override
            public void visitFile(VirtualFile virtualFile, String fileName) {

                if (!fileName.equals(finalText)) {
                    return;
                }

                PsiFile psiFile = PsiManager.getInstance(project).findFile(virtualFile);
                if (psiFile != null) {
                    psiElements.add(psiFile);
                }
            }
        }, SmartyPattern.TAG_LINK_FILE_EXTENSIONS);


    }

    @Nullable
    @Override
    public String getActionText(DataContext context) {
        return null;
    }

    private String normalizeFilename(String text) {

        if(text.startsWith("parent:")) {
            text = text.substring(7);
        }

        if(text.startsWith("./")) {
            text = text.substring(2);
        }

        return text;
    }

}
