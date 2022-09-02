package com.crowdin.activity;

import com.crowdin.client.Crowdin;
import com.crowdin.client.CrowdinProjectCacheProvider;
import com.crowdin.client.CrowdinConfiguration;
import com.crowdin.client.CrowdinPropertiesLoader;
import com.crowdin.event.FileChangeListener;
import com.crowdin.util.ActionUtils;
import com.crowdin.util.GitUtil;
import com.crowdin.util.NotificationUtil;
import com.crowdin.util.PropertyUtil;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.startup.StartupActivity;
import org.jetbrains.annotations.NotNull;

public class CrowdinStartupActivity implements StartupActivity {

    @Override
    public void runActivity(@NotNull Project project) {
        try {
            new FileChangeListener(project);
            if (!PropertyUtil.isPropertyFilesExist(project) ) {
                return;
            }
            //config validation
            CrowdinConfiguration[] configurationsCollection = CrowdinPropertiesLoader.loadAll(project);
            if(configurationsCollection.length == 0) {
                return;
            }

            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Crowdin") {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    for (CrowdinConfiguration crowdinConfiguration : configurationsCollection) {
                        try {
                            Crowdin crowdin = new Crowdin(project, crowdinConfiguration.getProjectId(), crowdinConfiguration.getApiToken(), crowdinConfiguration.getBaseUrl());
                            String branchName = ActionUtils.getBranchName(project, crowdinConfiguration, false);

                            indicator.setText("Updating Crowdin cache for configuration: " + crowdinConfiguration.getConfigurationName());
                            CrowdinProjectCacheProvider.getInstance(crowdin, crowdinConfiguration.getConfigurationName(), branchName, true);
                        } catch (Exception e) {
                            NotificationUtil.showErrorMessage(project, e.getMessage());
                        }
                    }
                }
            });
        } catch (Exception e) {
            NotificationUtil.showErrorMessage(project, e.getMessage());
        }
    }
}
