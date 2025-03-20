package de.keksuccino.promptgenerator.action;

import de.keksuccino.promptgenerator.ui.PromptGeneratorDialog;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnAction;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiFile;
import org.jetbrains.annotations.NotNull;

public class GeneratePromptAction extends AnAction {

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        // This specifies that update() should run in a background thread
        return ActionUpdateThread.BGT;
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        if (project != null && psiFile != null) {
            PromptGeneratorDialog dialog = new PromptGeneratorDialog(project, psiFile);
            dialog.show();
        }
    }

    // GeneratePromptAction.java update
    @Override
    public void update(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);

        // Enable for all files, not just Java files
        e.getPresentation().setEnabledAndVisible(project != null && psiFile != null);
    }

}