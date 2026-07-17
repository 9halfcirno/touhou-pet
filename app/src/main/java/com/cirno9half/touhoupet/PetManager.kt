package com.cirno9half.touhoupet

import android.content.Context
import android.util.Log
import org.json.JSONObject
import java.io.File
import java.io.IOException
import kotlin.io.path.Path
import kotlin.io.path.div

object PetManager {
    val petList = mutableMapOf<String, PetInfo>()
    private var assetPet = emptyArray<String>()
    private var externalPet = emptyArray<String>()

    fun loadPetList(context: Context) {
        // 先读assets目录的pet
        assetPet = try {
            context.assets.list("pets") ?: emptyArray()
        } catch (e: java.io.IOException) {
            emptyArray()
        }
        for (file in assetPet) {
            val petInfo = readInfo(context, true, ("pets/$file"))
            petList[petInfo.id] = petInfo
        }

        // 读外部目录的pet
        val extraPath = context.getExternalFilesDir(null)?.path
        if (extraPath != null) {
            val petPath = (Path(extraPath) / "pets")
            // 适配API 24的写法
            externalPet = try {
                File(petPath.toString()).listFiles { it.isDirectory }
                    ?.map { it.name }
                    ?.toTypedArray()
                    ?: emptyArray()
            } catch (e: IOException) {
                emptyArray()
            }

            for (file in externalPet) {
                val filePath = (petPath / file)
                val petInfo = readInfo(context, false, filePath.toString())
                petList[petInfo.id] = petInfo
            }
        }
    }

    private fun readInfo(context: Context, fromAsset: Boolean, path: String): PetInfo {
        val ins = makeStream(context, fromAsset, "$path/pet.json")
        val json = ins.bufferedReader().use { JSONObject(it.readText()) }
        return PetInfo(path, fromAsset, json)
    }
}