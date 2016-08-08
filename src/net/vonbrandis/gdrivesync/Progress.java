package net.vonbrandis.gdrivesync;

import com.google.api.services.drive.model.File;

public class Progress {

    private int createdFiles;
    private int updatedFiles;
    private int uploadedBytes;
    private int deletedFiles;
    private int createdFolders;
    private long startTime;

    private boolean debug = false;
    private boolean transactions = false;
    private boolean folderSummary = false;
    private boolean totalSummary = true;
    private boolean cancelled = false;

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

    private String formattedTime(long millis) {
        int hours = 0;
        int minutes = 0;
        int seconds = 0;
        while (millis > 3600000) {
            hours++;
            millis -= 3600000;
        }
        while (millis > 60000) {
            minutes++;
            millis -= 60000;
        }
        while (millis > 1000) {
            seconds++;
            millis -= 1000;
        }
        if (hours > 0) {
            return String.format("%dh %dm %ds", hours, minutes, seconds);
        } else if (minutes > 0) {
            return String.format("%dm %ds", minutes, seconds);
        } else {
            return String.format("%ds", seconds);
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
        createdFiles++;
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
        createdFiles++;
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
        long elapsed = System.currentTimeMillis() - startTime;
        System.out.println();
        System.out.println("********* SUMMARY *****************");
        printProperty("Created files", createdFiles);
        printProperty("Created folders", createdFolders);
        printProperty("Updated files", updatedFiles);
        printProperty("Deleted files", deletedFiles);
        printProperty("Uploaded bytes", formattedBytes(uploadedBytes));
        printProperty("Time spent", formattedTime(elapsed));
        printProperty("Average speed", transferSpeed(uploadedBytes, elapsed));
        System.out.println("***********************************");
    }

    private void printProperty(String key, Object value) {
        System.out.println(String.format("%30s: %s", key, value));
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

    public boolean isCancelled() {
        return cancelled;
    }

    public void cancel() {
        this.cancelled = true;
        totalSummary();
    }
}
