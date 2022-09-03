package com.crowdin.client;

import com.crowdin.logic.ContextLogic;
import com.crowdin.util.*;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.apache.commons.lang.StringUtils;
import org.jetbrains.annotations.NotNull;

import java.util.*;
import java.util.concurrent.ConcurrentHashMap;
import java.util.stream.Collectors;

import static com.crowdin.Constants.*;

public class CrowdinPropertiesLoader {
    private final static ConcurrentHashMap<String, CrowdinConfiguration> translationFiles = new ConcurrentHashMap<>();

    public static CrowdinConfiguration getConfigurationByTransFile(Project project, VirtualFile file) {
        CrowdinConfiguration crowdinConfiguration = translationFiles.get(file.getPath());
        if(translationFiles.size() > 20) {
            translationFiles.clear();
        }
        if (crowdinConfiguration != null) {
            return crowdinConfiguration;
        }

        CrowdinConfiguration[] crowdinConfigurations = CrowdinPropertiesLoader.loadAll(project);
        for (CrowdinConfiguration configuration : crowdinConfigurations) {
            if (isTranslationFile(project, file, configuration)) {
                translationFiles.put(file.getPath(), configuration);
                return configuration;
            }
        }

        return null;
    }

    private static boolean isTranslationFile(Project project, VirtualFile file, CrowdinConfiguration crowdinConfiguration) {
        boolean isTranslationFile = false;
        try {

            NotificationUtil.setLogDebugLevel(crowdinConfiguration.isDebug());
            NotificationUtil.logDebugMessage(project, crowdinConfiguration.getConfigurationName(), MESSAGES_BUNDLE.getString("messages.debug.started_action"));

            VirtualFile root = FileUtil.getProjectBaseDir(project);
            Crowdin crowdin = new Crowdin(project, crowdinConfiguration.getProjectId(), crowdinConfiguration.getApiToken(), crowdinConfiguration.getBaseUrl());

            String branchName = ActionUtils.getBranchName(project, crowdinConfiguration, false);

            CrowdinProjectCacheProvider.CrowdinProjectCache crowdinProjectCache =
                    CrowdinProjectCacheProvider.getInstance(crowdin, crowdinConfiguration.getConfigurationName(), branchName, false);

            isTranslationFile = ContextLogic.findSourceFileFromTranslationFile(file, crowdinConfiguration, root, crowdinProjectCache).isPresent();
        } catch (Exception exception) {
//            do nothing
        }
        return isTranslationFile;
    }

    public static CrowdinConfiguration getConfigurationBySourceFile(Project project, VirtualFile file) {
        CrowdinConfiguration[] crowdinConfigurations = CrowdinPropertiesLoader.loadAll(project);
        CrowdinConfiguration selectedConfig = null;
        try {
            selectedConfig = Arrays.stream(crowdinConfigurations)
                    .filter(
                            c -> c.getFiles()
                                    .stream()
                                    .flatMap(fb -> FileUtil.getSourceFilesRec(FileUtil.getProjectBaseDir(project), fb.getSource()).stream())
                                    .anyMatch(f -> Objects.equals(file, f))
                    )
                    .findFirst()
                    .orElse(null);

        } catch (Exception exception) {
            NotificationUtil.logDebugMessage(project, exception.getMessage());
        }

        return selectedConfig;
    }

    public static boolean isSourceFile(Project project, VirtualFile file) {
        CrowdinConfiguration configuration = getConfigurationBySourceFile(project, file);
        return configuration != null;
    }

    public static @NotNull CrowdinConfiguration[] loadAll(Project project) {
        Properties[] propertiesCollection = PropertyUtil.getPropertiesCollection(project);
        if (propertiesCollection == null) {
            NotificationUtil.showErrorMessage(project, "No configurations was found");
            return new CrowdinConfiguration[0];
        }

        CrowdinConfiguration[] crowdinProperties = new CrowdinConfiguration[propertiesCollection.length];
        int i = 0;
        for (Properties properties : propertiesCollection) {
            crowdinProperties[i++] = load(properties);
        }

        return crowdinProperties;
    }

    /** @deprecated use {@link #loadAll(Project)} */
    @Deprecated
    public static CrowdinConfiguration load(Project project) {
        Properties properties = PropertyUtil.getProperties(project);
        return CrowdinPropertiesLoader.load(properties);
    }

