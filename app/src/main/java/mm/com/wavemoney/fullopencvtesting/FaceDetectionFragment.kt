package mm.com.wavemoney.fullopencvtesting

import android.Manifest
import android.content.Context.MODE_PRIVATE
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.graphics.Color
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
import mm.com.wavemoney.fullopencvtesting.face_helper.FaceDetectionHelper
import mm.com.wavemoney.fullopencvtesting.face_helper.FaceResult

import mm.com.wavemoney.fullopencvtesting.utils.setIcon
import mm.com.wavemoney.fullopencvtesting.utils.setSystemNavigationBarColor
import mm.com.wavemoney.fullopencvtesting.utils.updateStatusBarColor
import org.opencv.android.OpenCVLoader
import org.opencv.objdetect.CascadeClassifier
//import org.opencv.android.OpenCVLoader
//import org.opencv.objdetect.CascadeClassifier
import pub.devrel.easypermissions.AppSettingsDialog
import pub.devrel.easypermissions.EasyPermissions
import pub.devrel.easypermissions.PermissionRequest
import java.io.File
import java.io.FileOutputStream
import java.util.concurrent.Executors

class FaceDetectionFragment : Fragment() , EasyPermissions.PermissionCallbacks{
    private var _binding: FragmentFaceDetectionBinding? = null
    // This property is only valid between onCreateView and
// onDestroyView.
    private val binding get() = _binding!!



    private var cascadeClassifier: CascadeClassifier? = null
    private val faceHelper: FaceDetectionHelper by lazy {
        FaceDetectionHelper()
    }

    private val executor = Executors.newSingleThreadExecutor()
    private var lastDetectionTime = 0L
    private var detectionStartTime = 0L
    private var consecutiveValidDetections = 0

