package com.axelsarassamit.migrationhelper;

import org.junit.Test;
import java.util.*;
import static org.junit.Assert.*;

public final class RestorePolicyTest {
    private static final Map<String,Object> EMPTY=Collections.emptyMap();
    private static Map<String,Object> value(int n){return Collections.singletonMap("synthetic",n);}
    @Test public void absentEmptyPreferencesArePlanned(){assertTrue(RestorePolicy.needsPreferenceWrite(false,EMPTY,EMPTY));}
    @Test public void matchingExistingPreferencesAreUntouched(){assertFalse(RestorePolicy.needsPreferenceWrite(true,value(1),value(1)));}
    @Test public void differentPreferencesRequireConflictCheck(){assertTrue(RestorePolicy.needsPreferenceWrite(true,value(2),value(1)));}
    @Test public void rollbackAllowsCompletedImport(){assertTrue(RestorePolicy.safePreferenceRollback(value(1),value(1),value(1)));}
    @Test public void rollbackAllowsUnwrittenEmptyState(){assertTrue(RestorePolicy.safePreferenceRollback(EMPTY,EMPTY,value(1)));}
    @Test public void rollbackPreservesLaterUserChanges(){assertFalse(RestorePolicy.safePreferenceRollback(value(2),value(2),value(1)));}
    @Test public void rollbackRejectsDiskCacheDivergence(){assertFalse(RestorePolicy.safePreferenceRollback(value(1),value(2),value(1)));}
    @Test public void rollbackRejectsMissingDiskWithNonemptyCache(){assertFalse(RestorePolicy.safePreferenceRollback(value(1),EMPTY,value(1)));}
    @Test public void ownershipAllowsEmptyAndPartialFile(){assertTrue(RestorePolicy.sameOwnedFile(1,2,1,2,0,10));assertTrue(RestorePolicy.sameOwnedFile(1,2,1,2,5,10));}
    @Test public void ownershipAllowsCompleteFile(){assertTrue(RestorePolicy.sameOwnedFile(1,2,1,2,10,10));}
    @Test public void ownershipRejectsReplacedInode(){assertFalse(RestorePolicy.sameOwnedFile(1,2,1,3,5,10));}
    @Test public void ownershipRejectsDifferentDevice(){assertFalse(RestorePolicy.sameOwnedFile(1,2,3,2,5,10));}
    @Test public void ownershipRejectsOversizeOrInvalidIdentity(){assertFalse(RestorePolicy.sameOwnedFile(1,2,1,2,11,10));assertFalse(RestorePolicy.sameOwnedFile(1,0,1,0,0,10));}
    @Test public void prefixAllowsOnlyVerifiedBytes(){assertTrue(RestorePolicy.matchingPrefixChunk(new byte[]{1,2,9},new byte[]{1,2,3},2));assertFalse(RestorePolicy.matchingPrefixChunk(new byte[]{1,9},new byte[]{1,2},2));}
    @Test public void prefixRejectsInvalidLength(){assertFalse(RestorePolicy.matchingPrefixChunk(new byte[]{1},new byte[]{1},2));assertFalse(RestorePolicy.matchingPrefixChunk(new byte[]{1},new byte[]{1},-1));}
    @Test public void emptyPrefixIsValid(){assertTrue(RestorePolicy.matchingPrefixChunk(new byte[0],new byte[]{1},0));}
    @Test public void copyPauseAcceptsPreviewBounds(){assertTrue(RestorePolicy.validPreviewCopyPause(true,"1",4294967296L));assertTrue(RestorePolicy.validPreviewCopyPause(true,"4294967296",4294967296L));}
    @Test public void copyPauseRejectsProduction(){assertFalse(RestorePolicy.validPreviewCopyPause(false,"4096",4294967296L));}
    @Test public void copyPauseRejectsInvalidAndOversize(){for(String value:new String[]{null,"0","-1","01","4294967297","99999999999","1.0"," 1"})assertFalse(RestorePolicy.validPreviewCopyPause(true,value,4294967296L));}
}
