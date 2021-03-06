package nic.goi.aarogyasetu.zxing;

import android.app.Activity;
import android.content.ActivityNotFoundException;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Build;
import android.os.Bundle;
import android.provider.Settings;
import android.text.TextUtils;
import android.view.KeyEvent;
import android.view.View;
import android.view.Window;
import android.view.WindowManager;
import android.widget.ImageView;
import android.widget.TextView;
import android.widget.Toast;

import androidx.annotation.NonNull;
import androidx.annotation.RequiresApi;
import androidx.appcompat.app.AlertDialog;
import androidx.core.content.ContextCompat;

import com.journeyapps.barcodescanner.DecoratedBarcodeView;

import java.security.NoSuchAlgorithmException;
import java.security.spec.InvalidKeySpecException;

import io.jsonwebtoken.Claims;
import io.jsonwebtoken.ExpiredJwtException;
import io.jsonwebtoken.Jws;
import io.jsonwebtoken.MalformedJwtException;
import io.jsonwebtoken.io.DecodingException;
import io.jsonwebtoken.security.SignatureException;
import nic.goi.aarogyasetu.R;
import nic.goi.aarogyasetu.utility.Constants;
import nic.goi.aarogyasetu.utility.CorUtility;
import nic.goi.aarogyasetu.utility.DecryptionUtil;
import nic.goi.aarogyasetu.utility.LocalizationUtil;
import nic.goi.aarogyasetu.views.QrActivity;

import static android.view.View.GONE;
import static android.view.View.VISIBLE;
import static nic.goi.aarogyasetu.utility.LocalizationUtil.getLocalisedString;

/**
 * Custom Scanner Activity extending from Activity to display a custom layout form scanner view.
 *
 * @author Niharika.Arora
 */
public class CustomScannerActivity extends Activity implements CustomCaptureManager.CaptureViewListener {

    private CustomCaptureManager capture;
    private DecoratedBarcodeView barcodeScannerView;
    private View statusContainer;
    private ImageView statusClose, close;
    private TextView desc, descReason, generateQr;
    private View promptContainer;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        requestWindowFeature(Window.FEATURE_NO_TITLE);
        getWindow().setFlags(WindowManager.LayoutParams.FLAG_FULLSCREEN,
                WindowManager.LayoutParams.FLAG_FULLSCREEN);
        setContentView(R.layout.activity_custom_scanner);

