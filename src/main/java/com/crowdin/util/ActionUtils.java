package com.crowdin.util;

import com.crowdin.client.CrowdinConfiguration;
import com.intellij.openapi.project.Project;

import static com.crowdin.Constants.MESSAGES_BUNDLE;

public class ActionUtils {

    public static String getBranchName(Project project, CrowdinConfiguration crowdinConfiguration, boolean performCheck) {
        String branchName = crowdinConfiguration.isDisabledBranches() ? "" : GitUtil.getCurrentBranch(project);
        if (performCheck) {
            if (!CrowdinFileUtil.isValidBranchName(branchName)) {
                throw new RuntimeException(MESSAGES_BUNDLE.getString("errors.branch_contains_forbidden_symbols"));
            }
        }
        return branchName;
    }
}
