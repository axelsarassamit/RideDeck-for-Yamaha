package app.pillion.core

import org.junit.Assert.*
import org.junit.Test
import java.io.ByteArrayOutputStream

class CcuReadPlanTest {
    private fun message(command:Int,hex:String)=CcuProtocol.Message(command,hex.chunked(2).map { it.toInt(16).toByte() }.toByteArray())
    private fun readyForLive(plan:CcuReadPlan) {
        assertTrue(plan.accepts(message(0x104,"1a02e009")));plan.advance()
        assertTrue(plan.accepts(message(0x127,"18e009220401030948")));plan.advance()
    }
    private fun live()=message(0x10f,"18e0092208084812040001e240")
    @Test fun keepaliveTrafficCannotCompleteOrAdvanceStartupReads() {
        val plan=CcuReadPlan()
        for (command in listOf(0x135,0x136)) {
            assertFalse(plan.accepts(message(command,"ff")))
            assertEquals(CcuReadPlan.Phase.SERVER,plan.phase)
        }
        readyForLive(plan)
        for (command in listOf(0x135,0x136)) {
            assertFalse(plan.accepts(message(command,"00")))
            assertEquals(CcuReadPlan.Phase.LIVE,plan.phase)
        }
        assertTrue(plan.accepts(live()))
    }
    @Test fun startupFollowsCapturedSequenceAndNeverRequestsActuators() {
        val plan=CcuReadPlan(1000)
        val commands=mutableListOf<Int>()
        commands.add(CcuProtocol.u(plan.request()!!.bytes,0,2));readyForLive(plan)
        assertEquals(listOf(1,72),plan.streamIds)
        commands.add(CcuProtocol.u(plan.request()!!.bytes,0,2))
        assertTrue(plan.accepts(live()));plan.advance()
        for((command,response) in listOf(0x10a to message(0x10b,"18e009"),0x10c to message(0x10d,"18e009"),
            0x105 to message(0x106,"3001"),0x109 to message(0x106,""),
            0x13b to message(0x114,"1a0708890412020102"),0x13b to message(0x114,"1a0708ae0412020102"))) {
            assertEquals(command,CcuProtocol.u(plan.request()!!.bytes,0,2));commands.add(command)
            assertTrue(plan.accepts(response));plan.advance()
        }
        assertEquals(0x11f,CcuProtocol.u(plan.request()!!.bytes,0,2))
        assertTrue(plan.accepts(message(0x120,"00001234")));plan.advance();assertNull(plan.request())
        assertEquals(CcuReadPlan.Phase.READY,plan.phase)
        assertFalse(commands.any { it in setOf(0x41,0x4c,0x4d,0x121,0x122,0x201) })
    }
    @Test fun optionalTimeoutsAdvanceButCapabilitiesAreMandatoryAndLateRepliesCannotSkipPhases() {
        val plan=CcuReadPlan();assertFalse(plan.timeout());readyForLive(plan)
        assertFalse(plan.accepts(message(0x10f,"18e1092208084812040001e240")))
        assertFalse(plan.timeout());assertTrue(plan.accepts(live()));plan.advance()
        assertTrue(plan.timeout());assertEquals(CcuReadPlan.Phase.MARKET,plan.phase)
        assertFalse(plan.accepts(message(0x10b,"18e009")))
        plan.advance();plan.advance()
        assertEquals(CcuReadPlan.Phase.FAULT_LIVE,plan.phase)
        assertFalse(plan.accepts(message(0x106,"3001")))
        assertTrue(plan.accepts(message(0x106,"")))
    }
    @Test fun wrongBikeAndUnsupportedFieldsCannotTriggerLiveRequests() {
        val plan=CcuReadPlan()
        try { plan.accepts(message(0x104,"1a020304"));fail() } catch(_:IllegalArgumentException) {}
        assertEquals(CcuReadPlan.Phase.SERVER,plan.phase)
        plan.advance()
        try { plan.accepts(message(0x127,"18e00922020309"));fail() } catch(_:IllegalArgumentException) {}
        assertEquals(CcuReadPlan.Phase.SUPPORTED,plan.phase)
    }
    @Test fun readEncodersMatchIndependentWireVectorsAndRejectOtherCommands() {
        assertArrayEquals(byteArrayOf(1,0x26,4,0xe0.toByte()),CcuProtocol.supportedRequest(1248))
        assertArrayEquals("011000000918e009220201483064".chunked(2).map { it.toInt(16).toByte() }.toByteArray(),CcuProtocol.readRequest(0x110,1248,listOf(1,72),100))
        try { CcuProtocol.control(0x4c,byteArrayOf(1));fail() } catch(_:IllegalArgumentException) {}
        try { CcuProtocol.readRequest(0x201,1248);fail() } catch(_:IllegalArgumentException) {}
        try { CcuProtocol.readRequest(0x110,1248,listOf(1),0);fail() } catch(_:IllegalArgumentException) {}
    }
}
