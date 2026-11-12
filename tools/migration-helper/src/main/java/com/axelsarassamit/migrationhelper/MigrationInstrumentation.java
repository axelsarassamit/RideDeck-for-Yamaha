package com.axelsarassamit.migrationhelper;

import android.app.*;
import android.content.*;
import android.content.pm.*;
import android.os.*;
import android.system.Os;
import android.util.AtomicFile;
import org.json.*;
import org.xmlpull.v1.XmlPullParser;
import java.io.*;
import java.lang.reflect.*;
import java.nio.*;
import java.nio.charset.*;
import java.nio.channels.*;
import java.nio.file.*;
import java.security.*;
import java.util.*;
import java.util.zip.*;

/** No launcher, production dependency, network, activity, service, camera or messaging action. */
public final class MigrationInstrumentation extends Instrumentation {
    static final long GIB = 1024L * 1024 * 1024;
    static final long MAX_FILE = 4 * GIB, MAX_TOTAL = 8 * GIB, MAX_ZIP = MAX_TOTAL + 16 * 1024 * 1024;
    static final int MAX_ENTRIES = 20000, MAX_MANIFEST = 4 * 1024 * 1024, MAX_PREF = 4 * 1024 * 1024;
    static final String YAMAHA = "com.axelsarassamit.gx12.yamaha", DASH = "com.axelsarassamit.ridedeck.dash";
    private Bundle arguments;
    private boolean startupSuppressed;
    private Context target;
    private File root, work, stage, journal;
    private String archiveHash;
    private Archive archive;
    private Plan plan;
    private int mutated;
    private boolean copyPauseConsumed;
    private boolean cleanupPauseConsumed;
    String argument(String key){return arguments.getString(key);}

