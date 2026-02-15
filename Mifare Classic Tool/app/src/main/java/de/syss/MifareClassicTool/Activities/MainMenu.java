/*
 * Copyright 2013 Gerhard Klostermeier
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * (at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */


package de.syss.MifareClassicTool.Activities;

import android.annotation.SuppressLint;
import android.app.Activity;
import android.app.AlertDialog;
import android.content.ActivityNotFoundException;
import android.content.Context;
import android.content.Intent;
import android.content.SharedPreferences;
import android.content.SharedPreferences.Editor;
import android.content.pm.PackageManager;
import android.content.pm.PackageManager.NameNotFoundException;
import android.content.res.AssetManager;
import android.net.Uri;
import android.nfc.NfcAdapter;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.method.LinkMovementMethod;
import android.util.Log;
import android.view.ContextMenu;
import android.view.ContextMenu.ContextMenuInfo;
import android.view.Menu;
import android.view.MenuInflater;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.CheckBox;
import android.widget.EditText;
import android.widget.LinearLayout;
import android.widget.ScrollView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.appcompat.app.AppCompatActivity;
import androidx.appcompat.app.AppCompatDelegate;
import androidx.core.content.pm.PackageInfoCompat;
import androidx.core.text.HtmlCompat;

import java.io.File;
import java.io.FileOutputStream;
import java.io.IOException;
import java.io.InputStream;
import java.io.OutputStream;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.R;


/**
 * Main App entry point showing the main menu.
 * Some stuff about the App:
 * <ul>
 * <li>Error/Debug messages (Log.e()/Log.d()) are hard coded</li>
 * <li>This is my first App, so please by decent with me ;)</li>
 * </ul>
 * @author Gerhard Klostermeier
 */
public class MainMenu extends AppCompatActivity {

    private static final String LOG_TAG =
            MainMenu.class.getSimpleName();

    private final static int FILE_CHOOSER_DUMP_FILE = 1;
    private final static int FILE_CHOOSER_KEY_FILE = 2;
    private final static int FILE_CHOOSER_FAST_CLONE_DUMP = 3;
    private static final int FAST_CLONE_TILE_COUNT = 4;
    private boolean mDonateDialogWasShown = false;
    private boolean mInfoExternalNfcDialogWasShown = false;
    private boolean mHasNoNfc = false;
    private Button mReadTag;
    private Button mWriteTag;
    private Button mKeyEditor;
    private Button mDumpEditor;
    private final Button[] mFastCloneButtons = new Button[FAST_CLONE_TILE_COUNT];
    private Intent mOldIntent = null;
    private int mConfigTileIndex = -1;
    private String mConfigDumpPath = null;
    private String mConfigKeyFiles = null;
    private AlertDialog mConfigDialog = null;

    /**
     * Nodes (stats) MCT passes through during its startup.
     */
    private enum StartUpNode {
        FirstUseDialog, DonateDialog, HasNfc, HasMifareClassicSupport,
        HasNfcEnabled, HasExternalNfc, ExternalNfcServiceRunning,
        HandleNewIntent
    }

    /**
     * Check for NFC hardware and MIFARE Classic support.
     * The directory structure and the std. keys files will be created as well.
     * Also, at the first run of this App, a warning
     * notice and a donate message will be displayed.
     * @see #initFolders()
     * @see #copyStdKeysFiles()
     */
    @SuppressLint("SetTextI18n")
    @Override
    public void onCreate(Bundle savedInstanceState) {
        setTheme();
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_main_menu);

        // Show App version and footer.
        TextView tv = findViewById(R.id.textViewMainFooter);
        tv.setText(getString(R.string.app_version)
                + ": " + Common.getVersionCode());

        // Add the context menu to the tools button.
        Button tools = findViewById(R.id.buttonMainTools);
        registerForContextMenu(tools);

        // Restore state.
        if (savedInstanceState != null) {
            mDonateDialogWasShown = savedInstanceState.getBoolean(
                    "donate_dialog_was_shown");
            mInfoExternalNfcDialogWasShown = savedInstanceState.getBoolean(
                    "info_external_nfc_dialog_was_shown");
            mHasNoNfc = savedInstanceState.getBoolean("has_no_nfc");
            if (android.os.Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                mOldIntent = savedInstanceState.getParcelable("old_intent", Intent.class );
            } else {
                mOldIntent = savedInstanceState.getParcelable("old_intent");
            }

        }

        // Bind main layout buttons.
        mReadTag = findViewById(R.id.buttonMainReadTag);
        mWriteTag = findViewById(R.id.buttonMainWriteTag);
        mKeyEditor = findViewById(R.id.buttonMainEditKeyDump);
        mDumpEditor = findViewById(R.id.buttonMainEditCardDump);

        // Bind fast clone tile buttons.
        int[] fastCloneIds = {
                R.id.buttonMainFastClone0, R.id.buttonMainFastClone1,
                R.id.buttonMainFastClone2, R.id.buttonMainFastClone3
        };
        for (int i = 0; i < FAST_CLONE_TILE_COUNT; i++) {
            mFastCloneButtons[i] = findViewById(fastCloneIds[i]);
            final int tileIndex = i;
            mFastCloneButtons[i].setOnClickListener(
                    v -> onFastCloneTap(tileIndex));
            mFastCloneButtons[i].setOnLongClickListener(v -> {
                onFastCloneLongPress(tileIndex);
                return true;
            });
        }
        updateFastCloneTiles();

