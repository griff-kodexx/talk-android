/*
 * Nextcloud Talk application
 *
 * @author Mario Danic
 * Copyright (C) 2017-2018 Mario Danic <mario@lovelyhq.com>
 *
 * This program is free software: you can redistribute it and/or modify
 * it under the terms of the GNU General Public License as published by
 * the Free Software Foundation, either version 3 of the License, or
 * at your option) any later version.
 *
 * This program is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the
 * GNU General Public License for more details.
 *
 * You should have received a copy of the GNU General Public License
 * along with this program.  If not, see <http://www.gnu.org/licenses/>.
 */

package com.nextcloud.talk.controllers;

import android.annotation.SuppressLint;
import android.database.Cursor;
import android.media.MediaPlayer;
import android.media.RingtoneManager;
import android.net.Uri;
import android.os.Bundle;
import android.os.Handler;
import androidx.annotation.NonNull;
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout;
import androidx.recyclerview.widget.RecyclerView;
import android.text.TextUtils;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.MenuItem;
import android.view.View;
import android.view.ViewGroup;

import com.bluelinelabs.logansquare.LoganSquare;
import com.nextcloud.talk.R;
import com.nextcloud.talk.adapters.items.NotificationSoundItem;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.controllers.base.BaseController;
import com.nextcloud.talk.models.RingtoneSettings;
import com.nextcloud.talk.utils.bundle.BundleKeys;
import com.nextcloud.talk.utils.preferences.AppPreferences;

import java.io.IOException;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import autodagger.AutoInjector;
import butterknife.BindView;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.SelectableAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import eu.davidea.flexibleadapter.items.AbstractFlexibleItem;

@AutoInjector(NextcloudTalkApplication.class)
public class RingtoneSelectionController extends BaseController implements FlexibleAdapter.OnItemClickListener {

    private static final String TAG = "RingtoneSelectionController";

    @BindView(R.id.recycler_view)
    RecyclerView recyclerView;

    @BindView(R.id.swipe_refresh_layout)
    SwipeRefreshLayout swipeRefreshLayout;

    @Inject
    AppPreferences appPreferences;

    private FlexibleAdapter adapter;
    private List<AbstractFlexibleItem> abstractFlexibleItemList = new ArrayList<>();

    private boolean callNotificationSounds = false;
    private MediaPlayer mediaPlayer;
    private Handler cancelMediaPlayerHandler;

    public RingtoneSelectionController(Bundle args) {
        super(args);
        setHasOptionsMenu(true);
        this.callNotificationSounds = args.getBoolean(BundleKeys.KEY_ARE_CALL_SOUNDS, false);
    }

    @Override
    protected View inflateView(@NonNull LayoutInflater inflater, @NonNull ViewGroup container) {
        return inflater.inflate(R.layout.controller_generic_rv, container, false);
    }

    @Override
    protected void onViewBound(@NonNull View view) {
        super.onViewBound(view);
        NextcloudTalkApplication.getSharedApplication().getComponentApplication().inject(this);

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(abstractFlexibleItemList, getActivity(), false);

            adapter.setNotifyChangeOfUnfilteredItems(true)
                    .setMode(SelectableAdapter.Mode.SINGLE);

            adapter.addListener(this);
            fetchNotificationSounds();

            cancelMediaPlayerHandler = new Handler();
        }

