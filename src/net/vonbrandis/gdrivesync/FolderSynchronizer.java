package net.vonbrandis.gdrivesync;

import com.google.api.services.drive.model.File;

import java.io.IOException;
import java.util.*;

import static java.lang.String.format;

public class FolderSynchronizer {
    private GDrive service;
    private Progress progress;
    private java.io.File localFolder;
    private File driveFolder;

    private Collection<java.io.File> missing = new ArrayList<>();
    private Collection<String> locallyRemoved = new ArrayList<>();
    private Collection<java.io.File> inSyncFiles = new ArrayList<>();
    private Collection<java.io.File> outdated = new ArrayList<>();
    private Collection<java.io.File> subfolders = new ArrayList<>();
    private Map<String, File> driveFileMap = new HashMap<>();
    private Map<String, java.io.File> localFileMap = new HashMap<>();

    public FolderSynchronizer(GDrive service, Progress progress, java.io.File localFolder, File driveFolder) {
        this.service = service;
        this.progress = progress;
        this.localFolder = localFolder;
        this.driveFolder = driveFolder;
    }

    public void sync() throws IOException {
        //first, create a map of remote names to gdrive file objects
        progress.debug("<<< Iterating drive folder %s", driveFolder.getTitle());
        service.iterateDriveFolder(driveFolder).forEach(f -> driveFileMap.put(f.getTitle(), f));
        //then iterate local files, and synchronize them
        iterateLocalFolder(localFolder).forEach(this::sync);
        //finally, look for remote files that are locally removed
        driveFileMap.keySet().stream().filter(name -> !localFileMap.containsKey(name)).forEach(locallyRemoved::add);
        //summary
        progress.folderSummary("Folder %s:\n %5d insync, %5d missing - %5d outdated - %3d locally removed - %3d subdirs"
                , this.localFolder, inSyncFiles.size(), missing.size(), outdated.size(), locallyRemoved.size(), subfolders.size());

        //upload missing files
        handleMissingFiles();
        //refresh outdated files
        handleOutdatedFiles();
        //delete locally removed files
        handleLocallyRemovedFiles();
        //sync subfolders
        syncSubfolders();
    }

    private void sync(java.io.File localFile) {
        //skip hidden files
        if (localFile.getName().startsWith(".")) {
            progress.debug("Skipping %s", localFile);
            return;
        }
        localFileMap.put(localFile.getName(), localFile);

        if (!driveFileMap.containsKey(localFile.getName())) {
            if (localFile.isDirectory()) {
                subfolders.add(localFile);
            }
            missing.add(localFile);
        } else {
            File driveFile = driveFileMap.get(localFile.getName());
            if (localFile.isDirectory()) {
                subfolders.add(localFile);
                if (!driveFile.getMimeType().equals(GDrive.APPLICATION_VND_GOOGLE_APPS_FOLDER)) {
                    throw new RuntimeException(format("Folder %s is not a folder in drive!", localFile));
                }
            } else if (isOutdated(driveFile, localFile)) {
                outdated.add(localFile);
            } else {
                inSyncFiles.add(localFile);
            }
        }
    }

    private File getDriveFile(String name) {
        if (!driveFileMap.containsKey(name)) throw new RuntimeException("No such item: " + name);
        return driveFileMap.get(name);
    }

    private void syncSubfolders() throws IOException {
        for (java.io.File dir : subfolders) {
            if (progress.isCancelled()) {
                return;
            }
            File driveFolder = getDriveFile(dir.getName());
            new FolderSynchronizer(service, progress, dir, driveFolder).sync();
        }
    }

    private void handleLocallyRemovedFiles() throws IOException {
        for (String name : locallyRemoved) {
            if (progress.isCancelled()) {
                return;
            }
            File driveFile = getDriveFile(name);
            progress.deleteFile(driveFile.getTitle());
            service.deleteDriveFile(driveFile);
        }
    }

    private void handleOutdatedFiles() throws IOException {
        for (java.io.File f : outdated) {
            if (progress.isCancelled()) {
                return;
            }
            File driveFile = getDriveFile(f.getName());
            long start = System.currentTimeMillis();
            progress.updateFile(driveFile, f);
            service.updateDriveFile(driveFile, f);
            long elapsed = System.currentTimeMillis() - start;
            progress.fileUpdated(driveFile, f, elapsed);
        }
    }

    private void handleMissingFiles() throws IOException {
        for (java.io.File f : missing) {
            if (progress.isCancelled()) {
                return;
            }

            File remoteFile;
            if (f.isDirectory()) {
                progress.createDirectory(driveFolder.getTitle() + "/" + f.getName());
                remoteFile = service.createDriveFolder(this.driveFolder, f.getName());
            } else {
                progress.createFile(f, driveFolder);
                long start = System.currentTimeMillis();
                remoteFile = service.createDriveFile(this.driveFolder, f.getName(), f);
                long elapsed = System.currentTimeMillis() - start;
                progress.fileCreated(f, driveFolder, elapsed);
            }
            if (remoteFile != null) driveFileMap.put(f.getName(), remoteFile);
        }
    }


    private List<java.io.File> iterateLocalFolder(java.io.File folder) {
        java.io.File[] files = folder.listFiles();
        if (files == null) return new ArrayList<>();
        return Arrays.asList(files);
    }

    private boolean isOutdated(File driveFile, java.io.File localFile) {
        long driveDate = driveFile.getModifiedDate().getValue();
        long fileDate = localFile.lastModified();
        long driveSize = driveFile.getFileSize();
        return driveDate < fileDate || driveSize != localFile.length();
    }

}
