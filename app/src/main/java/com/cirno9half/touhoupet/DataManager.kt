package com.cirno9half.touhoupet

import org.json.JSONObject
import java.io.File

/**
 * 数据管理器 —— 用于管理 JSON 数据的加载、保存和键值存取
 *
 * 支持从文件系统或 assets 加载 JSON 文件，将数据保存到文件系统，
 * 以及通过 get/set 方法操作内存中的键值数据。
 *
 * @param path 默认数据文件路径（可以是 assets 路径或文件系统路径）
 */
class DataManager(val path: String) {

    // 内部 JSON 数据存储
    private val data = JSONObject()

    // ==================== JSON 加载 / 保存 ====================

    /**
     * 从指定路径加载 JSON 数据到内存中。
     *
     * 加载策略：
     * 1. 优先从文件系统读取（若文件存在）
     * 2. 若文件不存在且提供了 Context，尝试从 assets 目录读取
     * 3. 若两者都失败，不清空现有数据
     *
     * @param path    要加载的文件路径（默认使用构造时传入的 path）
     */
    fun load(path: String = this.path) {
        val text: String?

        when {
            // 1️⃣ 从文件系统加载
            File(path).exists() -> {
                text = File(path).readText()
            }
            else -> text = null
        }

        // 解析并加载（非空时才替换数据）
        if (!text.isNullOrBlank()) {
            val json = JSONObject(text)
            // 清空旧数据
            clearData()
            // 填充新数据
            val keys = json.keys()
            while (keys.hasNext()) {
                val key = keys.next()
                data.put(key, json.get(key))
            }
        }
    }

    /**
     * 从 JSON 字符串加载数据到内存中（替换现有数据）。
     */
    fun fromJson(jsonString: String) {
        val json = JSONObject(jsonString)
        clearData()
        val keys = json.keys()
        while (keys.hasNext()) {
            val key = keys.next()
            data.put(key, json.get(key))
        }
    }

    /**
     * 将内存中的数据保存为格式化的 JSON 文件。
     *
     * @param path 保存路径（默认使用构造时传入的 path）
     */
    fun save(path: String = this.path) {
        val file = File(path)
        file.parentFile?.mkdirs()
        file.writeText(data.toString(2))
        // 你说要是手机空间不足怎么办, 我不到啊!
    }

    /**
     * 导出内存中的数据为格式化的 JSON 字符串。
     */
    fun toJson(): String = data.toString(2)

    // ==================== 设置方法 ====================

    fun setInt(key: String, value: Int) {
        data.put(key, value)
    }

    fun setString(key: String, value: String) {
        data.put(key, value)
    }

    fun setBoolean(key: String, value: Boolean) {
        data.put(key, value)
    }

    fun setLong(key: String, value: Long) {
        data.put(key, value)
    }

    fun setDouble(key: String, value: Double) {
        data.put(key, value)
    }

    fun setNumber(key: String, value: Double) {
        data.put(key, value)
    }

    fun setNull(key: String) {
        data.put(key, JSONObject.NULL)
    }

    fun delete(key: String) {
        data.remove(key)
    }

    /**
     * 设置任意类型值。可存储 JSONObject、JSONArray 等复合对象。
     */
    fun set(key: String, value: Any?) {
        data.put(key, value)
    }

    // ==================== 获取方法 ====================

    fun get(key: String, fallback: Any?): Any? {
        val v = data.opt(key)
        return v ?: fallback
    }

    fun getNumber(key: String): Double {
        return data.optDouble(key, 0.0)
    }

    /**
     * 获取整数值，若键不存在则返回默认值。
     */
    fun getInt(key: String, default: Int = 0): Int =
        data.optInt(key, default)

    /**
     * 获取字符串值，若键不存在则返回默认值。
     */
    fun getString(key: String, default: String = ""): String =
        data.optString(key, default)

    /**
     * 获取布尔值，若键不存在则返回默认值。
     */
    fun getBoolean(key: String, default: Boolean = false): Boolean =
        data.optBoolean(key, default)

    /**
     * 获取长整数值，若键不存在则返回默认值。
     */
    fun getLong(key: String, default: Long = 0L): Long =
        data.optLong(key, default)

    /**
     * 获取浮点值，若键不存在则返回默认值。
     */
    fun getDouble(key: String, default: Double = 0.0): Double =
        data.optDouble(key, default)


    // ==================== 数据管理方法 ====================

    /**
     * 检查是否存在指定键。
     */
    fun has(key: String): Boolean = data.has(key)

    /**
     * 删除指定键及其对应的值。
     */
    fun remove(key: String) {
        data.remove(key)
    }

    /**
     * 清空内存中的所有数据。
     */
    fun clear() {
        clearData()
    }

    /**
     * 获取当前数据中所有键的集合。
     */
    fun keys(): Iterator<String?> = data.keys()

    /**
     * 获取当前数据条目数。
     */
    fun size(): Int = data.length()

    /**
     * 检查数据是否为空。
     */
    fun isEmpty(): Boolean = data.length() == 0

    // ==================== 私有辅助方法 ====================

    /**
     * 清空 data 中所有条目（JSONObject 没有 clear 方法，通过遍历删除实现）。
     */
    private fun clearData() {
        val it = data.keys()
        while (it.hasNext()) {
            it.next()
            it.remove()
        }
    }

    fun dispose() {
        save()
        clear()
    }
}
