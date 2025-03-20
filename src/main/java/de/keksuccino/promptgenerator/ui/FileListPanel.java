package de.keksuccino.promptgenerator.ui;

import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeJavaClassChooserDialog;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PlatformIcons;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;

public class FileListPanel extends JPanel {
    private final Project project;
    private final SearchTextField searchField;
    private final CollectionListModel<FileInfo> fileListModel;
    private final JBList<FileInfo> fileList;
    private final boolean projectFilesOnly;
    private final String panelId;
    private JCheckBox classOnlyCheckBox;
    private boolean classOnlyMode;
    private JButton addButton; // Reference to the add button for updating text
    private final Map<String, Icon> iconCache = new HashMap<>(); // Cache for file icons

    public FileListPanel(Project project, String title, boolean projectFilesOnly, String panelId) {
        super(new BorderLayout());
        this.project = project;
        this.projectFilesOnly = projectFilesOnly;
        this.panelId = panelId;

        // Load settings
        PromptSettings settings = PromptSettings.getInstance(project);
        this.classOnlyMode = panelId.equals(PromptSettings.PROJECT_PANEL_ID) ?
                settings.isProjectPanelClassOnly() :
                settings.isAdditionalPanelClassOnly();

        setBorder(BorderFactory.createTitledBorder(title));

        // Search/control panel
        JPanel controlPanel = new JPanel(new BorderLayout());
        searchField = new SearchTextField();
        searchField.addDocumentListener(new com.intellij.ui.DocumentAdapter() {
            @Override
            protected void textChanged(javax.swing.event.DocumentEvent e) {
                filterFileList();
            }
        });

        JPanel buttonPanel = new JPanel(new FlowLayout(FlowLayout.LEFT));
        addButton = new JButton(); // Initialize without text
        JButton removeButton = new JButton("Remove Selected");
        classOnlyCheckBox = new JCheckBox("Java Classes Only", classOnlyMode);
        classOnlyCheckBox.addActionListener(e -> toggleClassOnlyMode());

        // Set initial button text based on mode
        updateAddButtonText();

        buttonPanel.add(addButton);
        buttonPanel.add(removeButton);
        buttonPanel.add(classOnlyCheckBox);

        controlPanel.add(searchField, BorderLayout.CENTER);
        controlPanel.add(buttonPanel, BorderLayout.SOUTH);

        // Initialize file list and model
        fileListModel = new CollectionListModel<>(new ArrayList<>());
        fileList = new JBList<>(fileListModel);

        // Set up actions
        addButton.addActionListener(e -> addFile());
        removeButton.addActionListener(e -> removeSelectedFiles());

        // Layout
        add(controlPanel, BorderLayout.NORTH);
        add(createScrollableList(), BorderLayout.CENTER);

        // Load saved selections after UI is set up
        loadSavedSelections();
    }

    // Update button text based on current mode
    private void updateAddButtonText() {
        addButton.setText(classOnlyMode ? "Add Class" : "Add File");
    }

    private JComponent createScrollableList() {
        // Set up cell renderer with smart display names and file icons
        fileList.setCellRenderer(new DefaultListCellRenderer() {
            @Override
            public Component getListCellRendererComponent(JList<?> list, Object value,
                                                          int index, boolean isSelected, boolean cellHasFocus) {
                JLabel label = (JLabel) super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus);

                if (value instanceof FileInfo) {
                    FileInfo fileInfo = (FileInfo) value;

                    // Set the text using smart display name
                    label.setText(getSmartDisplayName(fileInfo));

                    // Set the icon based on file type
                    label.setIcon(getIconForFile(fileInfo));

                    // Add tooltip for full path
                    label.setToolTipText(fileInfo.getPath());
                }

                return label;
            }
        });

        // Create a scroll pane with padding
        JScrollPane scrollPane = new JBScrollPane(fileList);

        // Add padding around the scrollpane
        JPanel paddedPanel = new JPanel(new BorderLayout());
        paddedPanel.setBorder(BorderFactory.createEmptyBorder(5, 5, 5, 5));
        paddedPanel.add(scrollPane, BorderLayout.CENTER);