    public static CrowdinConfiguration load(Properties properties) {
        List<String> errors = new ArrayList<>();
        List<String> notExistEnvVars = new ArrayList<>();

        if (properties == null) {
            errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.missing_config_file"), PROPERTIES_FILE));

            throw new RuntimeException(Util.prepareListMessageText(MESSAGES_BUNDLE.getString("errors.config.has_errors"), errors));
        }

        CrowdinConfiguration crowdinConfiguration = new CrowdinConfiguration();
        crowdinConfiguration.setConfigurationName(properties.getProperty(CONFIG_NAME));
        String propProjectId = properties.getProperty(PROJECT_ID);
        String propProjectIdEnv = properties.getProperty(PROJECT_ID_ENV);
        if (StringUtils.isNotEmpty(propProjectId)) {
            try {
                crowdinConfiguration.setProjectId(Long.valueOf(propProjectId));
            } catch (NumberFormatException e) {
                errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.property_is_not_number"), PROJECT_ID));
            }
        } else if (StringUtils.isNotEmpty(propProjectIdEnv)) {
            String propProjectIdEnvValue = System.getenv(propProjectIdEnv);
            if (propProjectIdEnvValue == null) {
                notExistEnvVars.add(propProjectIdEnv);
            } else {
                try {
                    crowdinConfiguration.setProjectId(Long.valueOf(propProjectIdEnvValue));
                } catch (NumberFormatException e) {
                    errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.env_property_is_not_number"), propProjectIdEnv));
                }
            }
        } else {
            errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.missing_property"), PROJECT_ID));
        }
        String propApiToken = properties.getProperty(API_TOKEN);
        String propApiTokenEnv = properties.getProperty(API_TOKEN_ENV);
        if (StringUtils.isNotEmpty(propApiToken)) {
            crowdinConfiguration.setApiToken(propApiToken);
        } else if (StringUtils.isNotEmpty(propApiTokenEnv)) {
            String propApiTokenEnvValue = System.getenv(propApiTokenEnv);
            if (propApiTokenEnvValue != null) {
                crowdinConfiguration.setApiToken(propApiTokenEnvValue);
            } else {
                notExistEnvVars.add(propApiTokenEnv);
            }
        } else {
            errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.missing_property"), API_TOKEN));
        }
        String propBaseUrl = properties.getProperty(BASE_URL);
        String propBaseUrlEnv = properties.getProperty(BASE_URL_ENV);
        if (StringUtils.isNotEmpty(propBaseUrl)) {
            if (isBaseUrlValid(propBaseUrl)) {
                crowdinConfiguration.setBaseUrl(propBaseUrl);
            } else {
                errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.invalid_url_property"), BASE_URL));
            }
        } else if (StringUtils.isNotEmpty(propBaseUrlEnv)) {
            String propBaseUrlEnvValue = System.getenv(propBaseUrlEnv);
            if (propBaseUrlEnvValue == null) {
                notExistEnvVars.add(propBaseUrlEnv);
            } else if (!isBaseUrlValid(propBaseUrlEnvValue)) {
                errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.invalid_url_env"), propBaseUrlEnv, propBaseUrlEnvValue));
            } else {
                crowdinConfiguration.setBaseUrl(propBaseUrlEnvValue);
            }
        }
        if (notExistEnvVars.size() == 1) {
            errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.sysenv_not_exist.single"), notExistEnvVars.get(0)));
        } else if (!notExistEnvVars.isEmpty()) {
            errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.sysenv_not_exist.plural"), StringUtils.join(notExistEnvVars, ", ")));
        }
        String disabledBranches = properties.getProperty(PROPERTY_DISABLE_BRANCHES);
        if (disabledBranches != null) {
            crowdinConfiguration.setDisabledBranches(Boolean.parseBoolean(disabledBranches));
        } else {
            crowdinConfiguration.setDisabledBranches(DISABLE_BRANCHES_DEFAULT);
        }
        String preserveHierarchy = properties.getProperty(PROPERTY_PRESERVE_HIERARCHY);
        if (preserveHierarchy != null) {
            crowdinConfiguration.setPreserveHierarchy(Boolean.parseBoolean(preserveHierarchy));
        } else {
            crowdinConfiguration.setPreserveHierarchy(PRESERVE_HIERARCHY_DEFAULT);
        }
        String debug = properties.getProperty(PROPERTY_DEBUG);
        if (debug != null) {
            crowdinConfiguration.setDebug(Boolean.parseBoolean(debug));
        } else {
            crowdinConfiguration.setDebug(false);
        }
        crowdinConfiguration.setFiles(getFileBeans(properties, errors));

        return crowdinConfiguration;
    }

