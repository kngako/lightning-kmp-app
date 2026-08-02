/*
 * Copyright 2019 ACINQ SAS
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package fr.acinq.phoenix.utils

import android.content.Context
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream

object Logging {

    private const val LOGS_DIR = "logs"
    private const val CURRENT_LOG_FILE = "phoenix.log"
    private const val ARCHIVED_LOG_FILE = "phoenix.archive-%i.log"

    fun exportLogFile(context: Context): File {
        val export = File(File(context.filesDir, LOGS_DIR), "phoenix_export.log")
        val exportOutputStream = FileOutputStream(export)
        val exportChannel = exportOutputStream.channel

        // write archive-1 to export file, if available
        File(File(context.filesDir, LOGS_DIR), "phoenix.archive-1.log").takeIf {
            it.exists() && it.isFile && it.canRead()
        }?.let {
            FileInputStream(it)
        }?.also {
            val channel = it.channel
            channel.transferTo(0, channel.size(), exportChannel)
            channel.close()
        }?.close()

        // write current log file to export file, if available
        File(File(context.filesDir, LOGS_DIR), CURRENT_LOG_FILE).takeIf {
            it.exists() && it.isFile && it.canRead()
        }?.let {
            FileInputStream(it)
        }?.also {
            val channel = it.channel
            channel.transferTo(0, channel.size(), exportChannel)
            channel.close()
        }?.close()

        exportChannel.close()
        exportOutputStream.close()
        return export
    }

}
