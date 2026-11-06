package app.pillion.core

import org.junit.Assert.*
import org.junit.Test

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
    @Test fun startupRequestsOnlyProductionTelemetryAndNotices() {
        val plan=CcuReadPlan(1000)
        assertEquals(0x103,CcuProtocol.u(plan.request()!!.bytes,0,2))
        assertTrue(plan.accepts(message(0x104,"1a02e009")));plan.advance()
        assertEquals(0x126,CcuProtocol.u(plan.request()!!.bytes,0,2))
        assertTrue(plan.accepts(message(0x127,"18e009220401030948")));plan.advance()
        assertEquals(listOf(1,72),plan.streamIds)
        for((command,response) in listOf(0x110 to live(), 0x105 to message(0x106,"3001"),
            0x109 to message(0x106,""))) {
            assertEquals(command,CcuProtocol.u(plan.request()!!.bytes,0,2))
            assertTrue(plan.accepts(response));plan.advance()
        }
        assertEquals(CcuReadPlan.Phase.READY,plan.phase)
        assertNull(plan.request())
    }
    @Test fun optionalTimeoutsAdvanceButCapabilitiesAreMandatoryAndLateRepliesCannotSkipPhases() {
        val plan=CcuReadPlan();assertFalse(plan.timeout());readyForLive(plan)
        assertFalse(plan.accepts(message(0x10f,"18e1092208084812040001e240")))
        assertFalse(plan.timeout());assertTrue(plan.accepts(live()));plan.advance()
        assertTrue(plan.timeout());assertEquals(CcuReadPlan.Phase.FAULT_LIVE,plan.phase)
        assertFalse(plan.accepts(message(0x10b,"18e009")))
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
