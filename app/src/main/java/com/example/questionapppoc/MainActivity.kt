package com.example.questionapppoc

import android.Manifest
import android.content.ContentValues
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.os.Environment
import android.provider.MediaStore
import android.speech.tts.TextToSpeech
import android.util.Log
import android.widget.Button
import android.widget.ImageView
import android.widget.TextView
import android.widget.Toast
import androidx.activity.result.ActivityResultLauncher
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.android.gms.tasks.Tasks
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.segmentation.Segmentation
import com.google.mlkit.vision.segmentation.Segmenter
import com.google.mlkit.vision.segmentation.selfie.SelfieSegmenterOptions
import com.google.mlkit.vision.labeling.ImageLabeling
import com.google.mlkit.vision.labeling.defaults.ImageLabelerOptions
import java.io.File
import java.io.FileNotFoundException
import java.io.FileOutputStream
import java.io.IOException
import java.nio.ByteBuffer
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlinx.coroutines.*

class MainActivity : AppCompatActivity(), TextToSpeech.OnInitListener {

    private val CAMERA_PERMISSION_REQUEST_CODE = 1001
    private val READ_STORAGE_PERMISSION_REQUEST_CODE = 1002
    private val WRITE_STORAGE_PERMISSION_REQUEST_CODE = 1004
    private val READ_MEDIA_IMAGES_PERMISSION_REQUEST_CODE = 1003

    private lateinit var imageViewPreview: ImageView
    private lateinit var buttonOpenCamera: Button
    private lateinit var buttonOpenGallery: Button
    private lateinit var buttonProcessImage: Button
    private lateinit var buttonRecognizeAndSpeak: Button
    private lateinit var buttonSaveSketch: Button
    private lateinit var textViewTitle: TextView

    private lateinit var cameraLauncher: ActivityResultLauncher<Uri>
    private lateinit var galleryLauncher: ActivityResultLauncher<String>

    private var currentPhotoUri: Uri? = null
    private var currentBitmap: Bitmap? = null
    private lateinit var segmenter: Segmenter
    private lateinit var imageLabeler: com.google.mlkit.vision.labeling.ImageLabeler
    private var tts: TextToSpeech? = null

    private val activityScope = CoroutineScope(Dispatchers.Main)

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        textViewTitle = findViewById(R.id.textViewTitle)
        imageViewPreview = findViewById(R.id.imageViewPreview)
        buttonOpenCamera = findViewById(R.id.buttonOpenCamera)
        buttonOpenGallery = findViewById(R.id.buttonOpenGallery)
        buttonProcessImage = findViewById(R.id.buttonProcessImage)
        buttonRecognizeAndSpeak = findViewById(R.id.buttonRecognizeAndSpeak)
        buttonSaveSketch = findViewById(R.id.buttonSaveSketch)

        textViewTitle.text = getString(R.string.app_name_poc)

        val segmenterOptions = SelfieSegmenterOptions.Builder()
            .setDetectorMode(SelfieSegmenterOptions.SINGLE_IMAGE_MODE)
            .enableRawSizeMask()
            .build()
        segmenter = Segmentation.getClient(segmenterOptions)

        val imageLabelerOptions = ImageLabelerOptions.Builder()
            .setConfidenceThreshold(0.7f)
            .build()
        imageLabeler = ImageLabeling.getClient(imageLabelerOptions)
        tts = TextToSpeech(this, this)

        cameraLauncher = registerForActivityResult(ActivityResultContracts.TakePicture()) { success ->
            if (success) {
                currentPhotoUri?.let { uri ->
                    currentBitmap = loadBitmapFromUri(uri)
                    imageViewPreview.setImageBitmap(currentBitmap)
                    enableActionButtons(true)
                }
            }
        }
        galleryLauncher = registerForActivityResult(ActivityResultContracts.GetContent()) { uri: Uri? ->
            uri?.let {
                currentPhotoUri = it
                currentBitmap = loadBitmapFromUri(it)
                imageViewPreview.setImageBitmap(currentBitmap)
                enableActionButtons(true)
            }
        }

