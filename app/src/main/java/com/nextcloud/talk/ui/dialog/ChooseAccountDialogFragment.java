/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Mario Danic
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2017 Mario Danic <mario@lovelyhq.com>
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
 *
 * Parts related to account import were either copied from or inspired by the great work done by David Luhmer at:
 * https://github.com/nextcloud/ownCloud-Account-Importer
 */

package com.nextcloud.talk.ui.dialog;

import android.annotation.SuppressLint;
import android.app.Dialog;
import android.content.Context;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.view.LayoutInflater;
import android.view.View;
import android.view.ViewGroup;

import com.bluelinelabs.conductor.Controller;
import com.bluelinelabs.conductor.RouterTransaction;
import com.bluelinelabs.conductor.changehandler.VerticalChangeHandler;
import com.nextcloud.talk.controllers.ServerSelectionController;
import com.nextcloud.talk.jobs.AccountRemovalWorker;
import com.facebook.drawee.backends.pipeline.Fresco;
import com.facebook.drawee.interfaces.DraweeController;
import com.google.android.material.dialog.MaterialAlertDialogBuilder;
import com.nextcloud.talk.R;
import com.nextcloud.talk.activities.MainActivity;
import com.nextcloud.talk.adapters.items.AdvancedUserItem;
import com.nextcloud.talk.application.NextcloudTalkApplication;
import com.nextcloud.talk.databinding.DialogChooseAccountBinding;
import com.nextcloud.talk.models.database.User;
import com.nextcloud.talk.models.database.UserEntity;
import com.nextcloud.talk.models.json.participants.Participant;
import com.nextcloud.talk.utils.ApiUtils;
import com.nextcloud.talk.utils.DisplayUtils;
import com.nextcloud.talk.utils.database.user.UserUtils;
import com.yarolegovich.lovelydialog.LovelyStandardDialog;

import java.net.CookieManager;
import java.util.ArrayList;
import java.util.List;

import javax.inject.Inject;

import androidx.annotation.NonNull;
import androidx.fragment.app.DialogFragment;
import androidx.recyclerview.widget.LinearLayoutManager;
import androidx.work.OneTimeWorkRequest;
import androidx.work.WorkManager;
import autodagger.AutoInjector;
import eu.davidea.flexibleadapter.FlexibleAdapter;
import eu.davidea.flexibleadapter.common.SmoothScrollLinearLayoutManager;
import io.reactivex.Observer;
import io.reactivex.disposables.Disposable;

@AutoInjector(NextcloudTalkApplication.class)
public class ChooseAccountDialogFragment extends DialogFragment {
    private static final String TAG = ChooseAccountDialogFragment.class.getSimpleName();
    private static final int ID_REMOVE_ACCOUNT_WARNING_DIALOG = 0;
    private static final String ARG_PARAM1 = "remove_account_shortcut";
    private Controller controller = null;
    private Context context = null;


    @Inject
    UserUtils userUtils;

    @Inject
    CookieManager cookieManager;

    private DialogChooseAccountBinding binding;
    private View dialogView;

    private FlexibleAdapter<AdvancedUserItem> adapter;
    private final List<AdvancedUserItem> userItems = new ArrayList<>();

    private UserEntity currentUser;
    private String credentials;


    @SuppressLint("InflateParams")
    @NonNull
    @Override
    public Dialog onCreateDialog(Bundle savedInstanceState) {
        binding = DialogChooseAccountBinding.inflate(LayoutInflater.from(requireContext()));
        dialogView = binding.getRoot();

        return new MaterialAlertDialogBuilder(requireContext()).setView(dialogView).create();
    }

    @Override
    public void onViewCreated(@NonNull View view, Bundle savedInstanceState) {
        super.onViewCreated(view, savedInstanceState);
        NextcloudTalkApplication.Companion.getSharedApplication().getComponentApplication().inject(this);
        getCurrentUser();

        // Defining user picture
        binding.currentAccount.userIcon.setTag("");

        // Defining user texts, accounts, etc.
        User user = userUtils.getCurrentUser();
        if (user != null) {
            binding.currentAccount.userName.setText(user.getDisplayName());
            binding.currentAccount.ticker.setVisibility(View.GONE);
            binding.currentAccount.account.setText((Uri.parse(user.getBaseUrl()).getHost()));

            if (user.getBaseUrl() != null &&
                    (user.getBaseUrl().startsWith("http://") || user.getBaseUrl().startsWith("https://"))) {
                binding.currentAccount.userIcon.setVisibility(View.VISIBLE);

                DraweeController draweeController = Fresco.newDraweeControllerBuilder()
                        .setOldController(binding.currentAccount.userIcon.getController())
                        .setAutoPlayAnimations(true)
                        .setImageRequest(DisplayUtils.getImageRequestForUrl(
                                ApiUtils.getUrlForAvatarWithName(
                                        user.getBaseUrl(),
                                        user.getUserId(),
                                        R.dimen.small_item_height),
                                null))
                        .build();
                binding.currentAccount.userIcon.setController(draweeController);

            } else {
                binding.currentAccount.userIcon.setVisibility(View.INVISIBLE);
            }
        }

        // Creating listeners for quick-actions
        binding.currentAccount.getRoot().setOnClickListener(v -> dismiss());

        if (getActivity() instanceof MainActivity) {
            binding.addAccount.setOnClickListener(v -> {
                dismiss();
                ((MainActivity) getActivity()).addAccount();
            });
            binding.manageSettings.setOnClickListener(v -> {
                dismiss();
                ((MainActivity) getActivity()).openSettings();
            });
            binding.logout.setOnClickListener(v -> {
                dismiss();
                showRemoveAccountWarning(savedInstanceState);
            });

        }

        if (adapter == null) {
            adapter = new FlexibleAdapter<>(userItems, getActivity(), false);

            UserEntity userEntity;
            Participant participant;

            for (Object userEntityObject : userUtils.getUsers()) {
                userEntity = (UserEntity) userEntityObject;
                if (!userEntity.getCurrent()) {
                    String userId;
                    if (userEntity.getUserId() != null) {
                        userId = userEntity.getUserId();
                    } else {
                        userId = userEntity.getUsername();
                    }

                    participant = new Participant();
                    participant.setActorType(Participant.ActorType.USERS);
                    participant.setActorId(userId);
                    participant.setDisplayName(userEntity.getDisplayName());
                    userItems.add(new AdvancedUserItem(participant, userEntity, null));
                }
            }

            adapter.addListener(onSwitchItemClickListener);
            adapter.updateDataSet(userItems, false);
        }

        prepareViews();
    }


