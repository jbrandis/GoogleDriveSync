package net.vonbrandis.gdrivesync;

import com.google.api.services.drive.model.File;

public class Progress {

    private int uploadedFiles;
    private int updatedFiles;
    private int uploadedBytes;
    private int deletedFiles;
    private int createdFolders;
    private long startTime;

    private boolean debug = false;
    private boolean transactions = false;
    private boolean folderSummary = false;
    private boolean totalSummary = true;

    public Progress() {
        startTime = System.currentTimeMillis();
    }

    public void newline() {
        System.out.println();
    }

    public void createFile(java.io.File localFile, File driveFolder) {
        if (!transactions) return;
        System.out.println(String.format(">>> Uploading file %s as %s/%s (%d bytes)", localFile.getName(), driveFolder.getTitle(), localFile.getName(), localFile.length()));
        uploadedFiles++;
        uploadedBytes += localFile.length();
    }

    public void createDirectory(String folder) {
        if (!transactions) return;
        System.out.println(String.format(">>> Creating folder %s", folder));
        createdFolders++;
    }

    public void updateFile(File driveFile, java.io.File localFile) {
        if (!transactions) return;
        System.out.println(String.format(">>> Updating file %s (%d bytes, %d bytes remote)", driveFile.getTitle(), localFile.length(), driveFile.getFileSize()));
        updatedFiles++;
        uploadedBytes += localFile.length();
    }

    public void deleteFile(String fileName) {
        if (!transactions) return;
        System.out.println(String.format("!!! Deleting file %s", fileName));
        deletedFiles++;
    }

    public void folderSummary(String msg, Object... params) {
        if (!folderSummary) return;
        System.out.println(String.format(msg, params));
    }

    public void totalSummary() {
        if (!totalSummary) return;
        System.out.println();
        System.out.println("*********************************");
        System.out.println(String.format(""));
        System.out.println("*********************************");
    }

    public void log(String msg, Object... params) {
        System.out.println(String.format(msg, params));
    }

    public void debug(String msg, Object... params) {
        if (!debug) return;
        System.out.println(String.format(msg, params));
    }

    public void setDebug(boolean debug) {
        this.debug = debug;
    }

    public void setTransactions(boolean transactions) {
        this.transactions = transactions;
    }

    public void setFolderSummary(boolean folderSummary) {
        this.folderSummary = folderSummary;
    }

    public void setTotalSummary(boolean totalSummary) {
        this.totalSummary = totalSummary;
    }
}
