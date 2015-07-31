package net.vonbrandis.gdrivesync;

import com.google.api.client.auth.oauth2.Credential;
import com.google.api.client.extensions.java6.auth.oauth2.AuthorizationCodeInstalledApp;
import com.google.api.client.extensions.jetty.auth.oauth2.LocalServerReceiver;
import com.google.api.client.googleapis.auth.oauth2.GoogleAuthorizationCodeFlow;
import com.google.api.client.http.HttpTransport;
import com.google.api.client.http.javanet.NetHttpTransport;
import com.google.api.client.json.JsonFactory;
import com.google.api.client.json.jackson2.JacksonFactory;
import com.google.api.client.util.store.DataStoreFactory;
import com.google.api.client.util.store.FileDataStoreFactory;
import com.google.api.services.drive.Drive;
import com.google.api.services.drive.DriveScopes;
import com.google.api.services.drive.model.File;
import gnu.getopt.Getopt;

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class Synchronizer {

    private static final boolean DRY_RUN = false;
    private static final String PROPERTIES_FILE = ".GoogleDriveSync";

    private GDrive service;
    private String localRootFolder;
    private String driveRootFolder;
    private Progress progress;

    public Synchronizer(Progress progress, Drive service, String localFolder, String driveFolder) {
        this.progress = progress;
        this.service = new GDrive(service, progress, DRY_RUN);
        this.localRootFolder = localFolder;
        this.driveRootFolder = driveFolder;
    }

    private static Drive setupGoogleDrive(java.io.File dataStoreLocation, Properties props) throws IOException {
        HttpTransport httpTransport = new NetHttpTransport();
        JsonFactory jsonFactory = new JacksonFactory();

        DataStoreFactory dataStoreFactory = new FileDataStoreFactory(dataStoreLocation);

        String clientID = props.getProperty("clientID");
        String clientSecret = props.getProperty("clientSecret");
        String accountID = props.getProperty("accountID");

        if (accountID == null) {
            throw new IllegalArgumentException("Missing required property: accountID");
        }
        if (clientID == null) {
            throw new IllegalArgumentException("Missing required property: clientID");
        }
        if (clientSecret == null) {
            throw new IllegalArgumentException("Missing required property: clientSecret");
        }

        //setup authorization flow
        GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow
                .Builder(httpTransport, jsonFactory, clientID, clientSecret, Arrays.asList(DriveScopes.DRIVE))
                .setAccessType("offline")
                .setApprovalPrompt("auto")
                .setDataStoreFactory(dataStoreFactory)
                .build();

        //run authorization flow
        Credential credential = new AuthorizationCodeInstalledApp(
                flow,
                new LocalServerReceiver.Builder()
                        .setHost("shell.vonbrandis.net")
                        .setPort(8080)
                        .build()
        ).authorize(accountID);

        //Create a new authorized API client
        return new Drive.Builder(httpTransport, jsonFactory, credential)
                .setApplicationName("GoogleDriveSync")
                .build();
    }

    private static Properties loadPropertiesFile(java.io.File propFile) throws IOException {
        if (!propFile.exists()) {
            throw new IllegalArgumentException("Could not find " + propFile);
        }

        InputStream propStream = new FileInputStream(propFile);
        Properties props = new Properties();
        props.load(propStream);
        System.out.println("Loaded properties from " + propFile);
        return props;
    }

    public static void main(String[] args) {
        try {
            Progress progress = new Progress();

            //determine which files to use
            java.io.File home = new java.io.File(System.getProperty("user.home"));

            String sourcePath = null;
            String destName = null;

            Getopt g = new Getopt("gdrivesync", args, "s:t:dfro");
            int c;
            while ((c = g.getopt()) != -1) {
                switch (c) {
                    case 's':
                        sourcePath = g.getOptarg();
                        break;
                    case 't':
                        destName = g.getOptarg();
                        break;
                    case 'd':
                        progress.setDebug(true);
                        break;
                    case 'f':
                        progress.setFolderSummary(true);
                        break;
                    case 'r':
                        progress.setTransactions(true);
                        break;
                    case 'o':
                        progress.setTotalSummary(false);
                        break;
                    default:
                        throw new IllegalArgumentException("getopt() returned " + c + "\n");
                }
            }

            java.io.File propFile = new java.io.File(home, PROPERTIES_FILE);
            progress.debug("Using properties file " + propFile);

            java.io.File dataStoreLocation = new java.io.File("/tmp/gsync");
            progress.debug("Using datastore " + dataStoreLocation);

            if (sourcePath == null)
                throw new IllegalArgumentException("Source not specified");
            java.io.File sourceFolder = new java.io.File(sourcePath);
            if (!sourceFolder.exists() || !sourceFolder.isDirectory())
                throw new IllegalArgumentException("Source path does not exist!");

            //setup GDrive service
            Properties props = loadPropertiesFile(propFile);
            Drive service = setupGoogleDrive(dataStoreLocation, props);

            //start sync

            progress.log("Synchronizing folder %s to %s", sourceFolder, destName);

            int retries = 5;
            for (int i = 0; i < retries; i++) {
                try {
                    new Synchronizer(progress, service, sourcePath, destName).sync();
                    return;
                } catch (IOException e) {
                    e.printStackTrace();
                    progress.newline();
                    progress.log(String.format("!!!!! Retrying (attempt %d of %d) !!!!!", i + 2, retries));
                    progress.newline();
                }
            }

        } catch (IllegalArgumentException e) {
            System.out.println(e.getMessage());
            System.out.println();
            System.out.println("Usage: java -jar GoogleDriveSync.jar -s <sourcefolder> -t [destinationname] [-d] [-f] [-r] [-o]");
            System.out.println();
            System.out.println("     -d    Enable debugging");
            System.out.println("     -f    Enable folder summaries");
            System.out.println("     -r    Enable transaction details");
            System.out.println("     -o    Disable total summary");
            System.exit(1);
        } catch (Exception e) {
            e.printStackTrace();
            System.exit(2);
        }
    }

    public void sync() throws IOException {
        progress.debug("Starting sync");

        //fetch root folder
        String parentFolderID = null;
        File driveFolder = null;
        for (String elem : this.driveRootFolder.split("/")) {
            progress.debug("<<< Fetching drive folder %s", elem);
            driveFolder = service.fetchFolderByID(parentFolderID, elem);
            parentFolderID = driveFolder.getId();
        }
        assert driveFolder != null;
        progress.debug("Fetched %s", driveFolder.getTitle());

        java.io.File localFolder = new java.io.File(this.localRootFolder);
        syncFolderWithDrive(localFolder, driveFolder);
    }

    private void syncFolderWithDrive(java.io.File localFolder, File driveFolder) throws IOException {
        progress.debug("Syncing folder %s with %s", localFolder, driveFolder.getTitle());

        if (!localFolder.exists() || !localFolder.isDirectory()) {
            throw new RuntimeException("Cannot sync with folder, does not exist: " + localFolder);
        }

        new FolderSynchronizer(service, progress, localFolder, driveFolder).sync();
    }
}
