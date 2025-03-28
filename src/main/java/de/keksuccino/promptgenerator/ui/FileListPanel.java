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
import com.intellij.openapi.vfs.StandardFileSystems;
import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.openapi.vfs.VirtualFileManager;
import com.intellij.psi.JavaPsiFacade;
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
import java.io.File;
import java.nio.file.Files;
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

    /**
     * Helper class to store classpath information. Used for serializing class references
     * in a way that's resilient to file system changes.
     */
    private static class ClassPathInfo {
        private final String packageName;
        private final String className;

        public ClassPathInfo(String packageName, String className) {
            this.packageName = packageName != null ? packageName : "";
            this.className = className;
        }

        @Override
        public String toString() {
            return packageName.isEmpty() ? className : packageName + "." + className;
        }

        public static ClassPathInfo fromPsiClassOwner(PsiClassOwner classOwner) {
            return new ClassPathInfo(classOwner.getPackageName(), classOwner.getName());
        }

        public static ClassPathInfo fromString(String classPath) {
            System.out.println("[DEBUG] Parsing classpath: " + classPath);
            int lastDot = classPath.lastIndexOf('.');
            if (lastDot == -1) {
                System.out.println("[DEBUG] No package found, using class name: " + classPath);
                return new ClassPathInfo("", classPath);
            }
            String pkg = classPath.substring(0, lastDot);
            String cls = classPath.substring(lastDot + 1);
            System.out.println("[DEBUG] Parsed package: " + pkg + ", class: " + cls);
            return new ClassPathInfo(pkg, cls);
        }
    }

    public FileListPanel(Project project, String title, boolean projectFilesOnly, String panelId) {
        super(new BorderLayout());
        this.project = project;
        this.projectFilesOnly = projectFilesOnly;
        this.panelId = panelId;

        System.out.println("[DEBUG] Creating FileListPanel: " + panelId + ", projectFilesOnly: " + projectFilesOnly);

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
        System.out.println("[DEBUG] Adding Java class with dialog");
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
            System.out.println("[DEBUG] Selected class: " + selectedClass.getQualifiedName());

            // Get the original file for the class
            PsiFile originalFile = selectedClass.getContainingFile();
            System.out.println("[DEBUG] Original file: " + originalFile.getName() + ", path: " +
                    (originalFile.getVirtualFile() != null ? originalFile.getVirtualFile().getPath() : "null"));

            // Process the class in a background task
            setLoading(true);
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing Class", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        indicator.setText("Finding source for " + selectedClass.getName());
                        indicator.setIndeterminate(true);  // Set to indeterminate to avoid the error

                        // Use read action for PSI access
                        PsiFile fileToUse = ReadAction.compute(() -> {
                            // Try to get the source file
                            PsiElement sourceElement = originalFile.getNavigationElement();

                            System.out.println("[DEBUG] Navigation element: " +
                                    (sourceElement == null ? "null" : sourceElement.getClass().getName()));

                            PsiFile sourceFile = sourceElement instanceof PsiFile ? (PsiFile)sourceElement :
                                    sourceElement != null ? sourceElement.getContainingFile() : null;

                            if (sourceFile != null && sourceFile.getVirtualFile() != null) {
                                System.out.println("[DEBUG] Found source file: " + sourceFile.getName() +
                                        ", path: " + sourceFile.getVirtualFile().getPath());
                            } else {
                                System.out.println("[DEBUG] No source file found from navigation element");
                            }

                            return sourceFile != null ? sourceFile : originalFile;
                        });

                        // Check if this is a class file
                        boolean isClassFile = false;
                        VirtualFile virtualFile = fileToUse.getVirtualFile();
                        if (virtualFile != null) {
                            isClassFile = virtualFile.getName().endsWith(".class");
                            System.out.println("[DEBUG] File to use is class file: " + isClassFile);
                        }

                        final PsiFile finalFileToUse = fileToUse;
                        final boolean finalIsClassFile = isClassFile;

                        // If it's a class file or a Java file, use it
                        if (fileToUse instanceof PsiJavaFile || isClassFile) {
                            final String content = ReadAction.compute(() -> finalFileToUse.getText());
                            System.out.println("[DEBUG] Using file: " + fileToUse.getName() +
                                    ", content length: " + content.length());

                            // For class files, we will explicitly try to find its source
                            if (finalIsClassFile) {
                                PsiFile sourceFile = ReadAction.compute(() -> findSourceForClassFile(virtualFile));
                                if (sourceFile != null) {
                                    System.out.println("[DEBUG] Found better source for class file: " +
                                            sourceFile.getVirtualFile().getPath());
                                    final String sourceContent = ReadAction.compute(() -> sourceFile.getText());

                                    // Update UI on EDT with source file
                                    ApplicationManager.getApplication().invokeLater(() -> {
                                        FileInfo fileInfo = new FileInfo(sourceFile, sourceContent);
                                        if (!fileListModel.getItems().contains(fileInfo)) {
                                            fileListModel.add(fileInfo);
                                            System.out.println("[DEBUG] Added source file to list: " + fileInfo.getPath());
                                            saveSelections(); // Save after adding
                                        } else {
                                            System.out.println("[DEBUG] Source file already in list: " + fileInfo.getPath());
                                        }
                                        setLoading(false);
                                    }, ModalityState.any());
                                    return;
                                }
                            }

                            // Update UI on EDT with original file if no source found
                            ApplicationManager.getApplication().invokeLater(() -> {
                                FileInfo fileInfo = new FileInfo(finalFileToUse, content);
                                if (!fileListModel.getItems().contains(fileInfo)) {
                                    fileListModel.add(fileInfo);
                                    saveSelections(); // Save after adding
                                    System.out.println("[DEBUG] Added file to list: " + fileInfo.getPath());
                                } else {
                                    System.out.println("[DEBUG] File already in list: " + fileInfo.getPath());
                                }
                                setLoading(false);
                            }, ModalityState.any());
                        } else {
                            System.out.println("[DEBUG] File to use is not a Java file or class file: " +
                                    (fileToUse != null ? fileToUse.getClass().getName() : "null"));
                            ApplicationManager.getApplication().invokeLater(() -> setLoading(false), ModalityState.any());
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR] Error processing class: " + e.getMessage());
                        e.printStackTrace();
                        ApplicationManager.getApplication().invokeLater(() -> {
                            setLoading(false);
                            JOptionPane.showMessageDialog(FileListPanel.this,
                                    "Error processing class: " + e.getMessage(),
                                    "Error", JOptionPane.ERROR_MESSAGE);
                        }, ModalityState.any());
                    }
                }
            });
        } else {
            System.out.println("[DEBUG] No class selected");
        }
    }

    /**
     * Find source file for a .class file. If multiple sources are available,
     * prioritizes ones with "loom" and "mappings" in the name.
     * This must be called inside a read action.
     */
    private PsiFile findSourceForClassFile(VirtualFile classFile) {
        System.out.println("[DEBUG] Finding source for class file: " + classFile.getPath());
        List<PsiFile> sources = new ArrayList<>();
        PsiManager psiManager = PsiManager.getInstance(project);

        try {
            // Get the PSI file for the class file
            PsiFile psiClassFile = psiManager.findFile(classFile);
            if (psiClassFile == null) {
                System.out.println("[DEBUG] Could not create PsiFile for class file");
                return null;
            }

            System.out.println("[DEBUG] Created PsiFile of type: " + psiClassFile.getClass().getName());

            // First try to get source through navigation element
            PsiElement navigationElement = psiClassFile.getNavigationElement();
            System.out.println("[DEBUG] Navigation element: " +
                    (navigationElement == null ? "null" :
                            navigationElement.getClass().getName() + " (same as file: " +
                                    (navigationElement == psiClassFile) + ")"));

            if (navigationElement != null && navigationElement != psiClassFile) {
                PsiFile sourceFile = navigationElement.getContainingFile();
                if (sourceFile != null && sourceFile.getVirtualFile() != null) {
                    sources.add(sourceFile);
                    System.out.println("[DEBUG] Found source via navigation: " + sourceFile.getVirtualFile().getPath());
                }
            }

            // Extract package info if it's a class file
            String className = classFile.getNameWithoutExtension();
            String packageName = "";

            // Try to extract the package name for more accurate source finding
            if (psiClassFile instanceof PsiClassOwner) {
                packageName = ((PsiClassOwner) psiClassFile).getPackageName();
                System.out.println("[DEBUG] Extracted package name: " + packageName + " for class: " + className);
            } else {
                System.out.println("[DEBUG] Not a PsiClassOwner, can't extract package name");
            }

            // For Minecraft classes, try to directly find the loom.mappings source
            if (packageName.startsWith("net.minecraft")) {
                System.out.println("[DEBUG] This is a Minecraft class, searching for loom.mappings source first");

                // Try direct path pattern for loom mappings
                String relativeSourcePath = packageName.replace('.', '/') + "/" + className + ".java";
                String projectPath = project.getBasePath();

                if (projectPath != null) {
                    try {
                        // Look for any file in the project that matches the loom.mappings pattern
                        File projectDir = new File(projectPath);
                        List<String> loomMappingsPaths = new ArrayList<>();

                        // Use Files.find with depth limit to find loom.mappings sources
                        Files.find(projectDir.toPath(), 15,
                                        (path, attrs) -> path.toString().contains("loom.mappings") &&
                                                path.toString().endsWith("-sources.jar"))
                                .forEach(p -> {
                                    loomMappingsPaths.add(p.toString());
                                    System.out.println("[DEBUG] Found loom.mappings source jar: " + p);
                                });

                        // Try each loom.mappings source jar
                        for (String loomPath : loomMappingsPaths) {
                            String jarUrl = "jar://" + loomPath.replace('\\', '/') + "!/" + relativeSourcePath;
                            System.out.println("[DEBUG] Trying direct jar URL: " + jarUrl);

                            VirtualFile sourceFile = VirtualFileManager.getInstance().findFileByUrl(jarUrl);
                            if (sourceFile != null && sourceFile.exists()) {
                                System.out.println("[DEBUG] Found direct loom.mappings source: " + jarUrl);
                                PsiFile psiFile = psiManager.findFile(sourceFile);
                                if (psiFile != null) {
                                    // Add as first source to prioritize it
                                    sources.add(0, psiFile);
                                    break; // We found the best source, no need to continue
                                }
                            }
                        }
                    } catch (Exception e) {
                        System.err.println("[ERROR] Error searching for loom.mappings: " + e.getMessage());
                    }
                }
            }

            // If we haven't found a loom.mappings source directly, continue with regular search
            if (sources.isEmpty()) {
                // Look for source files in the project and libraries
                Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(
                        JavaFileType.INSTANCE,
                        GlobalSearchScope.allScope(project)
                );

                System.out.println("[DEBUG] Found " + javaFiles.size() + " total Java files to search");
                List<VirtualFile> matchingFiles = new ArrayList<>();

                for (VirtualFile file : javaFiles) {
                    if (file.getName().equals(className + ".java")) {
                        matchingFiles.add(file);
                    }
                }

                System.out.println("[DEBUG] Found " + matchingFiles.size() + " files with matching name");

                // Process all matching name files
                for (VirtualFile file : matchingFiles) {
                    System.out.println("[DEBUG] Checking file: " + file.getPath());
                    // If we have package info, verify the package matches
                    if (!packageName.isEmpty()) {
                        PsiFile potentialSource = psiManager.findFile(file);
                        if (potentialSource instanceof PsiClassOwner) {
                            PsiClassOwner javaClassOwner = (PsiClassOwner) potentialSource;
                            String filePackage = javaClassOwner.getPackageName();
                            System.out.println("[DEBUG] File package: " + filePackage +
                                    ", matches needed package: " + filePackage.equals(packageName));

                            if (filePackage.equals(packageName)) {
                                sources.add(potentialSource);
                                System.out.println("[DEBUG] Added source: " + file.getPath());

                                // Check if this is a loom.mappings source
                                String path = file.getPath().toLowerCase();
                                if (path.contains("loom") && path.contains("mappings")) {
                                    System.out.println("[DEBUG] Found loom.mappings source via package check");
                                    // Immediately return this source as it's definitely the right one
                                    return potentialSource;
                                }
                            }
                        }
                    } else {
                        // If no package name, just add the file
                        PsiFile sourceFile = psiManager.findFile(file);
                        if (sourceFile != null) {
                            sources.add(sourceFile);
                            System.out.println("[DEBUG] Added source via name match: " + file.getPath());
                        }
                    }
                }
            }

            // If multiple sources found, prioritize ones with "loom" and "mappings"
            if (sources.size() > 1) {
                System.out.println("[DEBUG] Sorting " + sources.size() + " sources based on loom.mappings priority");
                for (PsiFile source : sources) {
                    String path = source.getVirtualFile().getPath().toLowerCase();
                    boolean hasLoomMappings = path.contains("loom") && path.contains("mappings");
                    System.out.println("[DEBUG] Source path: " + path + ", has loom.mappings: " + hasLoomMappings);
                }

                Collections.sort(sources, (f1, f2) -> {
                    String path1 = f1.getVirtualFile().getPath().toLowerCase();
                    String path2 = f2.getVirtualFile().getPath().toLowerCase();

                    boolean f1HasLoomMappings = path1.contains("loom") && path1.contains("mappings");
                    boolean f2HasLoomMappings = path2.contains("loom") && path2.contains("mappings");

                    if (f1HasLoomMappings && !f2HasLoomMappings) {
                        return -1; // f1 comes first
                    } else if (!f1HasLoomMappings && f2HasLoomMappings) {
                        return 1;  // f2 comes first
                    } else {
                        return 0;
                    }
                });

                System.out.println("[DEBUG] Selected source after sorting: " +
                        sources.get(0).getVirtualFile().getPath());
            }

            // Return the first source if any found
            if (!sources.isEmpty()) {
                PsiFile result = sources.get(0);
                System.out.println("[DEBUG] Returning source file: " + result.getVirtualFile().getPath());
                return result;
            } else {
                System.out.println("[DEBUG] No source files found, returning null");
            }
        } catch (Exception e) {
            System.err.println("[ERROR] Error finding source for class file: " + e.getMessage());
            e.printStackTrace();
        }

        return null;
    }

    private void addAnyFile() {
        System.out.println("[DEBUG] Adding any file with chooser");
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
        System.out.println("[DEBUG] Selected " + selectedFiles.length + " files/directories");

        if (selectedFiles.length > 0) {
            // Process files in a background task
            setLoading(true);
            ProgressManager.getInstance().run(new Task.Backgroundable(project, "Processing Files", false) {
                @Override
                public void run(@NotNull ProgressIndicator indicator) {
                    try {
                        indicator.setIndeterminate(true);  // Set to indeterminate to avoid the error
                        Set<VirtualFile> filesToProcess = new HashSet<>();

                        // First, collect all files to process (including recursion through directories)
                        for (VirtualFile selected : selectedFiles) {
                            indicator.setText("Collecting files from " + selected.getName());
                            if (selected.isDirectory()) {
                                System.out.println("[DEBUG] Processing directory: " + selected.getPath());
                                // Recursively collect all files in this directory
                                collectFilesRecursively(selected, filesToProcess);
                            } else {
                                System.out.println("[DEBUG] Adding file: " + selected.getPath());
                                filesToProcess.add(selected);
                            }
                        }

                        System.out.println("[DEBUG] Collected " + filesToProcess.size() + " files to process");

                        // Process all collected files
                        int totalFiles = filesToProcess.size();
                        int processedCount = 0;
                        final List<FileInfo> filesToAdd = new ArrayList<>();

                        for (VirtualFile file : filesToProcess) {
                            processedCount++;
                            indicator.setText("Processing file " + processedCount + "/" + totalFiles + ": " + file.getName());

                            // Skip directories (we should only have files at this point)
                            if (file.isDirectory()) {
                                System.out.println("[DEBUG] Skipping directory: " + file.getPath());
                                continue;
                            }

                            // Skip files outside project scope if we're in project files only mode
                            if (projectFilesOnly && !file.getPath().startsWith(project.getBasePath())) {
                                System.out.println("[DEBUG] Skipping file outside project: " + file.getPath());
                                continue;
                            }

                            try {
                                System.out.println("[DEBUG] Processing file: " + file.getPath() +
                                        ", is class file: " + file.getName().endsWith(".class"));

                                // Process file inside read action
                                ReadAction.run(() -> {
                                    try {
                                        PsiManager psiManager = PsiManager.getInstance(project);

                                        // First create the PsiFile from the virtual file
                                        PsiFile psiFile = psiManager.findFile(file);
                                        if (psiFile == null) {
                                            System.out.println("[DEBUG] Could not create PsiFile for: " + file.getPath());
                                            return;
                                        }

                                        System.out.println("[DEBUG] Created PsiFile type: " + psiFile.getClass().getName());

                                        // If this is a .class file, try to find its source
                                        if (file.getName().endsWith(".class")) {
                                            System.out.println("[DEBUG] Looking for source of class file: " + file.getPath());
                                            PsiFile sourceFile = findSourceForClassFile(file);
                                            if (sourceFile != null) {
                                                // We found a source file, use it instead
                                                System.out.println("[DEBUG] Using found source file: " +
                                                        sourceFile.getVirtualFile().getPath());
                                                psiFile = sourceFile;
                                            } else {
                                                System.out.println("[DEBUG] No source found, using class file directly");

                                                // Save the class file with its classpath info
                                                if (psiFile instanceof PsiClassOwner) {
                                                    String className = file.getNameWithoutExtension();
                                                    String packageName = ((PsiClassOwner) psiFile).getPackageName();
                                                    System.out.println("[DEBUG] Class file info - package: " + packageName +
                                                            ", class: " + className);
                                                }
                                            }
                                        } else if (psiFile != null) {
                                            // For non-class files, check if there's a better source via navigation element
                                            PsiElement sourceElement = psiFile.getNavigationElement();
                                            System.out.println("[DEBUG] Navigation element for non-class file: " +
                                                    (sourceElement == null ? "null" :
                                                            sourceElement.getClass().getName() +
                                                                    " (same as file: " + (sourceElement == psiFile) + ")"));

                                            if (sourceElement != null && sourceElement != psiFile) {
                                                PsiFile betterSource = sourceElement.getContainingFile();
                                                if (betterSource != null) {
                                                    System.out.println("[DEBUG] Using better source from navigation: " +
                                                            betterSource.getVirtualFile().getPath());
                                                    psiFile = betterSource;
                                                }
                                            }
                                        }

                                        // Add the file if we have a valid PsiFile
                                        if (psiFile != null) {
                                            // Create FileInfo with the file's content
                                            String content = psiFile.getText();
                                            System.out.println("[DEBUG] Got content, length: " + content.length());
                                            FileInfo fileInfo = new FileInfo(psiFile, content);
                                            synchronized (filesToAdd) {
                                                filesToAdd.add(fileInfo);
                                                System.out.println("[DEBUG] Added to list: " + fileInfo.getPath());
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.err.println("[ERROR] Error processing file " + file.getPath() + ": " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                });
                            } catch (Exception e) {
                                System.err.println("[ERROR] Error in read action for file " + file.getPath() + ": " + e.getMessage());
                                e.printStackTrace();
                            }
                        }

                        // Update UI on EDT
                        ApplicationManager.getApplication().invokeLater(() -> {
                            // Add all collected files to the model
                            System.out.println("[DEBUG] Adding " + filesToAdd.size() + " files to UI model");
                            for (FileInfo fileInfo : filesToAdd) {
                                if (!fileListModel.getItems().contains(fileInfo)) {
                                    fileListModel.add(fileInfo);
                                    System.out.println("[DEBUG] Added to UI model: " + fileInfo.getPath());
                                } else {
                                    System.out.println("[DEBUG] Already in UI model: " + fileInfo.getPath());
                                }
                            }

                            // Save selections after adding
                            if (!filesToAdd.isEmpty()) {
                                System.out.println("[DEBUG] Saving selections");
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
                        System.err.println("[ERROR] Error processing files: " + e.getMessage());
                        e.printStackTrace();
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
        System.out.println("[DEBUG] Removing " + toRemove.size() + " selected files");
        for (FileInfo info : toRemove) {
            fileListModel.remove(info);
            System.out.println("[DEBUG] Removed: " + info.getPath());
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

    // Save selections with improved handling for class files
    private void saveSelections() {
        List<String> paths = new ArrayList<>();
        System.out.println("[DEBUG] Saving " + fileListModel.getSize() + " files to settings");

        for (FileInfo info : fileListModel.getItems()) {
            String path = info.getPath();
            VirtualFile vFile = info.getVirtualFile();

            // For class files, save classpath information with a special prefix
            if (vFile != null && vFile.exists() && path.endsWith(".class")) {
                try {
                    System.out.println("[DEBUG] Handling class file for saving: " + path);
                    PsiManager psiManager = PsiManager.getInstance(project);
                    PsiFile psiFile = psiManager.findFile(vFile);

                    if (psiFile instanceof PsiClassOwner) {
                        PsiClassOwner classOwner = (PsiClassOwner) psiFile;
                        String classpath = classOwner.getPackageName() + "." + vFile.getNameWithoutExtension();
                        String pathToSave = "classpath:" + classpath;
                        paths.add(pathToSave);
                        System.out.println("[DEBUG] Saved as classpath: " + pathToSave);
                        continue;
                    } else {
                        System.out.println("[DEBUG] Class file not a PsiClassOwner: " +
                                (psiFile != null ? psiFile.getClass().getName() : "null"));
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Error getting classpath for: " + path);
                    e.printStackTrace();
                }
            }

            // Handle special case for jar:// URLs in file paths
            if (path.contains(".jar!/") || path.contains(".jar!/")) {
                try {
                    System.out.println("[DEBUG] Processing JAR file path: " + path);
                    // Get the Java class name from the path
                    int lastSlash = path.lastIndexOf('/');
                    if (lastSlash >= 0) {
                        String fileName = path.substring(lastSlash + 1);
                        if (fileName.endsWith(".java")) {
                            String className = fileName.substring(0, fileName.length() - 5);

                            // Try to determine package name from the path
                            String packagePath = "";
                            String packagePrefix = "!/";
                            int packageStart = path.indexOf(packagePrefix);
                            if (packageStart >= 0) {
                                packagePath = path.substring(packageStart + packagePrefix.length(), lastSlash);
                                packagePath = packagePath.replace('/', '.');

                                // Create a classpath reference
                                String classpath = packagePath + "." + className;
                                String pathToSave = "classpath:" + classpath;
                                paths.add(pathToSave);
                                System.out.println("[DEBUG] Converted JAR path to classpath: " + pathToSave);
                                continue;
                            }
                        }
                    }
                } catch (Exception e) {
                    System.err.println("[ERROR] Error processing JAR path: " + path);
                    e.printStackTrace();
                }
            }

            // For regular files, just save the path
            paths.add(path);
            System.out.println("[DEBUG] Saved regular path: " + path);
        }

        // Save to persistent storage
        System.out.println("[DEBUG] Saving " + paths.size() + " paths to settings with ID: " + panelId);
        PromptSettings.getInstance(project).saveFilePaths(panelId, paths);
    }

    // Start asynchronous loading of saved selections
    public void loadSavedSelectionsAsync() {
        // Skip if we're already loading
        if (isLoading.getAndSet(true)) {
            System.out.println("[DEBUG] Already loading, skipping loadSavedSelectionsAsync");
            return;
        }

        System.out.println("[DEBUG] Starting to load saved selections for panel: " + panelId);
        setLoading(true);

        ProgressManager.getInstance().run(new Task.Backgroundable(project, "Loading Saved Files", false) {
            @Override
            public void run(@NotNull ProgressIndicator indicator) {
                try {
                    // Set to indeterminate to avoid the error
                    indicator.setIndeterminate(true);

                    List<String> paths = PromptSettings.getInstance(project).getFilePaths(panelId);
                    System.out.println("[DEBUG] Loaded " + paths.size() + " paths from settings");

                    if (paths.isEmpty()) {
                        System.out.println("[DEBUG] No paths to load, finishing early");
                        ApplicationManager.getApplication().invokeLater(() -> setLoading(false), ModalityState.any());
                        return;
                    }

                    final List<FileInfo> filesToAdd = new ArrayList<>();
                    int totalPaths = paths.size();

                    for (int i = 0; i < totalPaths; i++) {
                        String path = paths.get(i);
                        // Don't set fraction - indicator.setFraction((double)i / totalPaths);
                        indicator.setText("Loading file " + (i+1) + "/" + totalPaths + ": " + path);
                        System.out.println("[DEBUG] Processing path: " + path);

                        try {
                            // Check if this is a classpath reference
                            if (path.startsWith("classpath:")) {
                                String classpath = path.substring("classpath:".length());
                                System.out.println("[DEBUG] Processing classpath reference: " + classpath);

                                ClassPathInfo classPathInfo = ClassPathInfo.fromString(classpath);
                                System.out.println("[DEBUG] Parsed package: " + classPathInfo.packageName +
                                        ", class: " + classPathInfo.className);

                                // For Minecraft classes, try to directly find the loom.mappings source
                                boolean found = false;

                                if (classPathInfo.packageName.startsWith("net.minecraft")) {
                                    System.out.println("[DEBUG] This is a Minecraft class, trying direct loom.mappings path first");

                                    // Directly try to build the loom.mappings path first for Minecraft classes
                                    String relativeSourcePath = classPathInfo.packageName.replace('.', '/') + "/" + classPathInfo.className + ".java";
                                    String projectPath = project.getBasePath();

                                    if (projectPath != null) {
                                        try {
                                            // Look for any file in the project that matches the loom.mappings pattern
                                            File projectDir = new File(projectPath);
                                            List<String> loomMappingsPaths = new ArrayList<>();

                                            // Use Files.find with depth limit to find loom.mappings sources
                                            Files.find(projectDir.toPath(), 15,
                                                            (p, attrs) -> p.toString().contains("loom.mappings") &&
                                                                    p.toString().endsWith("-sources.jar"))
                                                    .forEach(p -> {
                                                        loomMappingsPaths.add(p.toString());
                                                        System.out.println("[DEBUG] Found loom.mappings source jar: " + p);
                                                    });

                                            // Try each loom.mappings source jar
                                            for (String loomPath : loomMappingsPaths) {
                                                String jarUrl = "jar://" + loomPath.replace('\\', '/') + "!/" + relativeSourcePath;
                                                System.out.println("[DEBUG] Trying direct jar URL: " + jarUrl);

                                                VirtualFile sourceFile = VirtualFileManager.getInstance().findFileByUrl(jarUrl);
                                                if (sourceFile != null && sourceFile.exists()) {
                                                    System.out.println("[DEBUG] Found direct loom.mappings source: " + jarUrl);

                                                    PsiFile psiFile = PsiManager.getInstance(project).findFile(sourceFile);
                                                    if (psiFile != null) {
                                                        FileInfo fileInfo = new FileInfo(psiFile, psiFile.getText());
                                                        synchronized (filesToAdd) {
                                                            filesToAdd.add(fileInfo);
                                                        }
                                                        found = true;
                                                        break; // We found the best source, no need to continue
                                                    }
                                                }
                                            }
                                        } catch (Exception e) {
                                            System.err.println("[ERROR] Error searching for loom.mappings: " + e.getMessage());
                                            e.printStackTrace();
                                        }
                                    }
                                }

                                // If direct loom.mappings search failed, try the regular approach
                                if (!found) {
                                    // First, explicitly try to find the loom.mappings version by path pattern
                                    System.out.println("[DEBUG] Explicitly searching for files by name and package");
                                    Collection<VirtualFile> allFiles = FileTypeIndex.getFiles(
                                            JavaFileType.INSTANCE,
                                            GlobalSearchScope.allScope(project)
                                    );

                                    PsiManager psiManager = PsiManager.getInstance(project);
                                    List<VirtualFile> allMatches = new ArrayList<>();

                                    System.out.println("[DEBUG] Found " + allFiles.size() +
                                            " total Java files to search for " + classPathInfo.className + ".java");

                                    // Find all files that match the name and package, collecting all of them
                                    for (VirtualFile file : allFiles) {
                                        if (file.getName().equals(classPathInfo.className + ".java")) {
                                            String filePath = file.getPath();
                                            boolean hasLoomMappings = filePath.toLowerCase().contains("loom") &&
                                                    filePath.toLowerCase().contains("mappings");

                                            // Log potential matches
                                            System.out.println("[DEBUG] Found name match: " + filePath);

                                            try {
                                                PsiFile psiFile = psiManager.findFile(file);
                                                if (psiFile instanceof PsiClassOwner) {
                                                    PsiClassOwner javaClassOwner = (PsiClassOwner) psiFile;
                                                    String filePackage = javaClassOwner.getPackageName();

                                                    System.out.println("[DEBUG] File package: " + filePackage +
                                                            ", needed package: " + classPathInfo.packageName);

                                                    if (filePackage.equals(classPathInfo.packageName)) {
                                                        System.out.println("[DEBUG] Found exact Java source match: " + filePath);
                                                        allMatches.add(file);
                                                    }
                                                }
                                            } catch (Exception e) {
                                                System.err.println("[ERROR] Error checking potential file: " + e.getMessage());
                                                e.printStackTrace();
                                            }
                                        }
                                    }

                                    // Log how many matches we found
                                    System.out.println("[DEBUG] Found " + allMatches.size() + " total matching files");

                                    if (!allMatches.isEmpty()) {
                                        // First try to find loom mappings version with direct path check
                                        VirtualFile bestMatch = null;
                                        for (VirtualFile match : allMatches) {
                                            String matchPath = match.getPath().toLowerCase();
                                            if (matchPath.contains("loom") && matchPath.contains("mappings")) {
                                                bestMatch = match;
                                                System.out.println("[DEBUG] Selected loom.mappings match: " + match.getPath());
                                                break;
                                            }
                                        }

                                        // If no loom.mappings found, use the first match
                                        if (bestMatch == null) {
                                            bestMatch = allMatches.get(0);
                                            System.out.println("[DEBUG] No loom.mappings found, using first match: " + bestMatch.getPath());
                                        }

                                        // Use the selected match
                                        PsiFile psiFile = psiManager.findFile(bestMatch);
                                        if (psiFile != null) {
                                            FileInfo fileInfo = new FileInfo(psiFile, psiFile.getText());
                                            synchronized (filesToAdd) {
                                                filesToAdd.add(fileInfo);
                                            }
                                            found = true;
                                        }
                                    }
                                }

                                // If still not found, try JavaPsiFacade as a last resort
                                if (!found) {
                                    System.out.println("[DEBUG] No direct file matches found, trying JavaPsiFacade");

                                    try {
                                        // Try using JavaPsiFacade to find class directly
                                        JavaPsiFacade javaPsiFacade = JavaPsiFacade.getInstance(project);
                                        PsiClass foundClass = javaPsiFacade.findClass(
                                                classPathInfo.toString(),
                                                GlobalSearchScope.allScope(project));

                                        if (foundClass != null) {
                                            System.out.println("[DEBUG] Found class via JavaPsiFacade: " + foundClass.getQualifiedName());
                                            PsiFile containingFile = foundClass.getContainingFile();
                                            if (containingFile != null) {
                                                System.out.println("[DEBUG] Found containing file: " + containingFile.getName());
                                                // Get navigation element to find source file if available
                                                PsiElement sourceElement = containingFile.getNavigationElement();
                                                PsiFile sourceFile = sourceElement instanceof PsiFile ?
                                                        (PsiFile)sourceElement :
                                                        (sourceElement != null ? sourceElement.getContainingFile() : null);

                                                // Use source file if found, otherwise use the regular containing file
                                                PsiFile fileToUse = sourceFile != null ? sourceFile : containingFile;
                                                System.out.println("[DEBUG] Using file: " + fileToUse.getName());

                                                FileInfo fileInfo = new FileInfo(fileToUse, fileToUse.getText());
                                                synchronized (filesToAdd) {
                                                    filesToAdd.add(fileInfo);
                                                }
                                                found = true;
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.err.println("[ERROR] Error searching with JavaPsiFacade: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                }

                                if (!found) {
                                    System.out.println("[DEBUG] Could not find any source for classpath: " + classpath);
                                }

                                continue;
                            }

                            // For JAR paths, special handling
                            if (path.contains(".jar!/")) {
                                System.out.println("[DEBUG] Processing JAR path: " + path);

                                // Try to open as virtual file first
                                VirtualFile jarFile = null;
                                try {
                                    // Try to use jar:// protocol
                                    jarFile = StandardFileSystems.jar().findFileByPath(path);
                                    if (jarFile == null) {
                                        // Try to use jar:// protocol with different prefix
                                        if (!path.startsWith("jar://")) {
                                            jarFile = StandardFileSystems.jar().findFileByPath("jar://" + path);
                                        }
                                    }
                                } catch (Exception e) {
                                    System.out.println("[DEBUG] Error accessing JAR file: " + e.getMessage());
                                }

                                if (jarFile != null && jarFile.exists()) {
                                    System.out.println("[DEBUG] Found JAR file: " + jarFile.getPath());
                                    VirtualFile finalJarFile = jarFile;
                                    ReadAction.run(() -> {
                                        PsiManager psiManager = PsiManager.getInstance(project);
                                        PsiFile psiFile = psiManager.findFile(finalJarFile);
                                        if (psiFile != null) {
                                            FileInfo fileInfo = new FileInfo(psiFile, psiFile.getText());
                                            synchronized (filesToAdd) {
                                                filesToAdd.add(fileInfo);
                                            }
                                        } else {
                                            System.out.println("[DEBUG] Could not create PsiFile for JAR entry");
                                        }
                                    });
                                    continue;
                                }

                                // Extract class name for a more basic file search
                                int lastSlash = path.lastIndexOf('/');
                                if (lastSlash >= 0) {
                                    String fileName = path.substring(lastSlash + 1);

                                    // Try to find by class name
                                    ReadAction.run(() -> {
                                        System.out.println("[DEBUG] Searching for file by name: " + fileName);
                                        Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(
                                                JavaFileType.INSTANCE,
                                                GlobalSearchScope.allScope(project)
                                        );

                                        PsiManager psiManager = PsiManager.getInstance(project);
                                        boolean found = false;

                                        for (VirtualFile file : javaFiles) {
                                            if (file.getName().equals(fileName)) {
                                                System.out.println("[DEBUG] Found matching file: " + file.getPath());
                                                PsiFile psiFile = psiManager.findFile(file);
                                                if (psiFile != null) {
                                                    FileInfo fileInfo = new FileInfo(psiFile, psiFile.getText());
                                                    synchronized (filesToAdd) {
                                                        filesToAdd.add(fileInfo);
                                                        found = true;
                                                        break;
                                                    }
                                                }
                                            }
                                        }

                                        if (!found) {
                                            System.out.println("[DEBUG] No files found matching: " + fileName);
                                        }
                                    });
                                }
                                continue;
                            }

                            // For regular paths, try to find the file using local file system
                            System.out.println("[DEBUG] Processing regular file path: " + path);
                            LocalFileSystem fileSystem = LocalFileSystem.getInstance();
                            VirtualFile vFile = fileSystem.findFileByPath(path);

                            if (vFile != null && vFile.exists()) {
                                System.out.println("[DEBUG] Found file in file system: " + vFile.getPath());
                                // Process this file with proper read action
                                ReadAction.run(() -> {
                                    try {
                                        PsiManager psiManager = PsiManager.getInstance(project);

                                        // If this is a class file, try to find its source
                                        if (path.endsWith(".class")) {
                                            System.out.println("[DEBUG] Looking for source of class file: " + path);
                                            PsiFile sourceFile = findSourceForClassFile(vFile);
                                            if (sourceFile != null) {
                                                System.out.println("[DEBUG] Found source for class file: " +
                                                        sourceFile.getVirtualFile().getPath());
                                                FileInfo fileInfo = new FileInfo(sourceFile, sourceFile.getText());
                                                synchronized (filesToAdd) {
                                                    filesToAdd.add(fileInfo);
                                                }
                                                return;
                                            } else {
                                                System.out.println("[DEBUG] No source found for class file");
                                            }
                                        }

                                        // For regular files or if source wasn't found
                                        PsiFile psiFile = psiManager.findFile(vFile);
                                        if (psiFile != null) {
                                            System.out.println("[DEBUG] Using regular file: " + psiFile.getVirtualFile().getPath());
                                            FileInfo fileInfo = new FileInfo(psiFile, psiFile.getText());
                                            synchronized (filesToAdd) {
                                                filesToAdd.add(fileInfo);
                                            }
                                        } else {
                                            System.out.println("[DEBUG] Could not create PsiFile for: " + path);
                                        }
                                    } catch (Exception e) {
                                        System.err.println("[ERROR] Error processing file in read action: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                });
                            } else {
                                System.out.println("[DEBUG] File not found in file system, searching by name: " + path);
                                // If file not found by path, try searching by name
                                ReadAction.run(() -> {
                                    try {
                                        String fileName = path.substring(path.lastIndexOf('/') + 1);
                                        if (fileName.indexOf('\\') >= 0) {
                                            fileName = fileName.substring(fileName.lastIndexOf('\\') + 1);
                                        }
                                        System.out.println("[DEBUG] Extracted file name for search: " + fileName);

                                        // For Java files or class files, try to find by name
                                        boolean isJavaFile = fileName.endsWith(".java");
                                        boolean isClassFile = fileName.endsWith(".class");

                                        if (isJavaFile || isClassFile) {
                                            String searchName = isJavaFile ? fileName :
                                                    fileName.substring(0, fileName.lastIndexOf('.')) + ".java";
                                            System.out.println("[DEBUG] Searching for: " + searchName);

                                            // Search in all project and library files
                                            Collection<VirtualFile> javaFiles = FileTypeIndex.getFiles(
                                                    JavaFileType.INSTANCE,
                                                    GlobalSearchScope.allScope(project)
                                            );
                                            System.out.println("[DEBUG] Found " + javaFiles.size() +
                                                    " total Java files to search");

                                            PsiManager psiManager = PsiManager.getInstance(project);
                                            for (VirtualFile file : javaFiles) {
                                                if (file.getName().equals(searchName)) {
                                                    System.out.println("[DEBUG] Found matching file: " + file.getPath());
                                                    PsiFile psiFile = psiManager.findFile(file);
                                                    if (psiFile != null) {
                                                        FileInfo fileInfo = new FileInfo(psiFile, psiFile.getText());
                                                        synchronized (filesToAdd) {
                                                            filesToAdd.add(fileInfo);
                                                            System.out.println("[DEBUG] Added found file to list");
                                                        }
                                                        break;
                                                    }
                                                }
                                            }
                                        }
                                    } catch (Exception e) {
                                        System.err.println("[ERROR] Error searching for file by name: " + e.getMessage());
                                        e.printStackTrace();
                                    }
                                });
                            }
                        } catch (Exception e) {
                            System.err.println("[ERROR] Error loading file from path " + path + ": " + e.getMessage());
                            e.printStackTrace();
                        }
                    }

                    // Update UI on EDT
                    ApplicationManager.getApplication().invokeLater(() -> {
                        // Add all files to the model
                        System.out.println("[DEBUG] Adding " + filesToAdd.size() + " loaded files to UI model");
                        for (FileInfo fileInfo : filesToAdd) {
                            fileListModel.add(fileInfo);
                            System.out.println("[DEBUG] Added to UI: " + fileInfo.getPath());
                        }
                        setLoading(false);
                    }, ModalityState.any());

                } catch (Exception e) {
                    System.err.println("[ERROR] Error in loadSavedSelectionsAsync: " + e.getMessage());
                    e.printStackTrace();
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