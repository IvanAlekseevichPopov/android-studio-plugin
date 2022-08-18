package com.crowdin.logic;

import com.crowdin.client.Crowdin;
import com.crowdin.client.CrowdinConfiguration;
import com.crowdin.client.CrowdinProjectCacheProvider;
import com.crowdin.client.RequestBuilder;
import com.crowdin.client.sourcefiles.model.AddBranchRequest;
import com.crowdin.client.sourcefiles.model.Branch;
import com.crowdin.util.CrowdinFileUtil;
import com.crowdin.util.GitUtil;
import com.crowdin.util.NotificationUtil;
import com.intellij.openapi.project.Project;
import org.apache.commons.lang.StringUtils;

import static com.crowdin.Constants.MESSAGES_BUNDLE;

public class BranchLogic {

    private final Crowdin crowdin;
    private final Project project;
    private final CrowdinConfiguration crowdinConfiguration;

    private String branchName;

    public BranchLogic(Crowdin crowdin, Project project, CrowdinConfiguration crowdinConfiguration) {
        this.crowdin = crowdin;
        this.project = project;
        this.crowdinConfiguration = crowdinConfiguration;
    }

    public String acquireBranchName(boolean performCheck) {
        String branchName = crowdinConfiguration.isDisabledBranches() ? "" : GitUtil.getCurrentBranch(project);
        if (performCheck) {
            if (!CrowdinFileUtil.isValidBranchName(branchName)) {
                throw new RuntimeException(MESSAGES_BUNDLE.getString("errors.branch_contains_forbidden_symbols"));
            }
        }
        this.branchName = branchName;
        return branchName;
    }

    public Branch getBranch(CrowdinProjectCacheProvider.CrowdinProjectCache projectCache, boolean createIfNotExists) {
        if (branchName == null) {
            this.acquireBranchName(true);
        }
        Branch branch = projectCache.getBranches().get(branchName);
        if (branch == null && StringUtils.isNotEmpty(branchName)) {
            if (createIfNotExists) {
                AddBranchRequest addBranchRequest = RequestBuilder.addBranch(branchName);
                branch = crowdin.addBranch(addBranchRequest);
                NotificationUtil.logDebugMessage(project, String.format(MESSAGES_BUNDLE.getString("messages.debug.created_branch"), branch.getId(), branch.getName()));
            } else {
                throw new RuntimeException(String.format(MESSAGES_BUNDLE.getString("errors.branch_not_exists"), branchName));
            }
        } else if (branch != null) {
            NotificationUtil.logDebugMessage(project, String.format(MESSAGES_BUNDLE.getString("messages.debug.using_branch"), branch.getId(), branch.getName()));
        }
        return branch;
    }
}