    private var isNeedToStopObserving: Boolean = false
   // private val disposable = CompositeDisposable()
   private val viewModel: LivePhotoViewModel by viewModels()

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentFaceDetectionBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        updateStatusBarColor(Color.BLACK)
     initOpenCv()
        setupView()
        setupEvents()
        setupViewModel()
        observeFaceDetectionStateAndUpdateUi()
        updateStatusBarColor(Color.BLACK)
        setSystemNavigationBarColor(R.color.black)
    }
    override fun onResume() {
        super.onResume()
        viewModel.updateDetectionObservation(CaptureBtnState(false, null))
        viewModel.updateFaceDetectionState(
            isInitialState = true,
            detectionResult = "No Face",
            isMultipleFaces = false,
            isDetectionReady = false
        )

    }
    private fun initOpenCv() {
        OpenCVLoader.initLocal()
        val inputStream = resources.openRawResource(R.raw.haarcascade_frontalface_default)
        val cascadeDir = requireActivity().getDir("cascade", MODE_PRIVATE)
        val cascadeFile = File(cascadeDir, "haarcascade_frontalface_default.xml")
        val outputStream = FileOutputStream(cascadeFile)
        inputStream.copyTo(outputStream)
        inputStream.close()
        outputStream.close()
        cascadeClassifier = CascadeClassifier(cascadeFile.absolutePath)
        if (cascadeClassifier?.empty() == true) cascadeClassifier = null else cascadeDir.delete()
    }
    private fun setupView() {
        if (hasCameraPermission) {
            setTakeButtonEnable(true)
            binding.cameraPreviewView.startCamera()
        } else {
            setTakeButtonEnable(false)
            requestPermissionOrStartPreview()
        }
    }

    private fun setTakeButtonEnable(enable: Boolean) {
        binding.btnCapture.isEnabled = enable
    }

    private fun setupEvents() {
        binding.btnCapture.setOnClickListener {
            val bitmap = binding.cameraPreviewView.bitmap
            bitmap?.let { bitmap ->
                viewModel.updateFaceDetectionState(
                    isInitialState = true,
                    detectionResult = "",
                    isMultipleFaces = false,
                    isDetectionReady = false
                )
                resetFaceDetectionValues()
                viewModel.updateDetectionObservation(CaptureBtnState(true, bitmap))
            }
        }

        binding.btnRetake.setOnClickListener {
            viewModel.updateFaceDetectionState(
                isInitialState = true,
                detectionResult = "no face",
                isMultipleFaces = false,
                isDetectionReady = false
            )
            resetFaceDetectionValues()
            viewModel.updateDetectionObservation(CaptureBtnState(false, null))
        }

        binding.btnSubmit.setOnClickListener {
            binding.ivSelfie.drawable?.toBitmap()?.let { bmp ->
                executor.execute {
                    val safeBitmap = bmp.copy(Bitmap.Config.ARGB_8888, true)
                    val result = faceHelper.detectFaces(safeBitmap, cascadeClassifier)
                    when (result) {
                        FaceResult.SINGLE -> {
//                            if (screenId == LOOK_UP_RESULT_FRAGMENT || screenId == SELFIE_NOT_MATCH_FRAGMENT) {
//                                // todo add missing info for selfie condition in later
//                                //if this conditions meet save and call create-subscriber api else only Save and navigate back
//                                viewModel.saveBitmap(bmp, false)
//
//                            } else {
//                                viewModel.saveBitmap(bmp, true)
//                            }

                        }

                        else -> {
                            Log.d("##facee", "setupEvents: No Face Found")
                        }

                    }
                }
            }
        }
    }
    private fun observeFaceDetectionStateAndUpdateUi() {
        viewModel.isNeedStopFaceDetection.observe(viewLifecycleOwner) {
            isNeedToStopObserving = it.isNeedToStop
            toggleSubmitAndCaptureViewHolder(it.isNeedToStop, it.bitmap)
        }
        viewModel.faceUIState.observe(viewLifecycleOwner) { state ->
            updateUi(
                state,
                isNeedToStopObserving,
                false
            )
        }
    }
    private fun setupViewModel() {
    }



    private fun PreviewView.startCamera() {
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

                cameraProvider.unbindAll()

                cameraProvider.bindToLifecycle(
                    viewLifecycleOwner, cameraSelector, preview, imageAnalysis
                )

            } catch (exc: Exception) {

            }

        }, ContextCompat.getMainExecutor(requireContext()))
    }
    private val hasCameraPermission
        get() = ContextCompat.checkSelfPermission(
            requireContext(), Manifest.permission.CAMERA
        ) == PackageManager.PERMISSION_GRANTED

    private fun requestCameraPermission() {
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
        EasyPermissions.onRequestPermissionsResult(requestCode, permissions, grantResults, this)
    }

    override fun onPermissionsGranted(requestCode: Int, perms: MutableList<String>) {
        if (perms.contains(Manifest.permission.CAMERA)) {
            Toast.makeText(requireContext(), "Camera Permission Granted!", Toast.LENGTH_SHORT)
                .show()
            onPermissionCameraGranted()
        }
    }

    override fun onPermissionsDenied(requestCode: Int, perms: MutableList<String>) {
        if (EasyPermissions.somePermissionPermanentlyDenied(this, perms)) {
            AppSettingsDialog.Builder(this).build().show()
        }
    }

    private fun requestPermissionOrStartPreview() {
        if (hasCameraPermission.not()) {
            requestCameraPermission()
        } else {
            onPermissionCameraGranted()
        }
    }

    private fun onPermissionCameraGranted() {
        binding.cameraPreviewView.startCamera()
    }

    fun processImage(image: ImageProxy) {
        val now = System.currentTimeMillis()
        if (now < detectionStartTime || now - lastDetectionTime < DETECTION_INTERVAL) {
            image.close()
            return
        }
        lastDetectionTime = now
        val getRotatedBitmap = faceHelper.processImage(image)
        detectFaces(getRotatedBitmap)
    }


    private fun detectFaces(bitmap: Bitmap) {

        val detectingResult = faceHelper.detectFaces(bitmap, cascadeClassifier)
        when (detectingResult) {
            FaceResult.SINGLE -> {
                consecutiveValidDetections++
                if (consecutiveValidDetections >= REQUIRED_CONSECUTIVE_DETECTIONS) {
                    viewModel.updateFaceDetectionState(
                        isInitialState = false,
                        detectionResult = "Capture Now",
                        isMultipleFaces = false,
                        isDetectionReady = true
                    )
                } else {
                    viewModel.updateFaceDetectionState(
                        isInitialState = false,
                        detectionResult = "NO Face",
                        isMultipleFaces = false,
                        isDetectionReady = false
                    )
                }
            }

            FaceResult.MULTIPLE -> {
                consecutiveValidDetections = 0
                viewModel.updateFaceDetectionState(
                    isInitialState = false,
                    detectionResult = "Multi face",
                    isMultipleFaces = true,
                    isDetectionReady = false
                )
            }

            FaceResult.NO_FACE -> {
                consecutiveValidDetections = 0
                viewModel.updateFaceDetectionState(
                    isInitialState = false,
                    detectionResult = "No Face",
                    isMultipleFaces = false,
                    isDetectionReady = false
                )
            }
        }

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
        if (isStopDetecting) {
            binding.lyFocusingMain.visibility = View.GONE
            binding.lySubmittingMain.visibility = View.VISIBLE
            binding.ivSelfie.visibility = View.VISIBLE
            viewModel.updatePreviewState(true)
            binding.cameraPreviewView.visibility = View.INVISIBLE
            binding.overlayView.setBorderColor(Color.WHITE)
            bitmap?.let { bitmap ->
                binding.ivSelfie.setImageBitmap(bitmap)
            }
            resetFaceDetectionValues()
            consecutiveValidDetections = 0
        } else {
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
            binding.overlayView.setBorderColor(Color.WHITE)
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        resetFaceDetectionValues()
       // disposable.dispose()
        executor.shutdown()
        _binding = null
    }

    companion object {
        private const val REQUIRED_CONSECUTIVE_DETECTIONS = 5
        private const val CAMERA_WARMUP_TIME = 1000L
        private const val DETECTION_INTERVAL = 300L
    }

}