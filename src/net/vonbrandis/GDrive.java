package net.vonbrandis;

import com.google.api.client.http.FileContent;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.model.File;
import com.google.api.services.drive.model.FileList;
import com.google.api.services.drive.model.ParentReference;

import java.io.IOException;
import java.util.ArrayList;
import java.util.Collections;
import java.util.List;

class GDrive {
    public static final String APPLICATION_VND_GOOGLE_APPS_FOLDER = "application/vnd.google-apps.folder";
    public static final String APPLICATION_OCTET_STREAM = "application/octet-stream";
    private Drive service;
    private Logger logger;
    private boolean dryrun;

    public GDrive(Drive service, Logger logger, boolean dryrun) {
        this.service = service;
        this.logger = logger;
        this.dryrun = dryrun;
    }

    public File fetchFolderByID(String parentFolderID, String name) throws IOException {
        logger.debug("Fetching drive folder %s", name);

        Drive.Files.List request = service.files().list();
        if (parentFolderID != null) {
            request.setQ(String.format("title='%s' and '%s' in parents and trashed=False", name, parentFolderID));
        } else {
            request.setQ(String.format("title='%s' and trashed=False", name));
        }

        FileList files = request.execute();
        if (files.getItems().size() > 1) {
            throw new RuntimeException("Ambiguous folder: " + name);
        }
        if (files.getItems().isEmpty()) {
            throw new RuntimeException("No such folder: " + name);
        }
        return files.getItems().get(0);
    }

    public void deleteDriveFile(File driveFile) throws IOException {
        if (dryrun) {
            logger.debug("DRY RUN: Deleting file %s", driveFile.getTitle());
        } else {
            logger.debug("Deleting file %s", driveFile.getTitle());
            service.files().delete(driveFile.getId());
        }
    }

    public List<File> iterateDriveFolder(File parentFolder) throws IOException {
        Drive.Files.List request = service.files().list().setMaxResults(1000).setQ(String.format("'%s' in parents and trashed=False", parentFolder.getId()));
        FileList result;
        List<File> list = new ArrayList<>();
        int rc = 1;
        do {
            logger.debug("Iterating drive folder %s [%d]", parentFolder.getTitle(), rc);
            result = request.execute();
            list.addAll(result.getItems());
            request.setPageToken(result.getNextPageToken());
            rc++;
        } while (result.getNextPageToken() != null && result.getNextPageToken().length() > 0);
        return list;
    }

    public File createDriveFolder(File parentFolder, String name) throws IOException {
        if (dryrun) {
            logger.debug("DRY RUN: Would create folder %s/%s)", parentFolder.getTitle(), name);
            return null;
        } else {
            logger.debug("Creating folder %s/%s)", parentFolder.getTitle(), name);
            File newFile = new File();
            newFile.setTitle(name);
            newFile.setMimeType(APPLICATION_VND_GOOGLE_APPS_FOLDER);
            newFile.setParents(Collections.singletonList(new ParentReference().setId(parentFolder.getId())));
            return service.files().insert(newFile).execute();
        }
    }

    public File createDriveFile(File parentFolder, String driveFileName, java.io.File localFile) throws IOException {
        if (dryrun) {
            logger.debug("DRY RUN: Would create file %s as %s/%s (%d bytes)", localFile, parentFolder.getTitle(), driveFileName, localFile.length());
            return null;
        } else {
            File newFile = new File();
            String mimeType = APPLICATION_OCTET_STREAM;
            newFile.setTitle(driveFileName);
            newFile.setMimeType(mimeType);
            newFile.setParents(Collections.singletonList(new ParentReference().setId(parentFolder.getId())));
            java.io.File fileContent = new java.io.File(localFile.getAbsolutePath());
            FileContent mediaContent = new FileContent(mimeType, fileContent);
            logger.debug("Uploading file %s as %s/%s (%d bytes)", localFile, parentFolder.getTitle(), driveFileName, localFile.length());
            long start = System.currentTimeMillis();
            newFile = service.files().insert(newFile, mediaContent).execute();
            long timeUsed = System.currentTimeMillis() - start;
            logger.debug("Uploaded %d bytes in %.2f seconds", localFile.length(), timeUsed/1000.0);
            return newFile;
        }
    }

    public File updateDriveFile(File driveFile, java.io.File localFile) throws IOException {
        if (dryrun) {
            logger.debug("DRY RUN: Would update file %s (%d bytes)", localFile, localFile.length());
            return driveFile;
        } else {
            java.io.File fileContent = new java.io.File(localFile.getAbsolutePath());
            FileContent mediaContent = new FileContent(driveFile.getMimeType(), fileContent);
            logger.debug("Updating file %s (%d bytes)", driveFile.getTitle(), localFile.length());
            long start = System.currentTimeMillis();
            driveFile = service.files().update(driveFile.getId(), driveFile, mediaContent).execute();
            long timeUsed = System.currentTimeMillis() - start;
            logger.debug("Uploaded %d bytes in %.2f seconds", localFile.length(), timeUsed/1000.0);
            return driveFile;
        }
    }
}
