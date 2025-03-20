package de.keksuccino.promptgenerator.ui;

import com.intellij.openapi.vfs.VirtualFile;
import com.intellij.psi.PsiFile;

import java.util.Objects;

public class FileInfo {
    private final String name;
    private final String path;
    private final String content;
    private final VirtualFile virtualFile;

    public FileInfo(PsiFile psiFile, String content) {
        this.name = psiFile.getName();
        this.path = psiFile.getVirtualFile().getPath();
        this.content = content;
        this.virtualFile = psiFile.getVirtualFile();
    }

    public String getName() {
        return name;
    }

    public String getPath() {
        return path;
    }

    public String getContent() {
        return content;
    }

    public VirtualFile getVirtualFile() {
        return virtualFile;
    }

    @Override
    public boolean equals(Object o) {
        if (this == o) return true;
        if (o == null || getClass() != o.getClass()) return false;
        FileInfo fileInfo = (FileInfo) o;
        return Objects.equals(path, fileInfo.path);
    }

    @Override
    public int hashCode() {
        return Objects.hash(path);
    }

    @Override
    public String toString() {
        return path;
    }
}