        buttonOpenCamera.setOnClickListener { checkAndOpenCamera() }
        buttonOpenGallery.setOnClickListener { checkAndOpenGallery() }

        buttonProcessImage.setOnClickListener {
            currentBitmap?.let { bitmap ->
                activityScope.launch {
                    Toast.makeText(this@MainActivity, getString(R.string.toast_sketch_transforming), Toast.LENGTH_SHORT).show()
                    val resizedBitmap = resizeBitmap(bitmap, 720)
                    val sketchBitmapResult = processImageWithMLKit(resizedBitmap)
                    currentBitmap = sketchBitmapResult
                    imageViewPreview.setImageBitmap(currentBitmap)
                    Toast.makeText(this@MainActivity, getString(R.string.toast_sketch_transform_complete), Toast.LENGTH_LONG).show()
                }
            } ?: Toast.makeText(this, getString(R.string.toast_select_image_first), Toast.LENGTH_SHORT).show()
        }
        buttonRecognizeAndSpeak.setOnClickListener {
            currentBitmap?.let { bitmapToRecognize ->
                recognizeAndSpeak(resizeBitmap(bitmapToRecognize, 480))
            } ?: Toast.makeText(this, getString(R.string.toast_select_image_first), Toast.LENGTH_SHORT).show()
        }
        buttonSaveSketch.setOnClickListener {
            currentBitmap?.let { bitmapToSave ->
                checkAndSaveSketch(bitmapToSave)
            } ?: Toast.makeText(this, getString(R.string.toast_select_image_first), Toast.LENGTH_SHORT).show()
        }

