package com.engine.protoc.openapi.nativeimage

import org.graalvm.nativeimage.hosted.Feature
import org.graalvm.nativeimage.hosted.RuntimeReflection
import org.graalvm.nativeimage.hosted.RuntimeResourceAccess
import java.util.Locale

/*
 * Plugs holes in the agent-recorded native-image metadata for runtime lookups
 * the example suites don't exercise but real consumers do.
 *
 * Three categories so far:
 *
 *  1. Phantom `META-INF/services/<iface>` files. `kotlin-reflect` 2.3.20 ships
 *     `ModuleVisibilityHelper.class` but no provider file. On the JVM
 *     `ServiceLoader` silently returns no providers; under native-image with
 *     `-H:ThrowMissingRegistrationErrors=` the lookup throws
 *     `MissingResourceRegistrationError`. A pattern in `resource-config.json`
 *     does not help — native-image only honors patterns that match a real
 *     file at image-build time, and the two-arg
 *     `RuntimeResourceAccess.addResource(Module, String)` form does not record
 *     a strict-mode-visible negative query in GraalVM 23.1.2. Registering an
 *     empty payload via the three-arg form lets `ServiceLoader` parse zero
 *     providers, matching the JVM outcome.
 *
 *     `slf4j-api` 2.x is no longer in this category — `log4j-slf4j2-impl`
 *     ships a real `META-INF/services/org.slf4j.spi.SLF4JServiceProvider`
 *     that binds SLF4J to Log4j 2, so the entry must not be registered as
 *     empty here (it would shadow the binding).
 *
 *  2. Phantom class-as-resource probes. SLF4J 2.x falls back to
 *     `getResources("org/slf4j/impl/StaticLoggerBinder.class")` to detect a
 *     pre-2.0 binding that does not exist in this build. Same empty-payload
 *     treatment.
 *
 *  3. `ResourceBundleMessageSource` lookups in the OAS validator. Two distinct
 *     strict-mode failures arrive on the same code path: `ResourceBundle`'s
 *     default `Control` first probes for a class with the bundle's name
 *     (`jsv-messages`, `jsv-messages_en`, `jsv-messages_en_US`) before reading
 *     the `.properties` file. `Class.forName` of a missing name throws
 *     `MissingReflectionRegistrationError`; the `_en_US` properties candidate
 *     is also a phantom (the validator ships `_en` and the base bundle only).
 *     We register each candidate class for lookup (so `Class.forName` returns
 *     `null` instead of throwing) and pre-bind the bundle for `Locale.US` so
 *     GraalVM caches the full candidate chain.
 */
public class PhantomServiceFilesFeature : Feature {
    override fun beforeAnalysis(access: Feature.BeforeAnalysisAccess) {
        PHANTOM_RESOURCES.forEach { (anchorClass, resourcePath) ->
            val module = access.findClassByName(anchorClass)?.module ?: return@forEach
            RuntimeResourceAccess.addResource(module, resourcePath, EMPTY)
        }
        BUNDLE_CLASS_LOOKUPS.forEach { className ->
            RuntimeReflection.registerClassLookup(className)
        }
        val validatorModule =
            access.findClassByName("com.networknt.schema.i18n.ResourceBundleMessageSource")?.module
        if (validatorModule != null) {
            RuntimeResourceAccess.addResourceBundle(
                validatorModule,
                "jsv-messages",
                arrayOf(Locale.US, Locale.ENGLISH, Locale.ROOT),
            )
        }
    }

    private companion object {
        private val EMPTY = ByteArray(0)
        private val PHANTOM_RESOURCES =
            listOf(
                "kotlin.reflect.jvm.internal.impl.util.ModuleVisibilityHelper" to
                    "META-INF/services/kotlin.reflect.jvm.internal.impl.util.ModuleVisibilityHelper",
                "org.slf4j.LoggerFactory" to "org/slf4j/impl/StaticLoggerBinder.class",
                // ResourceBundle.getBundle("jsv-messages", Locale.US) probes
                // `_en_US` before falling back to `_en` / base. The validator
                // ships `_en` and base only, so the `_en_US` candidate is a
                // phantom — register empty bytes so ResourceBundle parses it
                // as an empty bundle and falls through to the populated `_en`
                // parent.
                "com.networknt.schema.i18n.ResourceBundleMessageSource" to
                    "jsv-messages_en_US.properties",
            )
        private val BUNDLE_CLASS_LOOKUPS =
            listOf(
                "jsv-messages",
                "jsv-messages_en",
                "jsv-messages_en_US",
            )
    }
}