    private void getCurrentUser() {
        currentUser = userUtils.getCurrentUser();
        credentials = ApiUtils.getCredentials(currentUser.getUsername(), currentUser.getToken());
    }


    private void showRemoveAccountWarning(Bundle savedInstanceState) {
        if (getActivity() != null) {
            new LovelyStandardDialog(getActivity(), LovelyStandardDialog.ButtonLayout.HORIZONTAL)
                .setTopColorRes(R.color.nc_darkRed)
                .setIcon(DisplayUtils.getTintedDrawable(getResources(),
                                                        R.drawable.ic_delete_black_24dp, R.color.bg_default))
                .setPositiveButtonColor(getActivity().getResources().getColor(R.color.nc_darkRed))
                .setTitle(R.string.nc_settings_remove_account)
                .setMessage(R.string.nc_settings_remove_confirmation)
                .setPositiveButton(R.string.nc_settings_remove, new View.OnClickListener() {
                    @Override
                    public void onClick(View v) {
                        removeCurrentAccount();
                    }
                })
                .setNegativeButton(R.string.nc_cancel, null)
                .setSavedInstanceState(savedInstanceState)
                .show();
        }
    }

    private void removeCurrentAccount() {
        boolean otherUserExists = userUtils.scheduleUserForDeletionWithId(currentUser.getId());

        OneTimeWorkRequest accountRemovalWork = new OneTimeWorkRequest.Builder(AccountRemovalWorker.class).build();
        WorkManager.getInstance().enqueue(accountRemovalWork);

        if (otherUserExists && getActivity() != null) {
           //todo refresh if multiple accounts exist
        } else if (!otherUserExists) {
            controller.getRouter().setRoot(RouterTransaction.with(
                new ServerSelectionController())
                                    .pushChangeHandler(new VerticalChangeHandler())
                                    .popChangeHandler(new VerticalChangeHandler()));
        }
    }
    private void prepareViews() {
        if (getActivity() != null) {
            LinearLayoutManager layoutManager = new SmoothScrollLinearLayoutManager(getActivity());
            binding.accountsList.setLayoutManager(layoutManager);
        }
        binding.accountsList.setHasFixedSize(true);
        binding.accountsList.setAdapter(adapter);
    }

    @Override
    public View onCreateView(@NonNull LayoutInflater inflater, ViewGroup container, Bundle savedInstanceState) {
        return dialogView;
    }

    @Override
    public void onDestroyView() {
        super.onDestroyView();
        binding = null;
    }

    private final FlexibleAdapter.OnItemClickListener onSwitchItemClickListener =
            new FlexibleAdapter.OnItemClickListener() {
        @Override
        public boolean onItemClick(View view, int position) {
            if (userItems.size() > position) {
                UserEntity userEntity = (userItems.get(position)).getEntity();
                userUtils.createOrUpdateUser(null,
                                             null,
                                             null,
                                             null,
                                             null,
                                             Boolean.TRUE,
                                             null, userEntity.getId(),
                                             null,
                                             null,
                                             null)
                        .subscribe(new Observer<UserEntity>() {
                            @Override
                            public void onSubscribe(@io.reactivex.annotations.NonNull Disposable d) {
                                // unused at the moment
                            }

                            @Override
                            public void onNext(@io.reactivex.annotations.NonNull UserEntity userEntity) {
                                cookieManager.getCookieStore().removeAll();
                                userUtils.disableAllUsersWithoutId(userEntity.getId());
                                if (getActivity() != null) {
                                    getActivity().runOnUiThread(
                                            () -> ((MainActivity) getActivity()).resetConversationsList());
                                }
                                dismiss();
                            }

                            @Override
                            public void onError(@io.reactivex.annotations.NonNull Throwable e) {
                                Log.w(TAG, "Error updating user", e);
                            }

                            @Override
                            public void onComplete() {
                                // DONE
                            }
                        });
            }

            return true;
        }
    };


    public static ChooseAccountDialogFragment newInstance(Boolean param1, Controller controller, Context context) {
        Bundle args = new Bundle();
        args.putBoolean(ARG_PARAM1, param1);
        ChooseAccountDialogFragment frag = new ChooseAccountDialogFragment();
        frag.controller = controller;
        frag.context = context;
        frag.setArguments(args);
        return frag;
    }

}
