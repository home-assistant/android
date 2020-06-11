package io.homeassistant.companion.android.common.dagger

import javax.inject.Scope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class AppScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class DataScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class DomainScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class PresenterScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class ReceiverScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class ServiceScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class NotificationScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class SensorScope

@Scope
@Retention(AnnotationRetention.RUNTIME)
annotation class ProviderScope
