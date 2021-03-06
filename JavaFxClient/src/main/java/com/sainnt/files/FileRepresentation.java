package com.sainnt.files;


import javafx.collections.ObservableList;

import java.io.File;

public interface FileRepresentation {
    String getPath();

    String getName();

    boolean isFile();

    ObservableList<FileRepresentation> getChildren();

    void copyFileToDirectory(File file);

    void loadContent();

    void rename(String name, Runnable onComplete);

}