    static final class Failure extends Exception {
        final String code;
        Failure(String code) { super(code); this.code = code; }
    }
    static void require(boolean value, String code) throws Failure { if (!value) throw new Failure(code); }
    static String sha(File file) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");
        try(InputStream in=new FileInputStream(file)){byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1)digest.update(b,0,n);}
        return hex(digest.digest());
    }
    static String hex(byte[] bytes){StringBuilder b=new StringBuilder();for(byte v:bytes)b.append(String.format(Locale.ROOT,"%02x",v&255));return b.toString();}
    static String utf8(byte[] bytes) throws Exception {
        return StandardCharsets.UTF_8.newDecoder().onMalformedInput(CodingErrorAction.REPORT)
            .onUnmappableCharacter(CodingErrorAction.REPORT).decode(ByteBuffer.wrap(bytes)).toString();
    }
    static byte[] readBounded(InputStream in,long limit) throws Exception {
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] buffer=new byte[8192];int n;
        while((n=in.read(buffer))!=-1){require((long)out.size()+n<=limit,"INPUT_LIMIT");out.write(buffer,0,n);}return out.toByteArray();
    }
    static Set<String> keys(JSONObject object){Set<String> result=new HashSet<>();Iterator<String> it=object.keys();while(it.hasNext())result.add(it.next());return result;}
    static Set<String> set(String... values){return new HashSet<>(Arrays.asList(values));}
    static JSONObject json(byte[] bytes) throws Exception {
        try(android.util.JsonReader reader=new android.util.JsonReader(new StringReader(utf8(bytes)))){
            reader.setLenient(false);Object value=jsonValue(reader,0);
            require(value instanceof JSONObject&&reader.peek()==android.util.JsonToken.END_DOCUMENT,"JSON_SCHEMA");return (JSONObject)value;
        }
    }
    private static Object jsonValue(android.util.JsonReader r,int depth) throws Exception {
        require(depth<=8,"JSON_LIMIT");
        switch(r.peek()){
            case BEGIN_OBJECT:{JSONObject o=new JSONObject();Set<String> seen=new HashSet<>();r.beginObject();while(r.hasNext()){String name=r.nextName();require(seen.add(name)&&seen.size()<=MAX_ENTRIES*4,"JSON_DUPLICATE_OR_LIMIT");o.put(name,jsonValue(r,depth+1));}r.endObject();return o;}
            case BEGIN_ARRAY:{JSONArray a=new JSONArray();r.beginArray();while(r.hasNext()){require(a.length()<MAX_ENTRIES*4,"JSON_LIMIT");a.put(jsonValue(r,depth+1));}r.endArray();return a;}
            case STRING:return r.nextString();
            case BOOLEAN:return r.nextBoolean();
            case NUMBER:{String n=r.nextString();require(n.matches("-?(0|[1-9][0-9]{0,18})"),"JSON_NUMBER");long v=Long.parseLong(n);if(v>=Integer.MIN_VALUE&&v<=Integer.MAX_VALUE)return Integer.valueOf((int)v);return Long.valueOf(v);}
            case NULL:r.nextNull();return JSONObject.NULL;
            default:throw new Failure("JSON_SCHEMA");
        }
    }
    static boolean preview(String name){return (YAMAHA+".preview").equals(name)||(DASH+".debug").equals(name);}
    static String sourceFor(String name) throws Failure {
        if(YAMAHA.equals(name)||(YAMAHA+".preview").equals(name))return YAMAHA;
        if(DASH.equals(name)||(DASH+".debug").equals(name))return DASH;
        throw new Failure("TARGET_POLICY");
    }

    /** This hook runs before target providers are installed. Never instantiate the real Application. */
    @Override public Application newApplication(ClassLoader loader,String ignored,Context context)
            throws InstantiationException,IllegalAccessException,ClassNotFoundException {
        try {
            require(BuildConfig.TARGET_PACKAGE.equals(context.getPackageName()),"TARGET_POLICY");
            sourceFor(context.getPackageName());
            require(!BuildConfig.EXPECTED_TARGET_APK_SHA256.equals(String.join("",Collections.nCopies(64,"0"))),"APK_PIN_UNSET");
            ApplicationInfo helper=getContext().getPackageManager().getApplicationInfo(getContext().getPackageName(),PackageManager.GET_META_DATA);
            require(helper.metaData!=null && BuildConfig.EXPECTED_TARGET_APK_SHA256.equals(helper.metaData.getString(
                "com.axelsarassamit.migrationhelper.EXPECTED_TARGET_APK_SHA256")),"HELPER_MANIFEST_PIN");
            require(sha(new File(context.getApplicationInfo().sourceDir)).equals(BuildConfig.EXPECTED_TARGET_APK_SHA256),"APK_PIN");
            require(context.getApplicationInfo().splitSourceDirs==null||context.getApplicationInfo().splitSourceDirs.length==0,"SPLIT_APK");
            PackageInfo info=context.getPackageManager().getPackageInfo(context.getPackageName(),PackageManager.GET_PROVIDERS|PackageManager.GET_META_DATA);
            if(info.providers!=null)for(ProviderInfo p:info.providers){
                boolean profileOnly="androidx.startup.InitializationProvider".equals(p.name)
                    && !p.exported && p.metaData!=null && p.metaData.size()==1
                    && "androidx.startup".equals(p.metaData.getString("androidx.profileinstaller.ProfileInstallerInitializer"));
                require("androidx.core.content.FileProvider".equals(p.name)||profileOnly||
                    (sourceFor(context.getPackageName()).equals(DASH)&&"com.axelsarassamit.ridedeck.dash.DashBridgeProvider".equals(p.name)),"PROVIDER_POLICY");
            }
            startupSuppressed=true;
            return super.newApplication(loader,Application.class.getName(),context);
        } catch(Exception rejected){throw new ClassNotFoundException("Migration target build rejected: "+
            (rejected instanceof Failure?((Failure)rejected).code:rejected.getClass().getSimpleName()));}
    }
    @Override public void callApplicationOnCreate(Application app){ /* Intentionally suppress all target initialization. */ }
    @Override public void onCreate(Bundle args){arguments=args==null?new Bundle():new Bundle(args);start();}

    @Override public void onStart(){
        Bundle result=new Bundle();boolean rollback=false;
        try {
            target=getTargetContext();root=target.getDataDir().getCanonicalFile();
            require(startupSuppressed&&BuildConfig.TARGET_PACKAGE.equals(target.getPackageName()),"TARGET_POLICY");
            require(target.getPackageName().equals(arguments.getString("targetPackage")),"TARGET_ARGUMENT");
            sourceFor(target.getPackageName());
            checkQuiescent();
            String operation=arguments.getString("operation","");
            String pause=arguments.getString("simulateCopyPauseAfterBytes");
            // Reject production/non-restore pause requests before workspace or
            // archive staging writes, not merely immediately before applyPlan.
            if(pause!=null)require(operation.equals("restore")&&RestorePolicy.validPreviewCopyPause(preview(target.getPackageName()),pause,MAX_FILE),"FAULT_POLICY");
            String cleanupPause=arguments.getString("simulateCleanupPause");
            if(cleanupPause!=null)require(preview(target.getPackageName())&&cleanupPause.equals("YES")&&set("restore","recover","verify").contains(operation),"FAULT_POLICY");
            if(operation.equals("importProviderOverrides")||operation.equals("verifyProviderOverrides")||operation.equals("recoverProviderOverrides")){
                new ProviderOverrides(this).run(operation,result);finish(Activity.RESULT_OK,result);return;
            }
            require(set("validate","restore","verify","recover").contains(operation),"OPERATION");
            archiveHash=arguments.getString("archiveSha256","");require(archiveHash.matches("[0-9a-f]{64}"),"ARCHIVE_PIN");
            require(!Files.isSymbolicLink(target.getNoBackupFilesDir().toPath()),"TARGET_LINK");
            work=new File(target.getNoBackupFilesDir(),"ridedeck-migration-v1");
            require(!Files.isSymbolicLink(work.toPath()),"TARGET_LINK");
            require(work.isDirectory()||work.mkdir(),"WORKSPACE");
            journal=new File(work,"journal.json");
            try(RandomAccessFile lock=new RandomAccessFile(new File(work,"lock"),"rw");FileLock held=lock.getChannel().tryLock()){
                require(held!=null,"BUSY");
                require(!new File(work,"provider-journal.json").exists(),"PROVIDER_JOURNAL_PENDING");
                if(journal.exists()){
                    require(operation.equals("recover")||operation.equals("verify"),"JOURNAL_PENDING");
                    require("YES".equals(arguments.getString("acknowledgeRecovery"))||operation.equals("verify"),"RECOVERY_ACK");
                    File retained=new File(work,"archive.zip");require(retained.isFile()&&sha(retained).equals(archiveHash),"ARCHIVE_PIN");
                    archive=validateArchive(retained,false);plan=readPlan();
                    if(operation.equals("recover")){require(!plan.done,"ALREADY_COMPLETE");rollbackPlan();cleanupCompleted();result.putString("status","RECOVERED");}
                    else{verifyAll();if(plan.done)cleanupCompleted();result.putString("status","VERIFIED");}
                } else {
                    require(!operation.equals("recover"),"NO_JOURNAL");
                    deleteOwnedContents(work,"lock");
                    File zip=new File(work,"archive.zip");copyShellArchive(zip);
                    stage=new File(work,"stage");require(stage.mkdir(),"WORKSPACE");
                    archive=validateArchive(zip,true);
                    // Verification checks only archive-owned state. Provider import
                    // deliberately adds a separate encrypted preferences file.
                    if(!operation.equals("verify"))plan=preflight();
                    if(operation.equals("restore")){
                        require("YES".equals(arguments.getString("acknowledgeFreshTarget")),"RESTORE_ACK");
                        String fault=arguments.getString("simulateFailureAfter");
                        if(fault!=null)require(preview(target.getPackageName())&&fault.matches("[1-9][0-9]{0,3}"),"FAULT_POLICY");
                        String copyFault=arguments.getString("simulateCopyFailureAfterBytes");
                        if(copyFault!=null)require(preview(target.getPackageName())&&copyFault.matches("[1-9][0-9]{0,9}")&&Long.parseLong(copyFault)<=MAX_FILE,"FAULT_POLICY");
                        String createFault=arguments.getString("simulateFailureAfterCreate");
                        if(createFault!=null)require(preview(target.getPackageName())&&createFault.equals("YES"),"FAULT_POLICY");
                        writePlan(false);applyPlan();verifyAll();writePlan(true);result.putString("status","RESTORED");
                    } else if(operation.equals("verify")){verifyAll();result.putString("status","VERIFIED");}
                    else result.putString("status","VALIDATED");
                    if(operation.equals("restore"))cleanupCompleted();else deleteOwnedContents(work,"lock");
                }
            }
            result.putInt("filesVerified",archive==null?0:archive.files.size());
            result.putInt("preferencesVerified",archive==null?0:archive.prefs.size());
            result.putInt("preferenceValuesVerified",archive==null?0:archive.valueCount);
            result.putBoolean("startupSuppressed",true);finish(Activity.RESULT_OK,result);
        }catch(Throwable failure){
            if(plan!=null&&!plan.done&&journal!=null&&journal.isFile())try(
                RandomAccessFile lock=new RandomAccessFile(new File(work,"lock"),"rw");FileLock held=lock.getChannel().tryLock()){
                require(held!=null,"BUSY");rollbackPlan();rollback=true;cleanupCompleted();
            }catch(Throwable refused){ /* Keep journal and staged data for explicit recovery. */ }
            result.putString("status","FAILED");result.putString("failureCode",failure instanceof Failure?((Failure)failure).code:"IO_OR_PLATFORM_FAILURE");
            result.putString("failureType",failure.getClass().getSimpleName());
            if(failure instanceof android.system.ErrnoException)result.putInt("errno",((android.system.ErrnoException)failure).errno);
            result.putBoolean("rollbackComplete",rollback);result.putBoolean("startupSuppressed",startupSuppressed);finish(1,result);
        }
    }

    void checkQuiescent() throws Exception {
        ActivityManager manager=target.getSystemService(ActivityManager.class);
        for(ActivityManager.RunningServiceInfo s:manager.getRunningServices(1000))require(s.uid!=android.os.Process.myUid(),"ACTIVE_TARGET");
        List<ActivityManager.RunningAppProcessInfo> processes=manager.getRunningAppProcesses();
        if(processes!=null)for(ActivityManager.RunningAppProcessInfo p:processes)require(p.uid!=android.os.Process.myUid()||p.pid==android.os.Process.myPid(),"OTHER_TARGET_PROCESS");
    }
    byte[] fixedProviderInput() throws Exception {
        // Fixed literal only. Never concatenate an argument, key, filename or package into a shell command.
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("cat /data/local/tmp/ridedeck-provider-overrides.json");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd)){
            return readBounded(in,4096);
        }
    }
    private void copyShellArchive(File output) throws Exception {
        MessageDigest digest=MessageDigest.getInstance("SHA-256");long count=0;
        try(ParcelFileDescriptor fd=getUiAutomation().executeShellCommand("cat /data/local/tmp/ridedeck-migration.zip");InputStream in=new ParcelFileDescriptor.AutoCloseInputStream(fd);FileOutputStream out=new FileOutputStream(output)){
            byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){count+=n;require(count<=MAX_ZIP,"ARCHIVE_LIMIT");digest.update(b,0,n);out.write(b,0,n);}out.getFD().sync();
        }
        require(hex(digest.digest()).equals(archiveHash),"ARCHIVE_PIN");
    }

    static boolean safePath(String name) {
        if(name==null||name.length()>1024||name.startsWith("/")||name.indexOf('\\')>=0||name.indexOf(':')>=0)return false;
        for(String part:name.split("/",-1))if(part.isEmpty()||part.equals(".")||part.equals("..")||part.matches(".*[\\x00-\\x1f\\x7f].*"))return false;
        return true;
    }
    static boolean allowedPath(String name,String source) {
        if(!safePath(name))return false;
        if(source.equals(YAMAHA))return set("files/bike-diagnostics.txt","files/mbgl-offline.db","databases/yamaha-rides.db","shared_prefs/ride_config.xml","shared_prefs/quiet_mode.xml","shared_prefs/dash_companion.xml","shared_prefs/ride_appearance.xml").contains(name);
        return source.equals(DASH)&&(name.startsWith("files/recordings/")||set("shared_prefs/dash-settings.xml","shared_prefs/ride_appearance.xml").contains(name));
    }
    static final class Entry {
        String path,hash;long bytes;File staged;
    }
    static final class Archive {
        final List<Entry> files=new ArrayList<>();
        final Map<String,Map<String,Object>> prefs=new LinkedHashMap<>();int valueCount;
    }
    private Archive validateArchive(File zip,boolean extract) throws Exception {
        Set<String> names=ZipMetadata.validate(zip);
        require(names.contains("manifest.json"),"MANIFEST");
        try(ZipFile z=new ZipFile(zip)){
            JSONObject manifest=json(readBounded(z.getInputStream(z.getEntry("manifest.json")),MAX_MANIFEST));
            require(keys(manifest).equals(set("schema","sourcePackage","targetPackage","snapshotSha256","files")),"MANIFEST");
            require(manifest.get("schema") instanceof Integer&&manifest.getInt("schema")==1,"MANIFEST");
            String source=sourceFor(target.getPackageName());
            require(source.equals(manifest.getString("sourcePackage"))&&target.getPackageName().equals(manifest.getString("targetPackage")),"TARGET_POLICY");
            require(manifest.getString("snapshotSha256").matches("[0-9a-f]{64}"),"MANIFEST");
            JSONArray rows=manifest.getJSONArray("files");require(rows.length()<=MAX_ENTRIES,"ENTRY_LIMIT");
            Set<String> expected=new HashSet<>();expected.add("manifest.json");Archive out=new Archive();long total=0;
            for(int i=0;i<rows.length();i++){
                JSONObject row=rows.getJSONObject(i);require(keys(row).equals(set("path","bytes","sha256")),"MANIFEST");
                Entry e=new Entry();e.path=row.getString("path");e.hash=row.getString("sha256");Object size=row.get("bytes");
                require(size instanceof Integer||size instanceof Long,"MANIFEST");e.bytes=((Number)size).longValue();
                require(allowedPath(e.path,source)&&expected.add(e.path),"ENTRY_POLICY");
                require(e.bytes>=0&&e.bytes<=MAX_FILE&&e.hash.matches("[0-9a-f]{64}"),"ENTRY_LIMIT");
                total+=e.bytes;require(total<=MAX_TOTAL,"ARCHIVE_LIMIT");
                ZipEntry ze=z.getEntry(e.path);require(ze!=null&&ze.getSize()==e.bytes&&!ze.isDirectory(),"ENTRY_MISSING_OR_SIZE");
                MessageDigest hash=MessageDigest.getInstance("SHA-256");long actual=0;
                e.staged=new File(stage==null?new File(work,"stage"):stage,Integer.toString(i));
                ByteArrayOutputStream pref=e.path.startsWith("shared_prefs/")?new ByteArrayOutputStream():null;
                if(pref!=null)require(e.bytes<=MAX_PREF,"PREF_LIMIT");
                try(InputStream in=z.getInputStream(ze);FileOutputStream file=extract?new FileOutputStream(e.staged):null){
                    byte[] b=new byte[65536];int n;while((n=in.read(b))!=-1){actual+=n;require(actual<=e.bytes,"ENTRY_LIMIT");hash.update(b,0,n);if(file!=null)file.write(b,0,n);if(pref!=null)pref.write(b,0,n);}if(file!=null)file.getFD().sync();
                }
                require(actual==e.bytes&&hex(hash.digest()).equals(e.hash),"ENTRY_HASH");
                if(pref!=null){String prefName=e.path.substring("shared_prefs/".length(),e.path.length()-4);Map<String,Object> values=Preferences.parse(pref.toByteArray());out.prefs.put(prefName,values);out.valueCount+=values.size();}
                else out.files.add(e);
            }
            require(names.equals(expected),"UNEXPECTED_ENTRY");
            if(source.equals(YAMAHA))validateRideDatabase(z,out,extract);
            return out;
        }
    }

    private static void validateRideDatabase(ZipFile zip,Archive archive,boolean extracted) throws Exception {
        final String dbPath="databases/yamaha-rides.db";
        Entry database=null;
        for(Entry e:archive.files)if(e.path.equals(dbPath))database=e;
        if(database==null)return;
        byte[] header=new byte[16];
        try(DataInputStream in=new DataInputStream(zip.getInputStream(zip.getEntry(dbPath)))){in.readFully(header);}
        require(Arrays.equals(header,"SQLite format 3\u0000".getBytes(StandardCharsets.US_ASCII)),"DATABASE_FORMAT");
        if(extracted){
            // The staged numbered filename has no adjacent SQLite sidecar and is
            // opened read-only. No target database is opened or cached.
            try(android.database.sqlite.SQLiteDatabase db=android.database.sqlite.SQLiteDatabase.openDatabase(database.staged.getPath(),null,android.database.sqlite.SQLiteDatabase.OPEN_READONLY|android.database.sqlite.SQLiteDatabase.NO_LOCALIZED_COLLATORS);
                android.database.Cursor cursor=db.rawQuery("PRAGMA quick_check",null)){
                require(cursor.moveToFirst()&&"ok".equals(cursor.getString(0))&&!cursor.moveToNext(),"DATABASE_CHECK");
            }
        }
    }

    static final class Plan { final List<Entry> files=new ArrayList<>();final Map<String,Boolean> prefs=new LinkedHashMap<>();final List<String> dirs=new ArrayList<>();final Map<String,long[]> owned=new LinkedHashMap<>();boolean done; }
    File destination(String path) throws Exception {
        require(safePath(path),"TARGET_PATH");
        File dest=new File(root,path);File p=dest;
        while(p!=null&&!p.equals(root)){require(!Files.isSymbolicLink(p.toPath()),"TARGET_LINK");p=p.getParentFile();}
        require(p!=null&&dest.getCanonicalPath().startsWith(root.getPath()+File.separator),"TARGET_PATH");return dest;
    }
    private void scanFiles(File dir,Set<String> expected,int depth,int[] count) throws Exception {
        require(depth<=64&&++count[0]<=MAX_ENTRIES*4,"TARGET_LIMIT");require(!Files.isSymbolicLink(dir.toPath()),"TARGET_LINK");
        File[] entries=dir.listFiles();require(entries!=null,"TARGET_IO");
        for(File f:entries){require(!Files.isSymbolicLink(f.toPath()),"TARGET_LINK");if(f.isDirectory())scanFiles(f,expected,depth+1,count);else require(f.isFile()&&expected.contains(root.toPath().relativize(f.toPath()).toString().replace(File.separatorChar,'/')),"TARGET_NOT_FRESH");}
    }
    private Plan preflight() throws Exception {
        Plan out=new Plan();Set<String> expected=new HashSet<>();for(Entry e:archive.files)expected.add(e.path);
        // AndroidX may write this optimization marker; never restore, replace or delete it.
        expected.add("files/profileInstalled");
        File files=destination("files");if(files.exists())scanFiles(files,expected,0,new int[1]);
        // Restore only the reviewed cold main database before startup.
        // Never mix it with stale or unknown target SQLite sidecars.
        File databases=destination("databases");if(databases.exists())scanFiles(databases,expected,0,new int[1]);
        File prefs=destination("shared_prefs");if(prefs.exists()){
            require(prefs.isDirectory(),"TARGET_NOT_FRESH");File[] entries=prefs.listFiles();require(entries!=null,"TARGET_IO");
            for(File f:entries)require(f.isFile()&&!Files.isSymbolicLink(f.toPath())&&f.getName().endsWith(".xml")&&archive.prefs.containsKey(f.getName().substring(0,f.getName().length()-4)),"TARGET_NOT_FRESH");
        }
        LinkedHashSet<String> dirs=new LinkedHashSet<>();
        for(Entry e:archive.files){File f=destination(e.path);if(f.exists())require(f.isFile()&&f.length()==e.bytes&&sha(f).equals(e.hash),"TARGET_CONFLICT");else{out.files.add(e);File parent=f.getParentFile();while(!parent.equals(root)){if(!parent.exists())dirs.add(root.toPath().relativize(parent.toPath()).toString().replace(File.separatorChar,'/'));parent=parent.getParentFile();}}}
        for(Map.Entry<String,Map<String,Object>> p:archive.prefs.entrySet()){
            Map<String,?> before=target.getSharedPreferences(p.getKey(),0).getAll();File file=destination("shared_prefs/"+p.getKey()+".xml");
            if(file.exists())require(Preferences.read(file).equals(before),"PREF_CACHE_MISMATCH");
            if(RestorePolicy.needsPreferenceWrite(file.exists(),before,p.getValue())){require(before.isEmpty(),"TARGET_CONFLICT");out.prefs.put(p.getKey(),file.exists());}
        }
        out.dirs.addAll(dirs);out.dirs.sort(Comparator.comparingInt(String::length));return out;
    }
    private void writePlan(boolean done) throws Exception {
        JSONObject j=new JSONObject().put("schema",1).put("target",target.getPackageName()).put("archive",archiveHash).put("done",done);
        JSONArray files=new JSONArray();for(Entry e:plan.files)files.put(e.path);j.put("files",files);j.put("prefs",new JSONObject(plan.prefs));j.put("dirs",new JSONArray(plan.dirs));
        JSONObject owned=new JSONObject();for(Map.Entry<String,long[]> e:plan.owned.entrySet())owned.put(e.getKey(),new JSONObject().put("dev",e.getValue()[0]).put("ino",e.getValue()[1]));j.put("owned",owned);
        byte[] bytes=j.toString().getBytes(StandardCharsets.UTF_8);require(bytes.length<=MAX_MANIFEST,"JOURNAL_LIMIT");
        AtomicFile file=new AtomicFile(journal);FileOutputStream out=file.startWrite();try{out.write(bytes);file.finishWrite(out);plan.done=done;}catch(Exception e){file.failWrite(out);throw e;}
    }
    private Plan readPlan() throws Exception {
        JSONObject j=json(readFile(journal,MAX_MANIFEST));require(j.getInt("schema")==1&&target.getPackageName().equals(j.getString("target"))&&archiveHash.equals(j.getString("archive")),"JOURNAL_POLICY");
        require(keys(j).equals(set("schema","target","archive","done","files","prefs","dirs","owned"))&&j.get("schema") instanceof Integer&&j.get("done") instanceof Boolean,"JOURNAL_POLICY");
        Plan out=new Plan();out.done=j.getBoolean("done");Map<String,Entry> known=new HashMap<>();for(Entry e:archive.files)known.put(e.path,e);
        JSONArray files=j.getJSONArray("files");Set<String> seen=new HashSet<>();for(int i=0;i<files.length();i++){String name=files.getString(i);require(known.containsKey(name)&&seen.add(name),"JOURNAL_POLICY");out.files.add(known.get(name));}
        JSONObject prefs=j.getJSONObject("prefs");for(String name:keys(prefs)){require(archive.prefs.containsKey(name)&&prefs.get(name) instanceof Boolean,"JOURNAL_POLICY");out.prefs.put(name,prefs.getBoolean(name));}
        JSONObject owned=j.getJSONObject("owned");for(String name:keys(owned)){
            require(seen.contains(name)&&owned.get(name) instanceof JSONObject,"JOURNAL_POLICY");JSONObject identity=owned.getJSONObject(name);
            require(keys(identity).equals(set("dev","ino")),"JOURNAL_POLICY");Object dev=identity.get("dev"),ino=identity.get("ino");
            require((dev instanceof Integer||dev instanceof Long)&&(ino instanceof Integer||ino instanceof Long)&&((Number)dev).longValue()>=0&&((Number)ino).longValue()>0,"JOURNAL_POLICY");
            out.owned.put(name,new long[]{((Number)dev).longValue(),((Number)ino).longValue()});destination(name);
        }
        JSONArray dirs=j.getJSONArray("dirs");Set<String> uniqueDirs=new HashSet<>();for(int i=0;i<dirs.length();i++){String name=dirs.getString(i);boolean parent=false;for(Entry e:out.files)if(e.path.startsWith(name+"/")){parent=true;break;}require((name.equals("files")||name.startsWith("files/")||name.equals("databases"))&&parent&&uniqueDirs.add(name),"JOURNAL_POLICY");destination(name);out.dirs.add(name);}return out;
    }
    static byte[] readFile(File file,long limit) throws Exception {require(file.isFile()&&file.length()<=limit,"INPUT_LIMIT");try(InputStream in=new FileInputStream(file)){return readBounded(in,limit);}}
    private void fault() throws Exception {String value=arguments.getString("simulateFailureAfter");if(value!=null&&++mutated==Integer.parseInt(value))throw new Failure("INJECTED_FAILURE");}
    private void applyPlan() throws Exception {
        for(String dir:plan.dirs){File f=destination(dir);require(f.isDirectory()||f.mkdir(),"TARGET_IO");}
        for(Entry e:plan.files){copyExclusive(e);fault();}
        for(String name:plan.prefs.keySet()){Preferences.replace(target.getSharedPreferences(name,0),archive.prefs.get(name));fault();}
    }
    private void copyExclusive(Entry e) throws Exception {
        File f=destination(e.path);
        java.io.FileDescriptor fd=Os.open(f.getPath(),android.system.OsConstants.O_CREAT|android.system.OsConstants.O_EXCL|android.system.OsConstants.O_WRONLY|android.system.OsConstants.O_NOFOLLOW,0600);
        try(FileOutputStream out=new FileOutputStream(fd)){
            if("YES".equals(arguments.getString("simulateFailureAfterCreate")))throw new Failure("INJECTED_AFTER_CREATE");
            android.system.StructStat identity=Os.fstat(fd);long[] record=new long[]{identity.st_dev,identity.st_ino};
            plan.owned.put(e.path,record);
            try{writePlan(false);}catch(Exception failure){plan.owned.remove(e.path);throw failure;}
            // Ownership is durable before the first byte. An unrecorded creation
            // crash is intentionally retained for manual inspection, never deleted.
            long written=0;String fault=arguments.getString("simulateCopyFailureAfterBytes");long failAt=fault==null?Long.MAX_VALUE:Long.parseLong(fault);
            String pause=arguments.getString("simulateCopyPauseAfterBytes");long pauseAt=pause==null||copyPauseConsumed?Long.MAX_VALUE:Long.parseLong(pause);
            try(InputStream in=new FileInputStream(e.staged)){
                byte[] b=new byte[65536];int n;
                while((n=in.read(b,0,(int)Math.min(b.length,Math.min(failAt,pauseAt)-written)))!=-1){
                    out.write(b,0,n);written+=n;
                    if(written>=failAt){out.getFD().sync();throw new Failure("INJECTED_PARTIAL_COPY");}
                    if(written>=pauseAt){out.getFD().sync();copyPauseConsumed=true;pauseAt=Long.MAX_VALUE;android.os.SystemClock.sleep(15000);}
                }
            }
            out.getFD().sync();require(written==e.bytes&&sha(f).equals(e.hash),"COPY_VERIFY");
        }
    }
    private boolean ownedFileValid(Entry e,File file)throws Exception{
        long[] owned=plan.owned.get(e.path);if(owned==null)return false;
        android.system.StructStat actual=Os.lstat(file.getPath());
        if(!RestorePolicy.sameOwnedFile(owned[0],owned[1],actual.st_dev,actual.st_ino,actual.st_size,e.bytes)||!file.isFile())return false;
        // Revalidate the retained source against the pinned archive metadata before
        // trusting it as a rollback prefix reference, including after process death.
        if(!e.staged.isFile()||e.staged.length()!=e.bytes||!sha(e.staged).equals(e.hash))return false;
        try(InputStream current=new FileInputStream(file);InputStream source=new FileInputStream(e.staged)){
            byte[] a=new byte[65536],b=new byte[65536];long remaining=actual.st_size;
            while(remaining>0){int n=(int)Math.min(a.length,remaining);new DataInputStream(current).readFully(a,0,n);new DataInputStream(source).readFully(b,0,n);if(!RestorePolicy.matchingPrefixChunk(a,b,n))return false;remaining-=n;}
            return current.read()==-1;
        }
    }
    private void verifyAll() throws Exception {
        for(Entry e:archive.files){File f=destination(e.path);require(f.isFile()&&f.length()==e.bytes&&sha(f).equals(e.hash),"VERIFY_FILES");}
        for(Map.Entry<String,Map<String,Object>> p:archive.prefs.entrySet()){
            require(target.getSharedPreferences(p.getKey(),0).getAll().equals(p.getValue()),"VERIFY_PREFS");
            File file=destination("shared_prefs/"+p.getKey()+".xml");
            // Android may elide an empty preferences file after a no-op commit.
            require((file.exists()?Preferences.read(file):Collections.emptyMap()).equals(p.getValue()),"VERIFY_PREFS_DISK");
        }
    }
    private void rollbackPlan() throws Exception {
        require(plan!=null&&!plan.done,"ROLLBACK_POLICY");
        // Validate every affected destination before deleting anything: preserve later user edits.
        for(Entry e:plan.files){File f=destination(e.path);if(f.exists())require(ownedFileValid(e,f),"ROLLBACK_CONFLICT");}
        for(String name:plan.prefs.keySet()){
            Map<String,?> current=target.getSharedPreferences(name,0).getAll();
            File file=destination("shared_prefs/"+name+".xml");
            Map<String,?> disk=file.exists()?Preferences.read(file):Collections.emptyMap();
            require(RestorePolicy.safePreferenceRollback(current,disk,archive.prefs.get(name)),"ROLLBACK_CONFLICT");
        }
        for(String name:plan.prefs.keySet()){
            if(plan.prefs.get(name))Preferences.replace(target.getSharedPreferences(name,0),Collections.emptyMap());
            else require(target.deleteSharedPreferences(name)||!destination("shared_prefs/"+name+".xml").exists(),"ROLLBACK_PREFS");
            require(target.getSharedPreferences(name,0).getAll().isEmpty(),"ROLLBACK_PREFS");
        }
        for(Entry e:plan.files){File f=destination(e.path);if(f.exists()){require(ownedFileValid(e,f),"ROLLBACK_CONFLICT");require(f.delete(),"ROLLBACK_FILES");}}
        List<String> dirs=new ArrayList<>(plan.dirs);Collections.reverse(dirs);for(String name:dirs){File f=destination(name);if(f.exists()){String[] contents=f.list();require(contents!=null,"ROLLBACK_DIRS");if(contents.length==0)require(f.delete(),"ROLLBACK_DIRS");}}
    }
    /** Call only after verified completion or fully completed rollback. */
    private void cleanupCompleted() throws Exception {
        // Retire the dependency before deleting the archive/stage it references.
        // A kill after retirement leaves only disposable staging, not a sticky
        // journal that would require an archive already removed by cleanup.
        require(journal.isFile()&&!Files.isSymbolicLink(journal.toPath())&&journal.delete(),"JOURNAL_RETIRE");
        if(!cleanupPauseConsumed&&"YES".equals(arguments.getString("simulateCleanupPause"))){cleanupPauseConsumed=true;android.os.SystemClock.sleep(15000);}
        deleteOwnedContents(work,"lock");
    }
    static void deleteOwnedContents(File dir,String keep) throws Exception {
        require(dir!=null&&!Files.isSymbolicLink(dir.toPath()),"CLEANUP_POLICY");File[] entries=dir.listFiles();require(entries!=null,"CLEANUP_IO");
        for(File f:entries){if(f.getName().equals(keep))continue;require(!Files.isSymbolicLink(f.toPath()),"CLEANUP_POLICY");if(f.isDirectory())deleteOwnedContents(f,"");require(f.delete(),"CLEANUP_IO");}
    }

    static final class Preferences {
        static Map<String,Object> read(File file) throws Exception {return parse(readFile(file,MAX_PREF));}
        static Map<String,Object> parse(byte[] bytes) throws Exception {
            XmlPullParser p=android.util.Xml.newPullParser();p.setFeature(XmlPullParser.FEATURE_PROCESS_NAMESPACES,true);p.setFeature(XmlPullParser.FEATURE_PROCESS_DOCDECL,false);p.setInput(new StringReader(utf8(bytes)));
            Map<String,Object> result=new LinkedHashMap<>();boolean root=false;int token;
            while((token=p.nextToken())!=XmlPullParser.END_DOCUMENT){
                require(token!=XmlPullParser.DOCDECL,"PREF_XML");
                if(token==XmlPullParser.START_TAG){
                    if(p.getDepth()==1){require(!root&&p.getName().equals("map")&&p.getAttributeCount()==0,"PREF_XML");root=true;continue;}
                    require(root&&p.getDepth()==2&&p.getNamespace().isEmpty(),"PREF_XML");String kind=p.getName(),name=p.getAttributeValue(null,"name");
                    require(name!=null&&!name.isEmpty()&&name.length()<=4096&&!result.containsKey(name)&&result.size()<10000,"PREF_XML");Object value;
                    if(kind.equals("string")){require(p.getAttributeCount()==1,"PREF_XML");value=text(p);}
                    else if(kind.equals("set")){
                        require(p.getAttributeCount()==1,"PREF_XML");Set<String> values=new HashSet<>();
                        while(true){int t=p.nextToken();require(t!=XmlPullParser.DOCDECL,"PREF_XML");if(t==XmlPullParser.END_TAG&&p.getDepth()==2)break;if(t==XmlPullParser.TEXT){require(p.isWhitespace(),"PREF_XML");continue;}if(t==XmlPullParser.COMMENT)continue;
                            require(t==XmlPullParser.START_TAG&&p.getDepth()==3&&p.getName().equals("string")&&p.getAttributeCount()==0&&values.size()<10000,"PREF_XML");require(values.add(text(p)),"PREF_XML");}
                        value=values;
                    }else{
                        require(p.getAttributeCount()==2&&p.getAttributeValue(null,"value")!=null,"PREF_XML");String raw=p.getAttributeValue(null,"value");
                        switch(kind){case "int":value=Integer.valueOf(raw);break;case "long":value=Long.valueOf(raw);break;case "float":Float f=Float.valueOf(raw);require(Float.isFinite(f),"PREF_XML");value=f;break;case "boolean":require(raw.equals("true")||raw.equals("false"),"PREF_XML");value=Boolean.valueOf(raw);break;default:throw new Failure("PREF_XML");}
                        require(text(p).trim().isEmpty(),"PREF_XML");
                    }
                    result.put(name,value);
                }else if(token==XmlPullParser.TEXT)require(p.isWhitespace(),"PREF_XML");
                else require(token!=XmlPullParser.ENTITY_REF,"PREF_XML");
            }
            require(root,"PREF_XML");return result;
        }
        private static String text(XmlPullParser p) throws Exception {
            int depth=p.getDepth();StringBuilder b=new StringBuilder();
            while(true){int t=p.nextToken();if(t==XmlPullParser.END_TAG&&p.getDepth()==depth)return b.toString();
                require(t==XmlPullParser.TEXT||t==XmlPullParser.CDSECT||t==XmlPullParser.ENTITY_REF||t==XmlPullParser.COMMENT,"PREF_XML");
                if(t!=XmlPullParser.COMMENT){require(p.getText()!=null&&(long)b.length()+p.getText().length()<=MAX_PREF,"PREF_LIMIT");b.append(p.getText());}}
        }
        @SuppressWarnings("unchecked") static void replace(SharedPreferences prefs,Map<String,?> values) throws Exception {
            SharedPreferences.Editor e=prefs.edit().clear();for(Map.Entry<String,?> entry:values.entrySet()){
                String k=entry.getKey();Object v=entry.getValue();if(v instanceof String)e.putString(k,(String)v);else if(v instanceof Integer)e.putInt(k,(Integer)v);else if(v instanceof Long)e.putLong(k,(Long)v);else if(v instanceof Float)e.putFloat(k,(Float)v);else if(v instanceof Boolean)e.putBoolean(k,(Boolean)v);else if(v instanceof Set)e.putStringSet(k,new HashSet<>((Set<String>)v));else throw new Failure("PREF_TYPE");}
            require(e.commit(),"PREF_COMMIT");require(prefs.getAll().equals(values),"PREF_COMMIT_VERIFY");
        }
    }

    /** ZIP central-directory inspection is needed because ZipEntry hides Unix symlink attributes. */
    static final class ZipMetadata {
        static int u16(byte[] b,int p){return(b[p]&255)|((b[p+1]&255)<<8);}
        static long u32(byte[] b,int p){return Integer.toUnsignedLong(ByteBuffer.wrap(b,p,4).order(ByteOrder.LITTLE_ENDIAN).getInt());}
        static long u64(byte[] b,int p) throws Exception {long n=ByteBuffer.wrap(b,p,8).order(ByteOrder.LITTLE_ENDIAN).getLong();require(n>=0,"ZIP_METADATA");return n;}
        static byte[] at(RandomAccessFile f,long p,int n) throws Exception {require(p>=0&&p+n<=f.length(),"ZIP_METADATA");byte[] b=new byte[n];f.seek(p);f.readFully(b);return b;}
        static Set<String> validate(File file) throws Exception {
            require(file.length()>=22&&file.length()<=MAX_ZIP,"ARCHIVE_LIMIT");
            try(RandomAccessFile f=new RandomAccessFile(file,"r")){
                int size=(int)Math.min(f.length(),65557);byte[] tail=at(f,f.length()-size,size);int eocd=-1;
                for(int i=size-22;i>=0;i--)if(u32(tail,i)==0x06054b50L&&i+22+u16(tail,i+20)==size){eocd=i;break;}
                require(eocd>=0,"ZIP_METADATA");long end=f.length()-size+eocd;
                require(u16(tail,eocd+4)==0&&u16(tail,eocd+6)==0&&u16(tail,eocd+8)==u16(tail,eocd+10),"ZIP_METADATA");
                long count=u16(tail,eocd+10),centralSize=u32(tail,eocd+12),offset=u32(tail,eocd+16),centralEnd=end;
                if(end>=20&&u32(at(f,end-20,20),0)==0x07064b50L){
                    byte[] locator=at(f,end-20,20);require(u32(locator,4)==0&&u32(locator,16)==1,"ZIP_METADATA");long zpos=u64(locator,8);byte[] z=at(f,zpos,56);
                    require(u32(z,0)==0x06064b50L&&u64(z,4)>=44&&zpos+12+u64(z,4)==end-20&&u32(z,16)==0&&u32(z,20)==0&&u64(z,24)==u64(z,32),"ZIP_METADATA");
                    count=u64(z,32);centralSize=u64(z,40);offset=u64(z,48);centralEnd=zpos;
                }
                require(count<=MAX_ENTRIES+1&&offset>=0&&centralSize>=0&&offset+centralSize==centralEnd,"ZIP_METADATA");
                Set<String> names=new HashSet<>();long cursor=offset;
                for(long i=0;i<count;i++){
                    byte[] h=at(f,cursor,46);require(u32(h,0)==0x02014b50L,"ZIP_METADATA");int flags=u16(h,8),method=u16(h,10),nl=u16(h,28),xl=u16(h,30),cl=u16(h,32);
                    require((flags&1)==0&&(method==0||method==8)&&u16(h,34)==0&&nl>0&&nl<=4096,"ZIP_METADATA");
                    byte[] nameBytes=at(f,cursor+46,nl);if((flags&2048)==0)for(byte b:nameBytes)require(b>=0,"ZIP_ENCODING");String name=utf8(nameBytes);
                    require(!name.endsWith("/")&&names.add(name),"ZIP_DUPLICATE_OR_DIRECTORY");int mode=(int)(u32(h,38)>>>16)&0170000;require(mode==0||mode==0100000,"ZIP_NON_REGULAR");
                    long compressed=u32(h,20),uncompressed=u32(h,24),local=u32(h,42);byte[] extra=at(f,cursor+46+nl,xl);
                    if(compressed==0xffffffffL||uncompressed==0xffffffffL||local==0xffffffffL){boolean found=false;for(int p=0;p+4<=extra.length;){int id=u16(extra,p),len=u16(extra,p+2);require(p+4+len<=extra.length,"ZIP_METADATA");if(id==1){int v=p+4;if(uncompressed==0xffffffffL){require(v+8<=p+4+len,"ZIP_METADATA");uncompressed=u64(extra,v);v+=8;}if(compressed==0xffffffffL){require(v+8<=p+4+len,"ZIP_METADATA");compressed=u64(extra,v);v+=8;}if(local==0xffffffffL){require(v+8<=p+4+len,"ZIP_METADATA");local=u64(extra,v);}found=true;break;}p+=4+len;}require(found,"ZIP_METADATA");}
                    require(uncompressed<=MAX_FILE&&compressed<=MAX_ZIP,"ENTRY_LIMIT");byte[] l=at(f,local,30);require(u32(l,0)==0x04034b50L&&u16(l,6)==flags&&u16(l,8)==method&&u16(l,26)==nl,"ZIP_METADATA");
                    require(Arrays.equals(nameBytes,at(f,local+30,nl))&&local+30+nl+u16(l,28)+compressed<=offset,"ZIP_METADATA");
                    cursor+=46L+nl+xl+cl;require(cursor<=offset+centralSize,"ZIP_METADATA");
                }
                require(cursor==offset+centralSize,"ZIP_METADATA");return names;
            }
        }
    }
}
