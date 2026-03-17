package com.clu.idea.utils;

import com.clu.idea.MyPluginException;
import com.intellij.notification.NotificationGroup;
import com.intellij.notification.NotificationGroupManager;
import com.intellij.notification.NotificationType;
import com.intellij.openapi.actionSystem.ActionUpdateThread;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.ide.CopyPasteManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.DumbAwareAction;
import com.intellij.openapi.project.Project;
import com.intellij.psi.PsiClassType;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiType;
import org.jetbrains.annotations.NotNull;

import java.awt.datatransfer.StringSelection;
import java.io.IOException;

import static com.clu.idea.utils.MyPojoToJsonCore.GSON;

public class MyPojoToJsonAction extends DumbAwareAction {

    private static final String NOTIFICATION_GROUP_ID = "MyPojoToJson";

    @Override
    public @NotNull ActionUpdateThread getActionUpdateThread() {
        return ActionUpdateThread.BGT;
    }

    @Override
    public void update(@NotNull AnActionEvent e) {
        PsiFile psiFile = e.getData(CommonDataKeys.PSI_FILE);
        if (!(psiFile instanceof PsiJavaFile)) {
            e.getPresentation().setEnabledAndVisible(false);
            return;
        }

        PsiType psiType = MyPojoToJsonCore.checkAndGetPsiType(e.getDataContext());
        boolean enabled = psiType != null;
        e.getPresentation().setEnabledAndVisible(enabled);
    }

    @Override
    public void actionPerformed(@NotNull AnActionEvent e) {
        Project project = e.getProject();
        if (project == null) {
            return;
        }

        PsiClassType psiType = MyPojoToJsonCore.checkAndGetPsiType(e.getDataContext());
        if (psiType == null) {
            return;
        }

        String className = MyPojoToJsonCore.getClassName(psiType);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Converting " + className + " to JSON...") {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                indicator.setFraction(0.1);
                indicator.setText("90% to finish");
                ProcessingInfo processingInfo = new ProcessingInfo().setProject(project).setProgressIndicator(indicator);
                try {
                    ReadAction.run(() -> {
                        Object result = MyPojoToJsonCore.resolveType(psiType, processingInfo);
                        processingInfo.setResultIfAbsent(result);
                    });
                } catch (ProcessCanceledException ignore) {
                    return;
                } finally {
                    indicator.setFraction(1.0);
                    indicator.setText("finished");
                    indicator.cancel();
                }

                Object result = processingInfo.getResult();
                if (result == null) {
                    return;
                }

                String json = GSON.toJson(result);
                try {
                    json = MyPojoToJsonCore.myFormat(json);
                } catch (IOException ex) {
                    notifyUser(project, NotificationType.ERROR, "Convert to JSON failed: " + ex.getMessage());
                    throw new MyPluginException("Error", ex);
                }

                CopyPasteManager.getInstance().setContents(new StringSelection(json));
                notifyUser(project, NotificationType.INFORMATION,
                        "Convert " + className + " to JSON success, copied to clipboard.");
            }
        });
    }

    private static void notifyUser(Project project, NotificationType type, String message) {
        NotificationGroup group = NotificationGroupManager.getInstance().getNotificationGroup(NOTIFICATION_GROUP_ID);
        group.createNotification(message, type).notify(project);
    }
}
