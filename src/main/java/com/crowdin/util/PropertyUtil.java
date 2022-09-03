package com.crowdin.util;

import com.crowdin.Constants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import org.jetbrains.annotations.NotNull;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Pattern;

import static com.crowdin.Constants.PROPERTIES_FILE_PATTERN;

public class PropertyUtil {

    public static String getPropertyValue(String key, Project project, @NotNull String configurationName) {
        if (key == null) {
            return "";
        }
        Properties properties = getPropertiesByConfiguration(project, configurationName);
        if (properties != null && properties.get(key) != null) {
            return properties.get(key).toString();
        } else {
            return "";
        }
    }

    private static Properties getPropertiesByConfiguration(Project project, String configurationName) {
        Properties[] propertiesCollection = getPropertiesCollection(project);
        for (Properties properties : propertiesCollection) {
            if (properties.get(Constants.CONFIG_NAME).equals(configurationName)) {
                return properties;
            }
        }

        return null;
    }

    public static @NotNull Properties[] getPropertiesCollection(Project project) {
        ArrayList<VirtualFile> propertiesFiles = getCrowdinPropertyFiles(project);
        if (propertiesFiles == null) {
            return new Properties[0];
        }

        Properties[] propertiesCollection = new Properties[propertiesFiles.size()];
        int i = 0;
        for (VirtualFile propFile : propertiesFiles) {
            try (InputStream in = propFile.getInputStream()) {
                Properties properties = new Properties();
                properties.load(in);
                properties.put(
                        Constants.CONFIG_NAME,
                        propFile
                                .getName()
                                .replace("crowdin.", "")
                                .replace(".properties", "")
                );
                propertiesCollection[i++] = properties;
            } catch (Exception e) {
                e.printStackTrace();
            }
        }
        return propertiesCollection;
    }

    public static boolean isPropertyFilesExist(Project project) {
        ArrayList<VirtualFile> crowdinPropertyFiles = getCrowdinPropertyFiles(project);
        if (crowdinPropertyFiles == null) {
            return false;
        }

        return crowdinPropertyFiles.size() != 0;
    }

    public static ArrayList<VirtualFile> getCrowdinPropertyFiles(Project project) {
        VirtualFile baseDir = FileUtil.getProjectBaseDir(project);
        if (baseDir == null || !baseDir.isDirectory()) {
            System.out.println("Base dir not exist");
            return null;
        }

        VirtualFile[] allChildren = baseDir.getChildren();
        if (allChildren == null) {
            return null;
        }
        final Pattern pattern = Pattern.compile(PROPERTIES_FILE_PATTERN);
        ArrayList<VirtualFile> configurationFiles = new ArrayList<>();
        for (VirtualFile child : allChildren) {
            if (!child.isDirectory() && pattern.matcher(child.getName()).matches()) {
                configurationFiles.add(child);
            }
        }
        if (configurationFiles.size() > 0) {
            return configurationFiles;
        }
        return null;
    }
}
