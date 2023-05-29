package com.birdthedeveloper.prometheus.android.prometheus.android.exporter.compose

import androidx.work.Data
import androidx.work.workDataOf

//@Serializable
data class PromConfiguration(
    // the following are default values for various configuration settings
    val prometheusServerEnabled : Boolean = true,
    val prometheusServerPort : Int = 10101, //TODO register with prometheus foundation
    val pushproxEnabled : Boolean = false,
    val pushproxFqdn : String? = null,
    val pushproxProxyUrl : String? = null,
    val remoteWriteEnabled : Boolean = false,
    val remoteWriteScrapeInterval : Int = 30,
    val remoteWriteEndpoint : String? = null,
) {
    private val filepath : String = ""

    companion object {
        suspend fun configFileExists(): Boolean {
            //TODO implement this asap
            return false
        }

        fun fromWorkData(data : Data) : PromConfiguration{
            //TODO implement this
            return PromConfiguration(
                prometheusServerEnabled = data.getBoolean("0", true),
                //prometheusServerPort = data.getInt("1", )
            )
        }

        suspend fun loadFromConfigFile(): PromConfiguration {
            //TODO open file, parse yaml, throw exception possibly
            return PromConfiguration()
        }
    }

    fun toWorkData() : Data{
        return workDataOf(
            "0" to this.prometheusServerEnabled,
            "1" to this.prometheusServerPort,
            "2" to this.pushproxEnabled,
            "3" to this.pushproxFqdn,
            "4" to this.pushproxProxyUrl,
            "5" to this.remoteWriteEnabled,
            "6" to this.remoteWriteScrapeInterval,
            "7" to this.remoteWriteEndpoint,
        )
    }
}