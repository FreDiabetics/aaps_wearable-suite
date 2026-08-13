from pathlib import Path

p = Path('complications/src/test/kotlin/app/aapswear/complications/TherapyComplicationsTest.kt')
s = p.read_text(encoding='utf-8')
s = s.replace('assertEquals(28, AllProviders.classes.distinct().size)', 'assertEquals(34, AllProviders.classes.distinct().size)')
s = s.replace('assertEquals("+5 · 0m",', 'assertEquals("0m · +5",')

if 'glucose plus delta exposes both values' not in s:
    marker = '    @Test\n    fun `glucose trend also supplies short text`()'
    tests = '''    @Test
    fun `glucose plus delta exposes both values`() {
        val service = Robolectric.buildService(GlucosePlusDeltaComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.SHORT_TEXT) as ShortTextComplicationData
        assertEquals("123 +5", data.text.getTextAt(service.resources, Instant.now()).toString())
    }

    @Test
    fun `IOB COB basal keeps basal in the title`() {
        val service = Robolectric.buildService(IobCobBasalComplication::class.java).create().get()
        val data = service.getPreviewData(ComplicationType.SHORT_TEXT) as ShortTextComplicationData
        assertEquals("1.2U · 15g", data.text.getTextAt(service.resources, Instant.now()).toString())
        assertEquals("Basal 0.80U/h", data.title!!.getTextAt(service.resources, Instant.now()).toString())
    }

'''
    if marker not in s:
        raise SystemExit('missing test insertion point')
    s = s.replace(marker, tests + marker, 1)

p.write_text(s, encoding='utf-8')
