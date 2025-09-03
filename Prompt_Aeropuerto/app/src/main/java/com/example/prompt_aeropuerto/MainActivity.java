package com.example.prompt_aeropuerto;

import android.content.pm.PackageManager;
import android.os.Bundle;
import android.os.SystemClock;
import android.view.View;
import android.view.ViewGroup;
import android.widget.Button;
import android.widget.Chronometer;
import android.Manifest;
import android.text.Editable;
import android.text.TextWatcher;
import android.util.Base64;

import androidx.activity.EdgeToEdge;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.biometric.BiometricPrompt;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;
import androidx.core.graphics.Insets;
import androidx.core.view.ViewCompat;
import androidx.core.view.WindowInsetsCompat;
import androidx.camera.core.CameraSelector;
import androidx.camera.core.ImageCapture;
import androidx.camera.core.ImageCaptureException;
import androidx.camera.core.Preview;
import androidx.camera.lifecycle.ProcessCameraProvider;
import androidx.camera.view.PreviewView;

import com.google.android.material.snackbar.Snackbar;
import com.google.android.material.textfield.TextInputEditText;
import com.google.common.util.concurrent.ListenableFuture;

import java.io.File;
import java.io.IOException;
import java.security.InvalidAlgorithmParameterException;
import java.security.KeyStore;
import java.security.KeyStoreException;
import java.security.NoSuchAlgorithmException;
import java.security.NoSuchProviderException;
import java.security.UnrecoverableKeyException;
import java.security.cert.CertificateException;
import java.util.concurrent.ExecutionException;
import java.util.concurrent.Executor;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import javax.crypto.Cipher;
import javax.crypto.KeyGenerator;
import javax.crypto.SecretKey;
import android.graphics.Bitmap;
import android.graphics.BitmapFactory;
import java.io.ByteArrayOutputStream;

public class MainActivity extends AppCompatActivity {
    private static final String KEY_NAME = "BiometricKey";
    private static final int BIOMETRIC_PERMISSION_CODE = 1001;
    private Chronometer chronometer;
    private TextInputEditText nationalityInput;
    private TextInputEditText nameInput;
    private Button fingerprintButton;
    private Button faceButton;
    private Button submitButton;
    private long startTime = 0;
    private boolean isTimerRunning = false;
    private boolean isBiometricCaptured = false;
    private byte[] fingerprintData;
    private byte[] faceData;
    private KeyStore keyStore;
    private Cipher cipher;
    private PreviewView previewView;
    private ImageCapture imageCapture;
    private ExecutorService cameraExecutor;
    private ListenableFuture<ProcessCameraProvider> cameraProviderFuture;
    private Executor mainExecutor;
    private boolean isPersonalDataComplete = false;
    private boolean isFingerprintCaptured = false;
    private ProcessCameraProvider cameraProvider;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        EdgeToEdge.enable(this);
        setContentView(R.layout.activity_main);
        ViewCompat.setOnApplyWindowInsetsListener(findViewById(R.id.main), (v, insets) -> {
            Insets systemBars = insets.getInsets(WindowInsetsCompat.Type.systemBars());
            v.setPadding(systemBars.left, systemBars.top, systemBars.right, systemBars.bottom);
            return insets;
        });

