package com.crowdin.action;

import com.crowdin.client.*;
import com.crowdin.client.languages.model.Language;
import com.crowdin.client.sourcefiles.model.Branch;
import com.crowdin.logic.BranchLogic;
import com.crowdin.logic.ContextLogic;
import com.crowdin.logic.CrowdinSettings;
import com.crowdin.util.FileUtil;
import com.crowdin.util.NotificationUtil;
import com.crowdin.util.UIUtil;
import com.intellij.openapi.actionSystem.AnActionEvent;
import com.intellij.openapi.actionSystem.CommonDataKeys;
import com.intellij.openapi.components.ServiceManager;
import com.intellij.openapi.progress.ProcessCanceledException;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import lombok.NonNull;
import org.apache.commons.lang3.tuple.Pair;

import java.net.URL;

import static com.crowdin.Constants.MESSAGES_BUNDLE;

public class DownloadTranslationFromContextAction extends BackgroundAction {
    @Override
    protected void performInBackground(@NonNull AnActionEvent anActionEvent, @NonNull ProgressIndicator indicator) {
        final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(anActionEvent.getDataContext());
        if (file == null) {
            return;
        }
        Project project = anActionEvent.getProject();
        try {
            VirtualFile root = FileUtil.getProjectBaseDir(project);

            CrowdinSettings crowdinSettings = ServiceManager.getService(project, CrowdinSettings.class);

            boolean confirmation = UIUtil.confirmDialog(project, crowdinSettings, MESSAGES_BUNDLE.getString("messages.confirm.download"), "Download");
            if (!confirmation) {
                return;
            }
            indicator.checkCanceled();

            CrowdinConfiguration crowdinConfiguration = CrowdinPropertiesLoader.getConfigurationByTransFile(project, file);
            if (crowdinConfiguration == null) {
                return;
            }
            NotificationUtil.setLogDebugLevel(crowdinConfiguration.isDebug());
            NotificationUtil.logDebugMessage(project, MESSAGES_BUNDLE.getString("messages.debug.started_action"));

            Crowdin crowdin = new Crowdin(project, crowdinConfiguration.getProjectId(), crowdinConfiguration.getApiToken(), crowdinConfiguration.getBaseUrl());

            BranchLogic branchLogic = new BranchLogic(crowdin, project, crowdinConfiguration);
            String branchName = branchLogic.acquireBranchName(true);
            indicator.checkCanceled();

            CrowdinProjectCacheProvider.CrowdinProjectCache crowdinProjectCache =
               CrowdinProjectCacheProvider.getInstance(crowdin, crowdinConfiguration.getConfigurationName(), branchName, true);

            if (!crowdinProjectCache.isManagerAccess()) {
                NotificationUtil.showErrorMessage(project, "You need to have manager access to perform this action");
                return;
            }

            Branch branch = branchLogic.getBranch(crowdinProjectCache, false);

            Pair<VirtualFile, Language> source = ContextLogic.findSourceFileFromTranslationFile(file, crowdinConfiguration, root, crowdinProjectCache)
                .orElseThrow(() -> new RuntimeException(MESSAGES_BUNDLE.getString("errors.file_no_representative_context")));

            Long sourceId = ContextLogic.findSourceIdFromSourceFile(crowdinConfiguration, crowdinProjectCache.getFileInfos(branch), source.getLeft(), root);

            URL url = crowdin.downloadFileTranslation(sourceId, RequestBuilder.buildProjectFileTranslation(source.getRight().getId()));
            FileUtil.downloadFile(this, file, url);
        } catch (ProcessCanceledException e) {
            throw e;
        } catch (Exception e) {
            NotificationUtil.logErrorMessage(project, e);
            NotificationUtil.showErrorMessage(project, e.getMessage());
        }
    }

    @Override
    public void update(AnActionEvent e) {
        Project project = e.getProject();
        final VirtualFile file = CommonDataKeys.VIRTUAL_FILE.getData(e.getDataContext());
        if (file == null) {
            return;
        }

        boolean isTranslationFile = CrowdinPropertiesLoader.getConfigurationByTransFile(project, file) != null;

        e.getPresentation().setEnabled(isTranslationFile);
        e.getPresentation().setVisible(isTranslationFile);
    }

    @Override
    protected String loadingText(AnActionEvent e) {
        return MESSAGES_BUNDLE.getString("labels.loading_text.download");
    }
}
