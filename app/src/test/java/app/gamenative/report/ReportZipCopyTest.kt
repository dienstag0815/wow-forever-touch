package app.gamenative.report

import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertEquals
import org.junit.Test
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.FileNotFoundException

class ReportZipCopyTest {
    @Test
    fun copiesTheBytesAndReturnsTheCount() {
        val zip = File.createTempFile("report", ".zip")
        try {
            val bytes = ByteArray(70_000) { (it % 251).toByte() }
            zip.writeBytes(bytes)
            val out = ByteArrayOutputStream()
            assertEquals(bytes.size.toLong(), ReportZipCopy.copy(zip, out))
            assertArrayEquals(bytes, out.toByteArray())
        } finally {
            zip.delete()
        }
    }

    @Test(expected = FileNotFoundException::class)
    fun aMissingZipThrows() {
        ReportZipCopy.copy(File("does-not-exist.zip"), ByteArrayOutputStream())
    }
}
