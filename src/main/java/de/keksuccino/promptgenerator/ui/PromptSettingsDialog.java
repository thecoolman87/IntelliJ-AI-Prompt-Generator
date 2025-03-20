package de.keksuccino.promptgenerator.ui;

import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import org.jetbrains.annotations.Nullable;

import javax.swing.*;
import java.awt.*;

public class PromptSettingsDialog extends DialogWrapper {
    private final Project project;
    private JTextField projectSectionField;
    private JTextField additionalSectionField;

    public PromptSettingsDialog(Project project) {
        super(project, true);
        this.project = project;
        setTitle("AI Prompt Generator Settings");
        init();
    }

    @Override
    protected @Nullable JComponent createCenterPanel() {
        PromptSettings settings = PromptSettings.getInstance(project);

        JPanel panel = new JPanel(new GridBagLayout());
        GridBagConstraints c = new GridBagConstraints();
        c.fill = GridBagConstraints.HORIZONTAL;
        c.insets = new Insets(5, 5, 5, 5);

        // Project section header
        c.gridx = 0;
        c.gridy = 0;
        panel.add(new JLabel("Project Section Header:"), c);

        c.gridx = 1;
        c.gridy = 0;
        c.weightx = 1.0;
        projectSectionField = new JTextField(settings.getProjectSectionHeader());
        panel.add(projectSectionField, c);

        // Additional section header
        c.gridx = 0;
        c.gridy = 1;
        c.weightx = 0.0;
        panel.add(new JLabel("Additional Context Header:"), c);

        c.gridx = 1;
        c.gridy = 1;
        c.weightx = 1.0;
        additionalSectionField = new JTextField(settings.getAdditionalSectionHeader());
        panel.add(additionalSectionField, c);

        return panel;
    }

    @Override
    protected void doOKAction() {
        PromptSettings settings = PromptSettings.getInstance(project);
        settings.setProjectSectionHeader(projectSectionField.getText());
        settings.setAdditionalSectionHeader(additionalSectionField.getText());
        super.doOKAction();
    }
}