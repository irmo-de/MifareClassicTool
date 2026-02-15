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

import android.app.Activity;
import android.app.AlertDialog;
import android.content.Intent;
import android.os.Bundle;
import android.os.Handler;
import android.os.Looper;
import android.util.SparseArray;
import android.view.Gravity;
import android.widget.LinearLayout;
import android.widget.ProgressBar;
import android.widget.TextView;
import android.widget.Toast;

import java.io.File;
import java.util.Collections;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Set;

import de.syss.MifareClassicTool.Common;
import de.syss.MifareClassicTool.MCReader;
import de.syss.MifareClassicTool.R;

/**
 * Fast clone activity that writes a preconfigured dump file to a tag
 * with a single tap. Always writes to manufacturer block (block 0).
 * Launched from the fast clone tiles on the main menu.
 */
public class FastCloneActivity extends BasicActivity {

    public static final String EXTRA_DUMP_PATH =
            "de.syss.MifareClassicTool.Activity.FastClone.DUMP_PATH";
    public static final String EXTRA_KEY_FILES =
            "de.syss.MifareClassicTool.Activity.FastClone.KEY_FILES";

    private static final int CKM_FAST_CLONE = 1;

    private String mDumpPath;
    private String mKeyFiles;
    private HashMap<Integer, HashMap<Integer, byte[]>> mDumpWithPos;
    private HashSet<String> mKeysFromDump;
    private boolean mKeyMapCreated = false;

    @Override
    public void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        // Simple layout showing "present tag" message.
        LinearLayout ll = new LinearLayout(this);
        ll.setOrientation(LinearLayout.VERTICAL);
        ll.setGravity(Gravity.CENTER);
        int pad = Common.dpToPx(30);
        ll.setPadding(pad, pad, pad, pad);

        TextView tv = new TextView(this);
        tv.setText(R.string.text_fast_clone_present_tag);
        tv.setTextSize(22);
        tv.setGravity(Gravity.CENTER);
        ll.addView(tv);

        ProgressBar pb = new ProgressBar(this);
        pb.setIndeterminate(true);
        pb.setPadding(0, Common.dpToPx(20), 0, 0);
        ll.addView(pb);

        setContentView(ll);
        setTitle(R.string.title_activity_fast_clone);

        // Get extras.
        Intent intent = getIntent();
        mDumpPath = intent.getStringExtra(EXTRA_DUMP_PATH);
        mKeyFiles = intent.getStringExtra(EXTRA_KEY_FILES);