        enableActionButtons(false)
    }

    private fun enableActionButtons(enable: Boolean) {
        buttonProcessImage.isEnabled = enable
        buttonRecognizeAndSpeak.isEnabled = enable
        buttonSaveSketch.isEnabled = enable
        buttonProcessImage.text = getString(R.string.button_transform_to_sketch)
    }

    private fun resizeBitmap(bitmap: Bitmap, targetWidth: Int): Bitmap {
        if (bitmap.width <= targetWidth) return bitmap
        val aspectRatio = bitmap.height.toFloat() / bitmap.width.toFloat()
        val targetHeight = (targetWidth * aspectRatio).toInt()
        return Bitmap.createScaledBitmap(bitmap, targetWidth, targetHeight, true)
    }

    private fun loadBitmapFromUri(uri: Uri): Bitmap? {
        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                BitmapFactory.decodeStream(inputStream)
            }
        } catch (e: FileNotFoundException) {
            null
        } catch (e: Exception) {
            null
        }
    }

    private suspend fun processImageWithMLKit(bitmap: Bitmap): Bitmap = withContext(Dispatchers.Default) {
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        var sketchBitmap: Bitmap?
        try {
            val segmentationMask = Tasks.await(segmenter.process(inputImage))
            sketchBitmap = createSketchFromMaskWithPathRefined(bitmap, segmentationMask.buffer, segmentationMask.width, segmentationMask.height)
        } catch (e: Exception) {
            sketchBitmap = convertToBasicSketch(bitmap)
        }
        return@withContext sketchBitmap ?: convertToBasicSketch(bitmap)
    }

    private fun createSketchFromMaskWithPathRefined(originalBitmap: Bitmap, maskBuffer: ByteBuffer, maskWidth: Int, maskHeight: Int): Bitmap {
        val finalSketch = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
        finalSketch.eraseColor(Color.WHITE)
        val canvas = Canvas(finalSketch)
        val linePaint = Paint().apply {
            color = Color.BLACK
            strokeWidth = 2f
            style = Paint.Style.STROKE
            isAntiAlias = true
            strokeJoin = Paint.Join.ROUND
            strokeCap = Paint.Cap.ROUND
        }
        val path = Path()
        val confidenceThreshold = 0.75f

        maskBuffer.rewind()
        val isForegroundPixel = Array(maskHeight) { y ->
            BooleanArray(maskWidth) { x ->
                maskBuffer.getFloat((y * maskWidth + x) * Float.SIZE_BYTES) >= confidenceThreshold
            }
        }
        maskBuffer.rewind()

        val scaledPixelWidth = originalBitmap.width.toFloat() / maskWidth
        val scaledPixelHeight = originalBitmap.height.toFloat() / maskHeight

        for (y in 0 until maskHeight) {
            for (x in 0 until maskWidth) {
                if (isForegroundPixel[y][x]) {
                    val currentScaledX = x * scaledPixelWidth
                    val currentScaledY = y * scaledPixelHeight
                    if (y == 0 || !isForegroundPixel[y - 1][x]) {
                        path.moveTo(currentScaledX, currentScaledY)
                        path.lineTo(currentScaledX + scaledPixelWidth, currentScaledY)
                    }
                    if (y == maskHeight - 1 || !isForegroundPixel[y + 1][x]) {
                        path.moveTo(currentScaledX, currentScaledY + scaledPixelHeight)
                        path.lineTo(currentScaledX + scaledPixelWidth, currentScaledY + scaledPixelHeight)
                    }
                    if (x == 0 || !isForegroundPixel[y][x - 1]) {
                        path.moveTo(currentScaledX, currentScaledY)
                        path.lineTo(currentScaledX, currentScaledY + scaledPixelHeight)
                    }
                    if (x == maskWidth - 1 || !isForegroundPixel[y][x + 1]) {
                        path.moveTo(currentScaledX + scaledPixelWidth, currentScaledY)
                        path.lineTo(currentScaledX + scaledPixelWidth, currentScaledY + scaledPixelHeight)
                    }
                }
            }
        }
        canvas.drawPath(path, linePaint)
        return finalSketch
    }

    private fun convertToBasicSketch(originalBitmap: Bitmap): Bitmap {
        val sketchBitmap = Bitmap.createBitmap(originalBitmap.width, originalBitmap.height, Bitmap.Config.ARGB_8888)
        val canvas = Canvas(sketchBitmap)
        val paint = Paint()
        val colorMatrix = android.graphics.ColorMatrix()
        colorMatrix.setSaturation(0f)
        val filter = android.graphics.ColorMatrixColorFilter(colorMatrix)
        paint.colorFilter = filter
        canvas.drawBitmap(originalBitmap, 0f, 0f, paint)
        return sketchBitmap
    }

    private fun recognizeAndSpeak(bitmap: Bitmap) {
        if (!::imageLabeler.isInitialized || tts == null) {
            Toast.makeText(this, getString(R.string.toast_tts_init_failed), Toast.LENGTH_SHORT).show()
            return
        }
        val inputImage = InputImage.fromBitmap(bitmap, 0)
        imageLabeler.process(inputImage)
            .addOnSuccessListener { labels ->
                if (labels.isNotEmpty()) {
                    val topLabel = labels.first()
                    val textToSpeak = topLabel.text
                    Toast.makeText(this, getString(R.string.toast_recognition_result, textToSpeak), Toast.LENGTH_SHORT).show()
                    speakOut(textToSpeak)
                } else {
                    Toast.makeText(this, getString(R.string.toast_no_object_recognized), Toast.LENGTH_SHORT).show()
                }
            }
            .addOnFailureListener { e ->
                Toast.makeText(this, getString(R.string.toast_recognition_failed, e.message ?: "Unknown error"), Toast.LENGTH_SHORT).show()
            }
    }

    private fun speakOut(text: String?) {
        text?.let {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.LOLLIPOP) {
                tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null, "utteranceId")
            } else {
                tts?.speak(it, TextToSpeech.QUEUE_FLUSH, null)
            }
        }
    }

    override fun onInit(status: Int) {
        if (status == TextToSpeech.SUCCESS) {
            val userPreferredLocale = Locale.getDefault()
            val result = tts?.setLanguage(userPreferredLocale)
            if (result == TextToSpeech.LANG_MISSING_DATA || result == TextToSpeech.LANG_NOT_SUPPORTED) {
                tts?.setLanguage(Locale.KOREAN)
            }
        }
    }

    private fun checkAndSaveSketch(bitmap: Bitmap) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q) {
            if (ContextCompat.checkSelfPermission(this, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED) {
                saveBitmapToGallery(bitmap)
            } else {
                ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.WRITE_EXTERNAL_STORAGE), WRITE_STORAGE_PERMISSION_REQUEST_CODE)
            }
        } else {
            saveBitmapToGallery(bitmap)
        }
    }

    private fun saveBitmapToGallery(bitmap: Bitmap) {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "QUESTION_Sketch_${timeStamp}.jpg"
        var fos: FileOutputStream? = null
        var imageUri: Uri? = null
        try {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                val resolver = contentResolver
                val contentValues = ContentValues().apply {
                    put(MediaStore.MediaColumns.DISPLAY_NAME, imageFileName)
                    put(MediaStore.MediaColumns.MIME_TYPE, "image/jpeg")
                    put(MediaStore.MediaColumns.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "QuestionApp")
                }
                imageUri = resolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
                fos = imageUri?.let { resolver.openOutputStream(it) } as FileOutputStream?
            } else {
                val imagesDir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_PICTURES + File.separator + "QuestionApp")
                if (!imagesDir.exists()) imagesDir.mkdirs()
                val imageFile = File(imagesDir, imageFileName)
                fos = FileOutputStream(imageFile)
            }
            fos?.let {
                bitmap.compress(Bitmap.CompressFormat.JPEG, 90, it)
                it.flush()
                Toast.makeText(this, getString(R.string.toast_sketch_saved), Toast.LENGTH_SHORT).show()
            }
        } catch (e: IOException) {
            Toast.makeText(this, getString(R.string.toast_sketch_save_failed), Toast.LENGTH_SHORT).show()
        } finally {
            fos?.close()
        }
    }

    private fun checkAndOpenCamera() {
        if (ContextCompat.checkSelfPermission(this, Manifest.permission.CAMERA) == PackageManager.PERMISSION_GRANTED) {
            openCamera()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(Manifest.permission.CAMERA), CAMERA_PERMISSION_REQUEST_CODE)
        }
    }

    private fun checkAndOpenGallery() {
        val permissionToRequest = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            Manifest.permission.READ_MEDIA_IMAGES
        } else {
            Manifest.permission.READ_EXTERNAL_STORAGE
        }
        val requestCode = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            READ_MEDIA_IMAGES_PERMISSION_REQUEST_CODE
        } else {
            READ_STORAGE_PERMISSION_REQUEST_CODE
        }
        if (ContextCompat.checkSelfPermission(this, permissionToRequest) == PackageManager.PERMISSION_GRANTED) {
            openGallery()
        } else {
            ActivityCompat.requestPermissions(this, arrayOf(permissionToRequest), requestCode)
        }
    }

    private fun openCamera() {
        val photoFileUri = createImageUri()
        if (photoFileUri == null) {
            Toast.makeText(this, "사진 파일을 만들 수 없습니다.", Toast.LENGTH_SHORT).show()
            return
        }
        currentPhotoUri = photoFileUri
        cameraLauncher.launch(currentPhotoUri)
    }

    private fun openGallery() {
        galleryLauncher.launch("image/*")
    }

    private fun createImageUri(): Uri? {
        val timeStamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date())
        val imageFileName = "JPEG_${timeStamp}_"
        val contentValues = ContentValues().apply {
            put(MediaStore.Images.Media.DISPLAY_NAME, "$imageFileName.jpg")
            put(MediaStore.Images.Media.MIME_TYPE, "image/jpeg")
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + File.separator + "QuestionAppPoc")
            }
        }
        return contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, contentValues)
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        when (requestCode) {
            CAMERA_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) openCamera()
            }
            READ_STORAGE_PERMISSION_REQUEST_CODE, READ_MEDIA_IMAGES_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) openGallery()
            }
            WRITE_STORAGE_PERMISSION_REQUEST_CODE -> {
                if (grantResults.isNotEmpty() && grantResults[0] == PackageManager.PERMISSION_GRANTED) {
                    currentBitmap?.let { saveBitmapToGallery(it) }
                }
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        activityScope.cancel()
        if(::segmenter.isInitialized) segmenter.close()
        tts?.stop()
        tts?.shutdown()
        if (::imageLabeler.isInitialized) imageLabeler.close()
    }
}
