package app.aapswear.mobile

import java.io.File
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class SugarliciousAnalogPreviewGeometryTest {
    @Test
    fun `preview geometry matches final analog WFF`() {
        val xml = watchfaceFile().readText()

        assertTrue(xml.contains("slotId=\"7\"") && xml.contains("x=\"78\" y=\"68\" width=\"294\" height=\"103\""))
        assertTrue(xml.contains("startAngle=\"250\" endAngle=\"336\" direction=\"CLOCKWISE\""))
        assertTrue(xml.contains("startAngle=\"8\" endAngle=\"67\" direction=\"CLOCKWISE\""))
        assertTrue(xml.contains("startAngle=\"96\" endAngle=\"158\" direction=\"CLOCKWISE\""))
        assertTrue(xml.contains("startAngle=\"266\" endAngle=\"190\" direction=\"COUNTER_CLOCKWISE\""))
        assertTrue(xml.contains("slotId=\"4\"") && xml.contains("x=\"72\" y=\"170\" width=\"110\" height=\"110\""))
        assertTrue(xml.contains("slotId=\"5\"") && xml.contains("x=\"268\" y=\"170\" width=\"110\" height=\"110\""))
        assertTrue(xml.contains("slotId=\"6\"") && xml.contains("x=\"160\" y=\"249\" width=\"130\" height=\"130\""))
        assertTrue(xml.contains("resource=\"sugarlicious_analog_template\""))
        assertTrue(xml.contains("x=\"216.21\" y=\"102.76\" width=\"17.58\" height=\"94.93\""))
        assertTrue(xml.contains("x=\"216.21\" y=\"33.28\" width=\"17.58\" height=\"164.36\""))

        assertTrue(SugarliciousAnalogGeometry.graph == AnalogRectGeometry(78f, 68f, 294f, 103f))
        assertTrue(SugarliciousAnalogGeometry.middleLeft == AnalogRectGeometry(72f, 170f, 110f, 110f))
        assertTrue(SugarliciousAnalogGeometry.middleRight == AnalogRectGeometry(268f, 170f, 110f, 110f))
        assertTrue(SugarliciousAnalogGeometry.bottomCenter == AnalogRectGeometry(160f, 249f, 130f, 130f))
    }

    @Test
    fun `system preview uses final template and overlay not stale target`() {
        val preview = previewFile().readText()
        assertTrue(preview.contains("@drawable/sugarlicious_analog_template"))
        assertTrue(preview.contains("@drawable/sugarlicious_analog_preview_overlay"))
        assertFalse(preview.contains("sugarlicious_analog_preview_target"))
    }

    private fun watchfaceFile(): File = repoFile(
        "watchfaces/sugarlicious-analog/src/main/res/raw/watchface.xml",
    )

    private fun previewFile(): File = repoFile(
        "watchfaces/sugarlicious-analog/src/main/res/drawable-nodpi/preview.xml",
    )

    private fun repoFile(path: String): File {
        val cwd = File(System.getProperty("user.dir"))
        val candidates = listOf(File(cwd, path), File(cwd.parentFile, path))
        return candidates.firstOrNull(File::isFile)
            ?: error("Repository file not found: $path from ${cwd.absolutePath}")
    }
}
