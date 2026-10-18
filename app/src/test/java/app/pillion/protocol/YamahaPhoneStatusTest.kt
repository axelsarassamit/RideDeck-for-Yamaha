package app.pillion.protocol

import app.pillion.protocol.YamahaAccessoryStatus.CellSignal
import app.pillion.protocol.YamahaPhoneStatus.Network
import app.pillion.protocol.YamahaStatusUpdates.Kind
import org.junit.Assert.*
import org.junit.Test

class YamahaPhoneStatusTest {
    private fun status()=YamahaPhoneStatus(63,true,CellSignal.LEVEL_3,false)
    @Test fun batteryScalesWithoutOverflowAndRejectsInvalidPlatformReadings() {
        assertEquals(50,YamahaPhoneStatus.percent(100,200))
        assertEquals(0,YamahaPhoneStatus.percent(0,100))
        assertEquals(100,YamahaPhoneStatus.percent(Int.MAX_VALUE,Int.MAX_VALUE))
        for((level,scale) in listOf(-1 to 100,101 to 100,0 to 0,5 to -1)) assertNull(YamahaPhoneStatus.percent(level,scale))
    }
    @Test fun cellularStateUsesAndroidLevelsWithoutTreatingWifiAsCellBars() {
        val expected=listOf(CellSignal.NO_SIGNAL,CellSignal.LEVEL_1,CellSignal.LEVEL_2,CellSignal.LEVEL_3,CellSignal.LEVEL_4)
        for(level in 0..4) assertEquals(expected[level],YamahaPhoneStatus.cellSignal(false,Network.IN_SERVICE,level))
        assertEquals(CellSignal.NO_CONNECTION,YamahaPhoneStatus.cellSignal(true,Network.UNKNOWN,4))
        assertEquals(CellSignal.NO_CONNECTION,YamahaPhoneStatus.cellSignal(false,Network.POWER_OFF,4))
        assertEquals(CellSignal.NO_SIGNAL,YamahaPhoneStatus.cellSignal(false,Network.OUT_OF_SERVICE,4))
        assertEquals(CellSignal.NO_SIGNAL,YamahaPhoneStatus.cellSignal(false,Network.EMERGENCY_ONLY,4))
        assertNull(YamahaPhoneStatus.cellSignal(false,Network.UNKNOWN,4))
        for(level in listOf(null,-1,5)) assertNull(YamahaPhoneStatus.cellSignal(false,Network.IN_SERVICE,level))
    }
    @Test fun bikeHandsFreeLinkDoesNotCountAsAConnectedHeadset() {
        assertEquals(false,YamahaPhoneStatus.headset("BIKE",listOf(listOf("bike"),emptyList()),false))
        assertEquals(true,YamahaPhoneStatus.headset("BIKE",listOf(listOf("BIKE"),listOf("HEADSET")),false))
        assertEquals(true,YamahaPhoneStatus.headset("BIKE",listOf(null,null),true))
        assertNull(YamahaPhoneStatus.headset("BIKE",listOf(null,emptyList()),false))
        assertNull(YamahaPhoneStatus.headset("BIKE",listOf(emptyList(),emptyList()),null))
        assertEquals(true,YamahaPhoneStatus.headset("BIKE",listOf(null,listOf("HEADSET")),null))
    }
    @Test fun statusPublicationRequiresSessionAndResendsAfterReconnect() {
        val updates=YamahaStatusUpdates()
        assertTrue(updates.updates(status()).isEmpty())
        updates.start();assertEquals(3,updates.updates(status()).size)
        updates.stop();assertTrue(updates.updates(status(),Kind.entries.toSet()).isEmpty())
        updates.start();assertEquals(3,updates.updates(status()).size)
    }
    @Test fun unchangedReadingsAreDeduplicatedButBikeRequestsGetAResponse() {
        val updates=YamahaStatusUpdates();updates.start();updates.updates(status())
        assertTrue(updates.updates(status()).isEmpty())
        val response=updates.updates(status(),setOf(Kind.SIGNAL))
        assertEquals(listOf(Kind.SIGNAL),response.map { it.kind })
        assertArrayEquals(byteArrayOf(1,0x33,4),response.single().bytes)
    }
    @Test fun chargingAndHeadsetTransitionsSendExplicitOnAndOffWithoutChangingOtherIcons() {
        val updates=YamahaStatusUpdates();updates.start();updates.updates(status())
        val changed=updates.updates(status().copy(charging=false,headset=true))
        assertEquals(listOf(Kind.BATTERY,Kind.HEADSET),changed.map { it.kind })
        assertArrayEquals(byteArrayOf(1,0x15,63,0),changed[0].bytes)
        assertArrayEquals(byteArrayOf(1,0x1c,1),changed[1].bytes)
        assertArrayEquals(byteArrayOf(1,0x1c,0),updates.updates(status().copy(charging=false,headset=false)).single().bytes)
    }
    @Test fun unavailableReadingsAreNeverPublishedAsEmptyBatteryOrZeroBars() {
        val updates=YamahaStatusUpdates();updates.start();updates.updates(status())
        assertTrue(updates.updates(YamahaPhoneStatus(),Kind.entries.toSet()).isEmpty())
        assertEquals(3,updates.updates(status()).size)
        assertTrue(updates.updates(YamahaPhoneStatus(101,false,null,null)).isEmpty())
    }
    @Test fun combinedStatusMatchesSdkPackingWithoutAdvertisingUnsupportedControls() {
        val value=status().copy(phoneVolume=25,mediaVolume=75,thermal=2,headset=true)
        assertArrayEquals(byteArrayOf(1,0x3c,0xbf.toByte(),25,75,0x4a),YamahaAccessoryStatus.combined(value))
        assertArrayEquals(byteArrayOf(1,0x3c,63,25,75,0x40),
            YamahaAccessoryStatus.combined(value.copy(charging=false,headset=false,thermal=0)))
        assertNull(YamahaAccessoryStatus.combined(status()))
        for (invalid in listOf(value.copy(phoneVolume=null),value.copy(mediaVolume=101),
            value.copy(thermal=3),value.copy(signal=null),value.copy(headset=null)))
            assertNull(YamahaAccessoryStatus.combined(invalid))
    }
    @Test fun combinedPublicationTracksChargingThermalAndVolumeAndReconnect() {
        val updates=YamahaStatusUpdates();updates.start()
        val value=status().copy(phoneVolume=0,mediaVolume=100,thermal=0)
        assertEquals(4,updates.updates(value).size)
        assertTrue(updates.updates(value).isEmpty())
        assertEquals(listOf(Kind.COMBINED),updates.updates(value.copy(mediaVolume=50)).map { it.kind })
        assertEquals(listOf(Kind.COMBINED),updates.updates(value.copy(mediaVolume=50,thermal=1)).map { it.kind })
        updates.stop();assertTrue(updates.updates(value).isEmpty())
        updates.start();assertEquals(4,updates.updates(value).size)
    }

}
