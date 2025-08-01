package mm.com.wavemoney.fullopencvtesting

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Color
import android.graphics.ImageFormat
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.YuvImage
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.util.Log
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.Toast
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.core.content.ContextCompat
import androidx.core.graphics.drawable.toBitmap
import androidx.fragment.app.viewModels
import androidx.navigation.fragment.findNavController
import mm.com.wavemoney.fullopencvtesting.databinding.FragmentCardDetectionBinding
import mm.com.wavemoney.fullopencvtesting.databinding.FragmentFaceDetectionBinding

import mm.com.wavemoney.fullopencvtesting.utils.setIcon
import mm.com.wavemoney.fullopencvtesting.utils.setSystemNavigationBarColor
import mm.com.wavemoney.fullopencvtesting.utils.updateStatusBarColor
//import org.opencv.android.OpenCVLoader
//import org.opencv.objdetect.CascadeClassifier
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.PermissionRequest
import java.io.File
import java.io.FileOutputStream
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer
import java.util.concurrent.Executors
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.face.FaceDetection
import com.google.mlkit.vision.face.FaceDetector
import com.google.mlkit.vision.face.FaceDetectorOptions
import com.google.mlkit.vision.face.Face

class FaceDetectionFragment : Fragment() , EasyPermissions.PermissionCallbacks{
    private var _binding: FragmentFaceDetectionBinding? = null
    private val binding get() = _binding!!

    private val executor = Executors.newSingleThreadExecutor()
    private var lastDetectionTime = 0L
    private var detectionStartTime = 0L
    private var consecutiveValidDetections = 0

    private var isNeedToStopObserving: Boolean = false
   // private val disposable = CompositeDisposable()
   private val viewModel: LivePhotoViewModel by viewModels()

    // ML Kit Face Detector
    private lateinit var faceDetector: FaceDetector