    private static List<FileBean> getFileBeans(Properties properties, List<String> errors) {
        List<FileBean> fileBeans = getSourcesList(properties);
        fileBeans.addAll(getSourcesWithTranslations(properties, errors));
        if (fileBeans.isEmpty()) {
            FileBean defaultFileBean = new FileBean();
            defaultFileBean.setSource(STANDARD_SOURCE_FILE_PATH);
            defaultFileBean.setTranslation(STANDARD_TRANSLATION_PATTERN);
            fileBeans.add(defaultFileBean);
        }
        List<String> labels = parsePropertyToList(properties.getProperty(PROPERTY_LABELS));
        List<String> excluded_target_languages = parsePropertyToList(properties.getProperty(PROPERTY_EXCLUDED_TARGET_LANGUAGES));
        for (FileBean fb : fileBeans) {
            if (fb.getLabels() == null) {
                fb.setLabels(labels);
            }
            if (fb.getExcludedTargetLanguages() == null) {
                fb.setExcludedTargetLanguages(excluded_target_languages);
            }
        }
        return fileBeans;
    }

    private static List<FileBean> getSourcesList(Properties properties) {
        String sources = properties.getProperty(PROPERTY_SOURCES);
        if (sources == null || StringUtils.isEmpty(sources)) {
            return new ArrayList<>();
        }
        return Arrays.stream(sources.split(","))
            .map(String::trim)
            .filter(StringUtils::isNotEmpty)
            .map(s -> STANDARD_SOURCE_PATH + s)
            .map(source -> {
                FileBean fb = new FileBean();
                fb.setSource(source);
                fb.setTranslation(STANDARD_TRANSLATION_PATTERN);
                return fb;
            })
            .collect(Collectors.toList());
    }

    private static List<FileBean> getSourcesWithTranslations(Properties properties, List<String> errors) {
        List<FileBean> fileBeans = new ArrayList<>();

        List<String> foundKeys = properties.keySet().stream()
            .map(key -> (String) key)
            .filter(PROPERTY_FILES_SOURCES_REGEX.asPredicate()
                .or(PROPERTY_FILES_TRANSLATIONS_REGEX.asPredicate()))
            .map(key -> StringUtils.removeStart(key, "files."))
            .map(key -> StringUtils.removeEnd(key, "source"))
            .map(key -> StringUtils.removeEnd(key, "translation"))
            .distinct()
            .sorted()
            .collect(Collectors.toList());

        for (String ident : foundKeys) {
            String source = properties.getProperty(String.format(PROPERTY_FILES_SOURCES_PATTERN, ident));
            String translation = properties.getProperty(String.format(PROPERTY_FILES_TRANSLATIONS_PATTERN, ident));
            List<String> labels = parsePropertyToList(properties.getProperty(String.format(PROPERTY_FILES_LABELS_PATTERN, ident)));
            List<String> excludedTargetLanguages = parsePropertyToList(properties.getProperty(String.format(PROPERTY_FILES_EXCLUDED_TARGET_LANGUAGES_PATTERN, ident)));
            if (StringUtils.isNotEmpty(source) && StringUtils.isNotEmpty(translation)) {
                FileBean fb = new FileBean();
                fb.setSource(FileUtil.noSepAtStart(FileUtil.unixPath(source)));
                fb.setTranslation(FileUtil.unixPath(translation));
                fb.setLabels(labels);
                fb.setExcludedTargetLanguages(excludedTargetLanguages);
                fileBeans.add(fb);
            } else if (StringUtils.isEmpty(translation)) {
                errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.missing_property"), String.format(PROPERTY_FILES_TRANSLATIONS_PATTERN, ident)));
            } else if (StringUtils.isEmpty(source)) {
                errors.add(String.format(MESSAGES_BUNDLE.getString("errors.config.missing_property"), String.format(PROPERTY_FILES_SOURCES_PATTERN, ident)));
            }
        }
        return fileBeans;
    }

    private static List<String> parsePropertyToList(String property) {
        if (StringUtils.isEmpty(property)) {
            return null;
        }
        List<String> parsedProperty = new ArrayList<>();
        for (String part : property.split(",")) {
            part = part.trim();
            if (StringUtils.isNotEmpty(part)) {
                parsedProperty.add(part);
            }
        }
        return parsedProperty;
    }

    protected static boolean isBaseUrlValid(String baseUrl) {
        return BASE_URL_PATTERN.matcher(baseUrl).matches();
    }
}
