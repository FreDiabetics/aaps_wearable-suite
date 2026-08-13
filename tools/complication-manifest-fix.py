from pathlib import Path

p = Path('app-wear/src/main/AndroidManifest.xml')
s = p.read_text(encoding='utf-8')

replacements = {
    'app.aapswear.complications.GlucoseComplication': ('SHORT_TEXT,RANGED_VALUE', 'SHORT_TEXT,RANGED_VALUE,LONG_TEXT'),
    'app.aapswear.complications.IobComplication': ('SHORT_TEXT', 'SHORT_TEXT,RANGED_VALUE'),
    'app.aapswear.complications.CobComplication': ('SHORT_TEXT', 'SHORT_TEXT,RANGED_VALUE'),
    'app.aapswear.complications.LoopComplication': ('SHORT_TEXT', 'SHORT_TEXT,ICON'),
}
for service, (old_types, new_types) in replacements.items():
    pos = s.find(service)
    if pos < 0:
        raise SystemExit(f'missing {service}')
    end = s.find('</service>', pos)
    block = s[pos:end]
    block2 = block.replace(f'android:value="{old_types}"', f'android:value="{new_types}"', 1)
    if block2 == block and f'android:value="{new_types}"' not in block:
        raise SystemExit(f'could not update {service}')
    s = s[:pos] + block2 + s[end:]

if 'GlucosePlusDeltaComplication' not in s:
    services = '''
        <service android:name="app.aapswear.complications.GlucosePlusDeltaComplication" android:label="29 Glukose + Delta" android:icon="@drawable/comp_preview_04" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,LONG_TEXT"/></service>
        <service android:name="app.aapswear.complications.SensorAgeComplication" android:label="30 Sensoralter" android:icon="@drawable/comp_preview_05" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,RANGED_VALUE"/></service>
        <service android:name="app.aapswear.complications.TirComplication" android:label="31 TIR" android:icon="@drawable/comp_preview_08" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,GOAL_PROGRESS,WEIGHTED_ELEMENTS"/></service>
        <service android:name="app.aapswear.complications.GlucoseTrendDeltaAgeComplication" android:label="32 Glukose + Trend + Delta + Zeit" android:icon="@drawable/comp_preview_04" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,LONG_TEXT"/></service>
        <service android:name="app.aapswear.complications.GlucoseTrendAgeComplication" android:label="33 Glukose + Trend + Zeit" android:icon="@drawable/comp_preview_02" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,LONG_TEXT"/></service>
        <service android:name="app.aapswear.complications.IobCobBasalComplication" android:label="34 IOB + COB + Basal" android:icon="@drawable/comp_preview_15" android:exported="true" android:permission="com.google.android.wearable.permission.BIND_COMPLICATION_PROVIDER"><intent-filter><action android:name="android.support.wearable.complications.ACTION_COMPLICATION_UPDATE_REQUEST"/></intent-filter><meta-data android:name="android.support.wearable.complications.SUPPORTED_TYPES" android:value="SHORT_TEXT,LONG_TEXT"/></service>
'''
    if '    </application>' not in s:
        raise SystemExit('missing application close')
    s = s.replace('    </application>', services + '    </application>', 1)

p.write_text(s, encoding='utf-8')
