package com.example.parser

import android.content.Context
import android.net.Uri
import android.util.Log
import com.example.data.MovieRecord
import org.xmlpull.v1.XmlPullParser
import org.xmlpull.v1.XmlPullParserFactory
import java.io.BufferedReader
import java.io.InputStream
import java.io.InputStreamReader
import java.util.zip.ZipInputStream

object FileImporter {
    private const val TAG = "FileImporter"

    fun importUri(context: Context, uri: Uri): List<MovieRecord> {
        val contentResolver = context.contentResolver
        val fileName = getFileName(context, uri).lowercase()

        return try {
            contentResolver.openInputStream(uri)?.use { inputStream ->
                if (fileName.endsWith(".csv") || fileName.endsWith(".txt")) {
                    parseCsv(inputStream)
                } else if (fileName.endsWith(".xlsx")) {
                    parseXlsx(inputStream)
                } else {
                    // Try parsing as CSV as fallback
                    parseCsv(inputStream)
                }
            } ?: emptyList()
        } catch (e: Exception) {
            Log.e(TAG, "Error importing file: ${e.message}", e)
            emptyList()
        }
    }

    private fun getFileName(context: Context, uri: Uri): String {
        var result: String? = null
        if (uri.scheme == "content") {
            val cursor = context.contentResolver.query(uri, null, null, null, null)
            try {
                if (cursor != null && cursor.moveToFirst()) {
                    val index = cursor.getColumnIndex(android.provider.OpenableColumns.DISPLAY_NAME)
                    if (index >= 0) {
                        result = cursor.getString(index)
                    }
                }
            } finally {
                cursor?.close()
            }
        }
        if (result == null) {
            result = uri.path
            val cut = result?.lastIndexOf('/') ?: -1
            if (cut != -1) {
                result = result?.substring(cut + 1)
            }
        }
        return result ?: "file.xlsx"
    }

    fun parseCsv(inputStream: InputStream): List<MovieRecord> {
        val reader = BufferedReader(InputStreamReader(inputStream, "UTF-8"))
        val records = mutableListOf<MovieRecord>()
        var line = reader.readLine() ?: return emptyList()

        val headers = parseCsvLine(line)
        
        var nameIdx = 1
        var sublinkIdx = 2
        var categoryIdx = 3
        var linkIdx = 4
        var pageUrlIdx = 5

        var matchedAny = false
        for (i in headers.indices) {
            val h = headers[i].trim().lowercase()
            if (h.contains("name")) { nameIdx = i; matchedAny = true }
            else if (h.contains("sublink")) { sublinkIdx = i; matchedAny = true }
            else if (h.contains("category")) { categoryIdx = i; matchedAny = true }
            else if ((h.contains("link") || h.contains("url")) && !h.contains("page")) { linkIdx = i; matchedAny = true }
            else if (h.contains("page") || h == "pageurl") { pageUrlIdx = i; matchedAny = true }
        }

        // If the first row was a header row, we skip it.
        // Otherwise, if no header matches, we treat the first row as data.
        if (matchedAny) {
            // First row was header, read next
            val nextLine = reader.readLine()
            if (nextLine != null) {
                line = nextLine
            } else {
                return emptyList()
            }
        }

        do {
            val row = parseCsvLine(line)
            if (row.isEmpty() || row.all { it.isEmpty() }) continue

            val name = row.getOrNull(nameIdx)?.trim() ?: ""
            if (name.isNotEmpty()) {
                val sublink = row.getOrNull(sublinkIdx)?.trim() ?: ""
                val category = row.getOrNull(categoryIdx)?.trim() ?: ""
                val link = row.getOrNull(linkIdx)?.trim() ?: ""
                val pageUrl = row.getOrNull(pageUrlIdx)?.trim() ?: ""

                records.add(
                    MovieRecord(
                        name = name,
                        sublink = sublink,
                        category = category,
                        link = link,
                        pageUrl = pageUrl
                    )
                )
            }
        } while (reader.readLine()?.also { line = it } != null)

        return records
    }

    private fun parseCsvLine(line: String): List<String> {
        val result = mutableListOf<String>()
        val current = StringBuilder()
        var inQuotes = false
        var i = 0
        while (i < line.length) {
            val c = line[i]
            if (c == '\"') {
                inQuotes = !inQuotes
            } else if (c == ',' && !inQuotes) {
                result.add(current.toString())
                current.setLength(0)
            } else {
                current.append(c)
            }
            i++
        }
        result.add(current.toString())
        return result.map { it.trim().removeSurrounding("\"") }
    }