        configureViews();
        configureClicks();
        initCapture(savedInstanceState);
    }

    private void configureViews() {
        barcodeScannerView = findViewById(R.id.barcode_scanner);
        close = findViewById(R.id.close);
        statusClose = findViewById(R.id.status_close);
        statusContainer = findViewById(R.id.status_container);
        desc = findViewById(R.id.failure_reason);
        descReason = findViewById(R.id.failure_reason_desc);
        generateQr = findViewById(R.id.generate_qr);
        generateQr.setText(LocalizationUtil.getLocalisedString(this, R.string.generate_my_qr_code));
        promptContainer = findViewById(R.id.prompt_container);
        TextView statusDescription = findViewById(R.id.status_view);
        statusDescription.setText(LocalizationUtil.getLocalisedString(this, R.string.scan_prompt));
    }

    private void configureClicks() {
        onGenerateQrClick();
        onCloseClick();
        onStatusCloseClick();
    }

    private void initCapture(Bundle savedInstanceState) {
        capture = new CustomCaptureManager(this, barcodeScannerView);
        barcodeScannerView.getBarcodeView().getCameraSettings().setAutoFocusEnabled(true);
        capture.setViewCaptureListener(this);
        capture.initializeFromIntent(getIntent(), savedInstanceState);
        capture.decode();
    }

    private void configurePromptContainer() {
        if (CorUtility.isQRPermissionAvailable(this)) {
            promptContainer.setVisibility(VISIBLE);
        } else {
            promptContainer.setVisibility(GONE);
        }
    }


    private void onGenerateQrClick() {
        generateQr.setOnClickListener(v -> {
            QrActivity.start(this);
            finish();
        });
    }

    private void onStatusCloseClick() {
        statusClose.setOnClickListener(v -> closeStausView());
    }

    private void closeStausView() {
        statusContainer.setVisibility(GONE);
        capture.setViewCaptureListener(this);
        capture.decode();
        capture.onResume();
    }

    private void onCloseClick() {
        close.setOnClickListener(v -> finish());
    }

    @Override
    protected void onResume() {
        super.onResume();
        configurePromptContainer();
        capture.onResume();
    }

    @Override
    protected void onPause() {
        super.onPause();
        capture.onPause();
    }

    @Override
    protected void onDestroy() {
        super.onDestroy();
        capture.onDestroy();
    }

    @Override
    protected void onSaveInstanceState(Bundle outState) {
        super.onSaveInstanceState(outState);
        capture.onSaveInstanceState(outState);
    }

    @Override
    public boolean onKeyDown(int keyCode, KeyEvent event) {
        return barcodeScannerView.onKeyDown(keyCode, event) || super.onKeyDown(keyCode, event);
    }

    @Override
    public void onResultFetched(String code) {
        statusContainer.setVisibility(VISIBLE);
        Jws<Claims> claimsJws = null;
        try {
            claimsJws = DecryptionUtil.decryptFile(code);
        } catch (ExpiredJwtException e) {
            showExpiredCode();
        } catch (NoSuchAlgorithmException | DecodingException | MalformedJwtException exception) {
            showInvalidStatus();
        } catch (Exception exception) {
            showCommonInvalidStatus();
        }
        if (claimsJws != null) {
            try {
                Claims body = claimsJws.getBody();
                if (body != null) {
                    long expiry = body.get(Constants.EXPIRY, Long.class);
                    String name = body.get(Constants.NAME, String.class);
                    String mobileNo = body.get(Constants.MOBILE, String.class);
                    String status = body.get(Constants.STATUS, String.class);
                    final long millisecondsMultiplier = 1000L;
                    long countDownMilliSeconds = expiry * millisecondsMultiplier;
                    if (expiry <= 0 || TextUtils.isEmpty(mobileNo) || TextUtils.isEmpty(status)) {
                        showInvalidStatus();
                    } else if (expiry > 0 && System.currentTimeMillis() - countDownMilliSeconds > 0) {
                        showExpiredCode();
                    } else if (!TextUtils.isEmpty(mobileNo) && !TextUtils.isEmpty(status)) {
                        showPersonStatus(name, mobileNo, status);
                    } else {
                        showInvalidStatus();
                    }
                } else {
                    showCommonInvalidStatus();
                }
            } catch (Exception ex) {
                showCommonInvalidStatus();
            }
        }
        hideStatusContainerAfterDelay();
    }

    private void hideStatusContainerAfterDelay() {
        statusContainer.postDelayed(this::closeStausView, 5000);
    }

    private void showPersonStatus(String scannerName, String mobileNo, String status) {
        String name = "";
        if (!TextUtils.isEmpty(scannerName)) {
            name = CorUtility.Companion.toTitleCase(scannerName);
        }
        desc.setVisibility(GONE);
        String descVal;
        if (status.equalsIgnoreCase(Constants.HEALTHY)) {
            descVal = name + " (" + mobileNo + ") " + LocalizationUtil.getLocalisedString(this, R.string.low_risk);
            descReason.setText(descVal);
            statusContainer.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.chat_bubble_green));
        } else if (status.equalsIgnoreCase(Constants.MODERATE)) {
            descVal = name + " (" + mobileNo + ") " + LocalizationUtil.getLocalisedString(this, R.string.moderate_risk);
            descReason.setText(descVal);
            statusContainer.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.chat_bubble_yellow));
        } else if (status.equalsIgnoreCase(Constants.HIGH)) {
            descVal = name + " (" + mobileNo + ") " + LocalizationUtil.getLocalisedString(this, R.string.high_risk);
            descReason.setText(descVal);
            statusContainer.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.chat_bubble_orange));
        } else {
            descVal = name + " (" + mobileNo + ") " + LocalizationUtil.getLocalisedString(this, R.string.tested_positive_status);
            descReason.setText(descVal);
            statusContainer.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.chat_bubble_red));
        }
        descReason.setTextColor(ContextCompat.getColorStateList(this, R.color.white));
        statusClose.setImageTintList(ContextCompat.getColorStateList(this, R.color.white));
    }

    private void showExpiredCode() {
        desc.setVisibility(VISIBLE);
        descReason.setText(LocalizationUtil.getLocalisedString(this, R.string.request_new_code));
        desc.setText(LocalizationUtil.getLocalisedString(this, R.string.expired_code));
        desc.setTextColor(ContextCompat.getColorStateList(this, R.color.chat_title_orange));
        descReason.setTextColor(ContextCompat.getColorStateList(this, R.color.black));
        statusContainer.setBackgroundTintList(ContextCompat.getColorStateList(this, R.color.chat_bubble_light_orange));
        statusClose.setImageTintList(ContextCompat.getColorStateList(this, R.color.chat_close_dark));
    }

    private void showInvalidStatus() {
        desc.setVisibility(VISIBLE);
        descReason.setText(LocalizationUtil.getLocalisedString(this, R.string.not_generated_aarogya_setu));
        desc.setText(LocalizationUtil.getLocalisedString(this, R.string.invalid_qr_code));
        desc.setTextColor(ContextCompat.getColorStateList(this, R.color.chat_title_red));
        descReason.setTextColor(ContextCompat.getColorStateList(this, R.color.black));
        statusContainer.setBackgroundTintList(null);
        statusClose.setImageTintList(ContextCompat.getColorStateList(this, R.color.chat_close_dark));
    }

    private void showCommonInvalidStatus() {
        desc.setVisibility(VISIBLE);
        descReason.setText(LocalizationUtil.getLocalisedString(this, R.string.common_scanning_error));
        desc.setText(LocalizationUtil.getLocalisedString(this, R.string.invalid_qr_code));
        desc.setTextColor(ContextCompat.getColorStateList(this, R.color.chat_title_red));
        descReason.setTextColor(ContextCompat.getColorStateList(this, R.color.black));
        statusContainer.setBackgroundTintList(null);
        statusClose.setImageTintList(ContextCompat.getColorStateList(this, R.color.chat_close_dark));
    }

    @RequiresApi(api = Build.VERSION_CODES.M)
    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        boolean isPermissionGranted = true;
        if (grantResults.length > 0) {
            if (grantResults[0] == PackageManager.PERMISSION_DENIED) {
                boolean showRationale = shouldShowRequestPermissionRationale(permissions[0]);
                if (!showRationale) {
                    isPermissionGranted = false;
                    showPermissionAlert();
                } else {
                    isPermissionGranted = false;
                }
            }
        } else {
            isPermissionGranted = false;
        }
        if (!isPermissionGranted) {
            Toast.makeText(this, getString(R.string.provide_necessary_permission), Toast.LENGTH_LONG).show();
        } else {
            promptContainer.setVisibility(VISIBLE);
        }
    }

    private void showPermissionAlert() {

        AlertDialog.Builder builder = new AlertDialog.Builder(this);
        builder.setMessage(getLocalisedString(this, R.string.scan_without_perm_alert))
                .setCancelable(false)
                .setPositiveButton(R.string.open_settings, (dialog, which) -> openAppSettings()
                );
        AlertDialog alertDialog = builder.create();
        if (!isFinishing()) {
            alertDialog.show();
        }
    }

    private void openAppSettings() {
        Intent intent = new Intent();
        intent.setAction(Settings.ACTION_APPLICATION_DETAILS_SETTINGS);
        Uri uri = Uri.fromParts(Constants.PACKAGE, getPackageName(), null);
        intent.setData(uri);
        try {
            startActivity(intent);
        } catch (ActivityNotFoundException exception) {
        }
    }
}
