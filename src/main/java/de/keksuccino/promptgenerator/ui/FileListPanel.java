package de.keksuccino.promptgenerator.ui;

import com.intellij.ide.highlighter.JavaFileType;
import com.intellij.ide.util.TreeClassChooserFactory;
import com.intellij.ide.util.TreeJavaClassChooserDialog;
import com.intellij.openapi.application.ApplicationManager;
import com.intellij.openapi.application.ModalityState;
import com.intellij.openapi.application.ReadAction;
import com.intellij.openapi.fileChooser.FileChooser;
import com.intellij.openapi.fileChooser.FileChooserDescriptor;
import com.intellij.openapi.progress.ProgressIndicator;
import com.intellij.openapi.progress.ProgressManager;
import com.intellij.openapi.progress.Task;
import com.intellij.openapi.project.Project;
import com.intellij.openapi.vfs.LocalFileSystem;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiClass;
import com.intellij.psi.PsiClassOwner;
import com.intellij.psi.PsiElement;
import com.intellij.psi.PsiFile;
import com.intellij.psi.PsiJavaFile;
import com.intellij.psi.PsiManager;
import com.intellij.psi.search.FileTypeIndex;
import com.intellij.psi.search.GlobalSearchScope;
import com.intellij.ui.CollectionListModel;
import com.intellij.ui.SearchTextField;
import com.intellij.ui.components.JBList;
import com.intellij.ui.components.JBScrollPane;
import com.intellij.util.PlatformIcons;
import org.jetbrains.annotations.NotNull;
import javax.swing.*;
import java.awt.*;
import java.util.ArrayList;
import java.util.Collection;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.List;
import java.util.Map;
import java.util.Set;
import java.util.concurrent.atomic.AtomicBoolean;

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
    private JButton removeButton;
    private final Map<String, Icon> iconCache = new HashMap<>(); // Cache for file icons
    private final JLabel loadingLabel = new JLabel("Loading files...", SwingConstants.CENTER);
    public final AtomicBoolean isLoading = new AtomicBoolean(false);
    private PromptGeneratorDialog dialogRef; // Reference to the parent dialog

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
        removeButton = new JButton("Remove Selected");
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

        // Add loading label with a card layout to switch between loading and content
        JPanel contentPanel = new JPanel(new CardLayout());
        contentPanel.add(createScrollableList(), "content");

        // Style the loading label
        loadingLabel.setFont(loadingLabel.getFont().deriveFont(Font.ITALIC));
        JPanel loadingPanel = new JPanel(new BorderLayout());
        loadingPanel.add(loadingLabel, BorderLayout.CENTER);
        contentPanel.add(loadingPanel, "loading");

        add(contentPanel, BorderLayout.CENTER);

        // Start in normal content view
        ((CardLayout) contentPanel.getLayout()).show(contentPanel, "content");
    }

    public void setDialogReference(PromptGeneratorDialog dialog) {
        this.dialogRef = dialog;
    }

    // Show loading state
    public void setLoading(boolean loading) {
        isLoading.set(loading);

        // Update UI on EDT
        ApplicationManager.getApplication().invokeLater(() -> {
            // Toggle between loading and content view
            JPanel contentPanel = (JPanel) getComponent(1); // The content panel is the second component
            CardLayout cardLayout = (CardLayout) contentPanel.getLayout();
            cardLayout.show(contentPanel, loading ? "loading" : "content");

            // Enable/disable controls
            setControlsEnabled(!loading);

            // If the dialog reference exists, update its controls too
            if (dialogRef != null) {
                dialogRef.setControlsEnabled(!loading);
            }
        }, ModalityState.any());
    }

    // Enable/disable controls
    public void setControlsEnabled(boolean enabled) {
        addButton.setEnabled(enabled);
        removeButton.setEnabled(enabled);
        classOnlyCheckBox.setEnabled(enabled);
        searchField.setEnabled(enabled);
        fileList.setEnabled(enabled);
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
        if (selectedClass != null) {
            // Get the original file for the class
            PsiFile originalFile = selectedClass.getContainingFile();

            // Process the class in a background task
            setLoading(true);
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing Class", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        indicator.setText("Finding source for " + selectedClass.getName());

                        // Use read action for PSI access
                        PsiFile fileToUse = ReadAction.compute(() -> {
                            // Try to get the source file
                            PsiElement sourceElement = originalFile.getNavigationElement();
                            PsiFile sourceFile = sourceElement instanceof PsiFile ? (PsiFile)sourceElement :
                                    sourceElement != null ? sourceElement.getContainingFile() : null;

                            return sourceFile != null ? sourceFile : originalFile;
                        });

                        // If the source file is a Java file, use it
                        if (fileToUse instanceof PsiJavaFile) {
                            final String content = ReadAction.compute(() -> fileToUse.getText());

                            // Update UI on EDT
                            ApplicationManager.getApplication().invokeLater(() -> {
                                FileInfo fileInfo = new FileInfo((PsiJavaFile)fileToUse, content);
                                if (!fileListModel.getItems().contains(fileInfo)) {
                                    fileListModel.add(fileInfo);
                                    saveSelections(); // Save after adding
                                }
                                setLoading(false);
                            }, ModalityState.any());
                        } else {
                            ApplicationManager.getApplication().invokeLater(() -> setLoading(false), ModalityState.any());
                        }
                    } catch (Exception e) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            setLoading(false);
                            JOptionPane.showMessageDialog(FileListPanel.this,
                                    "Error processing class: " + e.getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        }, ModalityState.any());
                    }
                }
            });
        }
    }

    /**
     * Find source file for a .class file. If multiple sources are available,
     * prioritizes ones with "loom" and "mappings" in the name.
     * This must be called inside a read action.
     */
    private PsiFile findSourceForClassFile(VirtualFile classFile) {
        List<PsiFile> sources = new ArrayList<>();

        // This method must be called inside a read action
        PsiManager psiManager = PsiManager.getInstance(project);

        try {
            // Get the PSI file for the class file
            PsiFile psiClassFile = psiManager.findFile(classFile);
            if (psiClassFile == null) return null;

            // Try to get the navigation element, which should point to the source
            PsiElement navigationElement = psiClassFile.getNavigationElement();
            if (navigationElement != null && navigationElement != psiClassFile) {
                PsiFile sourceFile = navigationElement.getContainingFile();
                if (sourceFile != null && sourceFile.getVirtualFile() != null) {
                    sources.add(sourceFile);
                }
            }

            // If we have a class file, try to extract the class name to find source files
            if (classFile.getName().endsWith(".class")) {
                String className = classFile.getNameWithoutExtension();
                String packageName = "";

                // Try to extract the package name for more accurate source finding
                if (psiClassFile instanceof PsiClassOwner) {
                    String[] packages = ((PsiClassOwner) psiClassFile).getPackageName().split("\\.");
                    packageName = String.join("/", packages);
                }

                // Look for source files in the project and libraries
                Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(
                        JavaFileType.INSTANCE,
                        GlobalSearchScope.allScope(project)
                );

                for (VirtualFile file : javaFiles) {
                    if (file.getName().equals(className + ".java")) {
                        // If we have package info, check if the source path contains it
                        if (!packageName.isEmpty()) {
                            if (file.getPath().contains(packageName)) {
                                PsiFile sourceFile = psiManager.findFile(file);
                                if (sourceFile != null) {
                                    sources.add(sourceFile);
                                }
                            }
                        } else {
                            PsiFile sourceFile = psiManager.findFile(file);
                            if (sourceFile != null) {
                                sources.add(sourceFile);
                            }
                        }
                    }
                }
            }

            // If multiple sources found, prioritize ones with "loom" and "mappings"
            if (sources.size() > 1) {
                Collections.sort(sources, (f1, f2) -> {
                    String path1 = f1.getVirtualFile().getPath().toLowerCase();
                    String path2 = f2.getVirtualFile().getPath().toLowerCase();

                    boolean f1HasLoomAndMappings = path1.contains("loom") && path1.contains("mappings");
                    boolean f2HasLoomAndMappings = path2.contains("loom") && path2.contains("mappings");

                    if (f1HasLoomAndMappings && !f2HasLoomAndMappings) {
                        return -1; // f1 comes first
                    } else if (!f1HasLoomAndMappings && f2HasLoomAndMappings) {
                        return 1;  // f2 comes first
                    } else {
                        return 0;
                    }
                });
            }

            // Return the first source if any found
            if (!sources.isEmpty()) {
                return sources.get(0);
            }

        } catch (Exception e) {
            // Log the error but continue
            System.err.println("Error finding source for class file: " + e.getMessage());
        }

        return null;
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
            // Process files in a background task
            setLoading(true);
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing Files", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        Set<VirtualFile> filesToProcess = new HashSet<>();

                        // First, collect all files to process (including recursion through directories)
                        for (VirtualFile selected : selectedFiles) {
                            indicator.setText("Collecting files from " + selected.getName());
                            if (selected.isDirectory()) {
                                // Recursively collect all files in this directory
                                collectFilesRecursively(selected, filesToProcess);
                            } else {
                                filesToProcess.add(selected);
                            }
                        }

                        // Process all collected files
                        int totalFiles = filesToProcess.size();
                        int processedCount = 0;
                        final List<FileInfo> filesToAdd = new ArrayList<>();

                        for (VirtualFile file : filesToProcess) {
                            processedCount++;
                            indicator.setFraction((double)processedCount / totalFiles);
                            indicator.setText("Processing file " + processedCount + "/" + totalFiles + ": " + file.getName());

                            // Skip directories (we should only have files at this point)
                            if (file.isDirectory()) continue;

                            // Skip files outside project scope if we're in project files only mode
                            if (projectFilesOnly && !file.getPath().startsWith(project.getBasePath())) {
                                continue;
                            }

                            try {
                                // Process file inside read action
                                ReadAction.run(() -> {
                                    try {
                                        PsiManager psiManager = PsiManager.getInstance(project);

                                        // First create the PsiFile from the virtual file
                                        PsiFile psiFile = psiManager.findFile(file);

                                        // If this is a .class file, try to find its source
                                        if (file.getName().endsWith(".class")) {
                                            PsiFile sourceFile = findSourceForClassFile(file);
                                            if (sourceFile != null) {
                                                // We found a source file, use it instead
                                                psiFile = sourceFile;
                                            }
                                        } else if (psiFile != null) {
                                            // For non-class files, check if there's a better source via navigation element
                                            PsiElement sourceElement = psiFile.getNavigationElement();
                                            if (sourceElement != null && sourceElement != psiFile) {
                                                PsiFile betterSource = sourceElement.getContainingFile();
                                                if (betterSource != null) {
                                                    psiFile = betterSource;
                                                }
                                            }
                                        }

                                        // Add the file if we have a valid PsiFile
                                        if (psiFile != null) {
                                            // Create FileInfo with the file's content
                                            String content = psiFile.getText();
                                            FileInfo fileInfo = new FileInfo(psiFile, content);
                                            synchronized (filesToAdd) {
                                                filesToAdd.add(fileInfo);
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.err.println("Error processing file " + file.getPath() + ": " + e.getMessage());
                                    }
                                });
                            } catch (Exception e) {
                                System.err.println("Error in read action for file " + file.getPath() + ": " + e.getMessage());
                            }
                        }

                        // Update UI on EDT
                        ApplicationManager.getApplication().invokeLater(() -> {
                            // Add all collected files to the model
                            for (FileInfo fileInfo : filesToAdd) {
                                if (!fileListModel.getItems().contains(fileInfo)) {
                                    fileListModel.add(fileInfo);
                                }
                            }

                            // Save selections after adding
                            if (!filesToAdd.isEmpty()) {
                                saveSelections();

                                // Show success message when adding many files
                                if (filesToAdd.size() > 1) {
                                    JOptionPane.showMessageDialog(
                                            FileListPanel.this,
                                            "Added " + filesToAdd.size() + " files to the list.",
                                            "Files Added",
                                            JOptionPane.INFORMATION_MESSAGE
                                    );
                                }
                            }

                            setLoading(false);
                        }, ModalityState.any());
                    } catch (Exception e) {
                        ApplicationManager.getApplication().invokeLater(() -> {
                            setLoading(false);
                            JOptionPane.showMessageDialog(FileListPanel.this,
                                    "Error processing files: " + e.getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        }, ModalityState.any());
                    }
                }
            });
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

    // Start asynchronous loading of saved selections
    public void loadSavedSelectionsAsync() {
        // Skip if we're already loading
        if (isLoading.getAndSet(true)) {
            return;
        }

        setLoading(true);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Loading Saved Files", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    List<String> paths = PromptSettings.getInstance(project).getFilePaths(panelId);
                    if (paths.isEmpty()) {
                        ApplicationManager.getApplication().invokeLater(() -> setLoading(false), ModalityState.any());
                        return;
                    }

                    final List<FileInfo> filesToAdd = new ArrayList<>();
                    int totalPaths = paths.size();

                    for (int i = 0; i < totalPaths; i++) {
                        String path = paths.get(i);
                        indicator.setFraction((double)i / totalPaths);
                        indicator.setText("Loading file " + (i+1) + "/" + totalPaths + ": " + path);

                        try {
                            // Try to find the file using local file system
                            LocalFileSystem fileSystem = LocalFileSystem.getInstance();
                            VirtualFile vFile = fileSystem.findFileByPath(path);

                            if (vFile != null && vFile.exists()) {
                                // Process this file with proper read action
                                ReadAction.run(() -> {
                                    try {
                                        PsiManager psiManager = PsiManager.getInstance(project);

                                        // If this is a class file, try to find its source
                                        if (path.endsWith(".class")) {
                                            PsiFile sourceFile = findSourceForClassFile(vFile);
                                            if (sourceFile != null) {
                                                FileInfo fileInfo = new FileInfo(sourceFile, sourceFile.getText());
                                                synchronized (filesToAdd) {
                                                    filesToAdd.add(fileInfo);
                                                }
                                                return;
                                            }
                                        }

                                        // For regular files or if source wasn't found
                                        PsiFile psiFile = psiManager.findFile(vFile);
                                        if (psiFile != null) {
                                            FileInfo fileInfo = new FileInfo(psiFile, psiFile.getText());
                                            synchronized (filesToAdd) {
                                                filesToAdd.add(fileInfo);
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.err.println("Error processing file in read action: " + e.getMessage());
                                    }
                                });
                            } else {
                                // If file not found by path, try searching by name
                                ReadAction.run(() -> {
                                    try {
                                        String fileName = path.substring(path.lastIndexOf('/') + 1);

                                        // For Java files or class files, try to find by name
                                        boolean isJavaFile = fileName.endsWith(".java");
                                        boolean isClassFile = fileName.endsWith(".class");

                                        if (isJavaFile || isClassFile) {
                                            String searchName = isJavaFile ? fileName :
                                                    fileName.substring(0, fileName.lastIndexOf('.')) + ".java";

                                            // Search in all project and library files
                                            Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(
                                                    JavaFileType.INSTANCE,
                                                    GlobalSearchScope.allScope(project)
                                            );

                                            PsiManager psiManager = PsiManager.getInstance(project);
                                            for (VirtualFile file : javaFiles) {
                                                if (file.getName().equals(searchName)) {
                                                    PsiFile psiFile = psiManager.findFile(file);
                                                    if (psiFile != null) {
                                                        FileInfo fileInfo = new FileInfo(psiFile, psiFile.getText());
                                                        synchronized (filesToAdd) {
                                                            filesToAdd.add(fileInfo);
                                                        }
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.err.println("Error searching for file by name: " + e.getMessage());
                                    }
                                });
                            }
                        } catch (Exception e) {
                            System.err.println("Error loading file from path " + path + ": " + e.getMessage());
                        }
                    }

                    // Update UI on EDT
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // Add all files to the model
                        for (FileInfo fileInfo : filesToAdd) {
                            fileListModel.add(fileInfo);
                        }
                        setLoading(false);
                    }, ModalityState.any());

                } catch (Exception e) {
                    ApplicationManager.getApplication().invokeLater(() -> {
                        setLoading(false);
                        JOptionPane.showMessageDialog(FileListPanel.this,
                                "Error loading saved files: " + e.getMessage(),
                                "Error", JOptionPane.ERROR_MESSAGE);
                    }, ModalityState.any());
                }
            }
        });
    }
}