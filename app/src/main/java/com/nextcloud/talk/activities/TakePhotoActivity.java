/*
 * Nextcloud Talk application
 *
 * @author Andy Scherzinger
 * @author Stefan Niedermann
 * Copyright (C) 2021 Andy Scherzinger <info@andy-scherzinger.de>
 * Copyright (C) 2021 Stefan Niedermann <info@niedermann.it>
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

package com.nextcloud.talk.activities;

import android.content.Context;
import android.content.Intent;
import android.net.Uri;
import android.os.Bundle;
import android.util.Log;
import android.util.Size;
import android.view.OrientationEventListener;
import android.view.Surface;
import android.widget.Toast;

import com.google.common.util.concurrent.ListenableFuture;
import com.nextcloud.talk.databinding.ActivityTakePictureBinding;
import com.nextcloud.talk.models.TakePictureViewModel;
import com.nextcloud.talk.utils.FileUtils;

import java.io.File;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.Locale;
import java.util.concurrent.ExecutionException;

import androidx.annotation.NonNull;
import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
import androidx.camera.core.Camera;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.core.content.ContextCompat;
import androidx.lifecycle.ViewModelProvider;

public class TakePhotoActivity extends AppCompatActivity {

    private static final String TAG = TakePhotoActivity.class.getSimpleName();

    private ActivityTakePictureBinding binding;
    private TakePictureViewModel viewModel;

    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private OrientationEventListener orientationEventListener;

    private SimpleDateFormat dateFormat = new SimpleDateFormat("yyyy-MM-dd HH-mm-ss", Locale.ROOT);

    @Override
    protected void onCreate(@Nullable Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);

        binding = ActivityTakePictureBinding.inflate(getLayoutInflater());
        viewModel = new ViewModelProvider(this).get(TakePictureViewModel.class);

        setContentView(binding.getRoot());

        cameraProviderFuture = ProcessCameraProvider.getInstance(this);
        cameraProviderFuture.addListener(() -> {
            try {
                final ProcessCameraProvider cameraProvider = cameraProviderFuture.get();
                final Preview preview = getPreview();
                final ImageCapture imageCapture = getImageCapture();
                final Camera camera = cameraProvider.bindToLifecycle(this, viewModel.getCameraSelector(), imageCapture, preview);

                viewModel.getCameraSelectorToggleButtonImageResource().observe(this, res -> binding.switchCamera.setImageDrawable(ContextCompat.getDrawable(this, res)));
                viewModel.getTorchToggleButtonImageResource().observe(this, res -> binding.toggleTorch.setImageDrawable(ContextCompat.getDrawable(this, res)));
                viewModel.isTorchEnabled().observe(this, enabled -> camera.getCameraControl().enableTorch(enabled));

                binding.toggleTorch.setOnClickListener((v) -> viewModel.toggleTorchEnabled());
                binding.switchCamera.setOnClickListener((v) -> {
                    viewModel.toggleCameraSelector();
                    cameraProvider.unbindAll();
                    cameraProvider.bindToLifecycle(this, viewModel.getCameraSelector(), imageCapture, preview);
                });
            } catch (IllegalArgumentException | ExecutionException | InterruptedException e) {
                Log.e(TAG, "Error taking picture", e);
                Toast.makeText(this, e.getMessage(), Toast.LENGTH_LONG).show();
                finish();
            }
        }, ContextCompat.getMainExecutor(this));
    }

    private ImageCapture getImageCapture() {
        final ImageCapture imageCapture = new ImageCapture.Builder().setTargetResolution(new Size(720, 1280)).build();

        orientationEventListener = new OrientationEventListener(this) {
            @Override
            public void onOrientationChanged(int orientation) {
                int rotation;

                // Monitors orientation values to determine the target rotation value
                if (orientation >= 45 && orientation < 135) {
                    rotation = Surface.ROTATION_270;
                } else if (orientation >= 135 && orientation < 225) {
                    rotation = Surface.ROTATION_180;
                } else if (orientation >= 225 && orientation < 315) {
                    rotation = Surface.ROTATION_90;
                } else {
                    rotation = Surface.ROTATION_0;
                }

                imageCapture.setTargetRotation(rotation);
            }
        };
        orientationEventListener.enable();

        binding.takePhoto.setOnClickListener((v) -> {
            binding.takePhoto.setEnabled(false);
            final String photoFileName = dateFormat.format(new Date())+ ".jpg";
            try {
                final File photoFile = FileUtils.getTempCacheFile(this, "photos/" + photoFileName);
                final ImageCapture.OutputFileOptions options = new ImageCapture.OutputFileOptions.Builder(photoFile).build();
                imageCapture.takePicture(options, ContextCompat.getMainExecutor(this), new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(@NonNull ImageCapture.OutputFileResults outputFileResults) {
                        final Uri savedUri = Uri.fromFile(photoFile);
                        Log.i(TAG, "onImageSaved - savedUri:" + savedUri.toString());
                        setResult(RESULT_OK, new Intent().setDataAndType(savedUri, "image/jpeg"));
                        finish();
                    }

                    @Override
                    public void onError(@NonNull ImageCaptureException e) {
                        Log.e(TAG, "Error", e);
                        //noinspection ResultOfMethodCallIgnored
                        photoFile.delete();
                        binding.takePhoto.setEnabled(true);
                    }
                });
            } catch (Exception e) {
                // TODO replace string with placeholder
                Toast.makeText(this, "Error taking picture", Toast.LENGTH_SHORT).show();
            }
        });

        return imageCapture;
    }

    private Preview getPreview() {
        Preview preview = new Preview.Builder().build();
        preview.setSurfaceProvider(binding.preview.getSurfaceProvider());
        return preview;
    }

    @Override
    protected void onPause() {
        if (this.orientationEventListener != null) {
            this.orientationEventListener.disable();
        }
        super.onPause();
    }

    @Override
    protected void onResume() {
        super.onResume();
        if (this.orientationEventListener != null) {
            this.orientationEventListener.enable();
        }
    }

    public static Intent createIntent(@NonNull Context context) {
        return new Intent(context, TakePhotoActivity.class).setFlags(Intent.FLAG_ACTIVITY_SINGLE_TOP);
    }
}
