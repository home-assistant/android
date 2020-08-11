package io.homeassistant.companion.android.nfc

import android.util.Log
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import java.util.UUID

class NfcViewModel : ViewModel() {

    // Create a LiveData with a String
    val nfcReadEvent: MutableLiveData<String> = MutableLiveData()
    val nfcWriteTagEvent: MutableLiveData<String> = MutableLiveData()
    val nfcWriteTagDoneEvent: SingleLiveEvent<String> = SingleLiveEvent()

    init {
        Log.i("NfcViewModel", "NfcViewModel created!")
    }

    override fun onCleared() {
        super.onCleared()
        Log.i("NfcViewModel", "NfcViewModel destroyed!")
    }

    fun postNewUUID() {
        nfcWriteTagEvent.postValue(UUID.randomUUID().toString())
    }
}
