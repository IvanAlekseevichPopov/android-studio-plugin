package com.crowdin.util;

import com.crowdin.Constants;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;

import java.io.InputStream;
import java.util.ArrayList;
import java.util.Properties;
import java.util.regex.Pattern;

import static com.crowdin.Constants.PROPERTIES_FILE;
import static com.crowdin.Constants.PROPERTIES_FILE_PATTERN;

public class PropertyUtil {

    public static String getPropertyValue(String key, Project project) {
        if (key == null) {
            return "";
        }
        Properties properties = getProperties(project);
        if (properties != null && properties.get(key) != null) {
            return properties.get(key).toString();
        } else {
            return "";
        }
    }

    public static Properties[] getPropertiesCollection(Project project) {
        ArrayList<VirtualFile> propertiesFiles = getCrowdinPropertyFiles(project);
        if (propertiesFiles == null) {
            return null;
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

    @Deprecated
    public static Properties getProperties(Project project) {
        Properties properties = new Properties();
        VirtualFile propertiesFile = getCrowdinPropertyFile(project);
        if (propertiesFile == null) {
            return null;
        }
        try (InputStream in = propertiesFile.getInputStream()) {
            properties.load(in);
        } catch (Exception e) {
            e.printStackTrace();
        }
        return properties;
    }

    public static boolean isPropertyFilesExist(Project project)
    {
        ArrayList<VirtualFile> crowdinPropertyFiles = getCrowdinPropertyFiles(project);
        if(crowdinPropertyFiles == null) {
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

    @Deprecated
    public static VirtualFile getCrowdinPropertyFile(Project project) {
        VirtualFile baseDir = FileUtil.getProjectBaseDir(project);
        if (baseDir == null || !baseDir.isDirectory()) {
            System.out.println("Base dir not exist");
            return null;
        }
        VirtualFile child = baseDir.findChild(PROPERTIES_FILE);
        if (child != null && child.exists()) {
            return child;
        } else {
            return null;
        }
    }
}
