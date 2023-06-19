package com.birdthedeveloper.prometheus.android.prometheus.android.exporter.worker

import android.util.Log
import java.util.LinkedList
import java.util.Queue

// HashMap<List of labels including name, List of TimeSeries samples to this TimeSeries>
private typealias ConverterHashMap = HashMap<List<TimeSeriesLabel>, MutableList<TimeSeriesSample>>

class RemoteWriteSenderMemoryStorage : RemoteWriteSenderStorage() {

    private fun filterExpiredMetrics(metrics : MutableList<MetricsScrape>){
        val oldestMetricTimeMs : Long = System.currentTimeMillis() - maxMetricsAge * 1000
        var howManyMetricsRemove : Int = 0

        // count how many metrics to remove
        for (i in 0 until metrics.size){
            val scrape : MetricsScrape = metrics[i]
            if(scrape.timeSeriesList.isNotEmpty()){
                if(scrape.timeSeriesList.first().sample.timeStampMs < oldestMetricTimeMs){
                    howManyMetricsRemove++
                }else{
                    break; // I suppose scrapes were performed one after another
                }
            }
        }

        // remove metrics
        for (i in 1..howManyMetricsRemove){
            metrics.removeFirst()
        }
    }


    private fun hashMapEntryToProtobufTimeSeries(
        labels: List<TimeSeriesLabel>, samples: MutableList<TimeSeriesSample>
    ): TimeSeries {

        val timeSeriesBuilder: TimeSeries.Builder = TimeSeries.newBuilder()

        timeSeriesBuilder.addAllLabels(labels.map {
            it.toProtobufLabel()
        })

        timeSeriesBuilder.addAllSamples(samples.map {
            it.toProtobufSample()
        })

        return timeSeriesBuilder.build()
    }

    private fun hashmapToProtobufWriteRequest(hashMap: ConverterHashMap): WriteRequest {
        val writeRequestBuilder: WriteRequest.Builder = WriteRequest.newBuilder()

        for (entry in hashMap) {
            val timeSeries = hashMapEntryToProtobufTimeSeries(entry.key, entry.value)
            writeRequestBuilder.addTimeseries(timeSeries)
        }

        return writeRequestBuilder.build()
    }

    private fun processStorageTimeSeries(hashMap: ConverterHashMap, timeSeries: StorageTimeSeries){

        // add remote write label to labels
        // this label ensures timeseries uniqueness among those scraped by pushprox or promserver
        // and those scraped by Remote Write
        val labels: MutableList<TimeSeriesLabel> = timeSeries.labels.toMutableList()
        labels.add(remoteWriteLabel)
        val immutableLabels : List<TimeSeriesLabel> = labels.toList()

        if (hashMap[immutableLabels] == null) {
            // this time series does not yet exist
            hashMap[immutableLabels] = mutableListOf(timeSeries.sample)
        } else {
            // this time series already exists
            hashMap[immutableLabels]!!.add(timeSeries.sample)
        }
    }

    private fun metricsScrapeListToProtobuf(input: List<MetricsScrape>): WriteRequest {
        if (input.isEmpty()) {
            throw Exception("Input is empty!")
        }

        val hashmap: ConverterHashMap = HashMap()

        for (metricsScrape in input) {
            for (timeSeries in metricsScrape.timeSeriesList){
                processStorageTimeSeries(hashmap, timeSeries)
            }
        }

        val result: WriteRequest = hashmapToProtobufWriteRequest(hashmap)

        return result
    }


    private val data: Queue<MetricsScrape> = LinkedList()

    override fun getScrapedSamplesCompressedProtobuf(howMany: Int): ByteArray {
        if (howMany < 1) {
            throw IllegalArgumentException("howMany must be bigger than zero")
        }

        val scrapedMetrics: MutableList<MetricsScrape> = mutableListOf()
        for (i in 1..howMany) {
            val oneMetric: MetricsScrape? = data.poll()
            if (oneMetric == null) {
                break
            } else {
                scrapedMetrics.add(oneMetric)
            }
        }
        Log.d(TAG, "Getting scraped samples: ${scrapedMetrics.size} samples")

        filterExpiredMetrics(scrapedMetrics)

        val writeRequest: WriteRequest = this.metricsScrapeListToProtobuf(scrapedMetrics.toList())
        val bytes: ByteArray = writeRequest.toByteArray()
        return this.encodeWithSnappy(bytes)
    }

    override fun removeNumberOfScrapedSamples(number: Int) {
        if (number > 0) {
            for (i in 1..number) {
                if(data.isEmpty()){
                    break;
                }else{
                    data.remove()
                }
            }
        } else {
            throw IllegalArgumentException("number must by higher than 0")
        }
    }

    override fun writeScrapedSample(metricsScrape: MetricsScrape) {
        Log.d(TAG, "Writing scraped sample to storage")
        data.add(metricsScrape)
    }

    override fun isEmpty(): Boolean {
        return data.isEmpty()
    }

    override fun getLength(): Int {
        return data.count()
    }
}