        if (mDumpPath == null || mDumpPath.isEmpty()) {
            Toast.makeText(this, R.string.info_fast_clone_not_configured,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        // Read and validate dump.
        File dumpFile = new File(mDumpPath);
        if (!dumpFile.exists()) {
            Toast.makeText(this, R.string.info_fast_clone_dump_not_found,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        String[] dump = Common.readFileLineByLine(dumpFile, false, this);
        if (dump == null) {
            Toast.makeText(this, R.string.info_fast_clone_dump_not_found,
                    Toast.LENGTH_LONG).show();
            finish();
            return;
        }

        int err = Common.isValidDump(dump, false);
        if (err != 0) {
            Common.isValidDumpErrorToast(err, this);
            finish();
            return;
        }

        // Initialize dump data structures.
        initDumpWithPosAndKeysFromDump(dump);
        saveKeysFromDumpToTempKeyFile();
    }

    @Override
    public void onNewIntent(Intent intent) {
        super.onNewIntent(intent);
        if (!mKeyMapCreated) {
            // Tag detected - start key mapping.
            createKeyMapForDump();
        }
    }

    /**
     * Parse dump into sector/block structure and extract keys from sector trailers.
     */
    private void initDumpWithPosAndKeysFromDump(String[] dump) {
        mDumpWithPos = new HashMap<>();
        mKeysFromDump = new HashSet<>();
        int sector = 0;
        int block = 0;
        for (int i = 0; i < dump.length; i++) {
            if (dump[i].startsWith("+")) {
                String[] tmp = dump[i].split(": ");
                sector = Integer.parseInt(tmp[tmp.length - 1]);
                block = 0;
                mDumpWithPos.put(sector, new HashMap<>());
            } else {
                if (i + 1 == dump.length || dump[i + 1].startsWith("+")) {
                    // Sector trailer - extract keys.
                    String keyA = dump[i].substring(0, 12);
                    String keyB = dump[i].substring(20);
                    if (!keyA.contains("-")) {
                        mKeysFromDump.add(keyA);
                    }
                    if (!keyB.contains("-")) {
                        mKeysFromDump.add(keyB);
                    }
                }
                if (!dump[i].contains("-")) {
                    mDumpWithPos.get(sector).put(block++,
                            Common.hex2Bytes(dump[i]));
                } else {
                    block++;
                }
            }
        }
    }

    /**
     * Save keys extracted from the dump to a temporary key file for KeyMapCreator.
     */
    private void saveKeysFromDumpToTempKeyFile() {
        if (mKeysFromDump == null || mKeysFromDump.isEmpty()) {
            return;
        }
        String[] keys = mKeysFromDump.toArray(new String[0]);
        File file = Common.getFile(Common.TMP_DIR + "/keys_from_dump.keys");
        Common.saveFile(file, keys, false);
    }

    /**
     * Launch KeyMapCreator to create a key map for the dump sectors.
     */
    private void createKeyMapForDump() {
        mKeyMapCreated = true;
        Intent intent = new Intent(this, KeyMapCreator.class);
        intent.putExtra(KeyMapCreator.EXTRA_KEYS_DIR,
                Common.getFile(Common.KEYS_DIR).getAbsolutePath());
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER, false);
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_FROM,
                (int) Collections.min(mDumpWithPos.keySet()));
        intent.putExtra(KeyMapCreator.EXTRA_SECTOR_CHOOSER_TO,
                (int) Collections.max(mDumpWithPos.keySet()));
        intent.putExtra(KeyMapCreator.EXTRA_BUTTON_TEXT,
                getString(R.string.action_create_key_map_and_write_dump));
        intent.putExtra(KeyMapCreator.EXTRA_USE_KEYS_FROM_DUMP, true);
        startActivityForResult(intent, CKM_FAST_CLONE);
    }

    @Override
    public void onActivityResult(int requestCode, int resultCode, Intent data) {
        super.onActivityResult(requestCode, resultCode, data);
        if (requestCode == CKM_FAST_CLONE) {
            if (resultCode == Activity.RESULT_OK) {
                checkDumpAgainstTag();
            } else {
                mKeyMapCreated = false;
                if (resultCode == 4) {
                    Toast.makeText(this, R.string.info_strange_error,
                            Toast.LENGTH_LONG).show();
                }
            }
        }
    }

    /**
     * Check the dump against the tag and write it.
     * Always enables manufacturer block writing.
     */
    private void checkDumpAgainstTag() {
        MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            Toast.makeText(this, R.string.info_tag_lost_check_dump,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Size check.
        if (reader.getSectorCount() - 1 < Collections.max(mDumpWithPos.keySet())) {
            Toast.makeText(this, R.string.info_tag_too_small,
                    Toast.LENGTH_LONG).show();
            reader.close();
            return;
        }

        // Check writability.
        final SparseArray<byte[][]> keyMap = Common.getKeyMap();
        HashMap<Integer, int[]> dataPos = new HashMap<>(mDumpWithPos.size());
        for (int sector : mDumpWithPos.keySet()) {
            int idx = 0;
            int[] blocks = new int[mDumpWithPos.get(sector).size()];
            for (int block : mDumpWithPos.get(sector).keySet()) {
                blocks[idx++] = block;
            }
            dataPos.put(sector, blocks);
        }
        HashMap<Integer, HashMap<Integer, Integer>> writeOnPos =
                reader.isWritableOnPositions(dataPos, keyMap);
        reader.close();

        if (writeOnPos == null) {
            Toast.makeText(this, R.string.info_tag_lost_check_dump,
                    Toast.LENGTH_LONG).show();
            return;
        }

        // Build safe write positions (skip unwritable blocks).
        final HashMap<Integer, HashMap<Integer, Integer>> writeOnPosSafe = new HashMap<>();
        HashSet<Integer> sectors = new HashSet<>();
        for (int sector : mDumpWithPos.keySet()) {
            if (keyMap.indexOfKey(sector) >= 0) {
                sectors.add(sector);
            }
        }

        for (int sector : sectors) {
            if (writeOnPos.get(sector) == null) {
                continue;
            }
            byte[][] keys = keyMap.get(sector);
            Set<Integer> blocks = mDumpWithPos.get(sector).keySet();
            for (int block : blocks) {
                boolean isSafeForWriting = true;

                // Manufacturer block: always write (fast clone mode).
                // No special skip for block 0.

                int writeInfo = writeOnPos.get(sector).get(block);
                switch (writeInfo) {
                    case 0:
                        isSafeForWriting = false;
                        break;
                    case 1:
                        if (keys[0] == null) isSafeForWriting = false;
                        break;
                    case 2:
                        if (keys[1] == null) isSafeForWriting = false;
                        break;
                    case 3:
                        writeInfo = (keys[0] != null) ? 1 : 2;
                        break;
                    case 4:
                        if (keys[0] == null) isSafeForWriting = false;
                        break;
                    case 5:
                        if (keys[1] == null) isSafeForWriting = false;
                        break;
                    case 6:
                        if (keys[1] == null) isSafeForWriting = false;
                        break;
                    case -1:
                        isSafeForWriting = false;
                        break;
                }
                if (isSafeForWriting) {
                    if (writeOnPosSafe.get(sector) == null) {
                        HashMap<Integer, Integer> blockInfo = new HashMap<>();
                        blockInfo.put(block, writeInfo);
                        writeOnPosSafe.put(sector, blockInfo);
                    } else {
                        writeOnPosSafe.get(sector).put(block, writeInfo);
                    }
                }
            }
        }

        writeDump(writeOnPosSafe, keyMap);
    }

    /**
     * Write the dump to the tag.
     */
    private void writeDump(
            final HashMap<Integer, HashMap<Integer, Integer>> writeOnPos,
            final SparseArray<byte[][]> keyMap) {
        if (writeOnPos.isEmpty()) {
            Toast.makeText(this, R.string.info_nothing_to_write,
                    Toast.LENGTH_LONG).show();
            return;
        }

        final MCReader reader = Common.checkForTagAndCreateReader(this);
        if (reader == null) {
            return;
        }

        // Display progress.
        LinearLayout ll = new LinearLayout(this);
        int pad = Common.dpToPx(20);
        ll.setPadding(pad, pad, pad, pad);
        ll.setGravity(Gravity.CENTER);
        ProgressBar progressBar = new ProgressBar(this);
        progressBar.setIndeterminate(true);
        progressBar.setPadding(0, 0, Common.dpToPx(20), 0);
        TextView tv = new TextView(this);
        tv.setText(getString(R.string.dialog_wait_write_tag));
        tv.setTextSize(18);
        ll.addView(progressBar);
        ll.addView(tv);
        final AlertDialog warning = new AlertDialog.Builder(this)
                .setTitle(R.string.dialog_wait_write_tag_title)
                .setView(ll)
                .create();
        warning.show();

        final Activity a = this;
        final Handler handler = new Handler(Looper.getMainLooper());
        new Thread(() -> {
            for (int sector : writeOnPos.keySet()) {
                byte[][] keys = keyMap.get(sector);
                for (int block : writeOnPos.get(sector).keySet()) {
                    byte[] writeKey = null;
                    boolean useAsKeyB = true;
                    int wi = writeOnPos.get(sector).get(block);
                    if (wi == 1 || wi == 4) {
                        writeKey = keys[0];
                        useAsKeyB = false;
                    } else if (wi == 2 || wi == 5 || wi == 6) {
                        writeKey = keys[1];
                    }

                    int result = 0;
                    for (int i = 0; i < 2; i++) {
                        result = reader.writeBlock(sector, block,
                                mDumpWithPos.get(sector).get(block),
                                writeKey, useAsKeyB);
                        if (result == 0) {
                            break;
                        }
                    }

                    if (result != 0) {
                        handler.post(() -> Toast.makeText(a,
                                R.string.info_write_error,
                                Toast.LENGTH_LONG).show());
                        reader.close();
                        warning.cancel();
                        return;
                    }
                }
            }
            reader.close();
            warning.cancel();
            handler.post(() -> Toast.makeText(a, R.string.info_write_successful,
                    Toast.LENGTH_LONG).show());
            a.finish();
        }).start();
    }
}
