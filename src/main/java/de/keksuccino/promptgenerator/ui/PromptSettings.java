package de.keksuccino.promptgenerator.ui;

import com.intellij.openapi.components.*;
import com.intellij.openapi.project.Project;
import org.jetbrains.annotations.NotNull;
import org.jetbrains.annotations.Nullable;

import java.util.ArrayList;
import java.util.List;

@State(
        name = "AIPromptGeneratorSettings",
        storages = {@Storage("aiPromptGeneratorSettings.xml")}
)
public class PromptSettings implements PersistentStateComponent<PromptSettings.State> {
    // Define an inner State class for XML serialization
    public static class State {
        public String promptHead = "Please fix this project for me";
        public String projectSectionHeader = "PROJECT CLASSES:";
        public String additionalSectionHeader = "ADDITIONAL CONTEXT:";

        // Use a simple representation for paths
        public List<String> projectFilePaths = new ArrayList<>();
        public List<String> additionalFilePaths = new ArrayList<>();

        // Class-only mode settings
        public boolean projectPanelClassOnly = false;
        public boolean additionalPanelClassOnly = false;
    }

    private State myState = new State();

    public static PromptSettings getInstance(Project project) {
        return project.getService(PromptSettings.class);
    }

    @Override
    public @Nullable State getState() {
        return myState;
    }

    @Override
    public void loadState(@NotNull State state) {
        myState = state;
    }

    // Getters and setters
    public String getPromptHead() {
        return myState.promptHead;
    }

    public void setPromptHead(String promptHead) {
        myState.promptHead = promptHead;
    }

    public String getProjectSectionHeader() {
        return myState.projectSectionHeader;
    }

    public void setProjectSectionHeader(String projectSectionHeader) {
        myState.projectSectionHeader = projectSectionHeader;
    }

    public String getAdditionalSectionHeader() {
        return myState.additionalSectionHeader;
    }

    public void setAdditionalSectionHeader(String additionalSectionHeader) {
        myState.additionalSectionHeader = additionalSectionHeader;
    }

    public boolean isProjectPanelClassOnly() {
        return myState.projectPanelClassOnly;
    }

    public void setProjectPanelClassOnly(boolean classOnly) {
        myState.projectPanelClassOnly = classOnly;
    }

    public boolean isAdditionalPanelClassOnly() {
        return myState.additionalPanelClassOnly;
    }

    public void setAdditionalPanelClassOnly(boolean classOnly) {
        myState.additionalPanelClassOnly = classOnly;
    }

    public List<String> getFilePaths(String panelId) {
        if (PROJECT_PANEL_ID.equals(panelId)) {
            return myState.projectFilePaths;
        } else if (ADDITIONAL_PANEL_ID.equals(panelId)) {
            return myState.additionalFilePaths;
        }
        return new ArrayList<>();
    }

    public void saveFilePaths(String panelId, List<String> paths) {
        if (PROJECT_PANEL_ID.equals(panelId)) {
            myState.projectFilePaths = new ArrayList<>(paths);
        } else if (ADDITIONAL_PANEL_ID.equals(panelId)) {
            myState.additionalFilePaths = new ArrayList<>(paths);
        }
    }

    // Constant panel IDs
    public static final String PROJECT_PANEL_ID = "project_files";
    public static final String ADDITIONAL_PANEL_ID = "additional_files";
}