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

    private String transferSpeed(long bytes, long millis) {
        double bps = bytes / (millis / 1000.0);
        if (bps > 1000000) {
            return String.format("%.1f MB/s", bps / 1000000);
        } else if (bytes > 1000) {
            return String.format("%.1f KB/s", bps / 1000000);
        } else {
            return String.format("%d B/s", bytes);
        }
    }

    private String formattedBytes(long bytes) {
        if (bytes > 1000000) {
            return String.format("%.1f MB", bytes / 1000000.0);
        } else if (bytes > 1000) {
            return String.format("%.1f KB", bytes / 1000.0);
        } else {
            return String.format("%d bytes", bytes);
        }
    }

    public void createFile(java.io.File localFile, File driveFolder) {
        if (!debug) return;
        System.out.println(String.format(">>> Uploading file %s as %s/%s (%s)",
                localFile.getName(),
                driveFolder.getTitle(),
                localFile.getName(),
                formattedBytes(localFile.length())
        ));
        uploadedFiles++;
        uploadedBytes += localFile.length();
    }

    public void fileCreated(java.io.File localFile, File driveFolder, long millis) {
        if (!transactions) return;
        System.out.println(String.format(">>> Uploaded file %s as %s/%s (%s, %.2f seconds, %s)",
                localFile.getName(),
                driveFolder.getTitle(),
                localFile.getName(),
                formattedBytes(localFile.length()),
                millis / 1000.0,
                transferSpeed(localFile.length(), millis)
        ));
        uploadedFiles++;
        uploadedBytes += localFile.length();
    }

    public void createDirectory(String folder) {
        if (!transactions) return;
        System.out.println(String.format(">>> Creating folder %s", folder));
        createdFolders++;
    }

    public void updateFile(File driveFile, java.io.File localFile) {
        if (!debug) return;
        System.out.println(String.format(">>> Updating file %s (%s, %s remote)",
                driveFile.getTitle(),
                formattedBytes(localFile.length()),
                formattedBytes(driveFile.getFileSize())
        ));
        updatedFiles++;
        uploadedBytes += localFile.length();
    }

    public void fileUpdated(File driveFile, java.io.File localFile, long millis) {
        if (!transactions) return;
        System.out.println(String.format(">>> Updated file %s (%s, %s remote, %.2f seconds, %s)",
                driveFile.getTitle(),
                formattedBytes(localFile.length()),
                formattedBytes(driveFile.getFileSize()),
                millis / 1000.0,
                transferSpeed(localFile.length(), millis)
        ));
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