        return paddedPanel;
    }

    // Get the appropriate icon for a file
    private Icon getIconForFile(FileInfo fileInfo) {
        // Check cache first
        if (iconCache.containsKey(fileInfo.getPath())) {
            return iconCache.get(fileInfo.getPath());
        }

        // Get the file's icon from the virtual file
        Icon icon = null;
        VirtualFile vFile = fileInfo.getVirtualFile();

        if (vFile != null && vFile.isValid()) {
            // Try to get file type icon with all flags
            icon = vFile.getFileType().getIcon();

            // For Java files, use class icon if it's a class file
            if (vFile.getName().endsWith(".java")) {
                icon = PlatformIcons.CLASS_ICON;
            }
        }

        // Fallback to a generic file icon if needed
        if (icon == null) {
            icon = PlatformIcons.FILE_ICON;
        }

        // Cache the icon
        iconCache.put(fileInfo.getPath(), icon);

        return icon;
    }

    private String getSmartDisplayName(FileInfo info) {
        // Get all file names in the list
        Map<String, Integer> fileNameCounts = new HashMap<>();

        for (FileInfo item : fileListModel.getItems()) {
            String name = item.getName();
            fileNameCounts.put(name, fileNameCounts.getOrDefault(name, 0) + 1);
        }

        // If this file name is unique, just show the file name
        if (fileNameCounts.getOrDefault(info.getName(), 0) <= 1) {
            return info.getName();
        }

        // Otherwise, show a shortened path to give context
        String path = info.getPath();
        String[] pathParts = path.split("/|\\\\");

        if (pathParts.length > 2) {
            // Show parent directory + filename
            return pathParts[pathParts.length - 2] + "/" + info.getName();
        }

        // Fallback to full path if needed
        return info.getPath();
    }

    private void toggleClassOnlyMode() {
        classOnlyMode = classOnlyCheckBox.isSelected();

        // Update button text when mode changes
        updateAddButtonText();

        // Save the mode
        PromptSettings settings = PromptSettings.getInstance(project);
        if (panelId.equals(PromptSettings.PROJECT_PANEL_ID)) {
            settings.setProjectPanelClassOnly(classOnlyMode);
        } else {
            settings.setAdditionalPanelClassOnly(classOnlyMode);
        }
    }

    private void addFile() {
        if (classOnlyMode) {
            addJavaClass();
        } else {
            addAnyFile();
        }
    }

    private void addJavaClass() {
        GlobalSearchScope scope = projectFilesOnly ?
                GlobalSearchScope.projectScope(project) :
                GlobalSearchScope.allScope(project);

        // Use TreeClassChooserFactory to create a class chooser
        TreeClassChooserFactory factory = TreeClassChooserFactory.getInstance(project);

        // Create a standard IntelliJ class chooser dialog
        TreeJavaClassChooserDialog dialog;
        if (projectFilesOnly) {
            dialog = new TreeJavaClassChooserDialog("Select Class", project);
        } else {
            dialog = new TreeJavaClassChooserDialog("Select Class", project, scope, null, null);
        }

        dialog.showDialog();

        // Handle selection
        PsiClass selectedClass = dialog.getSelected();
        if (selectedClass != null && selectedClass.getContainingFile() instanceof PsiJavaFile) {
            PsiJavaFile javaFile = (PsiJavaFile) selectedClass.getContainingFile();
            FileInfo fileInfo = new FileInfo(javaFile, javaFile.getText());

            if (!fileListModel.getItems().contains(fileInfo)) {
                fileListModel.add(fileInfo);
                saveSelections(); // Save after adding
            }
        }
    }

    private void addAnyFile() {
        // Create a file chooser that also allows directory selection
        FileChooserDescriptor descriptor = new FileChooserDescriptor(
                true, // Choose files
                true, // Choose folders
                false, // Don't choose jars
                false, // Don't choose jars
                false, // Don't choose jar directories
                true) // Allow multiple selection
                .withRoots(project.getBaseDir()); // Start from project root

        if (projectFilesOnly) {
            descriptor = descriptor.withFileFilter(file -> file.getPath().startsWith(project.getBasePath()));
        }

        VirtualFile[] selectedFiles = FileChooser.chooseFiles(descriptor, project, null);
        if (selectedFiles.length > 0) {
            Set<VirtualFile> filesToProcess = new HashSet<>();

            // First, collect all files to process (including recursion through directories)
            for (VirtualFile selected : selectedFiles) {
                if (selected.isDirectory()) {
                    // Recursively collect all files in this directory
                    collectFilesRecursively(selected, filesToProcess);
                } else {
                    filesToProcess.add(selected);
                }
            }

            // Process all collected files
            PsiManager psiManager = PsiManager.getInstance(project);
            int addedCount = 0;

            for (VirtualFile file : filesToProcess) {
                // Skip directories (we should only have files at this point)
                if (file.isDirectory()) continue;

                // Skip files outside project scope if we're in project files only mode
                if (projectFilesOnly && !file.getPath().startsWith(project.getBasePath())) {
                    continue;
                }

                // Convert to PsiFile and add
                PsiFile psiFile = psiManager.findFile(file);
                if (psiFile != null) {
                    String content = psiFile.getText();
                    FileInfo fileInfo = new FileInfo(psiFile, content);

                    if (!fileListModel.getItems().contains(fileInfo)) {
                        fileListModel.add(fileInfo);
                        addedCount++;
                    }
                }
            }

            // Save selections after adding
            if (addedCount > 0) {
                saveSelections();

                // Show success message when adding many files
                if (addedCount > 1) {
                    JOptionPane.showMessageDialog(
                            this,
                            "Added " + addedCount + " files to the list.",
                            "Files Added",
                            JOptionPane.INFORMATION_MESSAGE
                    );
                }
            }
        }
    }

    /**
     * Recursively collects all files from a directory and its subdirectories
     */
    private void collectFilesRecursively(VirtualFile directory, Set<VirtualFile> result) {
        if (!directory.isDirectory()) {
            result.add(directory);
            return;
        }

        for (VirtualFile child : directory.getChildren()) {
            if (child.isDirectory()) {
                collectFilesRecursively(child, result);
            } else {
                result.add(child);
            }
        }
    }

    private void removeSelectedFiles() {
        List<FileInfo> toRemove = new ArrayList<>(fileList.getSelectedValuesList());
        for (FileInfo info : toRemove) {
            fileListModel.remove(info);
        }

        // Save selections after removing
        saveSelections();
    }

    private void filterFileList() {
        String searchText = searchField.getText().toLowerCase();
        if (searchText.isEmpty()) {
            return; // No filtering needed
        }

        // Basic implementation for search functionality
        for (int i = 0; i < fileListModel.getSize(); i++) {
            FileInfo info = fileListModel.getElementAt(i);
            if (info.getPath().toLowerCase().contains(searchText)) {
                fileList.setSelectedValue(info, true);
                break;
            }
        }
    }

    public String getFormattedContent() {
        StringBuilder sb = new StringBuilder();

        for (FileInfo fileInfo : fileListModel.getItems()) {
            sb.append("------------------\n");
            sb.append("File: ").append(fileInfo.getPath()).append("\n");
            sb.append("------------------\n\n");
            sb.append(fileInfo.getContent()).append("\n\n");
        }

        return sb.toString();
    }

    public boolean isEmpty() {
        return fileListModel.getSize() == 0;
    }

    // Save and load selections
    private void saveSelections() {
        List<String> paths = new ArrayList<>();
        for (FileInfo info : fileListModel.getItems()) {
            paths.add(info.getPath());
        }

        // Save to persistent storage
        PromptSettings.getInstance(project).saveFilePaths(panelId, paths);
    }

    private void loadSavedSelections() {
        List<String> paths = PromptSettings.getInstance(project).getFilePaths(panelId);
        if (paths.isEmpty()) return;

        PsiManager psiManager = PsiManager.getInstance(project);
        for (String path : paths) {
            VirtualFile vFile = project.getBaseDir().getFileSystem().findFileByPath(path);
            if (vFile != null && vFile.exists()) {
                PsiFile psiFile = psiManager.findFile(vFile);
                if (psiFile != null) {
                    FileInfo fileInfo = new FileInfo(psiFile, psiFile.getText());
                    fileListModel.add(fileInfo);
                }
            }
        }
    }
}