        mainExecutor = ContextCompat.getMainExecutor(this);
        initializeViews();
        setupListeners();
        cameraExecutor = Executors.newSingleThreadExecutor();
        checkAndRequestPermissions();
    }

    private byte[] generateIV() {
        try {
            java.security.SecureRandom secureRandom = new java.security.SecureRandom();
            byte[] iv = new byte[16];
            secureRandom.nextBytes(iv);
            return iv;
        } catch (Exception e) {
            e.printStackTrace();
            return null;
        }
    }

    private void checkAndRequestPermissions() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.USE_BIOMETRIC)
                != PackageManager.PERMISSION_GRANTED ||
            ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA)
                != PackageManager.PERMISSION_GRANTED) {

            ActivityCompat.requestPermissions(this,
                    new String[]{
                            Manifest.permission.USE_BIOMETRIC,
                            Manifest.permission.CAMERA
                    },
                    BIOMETRIC_PERMISSION_CODE);
        }
    }

    private void initializeViews() {
        chronometer = findViewById(R.id.chronometer);
        nationalityInput = findViewById(R.id.nationalityInput);
        nameInput = findViewById(R.id.nameInput);
        fingerprintButton = findViewById(R.id.fingerprintButton);
        faceButton = findViewById(R.id.faceButton);
        submitButton = findViewById(R.id.submitButton);
        previewView = findViewById(R.id.previewView);
        previewView.setVisibility(View.GONE);
    }

    private void setupCamera() {
        cameraProviderFuture = ProcessCameraProvider.getInstance(this);

        cameraProviderFuture.addListener(() -> {
            try {
                cameraProvider = cameraProviderFuture.get();
                bindPreview(cameraProvider);
            } catch (ExecutionException | InterruptedException e) {
                showMessage("Error al inicializar la cámara: " + e.getMessage());
            }
        }, mainExecutor);
    }

    private void bindPreview(@NonNull ProcessCameraProvider cameraProvider) {
        try {
            Preview preview = new Preview.Builder().build();

            CameraSelector cameraSelector = new CameraSelector.Builder()
                    .requireLensFacing(CameraSelector.LENS_FACING_FRONT)
                    .build();

            imageCapture = new ImageCapture.Builder()
                    .setCaptureMode(ImageCapture.CAPTURE_MODE_MINIMIZE_LATENCY)
                    .setTargetRotation(previewView.getDisplay().getRotation())
                    .build();

            preview.setSurfaceProvider(previewView.getSurfaceProvider());

            cameraProvider.unbindAll();
            cameraProvider.bindToLifecycle(
                    this,
                    cameraSelector,
                    preview,
                    imageCapture
            );
        } catch (Exception e) {
            showMessage("Error al vincular la cámara: " + e.getMessage());
        }
    }

    private void validatePersonalData() {
        String nationality = nationalityInput.getText().toString().trim();
        String name = nameInput.getText().toString().trim();

        if (!nationality.isEmpty() && !name.isEmpty()) {
            if (nationality.equalsIgnoreCase("guatemalteco") ||
                nationality.equalsIgnoreCase("estadounidense")) {
                showMessage("Nacionalidad no elegible para el registro");
                isPersonalDataComplete = false;
                return;
            }
            isPersonalDataComplete = true;
            fingerprintButton.setEnabled(true);
        } else {
            isPersonalDataComplete = false;
            fingerprintButton.setEnabled(false);
        }
    }

    private void setupListeners() {
        nationalityInput.setOnFocusChangeListener((v, hasFocus) -> {
            if (hasFocus && !isTimerRunning) {
                startTimer();
            }
        });

        nationalityInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validatePersonalData();
            }
        });

        nameInput.addTextChangedListener(new TextWatcher() {
            @Override
            public void beforeTextChanged(CharSequence s, int start, int count, int after) {}

            @Override
            public void onTextChanged(CharSequence s, int start, int before, int count) {}

            @Override
            public void afterTextChanged(Editable s) {
                validatePersonalData();
            }
        });

        fingerprintButton.setOnClickListener(v -> {
            if (!isPersonalDataComplete) {
                showMessage("Primero complete los datos personales");
                return;
            }
            captureFingerprint();
        });

        faceButton.setOnClickListener(v -> {
            if (!isPersonalDataComplete) {
                showMessage("Primero complete los datos personales");
                return;
            }
            if (!isFingerprintCaptured) {
                showMessage("Primero capture la huella digital");
                return;
            }
            startFaceCapture();
        });

        submitButton.setOnClickListener(v -> validateAndSubmit());
    }

    private void startTimer() {
        String nationality = nationalityInput.getText().toString().trim().toLowerCase();
        if (nationality.equals("guatemalteco") || nationality.equals("estadounidense")) {
            showMessage("Usuario no elegible para el registro de tiempo");
            return;
        }

        isTimerRunning = true;
        startTime = SystemClock.elapsedRealtime();
        chronometer.setBase(startTime);
        chronometer.start();
    }

    private void stopTimer() {
        if (isTimerRunning) {
            chronometer.stop();
            isTimerRunning = false;
        }
    }

    private void captureFingerprint() {
        BiometricPrompt.PromptInfo promptInfo = new BiometricPrompt.PromptInfo.Builder()
                .setTitle("Captura de Huella Digital")
                .setSubtitle("Coloque su dedo en el sensor")
                .setNegativeButtonText("Cancelar")
                .build();

        BiometricPrompt biometricPrompt = new BiometricPrompt(this,
                ContextCompat.getMainExecutor(this),
                new BiometricPrompt.AuthenticationCallback() {
                    @Override
                    public void onAuthenticationSucceeded(@NonNull BiometricPrompt.AuthenticationResult result) {
                        super.onAuthenticationSucceeded(result);
                        fingerprintData = "fingerprint_captured".getBytes();
                        isBiometricCaptured = true;
                        isFingerprintCaptured = true;
                        faceButton.setEnabled(true);
                        showMessage("Huella capturada exitosamente");
                    }

                    @Override
                    public void onAuthenticationError(int errorCode, @NonNull CharSequence errString) {
                        super.onAuthenticationError(errorCode, errString);
                        showMessage("Error en captura: " + errString);
                    }

                    @Override
                    public void onAuthenticationFailed() {
                        super.onAuthenticationFailed();
                        showMessage("Autenticación fallida. Intente nuevamente");
                    }
                });

        biometricPrompt.authenticate(promptInfo);
    }

    private void saveDataLocally(String nationality, String name, long elapsedTime) {
        try {
            DatabaseHelper db = DatabaseHelper.getInstance(this);
            long result = db.saveUserData(nationality, name, elapsedTime,
                    fingerprintData, faceData);

            if (result != -1) {
                showMessage("Datos guardados exitosamente");
                resetForm();
            } else {
                showMessage("Error al guardar los datos");
            }
        } catch (Exception e) {
            e.printStackTrace();
            showMessage("Error en el proceso: " + e.getMessage());
        }
    }

    private void resetForm() {
        nationalityInput.setText("");
        nameInput.setText("");
        fingerprintData = null;
        faceData = null;
        isBiometricCaptured = false;
        isPersonalDataComplete = false;
        isFingerprintCaptured = false;
        chronometer.setBase(SystemClock.elapsedRealtime());
        chronometer.stop();
        isTimerRunning = false;
        fingerprintButton.setEnabled(false);
        faceButton.setEnabled(false);
    }

    private void validateAndSubmit() {
        if (!isPersonalDataComplete) {
            showMessage("Complete los datos personales primero");
            return;
        }

        if (!isBiometricCaptured) {
            showMessage("Capture los datos biométricos primero");
            return;
        }

        if (faceData == null) {
            showMessage("Capture la foto del rostro primero");
            return;
        }

        stopTimer();
        long elapsedTime = SystemClock.elapsedRealtime() - startTime;
        saveDataLocally(nationalityInput.getText().toString().trim(),
                       nameInput.getText().toString().trim(),
                       elapsedTime);
        resetForm();
    }

    private void showMessage(String message) {
        Snackbar.make(findViewById(android.R.id.content), message, Snackbar.LENGTH_LONG).show();
    }

    @Override
    protected void onPause() {
        super.onPause();
        stopTimer();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        cameraExecutor.shutdown();
        if (cameraProvider != null) {
            cameraProvider.unbindAll();
        }
    }

    private void startFaceCapture() {
        previewView.setVisibility(View.VISIBLE);
        setupCamera();

        // Crear y configurar el botón de captura
        Button captureButton = new Button(this);
        captureButton.setText("Capturar Foto");
        ViewGroup.LayoutParams params = new ViewGroup.LayoutParams(
            ViewGroup.LayoutParams.MATCH_PARENT,
            ViewGroup.LayoutParams.WRAP_CONTENT
        );
        captureButton.setLayoutParams(params);

        captureButton.setOnClickListener(v -> {
            if (imageCapture != null) {
                takePicture();
                ((ViewGroup) previewView.getParent()).removeView(captureButton);
                previewView.setVisibility(View.GONE);
            } else {
                showMessage("Cámara no inicializada correctamente");
            }
        });

        // Agregar el botón al layout
        ViewGroup parent = (ViewGroup) previewView.getParent();
        parent.addView(captureButton);
    }

    private void takePicture() {
        File photoFile = new File(getExternalCacheDir(), "face_photo.jpg");

        ImageCapture.OutputFileOptions outputOptions =
                new ImageCapture.OutputFileOptions.Builder(photoFile).build();

        imageCapture.takePicture(outputOptions, cameraExecutor,
                new ImageCapture.OnImageSavedCallback() {
                    @Override
                    public void onImageSaved(ImageCapture.OutputFileResults output) {
                        try {
                            Bitmap bitmap = BitmapFactory.decodeFile(photoFile.getAbsolutePath());
                            ByteArrayOutputStream stream = new ByteArrayOutputStream();
                            bitmap.compress(Bitmap.CompressFormat.JPEG, 80, stream);
                            faceData = stream.toByteArray();

                            runOnUiThread(() -> {
                                showMessage("Rostro capturado exitosamente");
                                submitButton.setEnabled(true);
                            });

                            // Limpiar el archivo temporal
                            photoFile.delete();
                        } catch (Exception e) {
                            runOnUiThread(() ->
                                showMessage("Error procesando la imagen: " + e.getMessage())
                            );
                        }
                    }

                    @Override
                    public void onError(ImageCaptureException exception) {
                        runOnUiThread(() ->
                            showMessage("Error al capturar la imagen: " + exception.getMessage())
                        );
                    }
                });
    }
}