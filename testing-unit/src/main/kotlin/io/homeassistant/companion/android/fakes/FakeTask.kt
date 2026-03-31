package io.homeassistant.companion.android.fakes

import android.app.Activity
import com.google.android.gms.tasks.OnFailureListener
import com.google.android.gms.tasks.OnSuccessListener
import com.google.android.gms.tasks.Task
import java.util.concurrent.Executor

class FakeTask<T>(private val result: Result<T>) : Task<T>() {

    override fun addOnFailureListener(p0: OnFailureListener): Task<T?> {
        TODO("Not yet implemented")
    }

    override fun addOnFailureListener(p0: Activity, p1: OnFailureListener): Task<T?> {
        TODO("Not yet implemented")
    }

    override fun addOnFailureListener(p0: Executor, p1: OnFailureListener): Task<T?> {
        TODO("Not yet implemented")
    }

    override fun addOnSuccessListener(p0: OnSuccessListener<in T>): Task<T?> {
        TODO("Not yet implemented")
    }

    override fun addOnSuccessListener(p0: Activity, p1: OnSuccessListener<in T>): Task<T?> {
        TODO("Not yet implemented")
    }

    override fun addOnSuccessListener(p0: Executor, p1: OnSuccessListener<in T>): Task<T?> {
        TODO("Not yet implemented")
    }

    override fun getException(): Exception? {
        return result.exceptionOrNull() as Exception?
    }

    override fun getResult(): T? {
        return result.getOrNull()
    }

    override fun <X : Throwable?> getResult(p0: Class<X?>): T? {
        TODO("Not yet implemented")
    }

    override fun isCanceled(): Boolean {
        return false
    }

    override fun isComplete(): Boolean {
        return true
    }

    override fun isSuccessful(): Boolean {
        return result.isSuccess
    }
}
