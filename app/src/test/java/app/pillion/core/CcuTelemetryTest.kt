package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

class CcuTelemetryTest {
    private fun sample(id:Int,raw:ByteArray,server:ByteArray=byteArrayOf(0x18,0xe0.toByte(),9)):CcuProtocol.Message {
        val item=byteArrayOf(8,id.toByte(),0x12,raw.size.toByte())+raw
        return CcuProtocol.Message(0x10f,server+byteArrayOf(0x22,item.size.toByte())+item)
    }
    @Test fun sourceConfirmedOffsetsDecodeUnitsAndRetainReceiptTimes() {
        val raw=ByteArray(33).apply { this[0]=6;this[1]=0x40;this[2]=42;this[6]=190.toByte();this[8]=128.toByte();this[14]=80;this[15]=66 }
        val values=CcuTelemetry.decode(sample(1,raw),1000,2000).associateBy { it.metric }
        assertEquals(1600.0,values.getValue(BikeMetric.RPM).value,0.0)
        assertEquals(42.0,values.getValue(BikeMetric.SPEED).value,0.0)
        assertEquals(13.916015625,values.getValue(BikeMetric.VOLTAGE).value,0.000001)
        assertEquals(62.5,values.getValue(BikeMetric.THROTTLE).value,0.0)
        assertEquals(50.0,values.getValue(BikeMetric.COOLANT).value,0.0)
        assertEquals(36.0,values.getValue(BikeMetric.AIR).value,0.0)
        assertEquals(2000L,values.getValue(BikeMetric.RPM).wallMs)
        assertFalse(values.getValue(BikeMetric.RPM).fresh(6001));assertTrue(values.getValue(BikeMetric.RPM).fresh(6000))
    }
    @Test fun differentLayoutsServersAndInvalidSentinelsCannotCreatePlausibleValues() {
        assertTrue(CcuTelemetry.decode(sample(1,ByteArray(32)),0,0).isEmpty())
        assertTrue(CcuTelemetry.decode(sample(1,ByteArray(33),byteArrayOf(0x18,0xe1.toByte(),9)),0,0).isEmpty())
        val values=CcuTelemetry.decode(sample(1,ByteArray(33){255.toByte()}),0,0)
        assertFalse(values.any { it.metric in setOf(BikeMetric.VOLTAGE,BikeMetric.COOLANT,BikeMetric.AIR,BikeMetric.THROTTLE,BikeMetric.SPEED) })
    }
    @Test fun odometerIsUnsignedFourBytesAndNotAPackedFuelPercentage() {
        val value=CcuTelemetry.decode(sample(72,byteArrayOf(0,1,0xe2.toByte(),0x40)),10,20).single()
        assertEquals(12345.6,value.value,0.000001);assertEquals(BikeMetric.ODOMETER,value.metric)
        assertEquals(214748364.8,CcuTelemetry.decode(sample(72,byteArrayOf(0x80.toByte(),0,0,0)),10,20).single().value,0.000001)
        assertTrue(CcuTelemetry.decode(sample(72,byteArrayOf(1,2)),10,20).isEmpty())
    }
    @Test fun diagnosticCodesAndRawInjectionCounterAreSeparatedFromFuelRange() {
        val message=CcuProtocol.Message(0x106,byteArrayOf(0x1a,7,8,0xe0.toByte(),9,0x10,42,0x18,1))
        assertEquals(listOf(CcuTelemetry.Fault(1248,42,1)),CcuTelemetry.faults(message))
        assertEquals(4294967295L,CcuTelemetry.injectionCount(CcuProtocol.Message(0x120,ByteArray(4){255.toByte()})))
        assertNull(CcuTelemetry.injectionCount(CcuProtocol.Message(0x120,ByteArray(3))))
        assertNull(FuelRange.estimate(null,0,50.0,1.0))
    }
}
