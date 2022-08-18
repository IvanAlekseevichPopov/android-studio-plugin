package com.crowdin.client;

import lombok.Data;

import java.util.List;

@Data
public class CrowdinConfiguration {

    private Long projectId;
    private String apiToken;
    private String baseUrl;
    private boolean disabledBranches;
    private boolean preserveHierarchy;
    private List<FileBean> files;
    private boolean debug;
}
