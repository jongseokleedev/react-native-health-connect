package dev.matinzd.healthconnect.records

import android.util.Log
import androidx.health.connect.client.aggregate.*
import androidx.health.connect.client.records.Record
import androidx.health.connect.client.request.*
import androidx.health.connect.client.response.*
import com.facebook.react.bridge.*
import dev.matinzd.healthconnect.utils.*
import java.lang.reflect.InvocationTargetException

class ReactHealthRecord {
  companion object {

    private fun <T : Record> createReactHealthRecordInstance(recordType: String?): ReactHealthRecordImpl<T> {
      val reactClass = reactRecordTypeToReactClassMap[recordType]
        ?: throw InvalidRecordType("Invalid record type: $recordType")

      return try {
        reactClass.getDeclaredConstructor().newInstance() as ReactHealthRecordImpl<T>
      } catch (e: Exception) {
        throw RuntimeException(
          "Failed to instantiate ReactHealthRecordImpl for type: $recordType",
          e
        )
      }
    }

    private fun <T : Record> createReactHealthRecordInstance(recordClass: Class<out Record>): ReactHealthRecordImpl<T> {
      val reactClass = healthConnectClassToReactClassMap[recordClass]
        ?: throw InvalidRecordType("Invalid record class: ${recordClass.simpleName}")

      return try {
        reactClass.getDeclaredConstructor().newInstance() as ReactHealthRecordImpl<T>
      } catch (e: Exception) {
        throw RuntimeException(
          "Failed to instantiate ReactHealthRecordImpl for class: ${recordClass.simpleName}",
          e
        )
      }
    }

    fun getRecordByType(recordType: String): Class<out Record>? {
      Log.d("HealthConnect", "getRecordByType() called with recordType: $recordType")
      Log.d("HealthConnect", "Available record types: ${reactRecordTypeToClassMap.keys}")

      return reactRecordTypeToClassMap[recordType]?.java?.also {
        Log.d("HealthConnect", "Returning record class: ${it.simpleName} for type: $recordType")
      } ?: run {
        Log.e("HealthConnect", "Invalid record type: $recordType")
        null
      }
    }

    fun parseWriteRecords(reactRecords: ReadableArray): List<Record> {
      val recordType = reactRecords.getMap(0).getString("recordType")
        ?: throw InvalidRecordType("Missing recordType in write request")

      val recordClass = createReactHealthRecordInstance<Record>(recordType)
      return recordClass.parseWriteRecord(reactRecords)
    }

    fun parseWriteResponse(response: InsertRecordsResponse?): WritableNativeArray {
      return WritableNativeArray().apply {
        response?.recordIdsList?.forEach { pushString(it) }
      }
    }

    fun parseReadRequest(recordType: String, reactRequest: ReadableMap): ReadRecordsRequest<*> {
      val recordClass = getRecordByType(recordType)
        ?: throw InvalidRecordType("Invalid record type: $recordType")

      return convertReactRequestOptionsFromJS(recordClass.kotlin, reactRequest)
    }

    fun parseAggregationResult(recordType: String, result: AggregationResult): WritableNativeMap {
      val recordClass = createReactHealthRecordInstance<Record>(recordType)
      return recordClass.parseAggregationResult(result)
    }

    fun parseAggregationResultGroupedByDuration(
      recordType: String,
      result: List<AggregationResultGroupedByDuration>
    ): WritableNativeArray {
      val recordClass = createReactHealthRecordInstance<Record>(recordType)
      return recordClass.parseAggregationResultGroupedByDuration(result)
    }

    fun parseAggregationResultGroupedByPeriod(
      recordType: String,
      result: List<AggregationResultGroupedByPeriod>
    ): WritableNativeArray {
      val recordClass = createReactHealthRecordInstance<Record>(recordType)
      return recordClass.parseAggregationResultGroupedByPeriod(result)
    }

    fun parseRecords(
      recordType: String,
      response: ReadRecordsResponse<out Record>
    ): WritableNativeMap {
      Log.d(
        "HealthConnect",
        "parseRecords() called with recordType: $recordType, response size: ${response.records.size}"
      )

      val recordClass = createReactHealthRecordInstance<Record>(recordType)
      return WritableNativeMap().apply {
        putString("pageToken", response.pageToken)
        putArray("records", WritableNativeArray().apply {
          response.records.forEach { record ->
            try {
              pushMap(recordClass.parseRecord(record))
              Log.d("HealthConnect", "Parsed record: $record")
            } catch (e: Exception) {
              Log.e("HealthConnect", "Error parsing record: $record", e)
            }
          }
        })
      }
    }

    fun parseRecord(
      recordType: String,
      response: ReadRecordResponse<out Record>
    ): WritableNativeMap {
      Log.d("HealthConnect", "parseRecord() called with recordType: $recordType")

      val recordClass = createReactHealthRecordInstance<Record>(recordType)
      return try {
        val parsedRecord = recordClass.parseRecord(response.record)
        Log.d("HealthConnect", "Successfully parsed record: ${response.record}")
        parsedRecord
      } catch (e: Exception) {
        Log.e("HealthConnect", "Error parsing single record: ${response.record}", e)
        WritableNativeMap()
      }
    }

    fun parseRecord(record: Record): WritableNativeMap {
      Log.d("HealthConnect", "parseRecord() called with record: $record")

      val reactRecordClass = createReactHealthRecordInstance<Record>(record.javaClass)
      return try {
        val reactRecord = reactRecordClass.parseRecord(record)
        val recordType = reactClassToReactTypeMap[reactRecordClass.javaClass]
        reactRecord.putString("recordType", recordType)
        Log.d("HealthConnect", "Successfully parsed record with type: $recordType")
        reactRecord
      } catch (e: Exception) {
        Log.e("HealthConnect", "Error parsing record: $record", e)
        WritableNativeMap()
      }
    }
  }
}