    companion object {
        private const val REQUIRED_CONSECUTIVE_DETECTIONS = 5
        private const val CAMERA_WARMUP_TIME = 1000L
        private const val DETECTION_INTERVAL = 100L // Reduced from 300L for more frequent detection
        private const val TAG = "FaceDetectionDebug"
    }

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFaceDetectionBinding.inflate(inflater, container, false)
        val view = binding.root
        Log.d(TAG, "onCreateView: Fragment created")
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        Log.d(TAG, "onViewCreated: Setting up fragment")
        updateStatusBarColor(Color.BLACK)
        setupMLKitFaceDetector()
        setupView()
        setupEvents()
        observeFaceDetectionStateAndUpdateUi()
        updateStatusBarColor(Color.BLACK)
        setSystemNavigationBarColor(R.color.black)
    }

    private fun setupMLKitFaceDetector() {
        Log.d(TAG, "setupMLKitFaceDetector: Initializing ML Kit face detector")
        val options = FaceDetectorOptions.Builder()
            .setPerformanceMode(FaceDetectorOptions.PERFORMANCE_MODE_ACCURATE) // Changed from FAST to ACCURATE
            .setContourMode(FaceDetectorOptions.CONTOUR_MODE_NONE)
            .setClassificationMode(FaceDetectorOptions.CLASSIFICATION_MODE_NONE)
            .setLandmarkMode(FaceDetectorOptions.LANDMARK_MODE_NONE)
            .setMinFaceSize(0.1f) // Reduced from 0.15f to 0.1f for better detection
            .build()

        faceDetector = FaceDetection.getClient(options)
    }

    override fun onResume() {
        super.onResume()
        Log.d(TAG, "onResume: Fragment resumed")
        viewModel.updateDetectionObservation(CaptureBtnState(false, null))
        viewModel.updateFaceDetectionState(
            isInitialState = true,
            detectionResult = "No Face",
            isMultipleFaces = false,
            isDetectionReady = false
        )

    }

    private fun setupView() {
        Log.d(TAG, "setupView: Setting up camera view")
        if (hasCameraPermission) {
            Log.d(TAG, "setupView: Camera permission granted, enabling capture button")
            setTakeButtonEnable(true)
            binding.cameraPreviewView.startCamera()
        } else {
            Log.d(TAG, "setupView: Camera permission not granted, requesting permission")
            setTakeButtonEnable(false)
            requestPermissionOrStartPreview()
        }
    }

    private fun setTakeButtonEnable(enable: Boolean) {
        Log.d(TAG, "setTakeButtonEnable: Setting capture button enabled = $enable")
        binding.btnCapture.isEnabled = enable
    }

    private fun setupEvents() {
        Log.d(TAG, "setupEvents: Setting up button click listeners")
        binding.btnCapture.setOnClickListener {
            Log.d(TAG, "setupEvents: Capture button clicked")
            val bitmap = binding.cameraPreviewView.bitmap
            bitmap?.let { bitmap ->
                Log.d(TAG, "setupEvents: Got bitmap from camera preview, size: ${bitmap.width}x${bitmap.height}")
                viewModel.updateFaceDetectionState(
                    isInitialState = true,
                    detectionResult = "",
                    isMultipleFaces = false,
                    isDetectionReady = false
                )
                resetFaceDetectionValues()
                viewModel.updateDetectionObservation(CaptureBtnState(true, bitmap))
            } ?: run {
                Log.w(TAG, "setupEvents: No bitmap available from camera preview")
            }
        }

        binding.btnRetake.setOnClickListener {
            Log.d(TAG, "setupEvents: Retake button clicked")
            viewModel.updateFaceDetectionState(
                isInitialState = true,
                detectionResult = "no face",
                isMultipleFaces = false,
                isDetectionReady = false
            )
            resetFaceDetectionValues()
            viewModel.updateDetectionObservation(CaptureBtnState(false, null))
        }


    }
    private fun observeFaceDetectionStateAndUpdateUi() {
        Log.d(TAG, "observeFaceDetectionStateAndUpdateUi: Setting up observers")
        viewModel.isNeedStopFaceDetection.observe(viewLifecycleOwner) {
            Log.d(TAG, "observeFaceDetectionStateAndUpdateUi: isNeedStopFaceDetection changed - isNeedToStop: ${it.isNeedToStop}")
            isNeedToStopObserving = it.isNeedToStop
            toggleSubmitAndCaptureViewHolder(it.isNeedToStop, it.bitmap)
        }
        viewModel.faceUIState.observe(viewLifecycleOwner) { state ->
            Log.d(TAG, "observeFaceDetectionStateAndUpdateUi: faceUIState changed - state: $state")
            updateUi(
                state,
                isNeedToStopObserving,
                false
            )
        }
    }
    private fun PreviewView.startCamera() {
        Log.d(TAG, "startCamera: Starting camera preview")
        detectionStartTime = System.currentTimeMillis() + CAMERA_WARMUP_TIME
        val cameraProviderFuture = ProcessCameraProvider.getInstance(context)

        cameraProviderFuture.addListener({
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            val cameraSelector = CameraSelector.DEFAULT_FRONT_CAMERA
            val resolutionSelector = ResolutionSelector.Builder()
                .setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY)
                .build()

            val preview = Preview.Builder().setResolutionSelector(resolutionSelector)
                .setTargetRotation(binding.cameraPreviewView.display.rotation).build().apply {
                    implementationMode = PreviewView.ImplementationMode.COMPATIBLE
                    setSurfaceProvider(binding.cameraPreviewView.surfaceProvider)
                }


            val imageAnalysis = ImageAnalysis.Builder().setResolutionSelector(resolutionSelector)
                .setTargetRotation(binding.cameraPreviewView.display.rotation)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888).build()

            imageAnalysis.setAnalyzer(executor) { imageProxy ->
                processImage(imageProxy)
            }
            try {
                Log.d(TAG, "startCamera: Binding camera to lifecycle")
                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageAnalysis
                )
                Log.d(TAG, "startCamera: Camera bound successfully")

            } catch (exc: Exception) {
                Log.e(TAG, "startCamera: Error binding camera", exc)
            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }
    private val hasCameraPermission
        get() = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
        Log.d(TAG, "requestCameraPermission: Requesting camera permission")
        EasyPermissions.requestPermissions(
            PermissionRequest.Builder(this, 223, Manifest.permission.CAMERA)
                .setRationale("We need to access your camera to proceed.")
                .setPositiveButtonText(R.string.ok).setNegativeButtonText(
                 R.string.cancel
                ).build()
        )
    }
    override fun onRequestPermissionsResult(
        requestCode: Int, permissions: Array<out String>, grantResults: IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        Log.d(TAG, "onRequestPermissionsResult: requestCode=$requestCode, permissions=${permissions.contentToString()}, grantResults=${grantResults.contentToString()}")
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        Log.d(TAG, "onPermissionsGranted: requestCode=$requestCode, perms=$perms")
        if (perms.contains(Manifest.permission.CAMERA)) {
            Toast.makeText(requireContext(), "Camera Permission Granted!", Toast.LENGTH_SHORT)
                .show()
            onPermissionCameraGranted()
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        Log.d(TAG, "onPermissionsDenied: requestCode=$requestCode, perms=$perms")
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }
    }

    private fun requestPermissionOrStartPreview() {
        Log.d(TAG, "requestPermissionOrStartPreview: Checking camera permission")
        if (hasCameraPermission.not()) {
            Log.d(TAG, "requestPermissionOrStartPreview: No camera permission, requesting")
            requestCameraPermission()
        } else {
            Log.d(TAG, "requestPermissionOrStartPreview: Camera permission granted, starting preview")
            onPermissionCameraGranted()
        }
    }

    private fun onPermissionCameraGranted() {
        Log.d(TAG, "onPermissionCameraGranted: Starting camera preview")
        binding.cameraPreviewView.startCamera()
    }

    fun processImage(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now < detectionStartTime || now - lastDetectionTime < DETECTION_INTERVAL) {
            Log.d(TAG, "processImage: Skipping detection - now=$now, detectionStartTime=$detectionStartTime, lastDetectionTime=$lastDetectionTime, interval=${now - lastDetectionTime}")
            image.close()
            return
        }
        lastDetectionTime = now
        Log.d(TAG, "processImage: Processing image - image size: ${image.width}x${image.height}")
        
        // Try direct InputImage approach first
        try {
            val mediaImage = image.image
            if (mediaImage != null) {
                val inputImage = InputImage.fromMediaImage(mediaImage, image.imageInfo.rotationDegrees)
                Log.d(TAG, "processImage: Created InputImage directly from ImageProxy with rotation: ${image.imageInfo.rotationDegrees}")

                Log.d("#NewLog", "processImage: try-1 mediaImage is not null case")
                detectFacesDirect(inputImage, image)
            } else {
                Log.d("#NewLog", "processImage: try-1 mediaImage is null case")
                Log.w(TAG, "processImage: MediaImage is null, falling back to bitmap conversion")
                throw Exception("MediaImage is null")
            }
        } catch (e: Exception) {
            Log.d("#NewLog", "processImage: Catch block")
            Log.w(TAG, "processImage: Failed to create InputImage directly, falling back to bitmap conversion", e)
            val bitmap = imageProxyToBitmap(image)
            bitmap?.let { 
                Log.d(TAG, "processImage: Successfully converted to bitmap, size: ${bitmap.width}x${bitmap.height}")
                Log.d("#NewLog", "processImage: detect face from bitmap")
                detectFaces(it)
                image.close() // Close image after bitmap conversion
            } ?: run {
                Log.d("#NewLog", "processImage: Failed to convert image to bitmap")
                Log.w(TAG, "processImage: Failed to convert image to bitmap")
                image.close() // Close image if bitmap conversion failed
            }
        }
        // Don't close image here - it will be closed in the callback
    }
    
    private fun detectFacesDirect(inputImage: InputImage, imageProxy: ImageProxy) {
        Log.d("#NewLog", "detectFacesDirect: entered")
        Log.d(TAG, "detectFacesDirect: Starting face detection on InputImage ${inputImage.width}x${inputImage.height}")
        
        faceDetector.process(inputImage)
            .addOnSuccessListener { faces ->
                Log.d("#NewLog", "detectFacesDirect: addOnSuccessListener ${faces.size}")
                Log.d(TAG, "detectFacesDirect: Face detection successful, found ${faces.size} faces")
                
                // Log details about each detected face
                faces.forEachIndexed { index, face ->

                    Log.d(TAG, "detectFacesDirect: Face $index - boundingBox: ${face.boundingBox}, " +
                        "trackingId: ${face.trackingId}, " +
                        "headEulerAngleY: ${face.headEulerAngleY}, " +
                        "headEulerAngleZ: ${face.headEulerAngleZ}")
                }
                
                handleFaceDetectionResult(faces)
                imageProxy.close() // Close image after successful processing
            }
            .addOnFailureListener { e ->
                Log.d("#NewLog", "detectFacesDirect: addOnFailureListener ${e.localizedMessage}")
                Log.e(TAG, "detectFacesDirect: Face detection failed", e)
                // Handle failure - keep overlay white
                Log.d(TAG, "detectFacesDirect: Setting overlay border to WHITE due to detection failure")
                binding.overlayView.setBorderColor(Color.WHITE)
                imageProxy.close() // Close image after failed processing
            }
    }

    private fun imageProxyToBitmap(imageProxy: ImageProxy): Bitmap? {
        Log.d("#NewLog", "imageProxyToBitmap: entered")
        Log.d(TAG, "imageProxyToBitmap: Converting ImageProxy to Bitmap")
        try {
            val yBuffer = imageProxy.planes[0].buffer
            val uBuffer = imageProxy.planes[1].buffer
            val vBuffer = imageProxy.planes[2].buffer

            val ySize = yBuffer.remaining()
            val uSize = uBuffer.remaining()
            val vSize = vBuffer.remaining()

            Log.d(TAG, "imageProxyToBitmap: Buffer sizes - Y: $ySize, U: $uSize, V: $vSize")

            val nv21 = ByteArray(ySize + uSize + vSize)

            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)

            val yuvImage = YuvImage(nv21, ImageFormat.NV21, imageProxy.width, imageProxy.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
            val imageBytes = out.toByteArray()
            val bitmap = BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size)
            
            Log.d(TAG, "imageProxyToBitmap: Original bitmap size: ${bitmap?.width}x${bitmap?.height}")
            
            // Check if bitmap is valid
            if (bitmap == null) {
                Log.e(TAG, "imageProxyToBitmap: Failed to decode bitmap from image bytes")
                return null
            }
            
            // For front camera, we need to mirror the image horizontally and rotate it
            val matrix = Matrix()
            matrix.postScale(-1f, 1f) // Mirror horizontally for front camera
            matrix.postRotate(90f) // Rotate 90 degrees for portrait mode
            
            val processedBitmap = Bitmap.createBitmap(bitmap, 0, 0, bitmap.width, bitmap.height, matrix, true)
            Log.d(TAG, "imageProxyToBitmap: Processed bitmap size: ${processedBitmap.width}x${processedBitmap.height}")
            
            return processedBitmap
        } catch (e: Exception) {
            Log.e(TAG, "imageProxyToBitmap: Error converting ImageProxy to Bitmap", e)
            return null
        }
    }

    private fun detectFaces(bitmap: Bitmap) {
        Log.d("#NewLog", "detectFaces: Starting face detection")
        Log.d(TAG, "detectFaces: Starting face detection on bitmap ${bitmap.width}x${bitmap.height}")
        
        // Log bitmap details for debugging
        Log.d(TAG, "detectFaces: Bitmap config: ${bitmap.config}, isMutable: ${bitmap.isMutable}, hasAlpha: ${bitmap.hasAlpha()}")
        
        val image = InputImage.fromBitmap(bitmap, 0)
        Log.d(TAG, "detectFaces: Created InputImage from bitmap")
        
        faceDetector.process(image)
            .addOnSuccessListener { faces ->
                Log.d("#NewLog", "detectFaces: On Success faces ${faces.size}")
                Log.d(TAG, "detectFaces: Face detection successful, found ${faces.size} faces")
                // Log details about each detected face
                faces.forEachIndexed { index, face ->
                    Log.d(TAG, "detectFaces: Face $index - boundingBox: ${face.boundingBox}, " +
                        "trackingId: ${face.trackingId}, " +
                        "headEulerAngleY: ${face.headEulerAngleY}, " +
                        "headEulerAngleZ: ${face.headEulerAngleZ}")
                }
                
                handleFaceDetectionResult(faces)
            }
            .addOnFailureListener { e ->
                Log.d("#NewLog", "detectFaces: addOnFailureListener faces ${e.localizedMessage}")
                Log.e(TAG, "detectFaces: Face detection failed", e)
                // Handle failure - keep overlay white
                Log.d(TAG, "detectFaces: Setting overlay border to WHITE due to detection failure")
                binding.overlayView.setBorderColor(Color.WHITE)
            }
    }

    private fun handleFaceDetectionResult(faces: List<Face>) {
        when {
            faces.isEmpty() -> {
                // No faces detected
                consecutiveValidDetections = 0
                viewModel.updateFaceDetectionState(
                    isInitialState = false,
                    detectionResult = "No face detected",
                    isMultipleFaces = false,
                    isDetectionReady = false
                )
                binding.overlayView.setBorderColor(Color.WHITE)
            }
            faces.size > 1 -> {
                Log.d(TAG, "handleFaceDetectionResult: Multiple faces detected (${faces.size})")
                // Multiple faces detected
                consecutiveValidDetections = 0
                viewModel.updateFaceDetectionState(
                    isInitialState = false,
                    detectionResult = "Too many faces detected. Please take a solo photo.",
                    isMultipleFaces = true,
                    isDetectionReady = false
                )
                Log.d(TAG, "handleFaceDetectionResult: Setting overlay border to WHITE (multiple faces)")
                binding.overlayView.setBorderColor(Color.WHITE)
            }
            faces.size == 1 -> {
                Log.d(TAG, "handleFaceDetectionResult: Single face detected")
                val face = faces[0]
                val boundingBox = face.boundingBox
                val imageWidth = binding.cameraPreviewView.width
                val imageHeight = binding.cameraPreviewView.height
                
                Log.d(TAG, "handleFaceDetectionResult: Face bounding box: $boundingBox, image size: ${imageWidth}x${imageHeight}")
                
                // Check if face is properly positioned (in the center area)
                val isFaceCentered = isFaceInCenterArea(boundingBox, imageWidth, imageHeight)
                val isFaceSizeAppropriate = isFaceSizeAppropriate(boundingBox, imageWidth, imageHeight)
                
                Log.d(TAG, "handleFaceDetectionResult: Face centered: $isFaceCentered, size appropriate: $isFaceSizeAppropriate")
                
                if (isFaceCentered && isFaceSizeAppropriate) {
                    consecutiveValidDetections++
                    Log.d(TAG, "handleFaceDetectionResult: Face is valid, consecutive detections: $consecutiveValidDetections/$REQUIRED_CONSECUTIVE_DETECTIONS")
                    
                    if (consecutiveValidDetections >= REQUIRED_CONSECUTIVE_DETECTIONS) {
                        // Face is ready for capture
                        Log.d(TAG, "handleFaceDetectionResult: Face ready for capture! Setting overlay to GREEN")
                        viewModel.updateFaceDetectionState(
                            isInitialState = false,
                            detectionResult = "Face detected! Ready to capture.",
                            isMultipleFaces = false,
                            isDetectionReady = true
                        )
                        binding.overlayView.setBorderColor(Color.GREEN)
                    } else {
                        // Face detected but not ready yet
                        Log.d(TAG, "handleFaceDetectionResult: Face detected but not ready yet, setting overlay to WHITE")
                        viewModel.updateFaceDetectionState(
                            isInitialState = false,
                            detectionResult = "Hold still...",
                            isMultipleFaces = false,
                            isDetectionReady = false
                        )
                        binding.overlayView.setBorderColor(Color.WHITE)
                    }
                } else {
                    // Face detected but not properly positioned
                    consecutiveValidDetections = 0
                    val message = when {
                        !isFaceCentered -> "Center your face in the frame"
                        !isFaceSizeAppropriate -> "Move closer to the camera"
                        else -> "Position your face properly"
                    }
                    Log.d(TAG, "handleFaceDetectionResult: Face not properly positioned: $message")
                    viewModel.updateFaceDetectionState(
                        isInitialState = false,
                        detectionResult = message,
                        isMultipleFaces = false,
                        isDetectionReady = false
                    )
                    Log.d(TAG, "handleFaceDetectionResult: Setting overlay border to WHITE (face not positioned)")
                    binding.overlayView.setBorderColor(Color.WHITE)
                }
            }
        }
    }

    private fun isFaceInCenterArea(boundingBox: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        val centerX = boundingBox.centerX()
        val centerY = boundingBox.centerY()
        
        // Define center area (60% of the image)
        val centerAreaWidth = imageWidth * 0.6f
        val centerAreaHeight = imageHeight * 0.6f
        val centerAreaStartX = (imageWidth - centerAreaWidth) / 2
        val centerAreaStartY = (imageHeight - centerAreaHeight) / 2
        
        val isCentered = centerX >= centerAreaStartX && 
               centerX <= centerAreaStartX + centerAreaWidth &&
               centerY >= centerAreaStartY && 
               centerY <= centerAreaStartY + centerAreaHeight
        
        Log.d(TAG, "isFaceInCenterArea: Face center ($centerX, $centerY), center area: ($centerAreaStartX, $centerAreaStartY) to (${centerAreaStartX + centerAreaWidth}, ${centerAreaStartY + centerAreaHeight}), isCentered: $isCentered")
        
        return isCentered
    }

    private fun isFaceSizeAppropriate(boundingBox: Rect, imageWidth: Int, imageHeight: Int): Boolean {
        val faceWidth = boundingBox.width()
        val faceHeight = boundingBox.height()
        // Face should be at least 30% of the image width and not more than 80%
        val minFaceWidth = imageWidth * 0.3f
        val maxFaceWidth = imageWidth * 0.8f
        val isAppropriate = faceWidth >= minFaceWidth && faceWidth <= maxFaceWidth
        Log.d(TAG, "isFaceSizeAppropriate: Face size ${faceWidth}x${faceHeight}, min: $minFaceWidth, max: $maxFaceWidth, isAppropriate: $isAppropriate")
        return isAppropriate
    }

    private fun resetFaceDetectionValues() {
        lastDetectionTime = 0L
        detectionStartTime = 0L
        consecutiveValidDetections = 0
    }

    override fun onPause() {
        super.onPause()
        resetFaceDetectionValues()
        viewModel.updateFaceDetectionState(
            isInitialState = true,
            detectionResult = "No Face",
            isMultipleFaces = false,
            isDetectionReady = false
        )
        viewModel.updateDetectionObservation(CaptureBtnState(false, null))
    }

    fun updateUi(
        state: FaceUIState,
        isNeedToStopObserving: Boolean,
        isAutoCaptureEnable: Boolean = false
    ) {
        when (state) {
            is FaceUIState.Initial -> {
                binding.lyFocusingMain.visibility = View.VISIBLE
                binding.lySubmittingMain.visibility = View.GONE
                binding.ivMultipleFaces.visibility = View.INVISIBLE
                binding.ivFocus.visibility = View.VISIBLE
                binding.ivFocus.setIcon(R.drawable.ic_focus_white, requireContext())
                binding.btnCapture.isEnabled = false
                binding.btnCapture.setIcon(R.drawable.ic_disable_btn, requireContext())
                binding.overlayView.setBorderColor(Color.WHITE)
                binding.tvResultText.text = state.message

            }

            is FaceUIState.DetectingMultipleFaces -> {
                binding.ivMultipleFaces.visibility = View.VISIBLE
                binding.ivFocus.visibility = View.INVISIBLE
                binding.btnCapture.isEnabled = false
                binding.overlayView.setBorderColor(Color.WHITE)
                binding.btnCapture.setIcon(R.drawable.ic_disable_btn, requireContext())
                binding.tvResultText.text = state.message
            }

            is FaceUIState.ReadyToCapture -> {
                binding.ivMultipleFaces.visibility = View.INVISIBLE
                binding.ivFocus.visibility = View.VISIBLE
                binding.ivFocus.setIcon(R.drawable.ic_focus_green, requireContext())
                binding.tvResultText.text = state.message
                binding.btnCapture.isEnabled = true
                binding.btnCapture.setIcon(R.drawable.ic_btn_capture, requireContext())
                if (isNeedToStopObserving) {
                    binding.overlayView.setBorderColor(Color.WHITE)
                } else {
                    binding.overlayView.setBorderColor(Color.GREEN)
                }

                if (isAutoCaptureEnable) {
                    Handler(Looper.getMainLooper()).postDelayed({
                        viewModel.updateFaceDetectionState(
                            isInitialState = true,
                            detectionResult = "",
                            isMultipleFaces = false,
                            isDetectionReady = false
                        )
                        resetFaceDetectionValues()
                        binding.cameraPreviewView.bitmap?.let { bmp ->
                            viewModel.updateDetectionObservation(
                                CaptureBtnState(true, bmp)
                            )
                        }
                    }, 500)
                }
            }

            is FaceUIState.DetectingSingleButNotReady -> {
                binding.ivMultipleFaces.visibility = View.INVISIBLE
                binding.ivFocus.visibility = View.VISIBLE
                binding.ivFocus.setIcon(R.drawable.ic_focus_white, requireContext())
                binding.tvResultText.text = state.message
                binding.btnCapture.isEnabled = false
                binding.btnCapture.setIcon(R.drawable.ic_disable_btn, requireContext())
                binding.overlayView.setBorderColor(Color.WHITE)
            }
        }
    }

    /**
     * After capture button clicked, this function will be invoke through the isNeedStopFaceDetection ( observable ) from VM
     * @param isStopDetecting mean that btnCapture successfully clicked.
     * If @param isStopDetecting show submit layout and reset face detection controller values .
     * So, this function only be trigger only when btnCapture clicked,
     *
     * But if auto capture enable, this function directly trigger from FaceUIState.ReadyToCapture block inside updateUi
     */

    private fun toggleSubmitAndCaptureViewHolder(isStopDetecting: Boolean, bitmap: Bitmap?) {
        Log.d(TAG, "toggleSubmitAndCaptureViewHolder: isStopDetecting: $isStopDetecting, bitmap: ${bitmap != null}")
        
        if (isStopDetecting) {
            Log.d(TAG, "toggleSubmitAndCaptureViewHolder: Showing submit layout")
            binding.lyFocusingMain.visibility = View.GONE
            binding.lySubmittingMain.visibility = View.VISIBLE
            binding.ivSelfie.visibility = View.VISIBLE
            viewModel.updatePreviewState(true)
            binding.cameraPreviewView.visibility = View.INVISIBLE
            Log.d(TAG, "toggleSubmitAndCaptureViewHolder: Setting overlay border to WHITE (submit mode)")
            binding.overlayView.setBorderColor(Color.WHITE)
            bitmap?.let { bitmap ->
                binding.ivSelfie.setImageBitmap(bitmap)
            }
            resetFaceDetectionValues()
            consecutiveValidDetections = 0
        } else {
            Log.d(TAG, "toggleSubmitAndCaptureViewHolder: Showing capture layout")
            binding.lyFocusingMain.visibility = View.VISIBLE
            binding.lySubmittingMain.visibility = View.GONE
            binding.ivSelfie.visibility = View.INVISIBLE
            viewModel.updatePreviewState(false)
            binding.cameraPreviewView.visibility = View.VISIBLE
            binding.ivMultipleFaces.visibility = View.INVISIBLE
            binding.ivFocus.visibility = View.VISIBLE
            binding.ivFocus.setIcon(R.drawable.ic_focus_white, requireContext())
            binding.btnCapture.isEnabled = false
            binding.btnCapture.setIcon(R.drawable.ic_disable_btn, requireContext())
            Log.d(TAG, "toggleSubmitAndCaptureViewHolder: Setting overlay border to WHITE (capture mode)")
            binding.overlayView.setBorderColor(Color.WHITE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.d(TAG, "onDestroy: Fragment destroyed")
        resetFaceDetectionValues()
       // disposable.dispose()
        executor.shutdown()
        faceDetector.close()
        _binding = null
    }

}