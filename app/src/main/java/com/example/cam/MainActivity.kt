package com.example.cam

import android.app.Activity
import android.content.ContentValues.TAG
import android.content.pm.PackageManager

import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.widget.Button
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.example.cam.databinding.ActivityMainBinding
import java.io.File
import java.text.SimpleDateFormat
import java.util.Locale
import android.content.ContentValues
import android.content.Intent
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var outputDirectory : File
    private var imageCapture:ImageCapture ?=null
    private lateinit var previewDialog: AlertDialog
    private lateinit var previewImage: ImageView
    private var isFlashEnabled = false
    private lateinit var photoName: String


    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val previewLayout = layoutInflater.inflate(R.layout.preview_layout, null)
        previewImage = previewLayout.findViewById(R.id.previewImage)

        val previewBuilder = AlertDialog.Builder(this)
            .setView(previewLayout)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                // Save the photo to the gallery
                savePhotoToGallery(File(outputDirectory, photoName))
                previewDialog.dismiss()
            }
            .setNegativeButton("Cancel") { _, _ ->
                // Delete the temporary file and dismiss the preview
                File(outputDirectory, photoName).delete()
                previewDialog.dismiss()
            }

        previewDialog = previewBuilder.create()





        var takephoto: Button = findViewById(R.id.btnTakePhoto)
        var openGallery: Button = findViewById(R.id.btnOpenGallery)
        val toggleFlash: Button = findViewById(R.id.btnToggleFlash)
        toggleFlash.setOnClickListener {
            isFlashEnabled = !isFlashEnabled
            startCamera()
        }



        outputDirectory = getoutputDirectory()

        if (allPermissionGranted()) {
            Toast.makeText(this, "We have Permission", Toast.LENGTH_SHORT).show()

            startCamera()
        } else {
            ActivityCompat.requestPermissions(
                this,
                Constants.REQUIRED_PERMISSIONS,
                Constants.REQUEST_CODE_PERMNISSIONS
            )
        }

        takephoto.setOnClickListener {
            //TODO:
            // Ensure that the camera is properly bound before taking a photo
            if (allPermissionGranted()) {
                photoName =
                    SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + ".jpg"
                takePhoto()
                takePhoto()
            } else {
                Toast.makeText(this, "Permission not Granted", Toast.LENGTH_SHORT).show()
            }
        }

        openGallery.setOnClickListener {
            openGallery()
        }
    }


    private fun getoutputDirectory(): File {
        val mediaDir  = externalMediaDirs.firstOrNull()?.let {mFile->
            File(mFile,resources.getString(R.string.app_name)).apply {
                mkdirs()
            }
        }

        return if(mediaDir!=null && mediaDir.exists())
            mediaDir else filesDir
    }

    //private fun takePhoto() {
 //       val photoFile = File(
   //         outputDirectory,
     //       SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.getDefault()).format(System.currentTimeMillis()) + ".jpg"
       // )

        //val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

       // imageCapture?.takePicture(
         //   outputOptions,
           // ContextCompat.getMainExecutor(this),
            //object : ImageCapture.OnImageSavedCallback {
              //  override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                //    val savedUri = Uri.fromFile(photoFile)
                  //  val msg = "Photo Saved"
                    //Toast.makeText(this@MainActivity, "$msg $savedUri", Toast.LENGTH_SHORT).show()
               // }

                //override fun onError(exception: ImageCaptureException) {
                  //  Log.e(Constants.tag, "OnError: ${exception.message}", exception)
                //}
           // }
        //)
    //}



    private fun openGallery() {
        val galleryIntent = Intent(Intent.ACTION_VIEW, MediaStore.Images.Media.EXTERNAL_CONTENT_URI)
        startActivity(galleryIntent)
    }


    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<out String>,
        grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if(requestCode == Constants.REQUEST_CODE_PERMNISSIONS){
            if(allPermissionGranted()){
                startCamera()

            }
            else{
                Toast.makeText(this,"Permission not Granted",Toast.LENGTH_SHORT).show()
                finish()
            }
        }

    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            val preview = Preview.Builder().build().also { mPreview ->
                mPreview.setSurfaceProvider(binding.ViewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(
                    (if (isFlashEnabled) {
                        ImageCapture.FLASH_MODE_ON
                        //model.close(////Toast.makeText(this,"Flash On",Toast.LENGTH_SHORT).show() as Int
                    } else {
                        ImageCapture.FLASH_MODE_OFF
                    })
                )
                .build()

            try {
                cameraProvider.unbindAll()
                val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
                cameraProvider.bindToLifecycle(this, cameraSelector, preview, imageCapture)
            } catch (e: Exception) {
                Log.e(TAG, "Error starting camera", e)
            }
        }, ContextCompat.getMainExecutor(this))
    }



    private fun allPermissionGranted() = Constants.REQUIRED_PERMISSIONS.all  {
        ContextCompat.checkSelfPermission(baseContext,it) == PackageManager.PERMISSION_GRANTED

    }


    private fun takePhoto() {
        val photoFile = File(outputDirectory, photoName)

        val outputOptions = ImageCapture.OutputFileOptions.Builder(photoFile).build()

        // Ensure that UI-related operations are done on the main thread
        imageCapture?.takePicture(
            outputOptions,
            ContextCompat.getMainExecutor(this),
            object : ImageCapture.OnImageSavedCallback {
                override fun onImageSaved(outputFileResults: ImageCapture.OutputFileResults) {
                    runOnUiThread {
                        previewImage.setImageURI(Uri.fromFile(photoFile))
                        previewDialog.show()
                    }
                }

                override fun onError(exception: ImageCaptureException) {
                    runOnUiThread {
                        Log.e(Constants.tag, "OnError: ${exception.message}", exception)
                        Toast.makeText(
                            this@MainActivity,
                            "Error capturing photo: ${exception.message}",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }
            }
        )
    }

    private fun savePhotoToGallery(photoFile: File) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            val contentValues = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, photoFile.name)
                put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES)
            }

            val contentResolver = contentResolver
            val imageUri = contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)

            imageUri?.let {
                try {
                    savePhotoToGalleryUsingOutputStream(photoFile, contentResolver.openOutputStream(it))
                } catch (e: Exception) {
                    e.printStackTrace()
                    Toast.makeText(this@MainActivity, "Error saving photo to gallery", Toast.LENGTH_SHORT).show()
                }
            }
        } else {
            try {
                savePhotoToGalleryUsingOutputStream(photoFile, FileOutputStream(photoFile))
            } catch (e: Exception) {
                e.printStackTrace()
                Toast.makeText(this@MainActivity, "Error saving photo to gallery", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun savePhotoToGalleryUsingOutputStream(photoFile: File, outputStream: OutputStream?) {
        outputStream?.use { output ->
            FileInputStream(photoFile).use { input ->
                input.copyTo(output)
            }
            Toast.makeText(this@MainActivity, "Photo saved to gallery", Toast.LENGTH_SHORT).show()
        } ?: run {
            Toast.makeText(this@MainActivity, "Error: OutputStream is null", Toast.LENGTH_SHORT).show()
        }
    }

}