package io.homeassistant.companion.android.common.sensors

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import dagger.multibindings.ElementsIntoSet
import dagger.multibindings.IntoSet
import io.homeassistant.companion.android.sensors.generated.GeneratedProvidesSensorCommon

/**
 * Hilt bindings for the `:common` sensor subsystem: the [SensorRepository], every `:common`
 * [SensorManager] contributed into the `Set<SensorManager>` multibinding, and the `:common`
 * slice of the `Set<BasicSensor>` catalog.
 *
 * This Hilt module is hand-written (not generated) on purpose: Hilt's KSP processor does not
 * reliably aggregate an `@InstallIn` module that is itself *generated* by another KSP processor (the
 * KSP2 round model doesn't feed it to Hilt). So the provides-sensor processor generates only a plain
 * data object ([GeneratedProvidesSensorCommon]), and this source module turns it into the binding.
 */
@Module
@InstallIn(SingletonComponent::class)
abstract class SensorModule {
    @Binds
    internal abstract fun bindSensorRepository(impl: SensorRepositoryImpl): SensorRepository

    @Binds @IntoSet
    abstract fun bindAndroidOsSensorManager(impl: AndroidOsSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindAudioSensorManager(impl: AudioSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindBatterySensorManager(impl: BatterySensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindBluetoothSensorManager(impl: BluetoothSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindDNDSensorManager(impl: DNDSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindDisplaySensorManager(impl: DisplaySensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindKeyguardSensorManager(impl: KeyguardSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindLastRebootSensorManager(impl: LastRebootSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindLastUpdateManager(impl: LastUpdateManager): SensorManager

    @Binds @IntoSet
    abstract fun bindLightSensorManager(impl: LightSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindMobileDataManager(impl: MobileDataManager): SensorManager

    @Binds @IntoSet
    abstract fun bindNetworkSensorManager(impl: NetworkSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindNextAlarmManager(impl: NextAlarmManager): SensorManager

    @Binds @IntoSet
    abstract fun bindNfcSensorManager(impl: NfcSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindPhoneStateSensorManager(impl: PhoneStateSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindPowerSensorManager(impl: PowerSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindPressureSensorManager(impl: PressureSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindProximitySensorManager(impl: ProximitySensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindStepsSensorManager(impl: StepsSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindStorageSensorManager(impl: StorageSensorManager): SensorManager

    @Binds @IntoSet
    abstract fun bindTimeZoneManager(impl: TimeZoneManager): SensorManager

    @Binds @IntoSet
    abstract fun bindTrafficStatsManager(impl: TrafficStatsManager): SensorManager

    companion object {
        @Provides
        @ElementsIntoSet
        fun commonProvidesSensors(): Set<SensorManager.BasicSensor> = GeneratedProvidesSensorCommon.sensors
    }
}
