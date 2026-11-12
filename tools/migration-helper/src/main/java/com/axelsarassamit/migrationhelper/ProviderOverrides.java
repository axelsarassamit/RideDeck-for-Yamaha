package com.axelsarassamit.migrationhelper;

import android.content.*;
import android.os.Bundle;
import android.util.*;
import android.util.Base64;
import java.io.*;
import java.nio.channels.FileLock;
import java.nio.file.Files;
import java.security.*;
import java.util.*;
import javax.crypto.*;
import javax.crypto.spec.GCMParameterSpec;
import org.json.*;
import static com.axelsarassamit.migrationhelper.MigrationInstrumentation.*;

/** Separate opt-in import; plaintext input and values never enter journal or output. */
final class ProviderOverrides {
    private final MigrationInstrumentation runner;
    private static final String ALIAS="navigation_provider_keys";
    ProviderOverrides(MigrationInstrumentation runner){this.runner=runner;}
    private String decrypt(String encoded,KeyStore store)throws Exception{
        require(encoded!=null,"PROVIDER_CIPHERTEXT");String[] parts=encoded.split(":",-1);
        require(parts.length==2&&store.containsAlias(ALIAS),"PROVIDER_CIPHERTEXT");
        Cipher cipher=Cipher.getInstance("AES/GCM/NoPadding");
        cipher.init(Cipher.DECRYPT_MODE,store.getKey(ALIAS,null),new GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)));
        return utf8(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)));
    }
    void run(String operation,Bundle result)throws Exception{
        Context target=runner.getTargetContext();
        require(sourceFor(target.getPackageName()).equals(YAMAHA),"PROVIDER_TARGET");
        String pin=runner.argument("providerSha256");require(pin!=null&&pin.matches("[0-9a-f]{64}"),"PROVIDER_PIN");
        byte[] input=runner.fixedProviderInput();
        require(hex(MessageDigest.getInstance("SHA-256").digest(input)).equals(pin),"PROVIDER_PIN");
        JSONObject values=json(input);require(keys(values).equals(set("maptiler","tomtom")),"PROVIDER_SCHEMA");
        for(String name:keys(values))require(values.get(name) instanceof String&&values.getString(name).matches("[A-Za-z0-9._~-]{8,256}"),"PROVIDER_SCHEMA");
        File base=target.getNoBackupFilesDir();require(!Files.isSymbolicLink(base.toPath()),"TARGET_LINK");
        File work=new File(base,"ridedeck-migration-v1");require(!Files.isSymbolicLink(work.toPath()),"TARGET_LINK");
        require(work.isDirectory()||work.mkdir(),"WORKSPACE");
        File state=new File(work,"provider-journal.json");
        try(RandomAccessFile file=new RandomAccessFile(new File(work,"lock"),"rw");FileLock lock=file.getChannel().tryLock()){
            require(lock!=null,"BUSY");require(!new File(work,"journal.json").exists(),"JOURNAL_PENDING");
            File prefFile=new File(target.getApplicationInfo().dataDir,"shared_prefs/navigation_keys.xml");
            require(!Files.isSymbolicLink(prefFile.toPath())&&!Files.isSymbolicLink(prefFile.getParentFile().toPath()),"TARGET_LINK");
            SharedPreferences prefs=target.getSharedPreferences("navigation_keys",0);
            if(prefFile.exists())require(Preferences.read(prefFile).equals(prefs.getAll()),"PREF_CACHE_MISMATCH");
            require(keysOf(prefs.getAll()).stream().allMatch(keys(values)::contains),"PROVIDER_CONFLICT");
            KeyStore store=KeyStore.getInstance("AndroidKeyStore");store.load(null);
            if(operation.equals("recoverProviderOverrides")){
                require("YES".equals(runner.argument("acknowledgeRecovery")),"RECOVERY_ACK");
                require(state.isFile(),"NO_JOURNAL");
                JSONObject journal=json(readFile(state,4096));
                require(keys(journal).equals(set("schema","pin","target","aliasExisted","prefsExisted","names"))
                    &&journal.get("schema") instanceof Integer&&journal.getInt("schema")==1
                    &&journal.get("pin") instanceof String&&pin.equals(journal.getString("pin"))
                    &&journal.get("target") instanceof String&&target.getPackageName().equals(journal.getString("target"))
                    &&journal.get("aliasExisted") instanceof Boolean&&journal.get("prefsExisted") instanceof Boolean
                    &&journal.get("names") instanceof JSONArray,"JOURNAL_POLICY");
                boolean aliasExisted=journal.getBoolean("aliasExisted"),prefsExisted=journal.getBoolean("prefsExisted");
                JSONArray names=journal.getJSONArray("names");Set<String> added=new HashSet<>();
                require(names.length()>=1&&names.length()<=2,"JOURNAL_POLICY");
                for(int i=0;i<names.length();i++){require(names.get(i) instanceof String,"JOURNAL_POLICY");String name=names.getString(i);require(keys(values).contains(name)&&added.add(name),"JOURNAL_POLICY");if(prefs.contains(name))require(decrypt(prefs.getString(name,null),store).equals(values.getString(name)),"ROLLBACK_CONFLICT");}
                // Validate all conditions before removing a single preference.
                // A previously absent file cannot contain unrelated later entries.
                require(prefsExisted||added.containsAll(prefs.getAll().keySet()),"ROLLBACK_CONFLICT");
                // An alias created by this import must not serve preserved keys.
                require(aliasExisted||added.containsAll(prefs.getAll().keySet()),"ROLLBACK_CONFLICT");
                SharedPreferences.Editor edit=prefs.edit();for(String name:added)edit.remove(name);require(edit.commit(),"PROVIDER_COMMIT");
                if(!prefsExisted){require(prefs.getAll().isEmpty(),"ROLLBACK_CONFLICT");require(target.deleteSharedPreferences("navigation_keys")||!prefFile.exists(),"PROVIDER_DELETE");}
                if(!aliasExisted&&prefs.getAll().isEmpty()&&store.containsAlias(ALIAS))store.deleteEntry(ALIAS);
                require(state.delete(),"JOURNAL_IO");result.putString("status","RECOVERED");return;
            }
            require(!state.exists(),"JOURNAL_PENDING");
            Set<String> added=new LinkedHashSet<>();
            for(String name:keys(values)){if(prefs.contains(name))require(decrypt(prefs.getString(name,null),store).equals(values.getString(name)),"PROVIDER_CONFLICT");else added.add(name);}
            Class<?> secrets=Class.forName("com.axelsarassamit.gx12.NavigationSecrets",true,target.getClassLoader());
            Object instance=secrets.getField("INSTANCE").get(null);
            if(operation.equals("importProviderOverrides")&&!added.isEmpty()){
                require("YES".equals(runner.argument("acknowledgeFreshTarget")),"RESTORE_ACK");
                JSONObject journal=new JSONObject().put("schema",1).put("pin",pin).put("target",target.getPackageName()).put("aliasExisted",store.containsAlias(ALIAS)).put("prefsExisted",prefFile.exists()).put("names",new JSONArray(added));
                AtomicFile atomic=new AtomicFile(state);FileOutputStream out=atomic.startWrite();
                try{out.write(journal.toString().getBytes(java.nio.charset.StandardCharsets.UTF_8));atomic.finishWrite(out);}catch(Exception e){atomic.failWrite(out);throw e;}
                for(String name:added)secrets.getMethod("save",Context.class,String.class,String.class).invoke(instance,target,name,values.getString(name));
                // commit waits for the target save() apply calls to reach disk.
                require(prefs.edit().commit(),"PROVIDER_COMMIT");
            }else require(added.isEmpty(),"PROVIDER_MISSING");
            Map<String,Object> disk=Preferences.read(prefFile);
            require(disk.equals(prefs.getAll()),"PREF_CACHE_MISMATCH");
            for(String name:keys(values)){
                require(decrypt((String)disk.get(name),store).equals(values.getString(name)),"PROVIDER_VERIFY");
                require(values.getString(name).equals(secrets.getMethod("read",Context.class,String.class).invoke(instance,target,name)),"PROVIDER_VERIFY");
            }
            if(state.exists())require(state.delete(),"JOURNAL_IO");
            result.putString("status",operation.equals("importProviderOverrides")?"IMPORTED":"VERIFIED");result.putInt("providersVerified",2);
        }
    }
    private static Set<String> keysOf(Map<String,?> map){return map.keySet();}
}
