package com.example.cam

import android.content.ContentValues
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
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.drawable.BitmapDrawable
import android.net.Uri
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import android.widget.ImageView
import androidx.appcompat.app.AlertDialog
import com.chaquo.python.Python
import com.chaquo.python.android.AndroidPlatform
import com.example.cam.ml.ModelAtmTripletSiameseV1
import java.io.ByteArrayOutputStream
import java.io.FileInputStream
import java.io.FileOutputStream
import java.io.IOException
import java.io.OutputStream

class MainActivity : AppCompatActivity() {

    lateinit var binding: ActivityMainBinding
    lateinit var outputDirectory : File
    private var imageCapture:ImageCapture ?=null
    private lateinit var previewDialog: AlertDialog
    private lateinit var previewImage: ImageView
    private var isFlashEnabled = false
    private lateinit var photoName: String
    lateinit var model: ModelAtmTripletSiameseV1
    lateinit var drawable : BitmapDrawable
    lateinit var bitmap: Bitmap
    lateinit var imageString: String
    lateinit var bmp: Bitmap
    lateinit var procView: ImageView



    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)
        procView = procView.findViewById(R.id.processedImage)


        if(!Python.isStarted()){
            Python.start(AndroidPlatform(this))
        }
        //Creating python instance
        val py = Python.getInstance()
        val pyobj = py.getModule("script.py")


        val previewLayout = layoutInflater.inflate(R.layout.preview_layout, null)
        previewImage = previewLayout.findViewById(R.id.previewImage)

        val previewBuilder = AlertDialog.Builder(this)
            .setView(previewLayout)
            .setCancelable(false)
            .setPositiveButton("Save") { _, _ ->
                // Save the photo to the gallery
                savePhotoToGallery(File(outputDirectory, photoName))
//                val bitmap = getBitmapFromImageView(previewLayout.findViewById(R.id.previewImage))
//                val processedBitmap = preprocessImageWithPythonScript(bitmap)
//                previewImage.setImageBitmap(processedBitmap)
                // Save or perform further actions with the processed image if needed
//                saveProcessedPhoto(processedBitmap)
                //savePhotoToGallery(File(outputDirectory,photoName))


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

            } else {
                Toast.makeText(this, "Permission not Granted", Toast.LENGTH_SHORT).show()
            }
        }

        openGallery.setOnClickListener {
            openGallery()
        }


    }

    private fun getBitmapFromImageView(imageView: ImageView): Bitmap? {
        val drawable = imageView.drawable
        if (drawable is BitmapDrawable) {
            return drawable.bitmap
        }
        return null
    }

    private fun preprocessImageWithPythonScript(originalBitmap: Bitmap?): Bitmap {
        var py = Python.getInstance()
        // Convert the original bitmap to a base64-encoded string
        val imageString = getStringImage(originalBitmap)

        // Call the Python script with the base64-encoded image
        val pyObject = py.getModule("myscript")
        val obj = pyObject.callAttr("main", imageString)
        val str = obj.toString()

        // Decode the result from base64 and create a Bitmap
        val data = android.util.Base64.decode(str, android.util.Base64.DEFAULT)
        return BitmapFactory.decodeByteArray(data, 0, data.size)
    }
    // Inside your MainActivity class

    // Function to save the processed image to the gallery
    private fun saveProcessedPhoto(processedBitmap: Bitmap) {
        // Save the processed image to the gallery using the same logic as before
        val photoName =
            SimpleDateFormat(Constants.FILE_NAME_FORMAT, Locale.US).format(System.currentTimeMillis()) + "_processed.jpg"
        val outputDirectory = getoutputDirectory()

        val processedPhotoFile = File(outputDirectory, photoName)

        try {
            val outputStream = FileOutputStream(processedPhotoFile)
            processedBitmap.compress(Bitmap.CompressFormat.JPEG, 100, outputStream)
            outputStream.flush()
            outputStream.close()

            // Save the processed photo to the gallery
            savePhotoToGallery(processedPhotoFile)
        } catch (e: IOException) {
            e.printStackTrace()
            Toast.makeText(this@MainActivity, "Error saving processed photo", Toast.LENGTH_SHORT).show()
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

            // Unbind all use cases before binding new ones
            cameraProvider.unbindAll()

            val preview = Preview.Builder().build().also { mPreview ->
                mPreview.setSurfaceProvider(binding.ViewFinder.surfaceProvider)
            }

            imageCapture = ImageCapture.Builder()
                .setFlashMode(
                    if (isFlashEnabled) ImageCapture.FLASH_MODE_ON else ImageCapture.FLASH_MODE_OFF
                )
                .build()

            try {
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




    //Function to convert the image to bytearray
    private fun getStringImage(bitmapDrawable: BitmapDrawable): String {
        val bitmap = bitmapDrawable.bitmap

        // Convert the Bitmap to a byte array
        val byteArrayOutputStream = ByteArrayOutputStream()
        bitmap.compress(Bitmap.CompressFormat.JPEG, 100, byteArrayOutputStream)
        val byteArray = byteArrayOutputStream.toByteArray()

        var encodedImage = android.util.Base64.encodeToString(byteArray,android.util.Base64.DEFAULT)

        return encodedImage
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

                        val bitmap = BitmapFactory.decodeFile(photoFile.absolutePath)
                        val bitmapDrawable = BitmapDrawable(resources, bitmap)
                        drawable = bitmapDrawable
                        imageString = getStringImage(drawable)
                        val py = Python.getInstance()
                        val pyo = py.getModule("script.py")
                        val obj = pyo.callAttr("main",imageString)
//
                        // Pass the byte array to the Python script
                       // passArrayToPythonScript(byteArray)

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