        initFolders();
        copyStdKeysFiles();
    }

    /**
     * Save important state data before this activity gets destroyed.
     * @param outState The state to put data into.
     */
    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        outState.putBoolean("donate_dialog_was_shown", mDonateDialogWasShown);
        outState.putBoolean("info_external_nfc_dialog_was_shown", mInfoExternalNfcDialogWasShown);
        outState.putBoolean("has_no_nfc", mHasNoNfc);
        outState.putParcelable("old_intent", mOldIntent);
    }

    /**
     * Each phase of the MCTs startup is called "node" (see {@link StartUpNode})
     * and can be started by this function. The following nodes will be
     * started automatically (e.g. if the "has NFC support?" node is triggered
     * the "has MIFARE classic support?" node will be run automatically
     * after that).
     * @param startUpNode The node of the startup checks chain.
     * @see StartUpNode
     */
    private void runStartUpNode(StartUpNode startUpNode) {
        SharedPreferences sharedPref =
                getPreferences(Context.MODE_PRIVATE);
        Editor sharedEditor = sharedPref.edit();
        switch (startUpNode) {
            case FirstUseDialog:
                boolean isFirstRun = sharedPref.getBoolean(
                        "is_first_run", true);
                if (isFirstRun) {
                    createFirstUseDialog().show();
                } else {
                    runStartUpNode(StartUpNode.HasNfc);
                }
                break;
            case HasNfc:
                Common.setNfcAdapter(NfcAdapter.getDefaultAdapter(this));
                if (Common.getNfcAdapter() == null) {
                    mHasNoNfc = true;
                    runStartUpNode(StartUpNode.HasExternalNfc);
                } else {
                    runStartUpNode(StartUpNode.HasMifareClassicSupport);
                }
                break;
            case HasMifareClassicSupport:
                if (!Common.hasMifareClassicSupport()
                        && !Common.useAsEditorOnly()) {
                    runStartUpNode(StartUpNode.HasExternalNfc);
                } else {
                    runStartUpNode(StartUpNode.HasNfcEnabled);
                }
                break;
            case HasNfcEnabled:
                Common.setNfcAdapter(NfcAdapter.getDefaultAdapter(this));
                if (!Common.getNfcAdapter().isEnabled()) {
                    if (!Common.useAsEditorOnly()) {
                        createNfcEnableDialog().show();
                    } else {
                        runStartUpNode(StartUpNode.DonateDialog);
                    }
                } else {
                    // Use MCT with internal NFC controller.
                    useAsEditorOnly(false);
                    Common.enableNfcForegroundDispatch(this);
                    runStartUpNode(StartUpNode.DonateDialog);
                }
                break;
            case HasExternalNfc:
                if (!Common.hasExternalNfcInstalled(this)
                        && !Common.useAsEditorOnly()) {
                    if (mHasNoNfc) {
                        // Here because the phone is not NFC enabled.
                        createInstallExternalNfcDialog().show();
                    } else {
                        // Here because phone does not support MIFARE Classic.
                        AlertDialog ad = createHasNoMifareClassicSupportDialog();
                        ad.show();
                        // Make links clickable.
                        ((TextView) ad.findViewById(android.R.id.message))
                                .setMovementMethod(
                                        LinkMovementMethod.getInstance());
                    }
                } else {
                    runStartUpNode(StartUpNode.ExternalNfcServiceRunning);
                }
                break;
            case ExternalNfcServiceRunning:
                int isExternalNfcRunning =
                        Common.isExternalNfcServiceRunning(this);
                if (isExternalNfcRunning == 0) {
                    // External NFC is not running.
                    if (!Common.useAsEditorOnly()) {
                        createStartExternalNfcServiceDialog().show();
                    } else {
                        runStartUpNode(StartUpNode.DonateDialog);
                    }
                } else if (isExternalNfcRunning == 1) {
                    // External NFC is running. Use MCT with External NFC.
                    useAsEditorOnly(false);
                    runStartUpNode(StartUpNode.DonateDialog);
                } else {
                    // Can not check if External NFC is running.
                    if (!Common.useAsEditorOnly()
                            && !mInfoExternalNfcDialogWasShown) {
                        createInfoExternalNfcServiceDialog().show();
                        mInfoExternalNfcDialogWasShown = true;
                    } else {
                        runStartUpNode(StartUpNode.DonateDialog);
                    }
                }
                break;
            case DonateDialog:
                if (Common.IS_DONATE_VERSION) {
                    runStartUpNode(StartUpNode.HandleNewIntent);
                    break;
                }
                if (mDonateDialogWasShown) {
                    runStartUpNode(StartUpNode.HandleNewIntent);
                    break;
                }
                int currentVersion = 0;
                try {
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        currentVersion = (int) PackageInfoCompat.getLongVersionCode(
                            getPackageManager().getPackageInfo(getPackageName(),
                                PackageManager.PackageInfoFlags.of(0)));
                    } else {
                        currentVersion = (int) PackageInfoCompat.getLongVersionCode(
                            getPackageManager().getPackageInfo(getPackageName(), 0));
                    }
                } catch (NameNotFoundException e) {
                    Log.d(LOG_TAG, "Version not found.");
                }
                int lastVersion = sharedPref.getInt("mct_version",
                        currentVersion - 1);
                boolean showDonateDialog = sharedPref.getBoolean(
                        "show_donate_dialog", true);

                if (lastVersion < currentVersion || showDonateDialog) {
                    // This is either a new version of MCT or the user
                    // wants to see the donate dialog.
                    if (lastVersion < currentVersion) {
                        // Update the version.
                        sharedEditor.putInt("mct_version", currentVersion);
                        sharedEditor.putBoolean("show_donate_dialog", true);
                        sharedEditor.apply();
                    }
                    createDonateDialog().show();
                    mDonateDialogWasShown = true;
                } else {
                    runStartUpNode(StartUpNode.HandleNewIntent);
                }
                break;
            case HandleNewIntent:
                Common.setPendingComponentName(null);
                Intent intent = getIntent();
                if (intent != null && intent.getAction() != null) {
                    boolean isIntentWithTag = intent.getAction().equals(
                            NfcAdapter.ACTION_TECH_DISCOVERED);
                    if (isIntentWithTag && intent != mOldIntent) {
                        // If MCT was called by another app or the dispatch
                        // system with a tag delivered by intent, handle it as
                        // new tag intent.
                        mOldIntent = intent;
                        onNewIntent(getIntent());
                    } else {
                        // Last node. Do nothing.
                        break;
                    }
                }
                break;
        }
    }

    /**
     * Set whether to use the app in editor only mode or not.
     * @param useAsEditorOnly True if the app should be used in editor
     * only mode.
     */
    private void useAsEditorOnly(boolean useAsEditorOnly) {
        Common.setUseAsEditorOnly(useAsEditorOnly);
        mReadTag.setEnabled(!useAsEditorOnly);
        mWriteTag.setEnabled(!useAsEditorOnly);
    }

    /**
     * Set the theme according to preferences or system settings.
     * Default to "dark" theme before Android 10.
     * Defualt to "follow system" theme for Android 10+.
     */
    private void setTheme() {
        SharedPreferences pref = Common.getPreferences();
        int themeID;
        if (android.os.Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            // Default to dark before Android 10.
            themeID = pref.getInt(Preferences.Preference.CustomAppTheme.toString(), 0);
        } else {
            // Follow system theme for Android 10+.
            themeID = pref.getInt(Preferences.Preference.CustomAppTheme.toString(), 2);
        }
        switch (themeID) {
            case 0:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_YES);
                break;
            case 1:
                AppCompatDelegate.setDefaultNightMode(AppCompatDelegate.MODE_NIGHT_NO);
                break;
        }
    }

    /**
     * Create the dialog which is displayed once the app was started for the
     * first time. After showing the dialog, {@link #runStartUpNode(StartUpNode)}
     * with {@link StartUpNode#HasNfc} will be called.
     * @return The created alert dialog.
     * @see #runStartUpNode(StartUpNode)
     */
    private AlertDialog createFirstUseDialog() {
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_first_run_title)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setMessage(R.string.dialog_first_run)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> dialog.cancel())
                .setOnCancelListener(
                        dialog -> {
                            SharedPreferences sharedPref =
                                    getPreferences(Context.MODE_PRIVATE);
                            Editor sharedEditor = sharedPref.edit();
                            sharedEditor.putBoolean("is_first_run", false);
                            sharedEditor.apply();
                            // Continue with "has NFC" check.
                            runStartUpNode(StartUpNode.HasNfc);
                        })
                .create();
    }

    /**
     * Create the dialog which is displayed if the device does not have
     * MIFARE classic support. After showing the dialog,
     * {@link #runStartUpNode(StartUpNode)} with {@link StartUpNode#DonateDialog}
     * will be called or the app will be exited.
     * @return The created alert dialog.
     * @see #runStartUpNode(StartUpNode)
     */
    private AlertDialog createHasNoMifareClassicSupportDialog() {
        CharSequence styledText = HtmlCompat.fromHtml(
                getString(R.string.dialog_no_mfc_support_device),
                HtmlCompat.FROM_HTML_MODE_LEGACY);
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_no_mfc_support_device_title)
                .setMessage(styledText)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_install_external_nfc,
                (dialog, which) -> {
                    // Open Google Play for the donate version of MCT.
                    Uri uri = Uri.parse(
                            "market://details?id=eu.dedb.nfc.service");
                    Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                    try {
                        startActivity(goToMarket);
                    } catch (ActivityNotFoundException e) {
                        startActivity(new Intent(Intent.ACTION_VIEW,
                                Uri.parse("https://play.google.com/store"
                                        + "/apps/details?id=eu.dedb.nfc"
                                        + ".service")));
                    }
                })
                .setNeutralButton(R.string.action_editor_only,
                        (dialog, which) -> {
                            // Only use Editor.
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNegativeButton(R.string.action_exit_app,
                        (dialog, id) -> {
                            // Exit the App.
                            finish();
                        })
                .setOnCancelListener(
                        dialog -> finish())
                .create();
    }

    /**
     * Create a dialog that send user to NFC settings if NFC is off.
     * Alternatively the user can chose to use the App in editor only
     * mode or exit the App.
     * @return The created alert dialog.
     * @see #runStartUpNode(StartUpNode)
     */
    private AlertDialog createNfcEnableDialog() {
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_nfc_not_enabled_title)
                .setMessage(R.string.dialog_nfc_not_enabled)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setPositiveButton(R.string.action_nfc,
                        (dialog, which) -> {
                            // Goto NFC Settings.
                            startActivity(new Intent(
                                    Settings.ACTION_NFC_SETTINGS));
                        })
                .setNeutralButton(R.string.action_editor_only,
                        (dialog, which) -> {
                            // Only use Editor.
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNegativeButton(R.string.action_exit_app,
                        (dialog, id) -> {
                            // Exit the App.
                            finish();
                        })
                .setOnCancelListener(
                        dialog -> finish())
                .create();
    }

    /**
     * Create the dialog which is displayed if the device has not "External NFC"
     * installed. After showing the dialog, {@link #runStartUpNode(StartUpNode)}
     * with {@link StartUpNode#DonateDialog} will be called or MCT will
     * redirect the user to the play store page of "External NFC"  or
     * the app will be exited.
     * @return The created alert dialog.
     * @see #runStartUpNode(StartUpNode)
     */
    private AlertDialog createInstallExternalNfcDialog() {
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_no_nfc_support_title)
                .setMessage(R.string.dialog_no_nfc_support)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_install_external_nfc,
                        (dialog, which) -> {
                            // Open Google Play for the donate version of MCT.
                            Uri uri = Uri.parse(
                                    "market://details?id=eu.dedb.nfc.service");
                            Intent goToMarket = new Intent(Intent.ACTION_VIEW, uri);
                            try {
                                startActivity(goToMarket);
                            } catch (ActivityNotFoundException e) {
                                startActivity(new Intent(Intent.ACTION_VIEW,
                                        Uri.parse("https://play.google.com/store"
                                                + "/apps/details?id=eu.dedb.nfc"
                                                + ".service")));
                            }
                        })
                .setNeutralButton(R.string.action_editor_only,
                        (dialog, which) -> {
                            // Only use Editor.
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNegativeButton(R.string.action_exit_app,
                        (dialog, id) -> {
                            // Exit the App.
                            finish();
                        })
                .setOnCancelListener(
                        dialog -> finish())
                .create();
    }

    /**
     * Create the dialog which is displayed if the "External NFC" service is
     * not running. After showing the dialog,
     * {@link #runStartUpNode(StartUpNode)} with {@link StartUpNode#DonateDialog}
     * will be called or MCT will redirect the user to the settings of
     * "External NFC" or the app will be exited.
     * @return The created alert dialog.
     * @see #runStartUpNode(StartUpNode)
     */
    private AlertDialog createStartExternalNfcServiceDialog() {
        final Context context = this;
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_start_external_nfc_title)
                .setMessage(R.string.dialog_start_external_nfc)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_start_external_nfc,
                        (dialog, which) -> {
                            useAsEditorOnly(true);
                            Common.openApp(context, "eu.dedb.nfc.service");
                        })
                .setNeutralButton(R.string.action_editor_only,
                        (dialog, which) -> {
                            // Only use Editor.
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNegativeButton(R.string.action_exit_app,
                        (dialog, id) -> {
                            // Exit the App.
                            finish();
                        })
                .setOnCancelListener(
                        dialog -> finish())
                .create();
    }

    /**
     * Create the dialog which is displayed if it is not clear if the
     * "External NFC" service running. After showing the dialog,
     * {@link #runStartUpNode(StartUpNode)} with {@link StartUpNode#DonateDialog}
     * will be called or MCT will redirect the user to the settings of
     * "External NFC".
     * @return The created alert dialog.
     * @see #runStartUpNode(StartUpNode)
     */
    private AlertDialog createInfoExternalNfcServiceDialog() {
        final Context context = this;
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_info_external_nfc_title)
                .setMessage(R.string.dialog_info_external_nfc)
                .setIcon(android.R.drawable.ic_dialog_alert)
                .setPositiveButton(R.string.action_external_nfc_is_running,
                        (dialog, which) -> {
                            // External NFC is running. Do "nothing".
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setNeutralButton(R.string.action_start_external_nfc,
                        (dialog, which) -> Common.openApp(context, "eu.dedb.nfc.service"))
                .setNegativeButton(R.string.action_editor_only,
                        (dialog, id) -> {
                            // Only use Editor.
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .setOnCancelListener(
                        dialog -> {
                            // Only use Editor.
                            useAsEditorOnly(true);
                            runStartUpNode(StartUpNode.DonateDialog);
                        })
                .create();
    }

    /**
     * Create the donate dialog. After showing the dialog,
     * {@link #runStartUpNode(StartUpNode)} with
     * {@link StartUpNode#HandleNewIntent} will be called.
     * @return The created alert dialog.
     * @see #runStartUpNode(StartUpNode)
     */
    private AlertDialog createDonateDialog() {
        View dialogLayout = getLayoutInflater().inflate(
                R.layout.dialog_donate,
                findViewById(android.R.id.content), false);
        TextView textView = dialogLayout.findViewById(
                R.id.textViewDonateDialog);
        textView.setMovementMethod(LinkMovementMethod.getInstance());
        final CheckBox showDonateDialogCheckBox = dialogLayout
                .findViewById(R.id.checkBoxDonateDialog);
        return new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_donate_title)
                .setIcon(android.R.drawable.ic_dialog_info)
                .setView(dialogLayout)
                .setPositiveButton(R.string.action_ok,
                        (dialog, which) -> dialog.cancel())
                .setOnCancelListener(
                        dialog -> {
                            if (showDonateDialogCheckBox.isChecked()) {
                                // Do not show the donate dialog again.
                                SharedPreferences sharedPref =
                                        getPreferences(Context.MODE_PRIVATE);
                                Editor sharedEditor = sharedPref.edit();
                                sharedEditor.putBoolean(
                                        "show_donate_dialog", false);
                                sharedEditor.apply();
                            }
                            runStartUpNode(StartUpNode.HandleNewIntent);
                        })
                .create();
    }

    /**
     * Create the directories needed by MCT and clean out the tmp folder.
     */
    @SuppressLint("ApplySharedPref")
    private void initFolders() {
        // Create keys directory.
        File path = Common.getFile(Common.KEYS_DIR);

        if (!path.exists() && !path.mkdirs()) {
            // Could not create directory.
            Log.e(LOG_TAG, "Error while creating '" + Common.HOME_DIR
                    + "/" + Common.KEYS_DIR + "' directory.");
            return;
        }

        // Create dumps directory.
        path = Common.getFile(Common.DUMPS_DIR);
        if (!path.exists() && !path.mkdirs()) {
            // Could not create directory.
            Log.e(LOG_TAG, "Error while creating '" + Common.HOME_DIR
                    + "/" + Common.DUMPS_DIR + "' directory.");
            return;
        }

        // Create tmp directory.
        path = Common.getFile(Common.TMP_DIR);
        if (!path.exists() && !path.mkdirs()) {
            // Could not create directory.
            Log.e(LOG_TAG, "Error while creating '" + Common.HOME_DIR
                    + Common.TMP_DIR + "' directory.");
            return;
        }
        // Try to clean up tmp directory.
        File[] tmpFiles = path.listFiles();
        if (tmpFiles != null) {
            for (File file : tmpFiles) {
                file.delete();
            }
        }
    }

    /**
     * Add a menu with "preferences", "about", etc. to the Activity.
     */
    @Override
    public boolean onCreateOptionsMenu(Menu menu) {
        // Inflate the menu; this adds items to the action bar if it is present.
        getMenuInflater().inflate(R.menu.main_menu_functions, menu);
        return true;
    }

    /**
     * Add the menu with the tools.
     * It will be shown if the user clicks on "Tools".
     */
    @Override
    public void onCreateContextMenu(ContextMenu menu, View v,
                ContextMenuInfo menuInfo) {
        super.onCreateContextMenu(menu, v, menuInfo);
        MenuInflater inflater = getMenuInflater();
        menu.setHeaderTitle(R.string.dialog_tools_menu_title);
        menu.setHeaderIcon(android.R.drawable.ic_menu_preferences);
        inflater.inflate(R.menu.tools, menu);
        // Enable/Disable tag info tool depending on NFC availability.
        menu.findItem(R.id.menuMainTagInfo).setEnabled(
                !Common.useAsEditorOnly());
        // Enable/Disable UID clone info tool depending on NFC availability.
        menu.findItem(R.id.menuMainCloneUidTool).setEnabled(
                !Common.useAsEditorOnly());
    }

    /**
     * Resume by triggering MCT's startup system
     * ({@link #runStartUpNode(StartUpNode)}).
     * @see #runStartUpNode(StartUpNode)
     */
    @Override
    public void onResume() {
        super.onResume();

        mKeyEditor.setEnabled(true);
        mDumpEditor.setEnabled(true);
        useAsEditorOnly(Common.useAsEditorOnly());
        // The start up nodes will also enable the NFC foreground dispatch if all
        // conditions are met (has NFC & NFC enabled).
        runStartUpNode(StartUpNode.FirstUseDialog);
    }

    /**
     * Disable NFC foreground dispatch system.
     * @see Common#disableNfcForegroundDispatch(Activity)
     */
    @Override
    public void onPause() {
        super.onPause();
        Common.disableNfcForegroundDispatch(this);
    }

    /**
     * Handle new Intent as a new tag Intent and if the tag/device does not
     * support MIFARE Classic, then run {@link TagInfoTool}.
     * @see Common#treatAsNewTag(Intent, android.content.Context)
     * @see TagInfoTool
     */
    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (Common.getPendingComponentName() != null) {
            intent.setComponent(Common.getPendingComponentName());
            startActivity(intent);
        } else {
            int typeCheck = Common.treatAsNewTag(intent, this);
            if (typeCheck == -1 || typeCheck == -2) {
                // Device or tag does not support MIFARE Classic.
                // Run the only thing that is possible: The tag info tool.
                Intent i = new Intent(this, TagInfoTool.class);
                startActivity(i);
            }
        }
    }

    /**
     * Show the {@link ReadTag}.
     * @param view The View object that triggered the method
     * (in this case the read tag button).
     * @see ReadTag
     */
    public void onShowReadTag(View view) {
        Intent intent = new Intent(this, ReadTag.class);
        startActivity(intent);
    }

    /**
     * Show the {@link WriteTag}.
     * @param view The View object that triggered the method
     * (in this case the write tag button).
     * @see WriteTag
     */
    public void onShowWriteTag(View view) {
        Intent intent = new Intent(this, WriteTag.class);
        startActivity(intent);
    }

    /**
     * Show the {@link HelpAndInfo}.
     * @param view The View object that triggered the method
     * (in this case the help/info button).
     */
    public void onShowHelp(View view) {
        Intent intent = new Intent(this, HelpAndInfo.class);
        startActivity(intent);
    }

    /**
     * Show the tools menu (as context menu).
     * @param view The View object that triggered the method
     * (in this case the tools button).
     */
    public void onShowTools(View view) {
        openContextMenu(view);
    }

    /**
     * Open a file chooser ({@link FileChooser}). The
     * Activity result will be processed in
     * {@link #onActivityResult(int, int, Intent)}.
     * If the dump files folder is empty display an additional error
     * message.
     * @param view The View object that triggered the method
     * (in this case the show/edit tag dump button).
     * @see FileChooser
     * @see #onActivityResult(int, int, Intent)
     */
    public void onOpenTagDumpEditor(View view) {
        File file = Common.getFile(Common.DUMPS_DIR);
        if (file.isDirectory() && (file.listFiles() == null
                || file.listFiles().length == 0)) {
            Toast.makeText(this, R.string.info_no_dumps,
                Toast.LENGTH_LONG).show();
        }
        Intent intent = new Intent(this, FileChooser.class);
        intent.putExtra(FileChooser.EXTRA_DIR, file.getAbsolutePath());
        intent.putExtra(FileChooser.EXTRA_TITLE,
                getString(R.string.text_open_dump_title));
        intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT,
                getString(R.string.action_open_dump_file));
        startActivityForResult(intent, FILE_CHOOSER_DUMP_FILE);
    }

    /**
     * Open a file chooser ({@link FileChooser}). The
     * Activity result will be processed in
     * {@link #onActivityResult(int, int, Intent)}.
     * @param view The View object that triggered the method
     * (in this case the show/edit key button).
     * @see FileChooser
     * @see #onActivityResult(int, int, Intent)
     */
    public void onOpenKeyEditor(View view) {
        Intent intent = new Intent(this, FileChooser.class);
        intent.putExtra(FileChooser.EXTRA_DIR,
                Common.getFile(Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(FileChooser.EXTRA_TITLE,
                getString(R.string.text_open_key_file_title));
        intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT,
                getString(R.string.action_open_key_file));
        intent.putExtra(FileChooser.EXTRA_ALLOW_NEW_FILE, true);
        startActivityForResult(intent, FILE_CHOOSER_KEY_FILE);
    }

    /**
     * Show the {@link Preferences}.
     */
    private void onShowPreferences() {
        Intent intent = new Intent(this, Preferences.class);
        startActivity(intent);
    }

    /**
     * Show the about dialog.
     */
    private void onShowAboutDialog() {
        CharSequence styledText = HtmlCompat.fromHtml(
                getString(R.string.dialog_about_mct, Common.getVersionCode()),
                HtmlCompat.FROM_HTML_MODE_LEGACY);
        AlertDialog ad = new AlertDialog.Builder(this)
            .setTitle(R.string.dialog_about_mct_title)
            .setMessage(styledText)
            .setIcon(R.mipmap.ic_launcher)
            .setPositiveButton(R.string.action_ok,
                    (dialog, which) -> {
                        // Do nothing.
                    }).create();
         ad.show();
         // Make links clickable.
         ((TextView)ad.findViewById(android.R.id.message)).setMovementMethod(
                 LinkMovementMethod.getInstance());
    }

    /**
     * Handle the user input from the general options menu
     * (e.g. show the about dialog).
     * @see #onShowAboutDialog()
     * @see #onShowPreferences()
     */
    @Override
    public boolean onOptionsItemSelected(MenuItem item) {
        // Handle item selection.
        int id = item.getItemId();
        if (id == R.id.menuMainPreferences) {
            onShowPreferences();
            return true;
        } else if (id == R.id.menuMainAbout) {
            onShowAboutDialog();
            return true;
        }
        return super.onOptionsItemSelected(item);
    }

    /**
     * Handle (start) the selected tool from the tools menu.
     * @see TagInfoTool
     * @see ValueBlockTool
     * @see AccessConditionTool
     */
    @Override
    public boolean onContextItemSelected(MenuItem item) {
        Intent intent;
        int id = item.getItemId();
        if (id == R.id.menuMainTagInfo) {
            intent = new Intent(this, TagInfoTool.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menuMainValueBlockTool) {
            intent = new Intent(this, ValueBlockTool.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menuMainAccessConditionTool) {
            intent = new Intent(this, AccessConditionTool.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menuMainDiffTool) {
            intent = new Intent(this, DiffTool.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menuMainBccTool) {
            intent = new Intent(this, BccTool.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menuMainCloneUidTool) {
            intent = new Intent(this, CloneUidTool.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menuMainImportExportTool) {
            intent = new Intent(this, ImportExportTool.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menuMainUidLogTool) {
            intent = new Intent(this, UidLogTool.class);
            startActivity(intent);
            return true;
        } else if (id == R.id.menuMainDataConversionTool) {
            intent = new Intent(this, DataConversionTool.class);
            startActivity(intent);
            return true;
        }
        return super.onContextItemSelected(item);
    }

    /**
     * Run the {@link DumpEditor} or the {@link KeyEditor}
     * if file chooser result is O.K.
     * @see DumpEditor
     * @see KeyEditor
     * @see #onOpenTagDumpEditor(View)
     * @see #onOpenKeyEditor(View)
     */
    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);

        switch(requestCode) {
        case FILE_CHOOSER_DUMP_FILE:
            if (resultCode == Activity.RESULT_OK) {
                Intent intent = new Intent(this, DumpEditor.class);
                intent.putExtra(FileChooser.EXTRA_CHOSEN_FILE,
                        data.getStringExtra(
                                FileChooser.EXTRA_CHOSEN_FILE));
                startActivity(intent);
            }
            break;
        case FILE_CHOOSER_KEY_FILE:
            if (resultCode == Activity.RESULT_OK) {
                Intent intent = new Intent(this, KeyEditor.class);
                intent.putExtra(FileChooser.EXTRA_CHOSEN_FILE,
                        data.getStringExtra(
                                FileChooser.EXTRA_CHOSEN_FILE));
                startActivity(intent);
            }
            break;
        case FILE_CHOOSER_FAST_CLONE_DUMP:
            if (resultCode == Activity.RESULT_OK) {
                mConfigDumpPath = data.getStringExtra(
                        FileChooser.EXTRA_CHOSEN_FILE);
            }
            break;
        }
    }

    /**
     * Update the fast clone tile button texts based on saved configuration.
     */
    private void updateFastCloneTiles() {
        SharedPreferences pref = Common.getPreferences();
        for (int i = 0; i < FAST_CLONE_TILE_COUNT; i++) {
            String name = pref.getString("fast_clone_name_" + i, null);
            String dump = pref.getString("fast_clone_dump_" + i, null);
            if (name != null && !name.isEmpty() && dump != null) {
                mFastCloneButtons[i].setText(name);
            } else {
                mFastCloneButtons[i].setText(
                        String.format(getString(R.string.action_fast_clone), i + 1));
            }
        }
    }

    /**
     * Handle a simple tap on a fast clone tile. If the tile is configured,
     * launch the FastCloneActivity. Otherwise, show a toast.
     * @param tileIndex The index of the tile (0-3).
     */
    private void onFastCloneTap(int tileIndex) {
        SharedPreferences pref = Common.getPreferences();
        String dumpPath = pref.getString("fast_clone_dump_" + tileIndex, null);
        String keyFiles = pref.getString("fast_clone_keys_" + tileIndex, null);
        if (dumpPath == null || dumpPath.isEmpty()) {
            Toast.makeText(this, R.string.info_fast_clone_not_configured,
                    Toast.LENGTH_LONG).show();
            return;
        }
        // Check if dump file exists.
        File dumpFile = new File(dumpPath);
        if (!dumpFile.exists()) {
            Toast.makeText(this, R.string.info_fast_clone_dump_not_found,
                    Toast.LENGTH_LONG).show();
            return;
        }
        Intent intent = new Intent(this, FastCloneActivity.class);
        intent.putExtra(FastCloneActivity.EXTRA_DUMP_PATH, dumpPath);
        if (keyFiles != null && !keyFiles.isEmpty()) {
            intent.putExtra(FastCloneActivity.EXTRA_KEY_FILES, keyFiles);
        }
        startActivity(intent);
    }

    /**
     * Handle a long press on a fast clone tile. Show configuration dialog.
     * @param tileIndex The index of the tile (0-3).
     */
    @SuppressLint("SetTextI18n")
    private void onFastCloneLongPress(int tileIndex) {
        mConfigTileIndex = tileIndex;
        SharedPreferences pref = Common.getPreferences();
        mConfigDumpPath = pref.getString("fast_clone_dump_" + tileIndex, null);
        mConfigKeyFiles = pref.getString("fast_clone_keys_" + tileIndex, null);

        // Build dialog layout.
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        int pad = Common.dpToPx(10);
        ll.setPadding(pad, pad, pad, pad);

        // Name field.
        final EditText nameField = new EditText(this);
        nameField.setHint(R.string.dialog_fast_clone_name_hint);
        String savedName = pref.getString("fast_clone_name_" + tileIndex, null);
        if (savedName != null) {
            nameField.setText(savedName);
        }
        ll.addView(nameField);

        // Dump file label.
        final TextView dumpLabel = new TextView(this);
        updateDumpLabel(dumpLabel);
        ll.addView(dumpLabel);

        // Select dump button.
        Button selectDump = new Button(this);
        selectDump.setText(R.string.action_select_dump_file);
        selectDump.setOnClickListener(v -> {
            Intent intent = new Intent(this, FileChooser.class);
            intent.putExtra(FileChooser.EXTRA_DIR,
                    Common.getFile(Common.DUMPS_DIR).getAbsolutePath());
            intent.putExtra(FileChooser.EXTRA_TITLE,
                    getString(R.string.text_open_dump_title));
            intent.putExtra(FileChooser.EXTRA_BUTTON_TEXT,
                    getString(R.string.action_open_dump_file));
            startActivityForResult(intent, FILE_CHOOSER_FAST_CLONE_DUMP);
        });
        ll.addView(selectDump);

        // Key files label.
        final TextView keysLabel = new TextView(this);
        updateKeysLabel(keysLabel);
        ll.addView(keysLabel);

        // Key files selection - show checkboxes for all key files.
        File keysDir = Common.getFile(Common.KEYS_DIR);
        File[] keyFileList = keysDir.listFiles();
        final CheckBox[] keyCheckBoxes;
        if (keyFileList != null && keyFileList.length > 0) {
            java.util.Arrays.sort(keyFileList);
            keyCheckBoxes = new CheckBox[keyFileList.length];
            // Parse currently selected key files.
            java.util.Set<String> selectedKeys = new java.util.HashSet<>();
            if (mConfigKeyFiles != null && !mConfigKeyFiles.isEmpty()) {
                String[] parts = mConfigKeyFiles.split("\\|");
                for (String p : parts) {
                    selectedKeys.add(p);
                }
            }
            for (int i = 0; i < keyFileList.length; i++) {
                keyCheckBoxes[i] = new CheckBox(this);
                keyCheckBoxes[i].setText(keyFileList[i].getName());
                keyCheckBoxes[i].setTag(keyFileList[i].getAbsolutePath());
                keyCheckBoxes[i].setChecked(
                        selectedKeys.contains(keyFileList[i].getAbsolutePath()));
                ll.addView(keyCheckBoxes[i]);
            }
        } else {
            keyCheckBoxes = new CheckBox[0];
        }

        // Wrap in ScrollView.
        ScrollView scrollView = new ScrollView(this);
        scrollView.addView(ll);

        mConfigDialog = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_fast_clone_config_title)
                .setView(scrollView)
                .setPositiveButton(R.string.action_ok, (dialog, which) -> {
                    // Gather selected key files.
                    StringBuilder sb = new StringBuilder();
                    for (CheckBox cb : keyCheckBoxes) {
                        if (cb.isChecked()) {
                            if (sb.length() > 0) sb.append("|");
                            sb.append(cb.getTag().toString());
                        }
                    }
                    mConfigKeyFiles = sb.toString();

                    // Save config.
                    String name = nameField.getText().toString().trim();
                    if (name.isEmpty()) {
                        name = String.format(getString(R.string.action_fast_clone),
                                tileIndex + 1);
                    }
                    Editor editor = pref.edit();
                    editor.putString("fast_clone_name_" + tileIndex, name);
                    if (mConfigDumpPath != null) {
                        editor.putString("fast_clone_dump_" + tileIndex, mConfigDumpPath);
                    }
                    editor.putString("fast_clone_keys_" + tileIndex, mConfigKeyFiles);
                    editor.apply();
                    updateFastCloneTiles();
                    Toast.makeText(this, R.string.info_fast_clone_saved,
                            Toast.LENGTH_SHORT).show();
                })
                .setNeutralButton(R.string.action_clear_tile, (dialog, which) -> {
                    Editor editor = pref.edit();
                    editor.remove("fast_clone_name_" + tileIndex);
                    editor.remove("fast_clone_dump_" + tileIndex);
                    editor.remove("fast_clone_keys_" + tileIndex);
                    editor.apply();
                    mConfigDumpPath = null;
                    mConfigKeyFiles = null;
                    updateFastCloneTiles();
                    Toast.makeText(this, R.string.info_fast_clone_cleared,
                            Toast.LENGTH_SHORT).show();
                })
                .setNegativeButton(R.string.action_cancel,
                        (dialog, which) -> { })
                .create();
        mConfigDialog.show();
    }

    private void updateDumpLabel(TextView label) {
        if (mConfigDumpPath != null && !mConfigDumpPath.isEmpty()) {
            String name = new File(mConfigDumpPath).getName();
            label.setText(String.format(getString(R.string.info_fast_clone_dump_selected), name));
        } else {
            label.setText(getString(R.string.info_fast_clone_no_dump));
        }
    }

    private void updateKeysLabel(TextView label) {
        if (mConfigKeyFiles != null && !mConfigKeyFiles.isEmpty()) {
            String[] parts = mConfigKeyFiles.split("\\|");
            StringBuilder names = new StringBuilder();
            for (String p : parts) {
                if (names.length() > 0) names.append(", ");
                names.append(new File(p).getName());
            }
            label.setText(String.format(getString(R.string.info_fast_clone_keys_selected),
                    names.toString()));
        } else {
            label.setText(String.format(getString(R.string.info_fast_clone_keys_selected), "None"));
        }
    }

    /**
     * Copy the standard key files ({@link Common#STD_KEYS},
     * {@link Common#STD_KEYS_EXTENDED} and {@link Common#STD_HOTEL_KEYS})
     * form assets to {@link Common#KEYS_DIR}.
     * @see Common#KEYS_DIR
     * @see Common#HOME_DIR
     * @see Common#copyFile(InputStream, OutputStream)
     */
    private void copyStdKeysFiles() {
        AssetManager assetManager = getAssets();
        try {
            for (String file : assetManager.list(Common.KEYS_DIR)) {
                String filePath = Common.KEYS_DIR + "/" + file;
                InputStream in = assetManager.open(filePath);
                OutputStream out = new FileOutputStream(Common.getFile(filePath));
                Common.copyFile(in, out);
                in.close();
                out.flush();
                out.close();
            }
        } catch(IOException e) {
            Log.e(LOG_TAG, "Error while copying files from assets.");
        }
    }
}
