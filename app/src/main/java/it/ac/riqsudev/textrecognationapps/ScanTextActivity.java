package it.ac.riqsudev.textrecognationapps;

import static android.Manifest.permission.CAMERA;
import static android.Manifest.permission.WRITE_EXTERNAL_STORAGE;

import androidx.activity.result.ActivityResult;
import androidx.activity.result.ActivityResultCallback;
import androidx.activity.result.ActivityResultLauncher;
import androidx.activity.result.contract.ActivityResultContracts;
import androidx.annotation.NonNull;
import androidx.appcompat.app.AppCompatActivity;
import androidx.core.app.ActivityCompat;
import androidx.core.content.ContextCompat;

import android.Manifest;
import android.app.Activity;
import android.app.ProgressDialog;
import android.content.ContentValues;
import android.content.Intent;
import android.content.pm.PackageManager;
import android.net.Uri;
import android.os.Bundle;
import android.provider.MediaStore;
import android.text.TextUtils;
import android.util.Log;
import android.view.Menu;
import android.view.MenuItem;
import android.view.View;
import android.widget.Button;
import android.widget.EditText;
import android.widget.ImageView;
import android.widget.PopupMenu;
import android.widget.Toast;

import com.google.android.gms.tasks.OnFailureListener;
import com.google.android.gms.tasks.OnSuccessListener;
import com.google.android.gms.tasks.Task;
import com.google.firebase.firestore.CollectionReference;
import com.google.firebase.firestore.DocumentReference;
import com.google.firebase.firestore.FirebaseFirestore;
import com.google.mlkit.vision.common.InputImage;
import com.google.mlkit.vision.text.Text;
import com.google.mlkit.vision.text.TextRecognition;
import com.google.mlkit.vision.text.TextRecognizer;
import com.google.mlkit.vision.text.latin.TextRecognizerOptions;

import java.io.IOException;

import it.ac.riqsudev.textrecognationapps.model.RecognizeText;

public class ScanTextActivity extends AppCompatActivity {

    private ImageView captureImage;
    private EditText edtResultText;
    private Button btnSaveText, btnCaptureImage, btnDetectText;

    private String TAG = "TAG";

    private String textRecognizeResult;

    private Uri imageUri = null;
    private static final int CAM_REQ_CODE = 200;
    private static final int STORAGE_REQ_CODE = 110;

    private String[] camPermissions;
    private String[] storagePermissions;

    private ProgressDialog dialog;

    private TextRecognizer recognizer;

    private FirebaseFirestore db;

    @Override
    protected void onCreate(Bundle savedInstanceState) {
        super.onCreate(savedInstanceState);
        setContentView(R.layout.activity_scan_text);


        captureImage = findViewById(R.id.imgCapture);
        edtResultText = findViewById(R.id.edtResult);
        btnSaveText = findViewById(R.id.btnSave);
        btnCaptureImage = findViewById(R.id.btnCapture);
        btnDetectText = findViewById(R.id.btnDetect);

        dialog = new ProgressDialog(this);
        dialog.setTitle("Mohon tunggu..");
        dialog.setCanceledOnTouchOutside(false);

        db = FirebaseFirestore.getInstance();

        recognizer = TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS);

        btnSaveText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                textRecognizeResult = edtResultText.getText().toString().trim();

