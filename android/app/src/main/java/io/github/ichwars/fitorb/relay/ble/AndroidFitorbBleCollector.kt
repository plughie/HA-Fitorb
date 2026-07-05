package io.github.ichwars.fitorb.relay.ble

import android.Manifest
import android.annotation.SuppressLint
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothGatt
import android.bluetooth.BluetoothGattCallback
import android.bluetooth.BluetoothGattCharacteristic
import android.bluetooth.BluetoothGattDescriptor
import android.bluetooth.BluetoothManager
import android.bluetooth.BluetoothProfile
import android.bluetooth.BluetoothStatusCodes
import android.bluetooth.le.ScanCallback
import android.bluetooth.le.ScanResult
import android.content.Context
import android.content.pm.PackageManager
import android.os.Build
import androidx.core.content.ContextCompat
import io.github.ichwars.fitorb.relay.data.RelaySampleDto
import java.time.Instant
import java.time.LocalDate
import java.time.ZoneOffset
import java.util.UUID
import kotlinx.coroutines.CompletableDeferred
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.withTimeout
import kotlinx.coroutines.withTimeoutOrNull

private val CLIENT_CONFIG_UUID: UUID = UUID.fromString("00002902-0000-1000-8000-00805F9B34FB")
private val UART_SERVICE_UUID: UUID = UUID.fromString(FitorbHistoryProtocol.NORDIC_UART_SERVICE_UUID)
private val UART_WRITE_UUID: UUID = UUID.fromString(FitorbHistoryProtocol.NORDIC_UART_WRITE_UUID)
private val UART_NOTIFY_UUID: UUID = UUID.fromString(FitorbHistoryProtocol.NORDIC_UART_NOTIFY_UUID)
private val BIG_DATA_SERVICE_UUID: UUID = UUID.fromString(FitorbHistoryProtocol.COLMI_BIG_DATA_SERVICE_UUID)
private val BIG_DATA_WRITE_UUID: UUID = UUID.fromString(FitorbHistoryProtocol.COLMI_BIG_DATA_WRITE_UUID)
private val BIG_DATA_NOTIFY_UUID: UUID = UUID.fromString(FitorbHistoryProtocol.COLMI_BIG_DATA_NOTIFY_UUID)

class FitorbBleCollectionException(
    message: String,
    cause: Throwable? = null,
) : RuntimeException(message, cause)

