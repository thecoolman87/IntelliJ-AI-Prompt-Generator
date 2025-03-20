package de.keksuccino.promptgenerator.ui;

import java.util.ArrayList;
import java.util.List;
import java.util.Objects;

public class PromptTemplate {
    private String name;
    private String promptHead;
    private String projectSectionHeader;
    private String additionalSectionHeader;
    private List<String> projectFilePaths;
    private List<String> additionalFilePaths;

    public PromptTemplate() {
        this.projectFilePaths = new ArrayList<>();
        this.additionalFilePaths = new ArrayList<>();
    }

    public PromptTemplate(String name, String promptHead, String projectSectionHeader,
                          String additionalSectionHeader, List<String> projectFilePaths,
                          List<String> additionalFilePaths) {
        this.name = name;
        this.promptHead = promptHead;
        this.projectSectionHeader = projectSectionHeader;
        this.additionalSectionHeader = additionalSectionHeader;
        this.projectFilePaths = new ArrayList<>(projectFilePaths);
        this.additionalFilePaths = new ArrayList<>(additionalFilePaths);
    }

    // Getters and setters
    public String getName() {
        return name;
    }

    public void setName(String name) {
        this.name = name;
    }

    public String getPromptHead() {
        return promptHead;
    }

    public void setPromptHead(String promptHead) {
        this.promptHead = promptHead;
    }

    public String getProjectSectionHeader() {
        return projectSectionHeader;
    }

    public void setProjectSectionHeader(String projectSectionHeader) {
        this.projectSectionHeader = projectSectionHeader;
    }

    public String getAdditionalSectionHeader() {
        return additionalSectionHeader;
    }

    public void setAdditionalSectionHeader(String additionalSectionHeader) {
        this.additionalSectionHeader = additionalSectionHeader;
    }

    public List<String> getProjectFilePaths() {
        return projectFilePaths;
    }

    public void setProjectFilePaths(List<String> projectFilePaths) {
        this.projectFilePaths = projectFilePaths;
    }

    public List<String> getAdditionalFilePaths() {
        return additionalFilePaths;
    }

    public void setAdditionalFilePaths(List<String> additionalFilePaths) {
        this.additionalFilePaths = additionalFilePaths;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        PromptTemplate template = (PromptTemplate) o;
        return Objects.equals(name, template.name);
    }

    @Override
    public int hashCode() {
        return Objects.hash(name);
    }

    @Override
    public String toString() {
        return name;
    }
}