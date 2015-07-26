package net.vonbrandis;

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

import java.io.FileInputStream;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import java.util.Properties;

public class Synchronizer {

    private static final boolean DRY_RUN = false;
    private static final String PROPERTIES_FILE = ".GoogleDriveSync";

    private GDrive service;
    private Logger logger;
    private String localRootFolder;
    private String driveRootFolder;

    public Synchronizer(Drive service, String localFolder, String driveFolder) {
        this.logger = new Logger();
        this.service = new GDrive(service, logger, DRY_RUN);
        this.localRootFolder = localFolder;
        this.driveRootFolder = driveFolder;
    }

    public static void main(String[] args) {
        try {
            HttpTransport httpTransport = new NetHttpTransport();
            JsonFactory jsonFactory = new JacksonFactory();
            java.io.File dataStoreLocation = new java.io.File("/tmp/gsync");
            DataStoreFactory dataStoreFactory = new FileDataStoreFactory(new java.io.File("/tmp/gsync"));
            System.out.println("Using datastore " + dataStoreLocation);

            java.io.File home = new java.io.File(System.getProperty("user.home"));
            java.io.File propFile = new java.io.File(home, PROPERTIES_FILE);
            if (!propFile.exists()) {
                System.out.println("Could not find " + propFile);
                System.exit(1);
                return;
            }

            InputStream propStream = new FileInputStream(propFile);
            Properties props = new Properties();
            props.load(propStream);
            System.out.println("Loaded properties from " + propFile);

            String clientID = props.getProperty("clientID");
            String clientSecret = props.getProperty("clientSecret");
            String accountID = props.getProperty("accountID");

            if (accountID == null) {
                System.out.println("Missing required property: accountID");
            }
            if (clientID == null) {
                System.out.println("Missing required property: clientID");
            }
            if (clientSecret == null) {
                System.out.println("Missing required property: clientSecret");
            }
            if (clientID == null || clientSecret == null || accountID == null) {
                System.exit(1);
                return;
            }

            //setup authorization flow
            GoogleAuthorizationCodeFlow flow = new GoogleAuthorizationCodeFlow.Builder(
                    httpTransport, jsonFactory, clientID, clientSecret, Arrays.asList(DriveScopes.DRIVE))
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
            Drive service = new Drive.Builder(httpTransport, jsonFactory, credential)
                    .setApplicationName("GoogleDriveSync")
                    .build();

            //start sync

            if (args.length < 1 || args.length > 2) {
                System.out.println("Usage: java -jar GoogleDriveSync.jar <sourcefolder> [destinationname]");
                System.exit(1);
                return;
            }

            String sourcePath = args[0];
            java.io.File sourceFolder = new java.io.File(sourcePath);

            if (!sourceFolder.exists() || !sourceFolder.isDirectory()) {
                System.out.println("Source path does not exist!");
                System.exit(1);
                return;
            }

            String destName;
            if (args.length == 2) {
                destName = args[1];
            } else {
                destName = sourceFolder.getName();
            }

            System.out.println(String.format("Synchronizing folder %s to %s", sourceFolder, destName));

            new Synchronizer(service, sourcePath, destName).sync();

        } catch (Exception e) {
            e.printStackTrace();
        }
    }

    public void sync() throws IOException {
        logger.debug("Sync");

        //fetch root folder
        String parentFolderID = null;
        File driveFolder = null;
        for (String elem : this.driveRootFolder.split("/")) {
            logger.debug("<<< Fetching drive folder %s", elem);
            driveFolder = service.fetchFolderByID(parentFolderID, elem);
            parentFolderID = driveFolder.getId();
        }
        assert driveFolder != null;
        logger.debug("Fetched %s", driveFolder.getTitle());

        java.io.File localFolder = new java.io.File(this.localRootFolder);
        syncFolderWithDrive(localFolder, driveFolder);
    }

    private void syncFolderWithDrive(java.io.File localFolder, File driveFolder) throws IOException {
        logger.debug("Syncing folder %s with %s", localFolder, driveFolder.getTitle());
        if (!localFolder.exists() || !localFolder.isDirectory()) {
            throw new RuntimeException("Cannot sync with folder, does not exist: " + localFolder);
        }

        new FolderSynchronizer(service, logger, localFolder, driveFolder).sync();
    }
}
