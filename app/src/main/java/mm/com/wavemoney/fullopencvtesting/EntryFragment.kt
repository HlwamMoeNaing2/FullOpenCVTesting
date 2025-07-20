package mm.com.wavemoney.fullopencvtesting

import android.os.Bundle
import androidx.fragment.app.Fragment
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.navigation.fragment.findNavController
import mm.com.wavemoney.fullopencvtesting.databinding.FragmentEntryBinding
import mm.com.wavemoney.fullopencvtesting.databinding.FragmentFaceDetectionBinding


class EntryFragment : Fragment() {
    private var _binding: FragmentEntryBinding? = null
    // This property is only valid between onCreateView and
// onDestroyView.
    private val binding get() = _binding!!

    override fun onCreateView(
        inflater: LayoutInflater, container: ViewGroup?,
        savedInstanceState: Bundle?
    ): View? {
        _binding = FragmentEntryBinding.inflate(inflater, container, false)
        val view = binding.root
        return view
    }

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)
        binding.faceDetect.setOnClickListener {
            findNavController().navigate(R.id.action_entryFragment_to_faceDetectionFragment)
        }
        binding.card.setOnClickListener {
            findNavController().navigate(R.id.action_entryFragment_to_cardDetectionFragment)
        }

    }


}