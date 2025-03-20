package de.keksuccino.promptgenerator.ui;

import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;
import de.keksuccino.promptgenerator.util.ClipboardUtil;
import de.keksuccino.promptgenerator.util.Version;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;
import java.time.Year;
import java.util.ArrayList;
import java.util.List;

public class PromptGeneratorDialog extends DialogWrapper {
    private final Project project;
    private final PsiFile currentFile;

    private JBTextArea promptHeadArea;
    private FileListPanel projectFilesPanel;
    private FileListPanel additionalFilesPanel;
    private JComboBox<String> templateComboBox;
    private JButton saveTemplateButton;
    private JButton deleteTemplateButton;

    public PromptGeneratorDialog(Project project, PsiFile currentFile) {
        super(project);
        this.project = project;
        this.currentFile = currentFile;

        setTitle("Generate AI Prompt with Context");
        init();
    }

    @Override
    protected String getDimensionServiceKey() {
        return "de.keksuccino.aiprompt.PromptGeneratorDialog";
    }

    @Override
    protected JComponent createCenterPanel() {
        PromptSettings settings = PromptSettings.getInstance(project);

        JPanel mainPanel = new JPanel(new BorderLayout(0, 10));
        mainPanel.setPreferredSize(new Dimension(900, 700));

        // Settings button row at the top (above everything else)
        JPanel settingsButtonPanel = new JPanel(new BorderLayout());
        settingsButtonPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        JLabel settingsButton = new JLabel(AllIcons.General.GearPlain);
        settingsButton.setToolTipText("Settings");
        settingsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));

        // Add hover effect
        settingsButton.addMouseListener(new MouseAdapter() {
            @Override
            public void mouseEntered(MouseEvent e) {
                settingsButton.setIcon(AllIcons.General.GearHover);
            }

            @Override
            public void mouseExited(MouseEvent e) {
                settingsButton.setIcon(AllIcons.General.GearPlain);
            }

            @Override
            public void mouseClicked(MouseEvent e) {
                openSettings();
            }
        });

        JPanel rightAlignPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 5, 0));
        rightAlignPanel.add(settingsButton);
        settingsButtonPanel.add(rightAlignPanel, BorderLayout.EAST);

        // Templates panel below the settings button
        JPanel templatesPanel = new JPanel(new BorderLayout());
        templatesPanel.setBorder(BorderFactory.createTitledBorder("Templates"));

        JPanel templateControlsPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        templateComboBox = new JComboBox<>();

        saveTemplateButton = new JButton("Save Current as Template");
        saveTemplateButton.addActionListener(e -> saveCurrentAsTemplate());

        deleteTemplateButton = new JButton("Delete Template");
        deleteTemplateButton.addActionListener(e -> deleteSelectedTemplate());

        // Initialize the combo box and enable/disable buttons AFTER initializing the buttons
        updateTemplatesList();

        templateComboBox.addActionListener(e -> loadSelectedTemplate());

        templateControlsPanel.add(new JLabel("Load Template: "));
        templateControlsPanel.add(templateComboBox);
        templateControlsPanel.add(saveTemplateButton);
        templateControlsPanel.add(deleteTemplateButton);

        templatesPanel.add(templateControlsPanel, BorderLayout.CENTER);

        // Create a panel to hold the expandable content using GridBagLayout
        JPanel contentWithResize = new JPanel(new GridBagLayout());
        GridBagConstraints gbc = new GridBagConstraints();
        gbc.fill = GridBagConstraints.BOTH;
        gbc.gridx = 0;
        gbc.gridy = 0;
        gbc.weightx = 1.0;

        // Prompt Head Section with vertical expansion
        JPanel promptHeadPanel = new JPanel(new BorderLayout());
        TitledBorder titledBorder = BorderFactory.createTitledBorder("Prompt Head");
        promptHeadPanel.setBorder(titledBorder);

        // Set up the prompt head text area
        promptHeadArea = new JBTextArea(settings.getPromptHead());
        promptHeadArea.setLineWrap(true);
        promptHeadArea.setWrapStyleWord(true);

        // Create scroll pane to allow scrolling for large content
        JScrollPane scrollPane = new JScrollPane(promptHeadArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Add padding around scroll pane
        JPanel paddedScrollPanel = new JPanel(new BorderLayout());
        paddedScrollPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        paddedScrollPanel.add(scrollPane, BorderLayout.CENTER);

        promptHeadPanel.add(paddedScrollPanel, BorderLayout.CENTER);

        // Add prompt head with weight 0.5 (50% of vertical space)
        gbc.weighty = 0.5;
        contentWithResize.add(promptHeadPanel, gbc);

        // File Selection Panels
        JPanel fileSelectionPanel = new JPanel(new GridLayout(1, 2, 10, 0));
        projectFilesPanel = new FileListPanel(
                project,
                "Project Context",
                true,
                PromptSettings.PROJECT_PANEL_ID);
        additionalFilesPanel = new FileListPanel(
                project,
                "Additional Context",
                false,
                PromptSettings.ADDITIONAL_PANEL_ID);

        fileSelectionPanel.add(projectFilesPanel);
        fileSelectionPanel.add(additionalFilesPanel);

        // Add file selection panel with weight 0.5 (50% of vertical space)
        gbc.gridy = 1;
        gbc.weighty = 0.5;
        contentWithResize.add(fileSelectionPanel, gbc);

        // Create a content panel for everything below the settings button
        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));

        // Add the templates panel at the top of the content
        contentPanel.add(templatesPanel, BorderLayout.NORTH);

        // Add the resizable content area
        contentPanel.add(contentWithResize, BorderLayout.CENTER);

        // Add components to the main panel in the correct order
        mainPanel.add(settingsButtonPanel, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBorder(JBUI.Borders.empty(8));

        // Generate button in center
        JButton generateButton = new JButton("Generate and Copy to Clipboard");
        generateButton.addActionListener(e -> generateAndCopy());

        // Version label in bottom-left
        String version = Version.VERSION.equals("@VERSION@") ? "DEVELOPMENT" : Version.VERSION;
        JLabel versionLabel = new JLabel("v" + version);
        versionLabel.setForeground(Color.GRAY);
        versionLabel.setFont(versionLabel.getFont().deriveFont(Font.PLAIN, 10f));

        // Copyright notice in bottom-right
        int currentYear = Year.now().getValue();
        JLabel copyrightLabel = new JLabel("Copyright © " + currentYear + " Keksuccino");
        copyrightLabel.setForeground(Color.GRAY);
        copyrightLabel.setFont(copyrightLabel.getFont().deriveFont(Font.PLAIN, 10f));

        // Layout for bottom panel
        JPanel bottomInfoPanel = new JPanel(new BorderLayout());
        bottomInfoPanel.add(versionLabel, BorderLayout.WEST);
        bottomInfoPanel.add(copyrightLabel, BorderLayout.EAST);

        // Add top margin to the bottom info panel
        bottomInfoPanel.setBorder(BorderFactory.createEmptyBorder(10, 0, 0, 0));

        // Add the components to the main button panel
        buttonPanel.add(generateButton, BorderLayout.CENTER);
        buttonPanel.add(bottomInfoPanel, BorderLayout.SOUTH);

        return buttonPanel;
    }

    private void updateTemplatesList() {
        if (templateComboBox != null) {
            templateComboBox.removeAllItems();

            PromptSettings settings = PromptSettings.getInstance(project);
            List<String> templateNames = settings.getTemplateNames();

            for (String name : templateNames) {
                templateComboBox.addItem(name);
            }

            // Only try to enable/disable if the button has been initialized
            if (deleteTemplateButton != null) {
                deleteTemplateButton.setEnabled(templateComboBox.getItemCount() > 0);
            }
        }
    }

    private void saveCurrentAsTemplate() {
        // Prompt for template name
        String templateName = Messages.showInputDialog(
                project,
                "Enter a name for this template:",
                "Save Template",
                Messages.getQuestionIcon()
        );

        if (templateName != null && !templateName.trim().isEmpty()) {
            PromptSettings settings = PromptSettings.getInstance(project);

            // Check if template name already exists
            if (settings.getTemplate(templateName) != null) {
                int result = Messages.showYesNoDialog(
                        project,
                        "A template with this name already exists. Do you want to overwrite it?",
                        "Template Exists",
                        "Overwrite",
                        "Cancel",
                        Messages.getWarningIcon()
                );

                if (result != Messages.YES) {
                    return;
                }
            }

            // Create template from current state
            PromptTemplate template = new PromptTemplate();
            template.setName(templateName);
            template.setPromptHead(promptHeadArea.getText());
            template.setProjectSectionHeader(settings.getProjectSectionHeader());
            template.setAdditionalSectionHeader(settings.getAdditionalSectionHeader());

            // Get file paths from both panels
            template.setProjectFilePaths(new ArrayList<>(settings.getFilePaths(PromptSettings.PROJECT_PANEL_ID)));
            template.setAdditionalFilePaths(new ArrayList<>(settings.getFilePaths(PromptSettings.ADDITIONAL_PANEL_ID)));

            // Save template
            settings.saveTemplate(template);

            // Update template list
            updateTemplatesList();
            templateComboBox.setSelectedItem(templateName);

            Messages.showInfoMessage(
                    project,
                    "Template '" + templateName + "' has been saved.",
                    "Template Saved"
            );
        }
    }

    private void deleteSelectedTemplate() {
        String selectedTemplate = (String) templateComboBox.getSelectedItem();
        if (selectedTemplate == null) return;

        int result = Messages.showYesNoDialog(
                project,
                "Are you sure you want to delete the template '" + selectedTemplate + "'?",
                "Delete Template",
                "Delete",
                "Cancel",
                Messages.getWarningIcon()
        );

        if (result == Messages.YES) {
            PromptSettings settings = PromptSettings.getInstance(project);
            settings.deleteTemplate(selectedTemplate);

            // Update template list
            updateTemplatesList();
        }
    }

    private void loadSelectedTemplate() {
        String selectedTemplate = (String) templateComboBox.getSelectedItem();
        if (selectedTemplate == null) return;

        PromptSettings settings = PromptSettings.getInstance(project);
        PromptTemplate template = settings.getTemplate(selectedTemplate);

        if (template != null) {
            // Load template data
            promptHeadArea.setText(template.getPromptHead());

            // Set section headers
            settings.setProjectSectionHeader(template.getProjectSectionHeader());
            settings.setAdditionalSectionHeader(template.getAdditionalSectionHeader());

            // Clear and reload file lists
            settings.saveFilePaths(PromptSettings.PROJECT_PANEL_ID, template.getProjectFilePaths());
            settings.saveFilePaths(PromptSettings.ADDITIONAL_PANEL_ID, template.getAdditionalFilePaths());

            // Refresh UI to show loaded template
            refreshFileLists();
        }
    }

    private void refreshFileLists() {
        // Remove and recreate panels to refresh their content
        Container parent = projectFilesPanel.getParent();
        int index = -1;
        for (int i = 0; i < parent.getComponentCount(); i++) {
            if (parent.getComponent(i) == projectFilesPanel) {
                index = i;
                break;
            }
        }

        if (index >= 0) {
            parent.remove(projectFilesPanel);
            parent.remove(additionalFilesPanel);

            projectFilesPanel = new FileListPanel(
                    project,
                    "Project Context",
                    true,
                    PromptSettings.PROJECT_PANEL_ID);
            additionalFilesPanel = new FileListPanel(
                    project,
                    "Additional Context",
                    false,
                    PromptSettings.ADDITIONAL_PANEL_ID);

            parent.add(projectFilesPanel, index);
            parent.add(additionalFilesPanel, index + 1);

            parent.revalidate();
            parent.repaint();
        }
    }

    private void openSettings() {
        PromptSettingsDialog dialog = new PromptSettingsDialog(project);
        dialog.showAndGet();
    }

    private void savePromptHead() {
        PromptSettings settings = PromptSettings.getInstance(project);
        String promptHeadText = promptHeadArea.getText().trim();
        settings.setPromptHead(promptHeadText);
    }

    private void generateAndCopy() {
        // Save prompt head
        savePromptHead();

        PromptSettings settings = PromptSettings.getInstance(project);
        StringBuilder sb = new StringBuilder();

        // Add prompt head
        sb.append(settings.getPromptHead()).append("\n\n");

        // Add project context
        sb.append(settings.getProjectSectionHeader()).append("\n\n");
        if (projectFilesPanel.isEmpty()) {
            sb.append("No project files selected.\n\n");
        } else {
            sb.append(projectFilesPanel.getFormattedContent());
        }

        // Add additional context
        sb.append(settings.getAdditionalSectionHeader()).append("\n\n");
        if (additionalFilesPanel.isEmpty()) {
            sb.append("No additional files selected.\n\n");
        } else {
            sb.append(additionalFilesPanel.getFormattedContent());
        }

        // Copy to clipboard
        ClipboardUtil.copyToClipboard(sb.toString());

        Messages.showInfoMessage("Prompt copied to clipboard!", "Success");
        close(OK_EXIT_CODE);
    }
}