                if(TextUtils.isEmpty(textRecognizeResult)) {
                    edtResultText.setError("Harap tulis / isi teks dahulu..");
                } else {
                    storeData(textRecognizeResult);
                }
            }
        });

        btnDetectText.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                if(imageUri == null) {
                    Toast.makeText(ScanTextActivity.this, "Masukan / pilih gambar dahulu", Toast.LENGTH_SHORT).show();
                } else {
                    recognizeText();
                }
            }
        });

        btnCaptureImage.setOnClickListener(new View.OnClickListener() {
            @Override
            public void onClick(View view) {
                showCaptureDialog();
            }
        });

        camPermissions = new String[] {CAMERA, Manifest.permission.WRITE_EXTERNAL_STORAGE};
        storagePermissions = new String[] {Manifest.permission.WRITE_EXTERNAL_STORAGE};
    }

    private void storeData(String textRecognizeResult) {
        CollectionReference reference = db.collection("Result");

        RecognizeText data = new RecognizeText(textRecognizeResult);

        reference.add(data).addOnSuccessListener(new OnSuccessListener<DocumentReference>() {
            @Override
            public void onSuccess(DocumentReference documentReference) {
                Log.d(TAG, "onSuccess: " +data);
                Toast.makeText(ScanTextActivity.this, "Data Berhasil Disimpan Ke Firestore ", Toast.LENGTH_SHORT).show();
            }
        }).addOnFailureListener(new OnFailureListener() {
            @Override
            public void onFailure(@NonNull Exception e) {
                Log.d(TAG, "onFailure: " +data);
                Toast.makeText(ScanTextActivity.this, "Gagal Menyimpan Data :" +e, Toast.LENGTH_SHORT).show();
            }
        });
    }

    private void recognizeText() {
        Log.d(TAG, "recognizedTextImage: ");

        dialog.setMessage("Analisa gambar..");
        dialog.show();

        try {
            InputImage image = InputImage.fromFilePath(this, imageUri);
            dialog.setMessage("Analisa teks..");
            Task<Text> textResult = recognizer.process(image).addOnSuccessListener(new OnSuccessListener<Text>() {
                @Override
                public void onSuccess(Text text) {
                    dialog.dismiss();

                    String recognizedText = text.getText();

                    Log.d(TAG, "onSuccess: recognizedText : " +recognizedText);

                    edtResultText.setText(recognizedText);
                }
            }).addOnFailureListener(new OnFailureListener() {
                @Override
                public void onFailure(@NonNull Exception e) {
                    dialog.dismiss();

                    Log.e(TAG, "onFailure: ", e);

                    Toast.makeText(ScanTextActivity.this, "Gagal analisa teks karena "+e.getMessage(), Toast.LENGTH_SHORT).show();
                }
            });
        } catch (IOException e) {
            dialog.dismiss();

            Log.e(TAG, "recognizedText: ", e);

            Toast.makeText(ScanTextActivity.this, "Gagal analisa gambar karena "+e.getMessage(), Toast.LENGTH_SHORT).show();
        }

    }

    private void showCaptureDialog() {
        PopupMenu popupMenu = new PopupMenu(this, btnCaptureImage);
        popupMenu.getMenu().add(Menu.NONE, 1, 1, "CAMERA");
        popupMenu.getMenu().add(Menu.NONE, 2, 2, "GALLERY");

        popupMenu.show();

        popupMenu.setOnMenuItemClickListener(new PopupMenu.OnMenuItemClickListener() {
            @Override
            public boolean onMenuItemClick(MenuItem menuItem) {
                int id = menuItem.getItemId();
                if(id == 1) {

                    Log.d(TAG, "onMenuItemClick: Camera clicked");
                    
                    if(checkPermissionCamera()) {
                        openCamera();
                    } else {
                        reqPermissionCamera();
                    }
                } else if(id == 2){

                    Log.d(TAG, "onMenuItemClick: Gallery clicked");
                    
                    if(checkPermissionStorage()) {
                        pickImageFromGallery();
                    } else {
                        reqPermissionStorage();
                    }
                }
                return false;
            }
        });
    }

    private void pickImageFromGallery() {
        Log.d(TAG, "pickImageFromGallery: ");
        
        Intent i = new Intent(Intent.ACTION_PICK);
        i.setType("image/*");
        galleryLauncher.launch(i);
    }

    private ActivityResultLauncher<Intent> galleryLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        Intent data = result.getData();
                        imageUri = data.getData();

                        Log.d(TAG, "onActivityResult: imageUri" +imageUri);
                        
                        captureImage.setImageURI(imageUri);
                    } else {
                        Log.d(TAG, "onActivityResult: cancelled");
                        
                        Toast.makeText(ScanTextActivity.this, "Aksi dibatalkan..", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private void openCamera() {
        Log.d(TAG, "openCamera: ");
        
        ContentValues values = new ContentValues();
        values.put(MediaStore.Images.Media.TITLE, "Sample Title");
        values.put(MediaStore.Images.Media.DESCRIPTION, "Sample Description");

        imageUri = getContentResolver().insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, values);

        Intent i = new Intent(MediaStore.ACTION_IMAGE_CAPTURE);
        i.putExtra(MediaStore.EXTRA_OUTPUT, imageUri);
        cameraLauncher.launch(i);
    }

    private  ActivityResultLauncher<Intent> cameraLauncher = registerForActivityResult(
            new ActivityResultContracts.StartActivityForResult(),
            new ActivityResultCallback<ActivityResult>() {
                @Override
                public void onActivityResult(ActivityResult result) {
                    if(result.getResultCode() == Activity.RESULT_OK) {
                        Log.d(TAG, "onActivityResult: imageUri" +imageUri);
                        
                        captureImage.setImageURI(imageUri);
                    } else {
                        Log.d(TAG, "onActivityResult: cancelled");
                        
                        Toast.makeText(ScanTextActivity.this, "Aksi dibatalkan..", Toast.LENGTH_SHORT).show();
                    }
                }
            }
    );

    private boolean checkPermissionStorage() {
        boolean result = ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
        return result;
    }

    private void reqPermissionStorage() {
        ActivityCompat.requestPermissions(this, storagePermissions, STORAGE_REQ_CODE);
    }

    private boolean checkPermissionCamera() {
        boolean camResult = ContextCompat.checkSelfPermission(this, CAMERA) == (PackageManager.PERMISSION_GRANTED);
        boolean storageResult = ContextCompat.checkSelfPermission(this, WRITE_EXTERNAL_STORAGE) == (PackageManager.PERMISSION_GRANTED);
        return camResult && storageResult;
    }

    private void  reqPermissionCamera() {
        ActivityCompat.requestPermissions(this, camPermissions, CAM_REQ_CODE);
    }

    @Override
    public void onRequestPermissionsResult(int requestCode, @NonNull String[] permissions, @NonNull int[] grantResults) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults);
        switch (requestCode) {
            case CAM_REQ_CODE: {
                if(grantResults.length > 0) {
                    boolean cameraAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    boolean storageAccepted = grantResults[0] == PackageManager.PERMISSION_GRANTED;

                    if(cameraAccepted && storageAccepted) {
                        openCamera();
                    } else {
                        Toast.makeText(this, "Izin akses kamera dan storage dibutuhkan", Toast.LENGTH_SHORT).show();
                    }
                } else {
                    Toast.makeText(this, "Aksi dibatalkan..", Toast.LENGTH_SHORT).show();
                }
            }
            break;
            case STORAGE_REQ_CODE: {
                if(grantResults.length > 0) {
                    boolean storageAcc = grantResults[0] == PackageManager.PERMISSION_GRANTED;
                    if(storageAcc) {
                        pickImageFromGallery();
                    } else {
                        Toast.makeText(this, "Izin akses storage dibutuhkan", Toast.LENGTH_SHORT).show();
                    }
                }
            }
            break;
        }
    }
}