class AndroidFitorbBleCollector(
    context: Context,
    private val scanTimeoutMillis: Long = 10_000,
    private val operationTimeoutMillis: Long = 6_000,
) : FitorbBleCollector {
    private val appContext = context.applicationContext

    override suspend fun collectOnce(ringId: String): List<RelaySampleDto> {
        val normalizedRingId = ringId.trim()
        require(normalizedRingId.isNotEmpty()) { "Ring ID required" }
        val adapter = bluetoothAdapter()
        if (!adapter.isEnabled) {
            throw FitorbBleCollectionException("Bluetooth is disabled")
        }
        val device = findDevice(adapter, normalizedRingId)
        val capturedAt = Instant.now()
        return FitorbGattSession(appContext, device, operationTimeoutMillis).use { session ->
            session.connect()
            session.collectSamples()
        }.map { sample ->
            sample.toRelaySampleDto(normalizedRingId, capturedAt)
        }
    }

    private fun bluetoothAdapter(): BluetoothAdapter {
        val manager = appContext.getSystemService(BluetoothManager::class.java)
            ?: throw FitorbBleCollectionException("Bluetooth manager unavailable")
        return manager.adapter ?: throw FitorbBleCollectionException("Bluetooth adapter unavailable")
    }

    @SuppressLint("MissingPermission")
    private suspend fun findDevice(adapter: BluetoothAdapter, ringId: String): BluetoothDevice {
        ensureConnectPermission()
        if (BluetoothAdapter.checkBluetoothAddress(ringId)) {
            return adapter.getRemoteDevice(ringId)
        }
        ensureScanPermission()
        val scanner = adapter.bluetoothLeScanner
            ?: throw FitorbBleCollectionException("Bluetooth LE scanner unavailable")
        val found = CompletableDeferred<BluetoothDevice>()
        val callback = object : ScanCallback() {
            override fun onScanResult(callbackType: Int, result: ScanResult) {
                val device = result.device ?: return
                val name = result.scanRecord?.deviceName ?: device.name.orEmpty()
                val acceptsAnyColmiRing = ringId.equals("R02", ignoreCase = true) ||
                    ringId.equals("COLMI", ignoreCase = true)
                if (device.address.equals(ringId, ignoreCase = true) ||
                    name.equals(ringId, ignoreCase = true) ||
                    name.startsWith(ringId, ignoreCase = true) ||
                    (acceptsAnyColmiRing && name.startsWith("R02_", ignoreCase = true)) ||
                    (acceptsAnyColmiRing && name.contains("COLMI", ignoreCase = true))
                ) {
                    found.complete(device)
                }
            }

            override fun onScanFailed(errorCode: Int) {
                found.completeExceptionally(FitorbBleCollectionException("BLE scan failed: $errorCode"))
            }
        }
        scanner.startScan(callback)
        return try {
            withTimeout(scanTimeoutMillis) { found.await() }
        } finally {
            scanner.stopScan(callback)
        }
    }

    private fun ensureConnectPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_CONNECT) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw FitorbBleCollectionException("Bluetooth connect permission missing")
        }
    }

    private fun ensureScanPermission() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S &&
            ContextCompat.checkSelfPermission(appContext, Manifest.permission.BLUETOOTH_SCAN) !=
            PackageManager.PERMISSION_GRANTED
        ) {
            throw FitorbBleCollectionException("Bluetooth scan permission missing")
        }
    }
}

