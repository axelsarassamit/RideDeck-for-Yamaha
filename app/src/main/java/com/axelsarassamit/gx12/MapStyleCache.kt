package com.axelsarassamit.gx12

import android.content.Context
import android.security.keystore.KeyGenParameterSpec
import android.security.keystore.KeyProperties
import android.util.Base64
import org.json.JSONObject
import app.pillion.core.SolarMapTheme
import java.security.KeyStore
import java.security.MessageDigest
import javax.crypto.Cipher
import javax.crypto.KeyGenerator
import javax.crypto.SecretKey
import javax.crypto.spec.GCMParameterSpec

/** Two bounded encrypted style documents. Tile/glyph/sprite storage remains owned by MapLibre. */
object MapStyleCache {
    private const val ALIAS="navigation_map_style_cache"
    private const val MAX_BYTES=512*1024
    private fun field(dark:Boolean)=if(dark)"night" else "day"
    private fun identity(key:String)=MessageDigest.getInstance("SHA-256").digest(key.toByteArray())
        .joinToString(""){"%02x".format(it)}
    private fun key():SecretKey {
        val store=KeyStore.getInstance("AndroidKeyStore").apply{load(null)}
        (store.getKey(ALIAS,null) as? SecretKey)?.let{return it}
        return KeyGenerator.getInstance(KeyProperties.KEY_ALGORITHM_AES,"AndroidKeyStore").apply {
            init(KeyGenParameterSpec.Builder(ALIAS,KeyProperties.PURPOSE_ENCRYPT or KeyProperties.PURPOSE_DECRYPT)
                .setBlockModes(KeyProperties.BLOCK_MODE_GCM).setEncryptionPaddings(KeyProperties.ENCRYPTION_PADDING_NONE).build())
        }.generateKey()
    }
    @Synchronized fun read(c:Context,dark:Boolean,providerKey:String,now:Long=System.currentTimeMillis()):String? = runCatching {
        if(providerKey.isBlank())return null
        val encoded=c.getSharedPreferences("map_style_cache",0).getString(field(dark),null)?:return null
        if(encoded.length>MAX_BYTES*2)return null
        val parts=encoded.split(':');if(parts.size!=2)return null
        val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply {
            init(Cipher.DECRYPT_MODE,key(),GCMParameterSpec(128,Base64.decode(parts[0],Base64.NO_WRAP)))
        }
        val data=JSONObject(String(cipher.doFinal(Base64.decode(parts[1],Base64.NO_WRAP)),Charsets.UTF_8))
        if(!MapCachePolicy.reusable(data.getString("identity"),identity(providerKey+":"+SolarMapTheme.styleId(dark)),data.getLong("at"),data.getLong("expires"),now))return null
        data.getString("style").takeIf{it.toByteArray().size<=MAX_BYTES}
    }.getOrNull()

    @Synchronized fun save(c:Context,dark:Boolean,providerKey:String,style:String,at:Long,expires:Long) {
        if(providerKey.isBlank()||style.toByteArray().size>MAX_BYTES||!MapCachePolicy.reusable(identity(providerKey),identity(providerKey),at,expires,at))return
        runCatching {
            val data=JSONObject().put("identity",identity(providerKey+":"+SolarMapTheme.styleId(dark))).put("at",at).put("expires",expires).put("style",style)
            val cipher=Cipher.getInstance("AES/GCM/NoPadding").apply{init(Cipher.ENCRYPT_MODE,key())}
            val encrypted=cipher.doFinal(data.toString().toByteArray(Charsets.UTF_8))
            c.getSharedPreferences("map_style_cache",0).edit().putString(field(dark),
                Base64.encodeToString(cipher.iv,Base64.NO_WRAP)+":"+Base64.encodeToString(encrypted,Base64.NO_WRAP)).apply()
        }
    }
    fun remove(c:Context,dark:Boolean) { c.getSharedPreferences("map_style_cache",0).edit().remove(field(dark)).apply() }
}
