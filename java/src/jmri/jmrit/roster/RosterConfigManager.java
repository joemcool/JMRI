package jmri.jmrit.roster;

import java.beans.PropertyChangeEvent;
import java.io.File;
import java.io.FileNotFoundException;
import java.util.HashMap;
import java.util.Locale;
import java.util.Set;
import java.util.prefs.BackingStoreException;
import java.util.prefs.Preferences;

import javax.annotation.CheckForNull;
import javax.annotation.Nonnull;
import jmri.implementation.FileLocationsPreferences;
import jmri.profile.Profile;
import jmri.profile.ProfileManager;
import jmri.profile.ProfileUtils;
import jmri.spi.PreferencesManager;
import jmri.util.FileUtil;
import jmri.util.FileUtilSupport;
import jmri.util.prefs.AbstractPreferencesManager;
import jmri.util.prefs.InitializationException;
import org.openide.util.lookup.ServiceProvider;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;

/**
 * Load and store the Roster configuration.
 *
 * This only configures the Roster when initialized so that configuration
 * changes made by users do not affect the running instance of JMRI, but only
 * take effect after restarting JMRI.
 *
 * @author Randall Wood (C) 2015
 */
@ServiceProvider(service = PreferencesManager.class)
public class RosterConfigManager extends AbstractPreferencesManager {

    private final HashMap<Profile, String> directory = new HashMap<>();
    private final HashMap<Profile, String> defaultOwner = new HashMap<>();
    private final HashMap<Profile, Roster> rosters = new HashMap<>();

    public static final String DIRECTORY = "directory";
    public static final String DEFAULT_OWNER = "defaultOwner";
    private static final Logger log = LoggerFactory.getLogger(RosterConfigManager.class);

    public RosterConfigManager() {
        log.debug("Roster is {}", this.directory);
        FileUtilSupport.getDefault().addPropertyChangeListener(FileUtil.PREFERENCES, (PropertyChangeEvent evt) -> {
            log.debug("UserFiles changed from {} to {}", evt.getOldValue(), evt.getNewValue());
            Profile project = ProfileManager.getDefault().getActiveProfile();
            if (RosterConfigManager.this.getDirectory(project).equals(evt.getOldValue())) {
                RosterConfigManager.this.setDirectory(project, FileUtil.PREFERENCES);
            }
        });
    }

    @Override
    public void initialize(Profile profile) throws InitializationException {
        if (!this.isInitialized(profile)) {
            Preferences preferences = ProfileUtils.getPreferences(profile, this.getClass(), true);
            this.setDefaultOwner(profile, preferences.get(DEFAULT_OWNER, this.getDefaultOwner(profile)));
            try {
                this.setDirectory(profile, preferences.get(DIRECTORY, this.getDirectory()));
            } catch (IllegalArgumentException ex) {
                this.setInitialized(profile, true);
                throw new InitializationException(
                        Bundle.getMessage(Locale.ENGLISH, "IllegalRosterLocation", preferences.get(DIRECTORY, this.getDirectory())),
                        ex.getMessage(),
                        ex);
            }
            getRoster(profile).setRosterLocation(this.getDirectory());
            this.setInitialized(profile, true);
        }
    }

    @Override
    public void savePreferences(Profile profile) {
        Preferences preferences = ProfileUtils.getPreferences(profile, this.getClass(), true);
        preferences.put(DIRECTORY, FileUtil.getPortableFilename(this.getDirectory()));
        preferences.put(DEFAULT_OWNER, this.getDefaultOwner(profile));
        try {
            preferences.sync();
        } catch (BackingStoreException ex) {
            log.error("Unable to save preferences", ex);
        }
    }

    @Override
    public Set<Class<? extends PreferencesManager>> getRequires() {
        Set<Class<? extends PreferencesManager>> requires = super.getRequires();
        requires.add(FileLocationsPreferences.class);
        return requires;
    }

    /**
     * Get the default owner for the active profile.
     * 
     * @return the default owner
     */
    @Nonnull
    public String getDefaultOwner() {
        return getDefaultOwner(ProfileManager.getDefault().getActiveProfile());
    }

    /**
     * Get the default owner for the specified profile.
     * 
     * @param profile the profile to get the default owner for
     * @return the default owner
     */
    @Nonnull
    public String getDefaultOwner(@CheckForNull Profile profile) {
        String owner = defaultOwner.get(profile);
        // defaultOwner should never be null, but check anyway to ensure its not
        if (owner == null) {
            owner = ""; // NOI18N
            defaultOwner.put(profile, owner);
        }
        return owner;
    }

    /**
     * Set the default owner for the specified profile.
     * 
     * @param profile      the profile to set the default owner for
     * @param defaultOwner the default owner to set
     */
    public void setDefaultOwner(@CheckForNull Profile profile, @CheckForNull String defaultOwner) {
        if (defaultOwner == null) {
            defaultOwner = "";
        }
        String oldDefaultOwner = this.defaultOwner.get(profile);
        this.defaultOwner.put(profile, defaultOwner);
        firePropertyChange(DEFAULT_OWNER, oldDefaultOwner, defaultOwner);
    }

    /**
     * Get the roster directory for the active profile.
     * 
     * @return the directory
     */
    @Nonnull
    public String getDirectory() {
        return getDirectory(ProfileManager.getDefault().getActiveProfile());
    }

    /**
     * Get the roster directory for the specified profile.
     * 
     * @param profile the profile to get the directory for
     * @return the directory
     */
    @Nonnull
    public String getDirectory(@CheckForNull Profile profile) {
        String directory = this.directory.get(profile);
        if (directory == null) {
            directory = FileUtil.PREFERENCES;
        }
        if (FileUtil.PREFERENCES.equals(directory)) {
            return FileUtil.getUserFilesPath();
        }
        return directory;
    }

    /**
     * Set the roster directory for the specified profile.
     * 
     * @param profile   the profile to set the directory for
     * @param directory the directory to set
     */
    public void setDirectory(@CheckForNull Profile profile, @CheckForNull String directory) {
        if (directory == null || directory.isEmpty()) {
            directory = FileUtil.PREFERENCES;
        }
        String oldDirectory = this.directory.get(profile);
        try {
            if (!FileUtil.getFile(directory).isDirectory()) {
                throw new IllegalArgumentException(Bundle.getMessage("IllegalRosterLocation", directory)); // NOI18N
            }
        } catch (FileNotFoundException ex) { // thrown by getFile() if directory does not exist
            throw new IllegalArgumentException(Bundle.getMessage("IllegalRosterLocation", directory)); // NOI18N
        }
        if (!directory.equals(FileUtil.PREFERENCES)) {
            directory = FileUtil.getAbsoluteFilename(directory);
            if (!directory.endsWith(File.separator)) {
                directory = directory + File.separator;
            }
        }
        this.directory.put(profile, directory);
        log.debug("Roster changed from {} to {}", oldDirectory, this.directory);
        firePropertyChange(DIRECTORY, oldDirectory, directory);
    }

    /**
     * Get the roster for the profile.
     * 
     * @param profile the profile to get the roster for
     * @return the roster for the profile
     */
    @Nonnull
    public Roster getRoster(@CheckForNull Profile profile) {
        Roster roster = rosters.get(profile);
        if (roster == null) {
            roster = new Roster();
            rosters.put(profile, roster);
        }
        return roster;
    }

}
