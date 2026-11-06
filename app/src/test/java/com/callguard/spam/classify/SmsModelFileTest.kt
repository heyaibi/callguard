package com.callguard.spam.classify

import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.io.File

class SmsModelFileTest {
    @get:Rule
    val folder = TemporaryFolder()

    @Test
    fun missingFileLoadsAnEmptyModel() {
        val store = SmsModelFile(File(folder.root, "sms_model.bin"))

        assertEquals(0, store.load().docs)
    }

    @Test
    fun savedModelLoadsAsTheSameModel() {
        val store = SmsModelFile(File(folder.root, "sms_model.bin"))
        val model = TokenModel.empty()
        model.learn("VM-HDFCBK-S", "Your OTP is 482913. Do not share it.", spam = false)

        store.save(model)

        assertEquals(model, store.load())
    }

    @Test
    fun damagedFileLoadsAnEmptyModel() {
        val file = File(folder.root, "sms_model.bin")
        file.writeBytes(byteArrayOf(9, 9, 9))

        val store = SmsModelFile(file)

        assertEquals(0, store.load().docs)
    }
}
