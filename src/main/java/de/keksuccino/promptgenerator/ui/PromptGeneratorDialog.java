package de.keksuccino.promptgenerator.ui;

import de.keksuccino.promptgenerator.util.ClipboardUtil;
import com.intellij.icons.AllIcons;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.ui.DialogWrapper;
import com.intellij.openapi.ui.Messages;
import com.intellij.psi.PsiFile;
import com.intellij.ui.components.JBTextArea;
import com.intellij.util.ui.JBUI;

import javax.swing.*;
import javax.swing.border.TitledBorder;
import java.awt.*;
import java.awt.event.MouseAdapter;
import java.awt.event.MouseEvent;

public class PromptGeneratorDialog extends DialogWrapper {
    private final Project project;
    private final PsiFile currentFile;

    private JBTextArea promptHeadArea;
    private FileListPanel projectFilesPanel;
    private FileListPanel additionalFilesPanel;

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

        // Top panel for dialog title and settings button
        JPanel topPanel = new JPanel(new BorderLayout());
        topPanel.setBorder(BorderFactory.createEmptyBorder(0, 0, 5, 0));

        // Settings button in the top-right of the dialog
        JLabel settingsButton = new JLabel(AllIcons.General.GearPlain);
        settingsButton.setToolTipText("Settings");
        settingsButton.setCursor(Cursor.getPredefinedCursor(Cursor.HAND_CURSOR));
        settingsButton.setBorder(BorderFactory.createEmptyBorder(0, 0, 0, 5));

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

        JPanel rightPanel = new JPanel(new FlowLayout(FlowLayout.RIGHT, 0, 0));
        rightPanel.add(settingsButton);
        topPanel.add(rightPanel, BorderLayout.EAST);

        // Prompt Head Section with proper spacing
        JPanel promptHeadPanel = new JPanel(new BorderLayout());

        // Create titled border
        TitledBorder titledBorder = BorderFactory.createTitledBorder("Prompt Head");
        promptHeadPanel.setBorder(titledBorder);

        // Set up the prompt head text area with better padding
        promptHeadArea = new JBTextArea(settings.getPromptHead());
        promptHeadArea.setRows(3);
        promptHeadArea.setLineWrap(true);
        promptHeadArea.setWrapStyleWord(true);

        // Create scroll pane with proper padding
        JScrollPane scrollPane = new JScrollPane(promptHeadArea);
        scrollPane.setBorder(BorderFactory.createEmptyBorder());

        // Add padding around scroll pane
        JPanel paddedScrollPanel = new JPanel(new BorderLayout());
        paddedScrollPanel.setBorder(BorderFactory.createEmptyBorder(10, 10, 10, 10));
        paddedScrollPanel.add(scrollPane, BorderLayout.CENTER);

        promptHeadPanel.add(paddedScrollPanel, BorderLayout.CENTER);

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

        // Add to main panel
        JPanel contentPanel = new JPanel(new BorderLayout(0, 10));
        contentPanel.add(promptHeadPanel, BorderLayout.NORTH);
        contentPanel.add(fileSelectionPanel, BorderLayout.CENTER);

        mainPanel.add(topPanel, BorderLayout.NORTH);
        mainPanel.add(contentPanel, BorderLayout.CENTER);

        return mainPanel;
    }

    private void openSettings() {
        PromptSettingsDialog dialog = new PromptSettingsDialog(project);
        dialog.showAndGet();
    }

    @Override
    protected JComponent createSouthPanel() {
        JPanel buttonPanel = new JPanel(new BorderLayout());
        buttonPanel.setBorder(JBUI.Borders.empty(8));

        JButton generateButton = new JButton("Generate and Copy to Clipboard");
        generateButton.addActionListener(e -> generateAndCopy());

        buttonPanel.add(generateButton, BorderLayout.CENTER);

        return buttonPanel;
    }

    @Override
    public void doCancelAction() {
        savePromptHead();
        super.doCancelAction();
    }

    @Override
    protected void dispose() {
        savePromptHead();
        super.dispose();
    }

    private void savePromptHead() {
        PromptSettings settings = PromptSettings.getInstance(project);
        String promptHeadText = promptHeadArea.getText().trim();
        settings.setPromptHead(promptHeadText);
    }

    private void generateAndCopy() {
        savePromptHead();

        PromptSettings settings = PromptSettings.getInstance(project);
        StringBuilder sb = new StringBuilder();

        sb.append(settings.getPromptHead()).append("\n\n");

        sb.append(settings.getProjectSectionHeader()).append("\n\n");
        if (projectFilesPanel.isEmpty()) {
            sb.append("No project files selected.\n\n");
        } else {
            sb.append(projectFilesPanel.getFormattedContent());
        }

        sb.append(settings.getAdditionalSectionHeader()).append("\n\n");
        if (additionalFilesPanel.isEmpty()) {
            sb.append("No additional files selected.\n\n");
        } else {
            sb.append(additionalFilesPanel.getFormattedContent());
        }

        ClipboardUtil.copyToClipboard(sb.toString());

        Messages.showInfoMessage("Prompt copied to clipboard!", "Success");
        close(OK_EXIT_CODE);
    }
}