    fun parseXlsx(inputStream: InputStream): List<MovieRecord> {
        val zipInputStream = ZipInputStream(inputStream)
        var sharedStrings = listOf<String>()
        var sheetBytes: ByteArray? = null
        var sharedStringsBytes: ByteArray? = null

        var zipEntry = zipInputStream.nextEntry
        while (zipEntry != null) {
            val name = zipEntry.name
            if (name == "xl/sharedStrings.xml") {
                sharedStringsBytes = zipInputStream.readBytes()
            } else if (name == "xl/worksheets/sheet1.xml") {
                sheetBytes = zipInputStream.readBytes()
            }
            zipInputStream.closeEntry()
            zipEntry = zipInputStream.nextEntry
        }

        if (sharedStringsBytes != null) {
            sharedStrings = parseSharedStrings(sharedStringsBytes.inputStream())
        }

        if (sheetBytes != null) {
            return parseSheet(sheetBytes.inputStream(), sharedStrings)
        }

        return emptyList()
    }

    private fun parseSharedStrings(inputStream: InputStream): List<String> {
        val strings = mutableListOf<String>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType
            val currentText = StringBuilder()
            var insideT = false

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "t") {
                            insideT = true
                            currentText.setLength(0)
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideT) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "t") {
                            strings.add(currentText.toString())
                            insideT = false
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing shared strings: ${e.message}", e)
        }
        return strings
    }

    private fun parseSheet(inputStream: InputStream, sharedStrings: List<String>): List<MovieRecord> {
        val records = mutableListOf<MovieRecord>()
        try {
            val factory = XmlPullParserFactory.newInstance()
            val parser = factory.newPullParser()
            parser.setInput(inputStream, "UTF-8")
            var eventType = parser.eventType

            var currentCellRef: String? = null
            var currentCellType: String? = null
            var insideValOrStr = false
            val currentText = StringBuilder()

            var rowData = mutableMapOf<String, String>()
            
            // Default mappings based on letters
            val colMap = mutableMapOf(
                "name" to "B",
                "sublink" to "C",
                "category" to "D",
                "link" to "E",
                "page" to "F"
            )
            var isFirstRow = true

            while (eventType != XmlPullParser.END_DOCUMENT) {
                val name = parser.name
                when (eventType) {
                    XmlPullParser.START_TAG -> {
                        if (name == "row") {
                            rowData.clear()
                        } else if (name == "c") {
                            currentCellRef = parser.getAttributeValue(null, "r")
                            currentCellType = parser.getAttributeValue(null, "t")
                        } else if (name == "v" || name == "t") {
                            insideValOrStr = true
                            currentText.setLength(0)
                        }
                    }
                    XmlPullParser.TEXT -> {
                        if (insideValOrStr) {
                            currentText.append(parser.text)
                        }
                    }
                    XmlPullParser.END_TAG -> {
                        if (name == "v" || name == "t") {
                            insideValOrStr = false
                        } else if (name == "c") {
                            val ref = currentCellRef
                            if (ref != null) {
                                val colLetter = ref.takeWhile { it.isLetter() }.uppercase()
                                val rawText = currentText.toString()
                                
                                val finalValue = if (currentCellType == "s") {
                                    val idx = rawText.toIntOrNull()
                                    if (idx != null && idx in sharedStrings.indices) {
                                        sharedStrings[idx]
                                    } else {
                                        rawText
                                    }
                                } else {
                                    rawText
                                }
                                rowData[colLetter] = finalValue
                            }
                            currentCellRef = null
                            currentCellType = null
                        } else if (name == "row") {
                            if (isFirstRow) {
                                var headerMatched = false
                                rowData.forEach { (col, value) ->
                                    val v = value.trim().lowercase()
                                    if (v.contains("name")) { colMap["name"] = col; headerMatched = true }
                                    else if (v.contains("sublink")) { colMap["sublink"] = col; headerMatched = true }
                                    else if (v.contains("category")) { colMap["category"] = col; headerMatched = true }
                                    else if ((v.contains("link") || v.contains("url")) && !v.contains("page")) { colMap["link"] = col; headerMatched = true }
                                    else if (v.contains("page") || v == "pageurl") { colMap["page"] = col; headerMatched = true }
                                }
                                isFirstRow = false
                                // If headers were matched, skip this row for records.
                                if (headerMatched) {
                                    eventType = parser.next()
                                    continue
                                }
                            }

                            val movieName = rowData[colMap["name"] ?: "B"]?.trim() ?: ""
                            if (movieName.isNotEmpty()) {
                                val sublink = rowData[colMap["sublink"] ?: "C"]?.trim() ?: ""
                                val category = rowData[colMap["category"] ?: "D"]?.trim() ?: ""
                                val link = rowData[colMap["link"] ?: "E"]?.trim() ?: ""
                                val pageUrl = rowData[colMap["page"] ?: "F"]?.trim() ?: ""

                                records.add(
                                    MovieRecord(
                                        name = movieName,
                                        sublink = sublink,
                                        category = category,
                                        link = link,
                                        pageUrl = pageUrl
                                    )
                                )
                            }
                        }
                    }
                }
                eventType = parser.next()
            }
        } catch (e: Exception) {
            Log.e(TAG, "Error parsing sheet XML: ${e.message}", e)
        }
        return records
    }
}