private class FitorbGattSession(
    private val context: Context,
    private val device: BluetoothDevice,
    private val timeoutMillis: Long,
) : AutoCloseable {
    private var gatt: BluetoothGatt? = null
    private var connected = CompletableDeferred<Unit>()
    private var servicesDiscovered = CompletableDeferred<Unit>()
    private var characteristicWrite: CompletableDeferred<Unit>? = null
    private var descriptorWrite: CompletableDeferred<Unit>? = null
    private val uartNotifications = Channel<ByteArray>(Channel.UNLIMITED)
    private val bigDataNotifications = Channel<ByteArray>(Channel.UNLIMITED)

    private var uartWrite: BluetoothGattCharacteristic? = null
    private var uartNotify: BluetoothGattCharacteristic? = null
    private var bigDataWrite: BluetoothGattCharacteristic? = null
    private var bigDataNotify: BluetoothGattCharacteristic? = null

    private val callback = object : BluetoothGattCallback() {
        override fun onConnectionStateChange(gatt: BluetoothGatt, status: Int, newState: Int) {
            if (status != BluetoothGatt.GATT_SUCCESS) {
                connected.completeExceptionally(FitorbBleCollectionException("GATT connection failed: $status"))
                return
            }
            if (newState == BluetoothProfile.STATE_CONNECTED) {
                connected.complete(Unit)
            } else if (newState == BluetoothProfile.STATE_DISCONNECTED) {
                uartNotifications.close()
                bigDataNotifications.close()
            }
        }

        override fun onServicesDiscovered(gatt: BluetoothGatt, status: Int) {
            if (status == BluetoothGatt.GATT_SUCCESS) {
                servicesDiscovered.complete(Unit)
            } else {
                servicesDiscovered.completeExceptionally(
                    FitorbBleCollectionException("GATT service discovery failed: $status"),
                )
            }
        }

        @Deprecated("Deprecated on Android 13 but still called by older devices")
        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
        ) {
            characteristic.value?.let { routeNotification(characteristic.uuid, it) }
        }

        override fun onCharacteristicChanged(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            value: ByteArray,
        ) {
            routeNotification(characteristic.uuid, value)
        }

        override fun onCharacteristicWrite(
            gatt: BluetoothGatt,
            characteristic: BluetoothGattCharacteristic,
            status: Int,
        ) {
            completeGattOperation(characteristicWrite, status, "characteristic write")
            characteristicWrite = null
        }

        override fun onDescriptorWrite(
            gatt: BluetoothGatt,
            descriptor: BluetoothGattDescriptor,
            status: Int,
        ) {
            completeGattOperation(descriptorWrite, status, "descriptor write")
            descriptorWrite = null
        }
    }

    @SuppressLint("MissingPermission")
    suspend fun connect() {
        connected = CompletableDeferred()
        servicesDiscovered = CompletableDeferred()
        gatt = device.connectGatt(context, false, callback, BluetoothDevice.TRANSPORT_LE)
            ?: throw FitorbBleCollectionException("Unable to create GATT connection")
        withTimeout(timeoutMillis) { connected.await() }
        val activeGatt = requireGatt()
        if (!activeGatt.discoverServices()) {
            throw FitorbBleCollectionException("Unable to start GATT service discovery")
        }
        withTimeout(timeoutMillis) { servicesDiscovered.await() }
        bindCharacteristics(activeGatt)
        enableNotification(requireNotNull(uartNotify) { "UART notify characteristic unavailable" })
        bigDataNotify?.let { enableNotification(it) }
    }

    suspend fun collectSamples(): List<RingCollectedSample> {
        val samples = mutableListOf<RingCollectedSample>()
        readBattery()?.let { samples += it }
        samples += readActivityHistory()
        samples += readHeartRateHistory()
        samples += readSleepHistory()
        return samples
    }

    @SuppressLint("MissingPermission")
    override fun close() {
        try {
            gatt?.disconnect()
        } finally {
            gatt?.close()
            gatt = null
            uartNotifications.close()
            bigDataNotifications.close()
        }
    }

    private fun bindCharacteristics(gatt: BluetoothGatt) {
        val uart = gatt.getService(UART_SERVICE_UUID)
            ?: throw FitorbBleCollectionException("Ring UART service not found")
        uartWrite = uart.getCharacteristic(UART_WRITE_UUID)
        uartNotify = uart.getCharacteristic(UART_NOTIFY_UUID)
        val bigData = gatt.getService(BIG_DATA_SERVICE_UUID)
        bigDataWrite = bigData?.getCharacteristic(BIG_DATA_WRITE_UUID)
        bigDataNotify = bigData?.getCharacteristic(BIG_DATA_NOTIFY_UUID)
        if (uartWrite == null || uartNotify == null) {
            throw FitorbBleCollectionException("Ring UART characteristics not found")
        }
    }

    private suspend fun readBattery(): List<RingCollectedSample>? {
        writeUart(FitorbProtocol.buildCommand("03"))
        val parsed = readUartParsed {
            it is ParsedRingPacket.Battery
        } ?: return null
        return parsed.toCollectedSamples(Instant.now())
    }

    private suspend fun readActivityHistory(): List<RingCollectedSample> {
        val sourceDay = LocalDate.now(ZoneOffset.UTC)
        val parser = ActivityHistoryParser(sourceDay)
        writeUart(FitorbHistoryProtocol.buildActivityHistoryCommand(dayOffset = 0))
        return withTimeoutOrNull(timeoutMillis) {
            while (true) {
                parser.consume(uartNotifications.receive())?.let { return@withTimeoutOrNull it }
            }
            emptyList()
        } ?: emptyList()
    }

    private suspend fun readHeartRateHistory(): List<RingCollectedSample> {
        val parser = HeartRateHistoryParser()
        writeUart(FitorbHistoryProtocol.buildHeartRateHistoryCommand(LocalDate.now(ZoneOffset.UTC)))
        return withTimeoutOrNull(timeoutMillis * 2) {
            while (true) {
                parser.consume(uartNotifications.receive())?.let { return@withTimeoutOrNull it }
            }
            emptyList()
        } ?: parser.finishPartial()
    }

    private suspend fun readSleepHistory(): List<RingCollectedSample> {
        val write = bigDataWrite ?: return emptyList()
        if (bigDataNotify == null) return emptyList()
        writeCharacteristic(write, FitorbHistoryProtocol.buildBigDataRequest(FitorbHistoryProtocol.BIG_DATA_SLEEP_ID))
        val parser = BigDataFrameParser()
        return withTimeoutOrNull(timeoutMillis * 2) {
            while (true) {
                val frames = parser.consume(bigDataNotifications.receive())
                val frame = frames.firstOrNull { it.dataId == FitorbHistoryProtocol.BIG_DATA_SLEEP_ID }
                if (frame != null) {
                    return@withTimeoutOrNull FitorbHistoryProtocol.parseSleepPayload(frame.payload).samples
                }
            }
            emptyList()
        } ?: emptyList()
    }

    private suspend fun readUartParsed(
        predicate: (ParsedRingPacket) -> Boolean,
    ): ParsedRingPacket? =
        withTimeoutOrNull(timeoutMillis) {
            while (true) {
                val parsed = FitorbProtocol.parseNotification(uartNotifications.receive())
                if (parsed != null && predicate(parsed)) {
                    return@withTimeoutOrNull parsed
                }
            }
            null
        }

    private suspend fun writeUart(payload: ByteArray) {
        writeCharacteristic(requireNotNull(uartWrite) { "UART write characteristic unavailable" }, payload)
    }

    @SuppressLint("MissingPermission")
    private suspend fun enableNotification(characteristic: BluetoothGattCharacteristic) {
        val activeGatt = requireGatt()
        if (!activeGatt.setCharacteristicNotification(characteristic, true)) {
            throw FitorbBleCollectionException("Unable to enable local notifications")
        }
        val descriptor = characteristic.getDescriptor(CLIENT_CONFIG_UUID)
            ?: throw FitorbBleCollectionException("Notification descriptor unavailable")
        descriptorWrite = CompletableDeferred()
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activeGatt.writeDescriptor(
                descriptor,
                BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            @Suppress("DEPRECATION")
            descriptor.value = BluetoothGattDescriptor.ENABLE_NOTIFICATION_VALUE
            @Suppress("DEPRECATION")
            activeGatt.writeDescriptor(descriptor)
        }
        if (!started) {
            descriptorWrite = null
            throw FitorbBleCollectionException("Unable to write notification descriptor")
        }
        withTimeout(timeoutMillis) { descriptorWrite?.await() }
    }

    @SuppressLint("MissingPermission")
    private suspend fun writeCharacteristic(
        characteristic: BluetoothGattCharacteristic,
        payload: ByteArray,
    ) {
        characteristicWrite = CompletableDeferred()
        val activeGatt = requireGatt()
        val started = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            activeGatt.writeCharacteristic(
                characteristic,
                payload,
                BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT,
            ) == BluetoothStatusCodes.SUCCESS
        } else {
            characteristic.writeType = BluetoothGattCharacteristic.WRITE_TYPE_DEFAULT
            @Suppress("DEPRECATION")
            characteristic.value = payload
            @Suppress("DEPRECATION")
            activeGatt.writeCharacteristic(characteristic)
        }
        if (!started) {
            characteristicWrite = null
            throw FitorbBleCollectionException("Unable to write characteristic")
        }
        withTimeout(timeoutMillis) { characteristicWrite?.await() }
    }

    private fun routeNotification(uuid: UUID, value: ByteArray) {
        val copy = value.copyOf()
        when (uuid) {
            UART_NOTIFY_UUID -> uartNotifications.trySend(copy)
            BIG_DATA_NOTIFY_UUID -> bigDataNotifications.trySend(copy)
        }
    }

    private fun completeGattOperation(
        deferred: CompletableDeferred<Unit>?,
        status: Int,
        label: String,
    ) {
        if (status == BluetoothGatt.GATT_SUCCESS) {
            deferred?.complete(Unit)
        } else {
            deferred?.completeExceptionally(FitorbBleCollectionException("GATT $label failed: $status"))
        }
    }

    private fun requireGatt(): BluetoothGatt =
        gatt ?: throw FitorbBleCollectionException("GATT connection unavailable")
}