        adapter.addListener(this);
        prepareViews();
    }

    @Override
    protected void onAttach(@NonNull View view) {
        super.onAttach(view);

        if (getActionBar() != null) {
            getActionBar().setDisplayHomeAsUpEnabled(true);
        }
    }

    @Override
    public boolean onOptionsItemSelected(@NonNull MenuItem item) {
        switch (item.getItemId()) {
            case android.R.id.home:
                getRouter().popCurrentController();
                return true;
            default:
                return super.onOptionsItemSelected(item);
        }
    }

    private void prepareViews() {
        RecyclerView.LayoutManager layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
        recyclerView.setLayoutManager(layoutManager);
        recyclerView.setHasFixedSize(true);
        recyclerView.setAdapter(adapter);

        swipeRefreshLayout.setEnabled(false);
    }

    @SuppressLint("LongLogTag")
    private void fetchNotificationSounds() {
        abstractFlexibleItemList = new ArrayList<>();
        abstractFlexibleItemList.add(new NotificationSoundItem(getResources().getString(R.string.nc_settings_no_ringtone),
                null));

        String ringtoneString;

        if (callNotificationSounds) {
            ringtoneString = "android.resource://" + getApplicationContext().getPackageName() +
                    "/raw/librem_by_feandesign_call";
        } else {
            ringtoneString = "android.resource://" + getApplicationContext().getPackageName() +
                    "/raw/librem_by_feandesign_message";
        }

        abstractFlexibleItemList.add(new NotificationSoundItem(getResources()
                .getString(R.string.nc_settings_default_ringtone), ringtoneString));

        boolean foundDefault = false;

        String preferencesString = null;
        if ((callNotificationSounds && TextUtils.isEmpty((preferencesString = appPreferences.getCallRingtoneUri())))
                || (!callNotificationSounds && TextUtils.isEmpty((preferencesString = appPreferences
                .getMessageRingtoneUri())))) {
            ((NotificationSoundItem) abstractFlexibleItemList.get(1)).setSelected(true);
            foundDefault = true;
        }


        if (getActivity() != null) {
            RingtoneManager manager = new RingtoneManager(getActivity());

            if (callNotificationSounds) {
                manager.setType(RingtoneManager.TYPE_RINGTONE);
            } else {
                manager.setType(RingtoneManager.TYPE_NOTIFICATION);
            }

            Cursor cursor = manager.getCursor();

            NotificationSoundItem notificationSoundItem;

            while (cursor.moveToNext()) {
                String notificationTitle = cursor.getString(RingtoneManager.TITLE_COLUMN_INDEX);
                String notificationUri = cursor.getString(RingtoneManager.URI_COLUMN_INDEX);
                String completeNotificationUri = notificationUri + "/" + cursor.getString(RingtoneManager
                        .ID_COLUMN_INDEX);

                notificationSoundItem = new NotificationSoundItem(notificationTitle, completeNotificationUri);

                abstractFlexibleItemList.add(notificationSoundItem);

                if (!TextUtils.isEmpty(preferencesString) && !foundDefault) {
                    try {
                        RingtoneSettings ringtoneSettings = LoganSquare.parse(preferencesString, RingtoneSettings.class);
                        if (ringtoneSettings.getRingtoneUri() == null) {
                            ((NotificationSoundItem) abstractFlexibleItemList.get(0)).setSelected(true);
                            foundDefault = true;
                        } else if (completeNotificationUri.equals(ringtoneSettings.getRingtoneUri().toString())) {
                            notificationSoundItem.setSelected(true);
                            foundDefault = true;
                        } else if (ringtoneSettings.getRingtoneUri().toString().equals(ringtoneString)) {
                            ((NotificationSoundItem) abstractFlexibleItemList.get(1)).setSelected(true);
                            foundDefault = true;
                        }
                    } catch (IOException e) {
                        Log.e(TAG, "Failed to parse ringtone settings");
                    }
                }
            }

        }

        adapter.updateDataSet(abstractFlexibleItemList, true);
    }

    @Override
    protected String getTitle() {
        return getResources().getString(R.string.nc_settings_notification_sounds);
    }

    @SuppressLint("LongLogTag")
    @Override
    public boolean onItemClick(View view, int position) {
        NotificationSoundItem notificationSoundItem = (NotificationSoundItem) adapter.getItem(position);

        Uri ringtoneUri = null;

        if (!TextUtils.isEmpty(notificationSoundItem.getNotificationSoundUri())) {
            ringtoneUri = Uri.parse(notificationSoundItem.getNotificationSoundUri());

            endMediaPlayer();
            mediaPlayer = MediaPlayer.create(getActivity(), ringtoneUri);

            cancelMediaPlayerHandler = new Handler();
            cancelMediaPlayerHandler.postDelayed(new Runnable() {
                @Override
                public void run() {
                    endMediaPlayer();
                }
            }, mediaPlayer.getDuration() + 25);
            mediaPlayer.start();
        }

        if (adapter.getSelectedPositions().size() == 0 || adapter.getSelectedPositions().get(0) != position) {
            RingtoneSettings ringtoneSettings = new RingtoneSettings();
            ringtoneSettings.setRingtoneName(notificationSoundItem.getNotificationSoundName());
            ringtoneSettings.setRingtoneUri(ringtoneUri);

            if (callNotificationSounds) {
                try {
                    appPreferences.setCallRingtoneUri(LoganSquare.serialize(ringtoneSettings));
                    toggleSelection(position);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to store selected ringtone for calls");
                }
            } else {
                try {
                    appPreferences.setMessageRingtoneUri(LoganSquare.serialize(ringtoneSettings));
                    toggleSelection(position);
                } catch (IOException e) {
                    Log.e(TAG, "Failed to store selected ringtone for calls");
                }
            }
        }

        return true;
    }

    private void toggleSelection(int position) {
        adapter.toggleSelection(position);
        ((NotificationSoundItem) adapter.getItem(position)).flipItemSelection();

        NotificationSoundItem notificationSoundItem;
        for (int i = 0; i < adapter.getItemCount(); i++) {
            if (i != position) {
                notificationSoundItem = (NotificationSoundItem) adapter.getItem(i);
                notificationSoundItem.flipToFront();
            }
        }
    }

    private void endMediaPlayer() {
        if (cancelMediaPlayerHandler != null) {
            cancelMediaPlayerHandler.removeCallbacksAndMessages(null);
        }

        if (mediaPlayer != null) {
            if (mediaPlayer.isPlaying()) {
                mediaPlayer.stop();
            }

            mediaPlayer.release();
            mediaPlayer = null;
        }
    }

    @Override
    public void onDestroy() {
        endMediaPlayer();
        super.onDestroy();
    }

}
